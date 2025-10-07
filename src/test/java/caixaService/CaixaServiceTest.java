package caixaService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import net.originmobi.pdv.enumerado.caixa.CaixaTipo;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.filter.BancoFilter;
import net.originmobi.pdv.filter.CaixaFilter;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.model.Usuario;
import net.originmobi.pdv.repository.CaixaRepository;
import net.originmobi.pdv.service.CaixaLancamentoService;
import net.originmobi.pdv.service.CaixaService;
import net.originmobi.pdv.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaixaService — testes unitarios")
class CaixaServiceTest {

  @InjectMocks
  private CaixaService caixaService;

  @Mock private CaixaRepository caixas;
  @Mock private UsuarioService usuarios;
  @Mock private CaixaLancamentoService lancamentos;
  
  @Test
  @DisplayName("cadastro(): se tipo=CAIXA e já houver caixa aberto deve retornar 'Existe caixa de dias anteriores em aberto, favor verifique'")
  void cadastro_quandoCaixaJaAberto_RetornaErro() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.CAIXA);
    c.setValor_abertura(0.0);
    c.setDescricao("");

    when(caixas.caixaAberto()).thenReturn(Optional.of(new Caixa()));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.cadastro(c));
    assertEquals("Existe caixa de dias anteriores em aberto, favor verifique", ex.getMessage());
    verify(caixas, never()).save(any());
  }


  @Test
  @DisplayName("cadastro(): se valor_abertura < 0 deve retornar 'Valor informado é inválido'")
  void cadastro_quandoValorAberturaNegativo_RetornaErro() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.COFRE);
    c.setValor_abertura(-10.0);
    c.setDescricao("");

    RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.cadastro(c));
    assertEquals("Valor informado é inválido", ex.getMessage());
    verify(caixas, never()).save(any());
  }
  
  
  @Test
  @DisplayName("cadastro(): se tipo=BANCO deve normalizar agência/conta, definir descrição padrão e salvar sem lançamento quando abertura=0")
  void cadastro_BancoSemSaldoInicial_NormalizaESalvaSemLancamento() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.BANCO);
    c.setValor_abertura(0.0);
    c.setDescricao(""); 
    c.setAgencia("12-3/4");
    c.setConta(" 98.7-6 ");

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("ana");

      Usuario u = new Usuario();
      u.setCodigo(55L);
      u.setUser("ana");
      when(usuarios.buscaUsuario("ana")).thenReturn(u);

      when(caixas.save(any(Caixa.class))).thenAnswer(inv -> {
        Caixa saved = inv.getArgument(0);
        saved.setCodigo(999L);
        return saved;
      });

      Long id = caixaService.cadastro(c);

      assertEquals(999L, id);
      assertEquals("Banco", c.getDescricao());
      assertEquals("1234", c.getAgencia());
      assertEquals("9876", c.getConta());
      assertEquals(0.0, c.getValor_total());
      assertNotNull(c.getData_cadastro());
      assertSame(u, c.getUsuario());
      verify(lancamentos, never()).lancamento(any());
      verify(caixas).save(same(c));
    }
  }
  
  @Test
  @DisplayName("cadastro(): se valor_abertura > 0 deve realizar lançamento SALDOINICIAL")
  void cadastro_SaldoInicialPositivo_LancaSaldoInicial() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.COFRE);
    c.setValor_abertura(150.0);
    c.setDescricao("");

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("maria");

      Usuario u = new Usuario();
      u.setUser("maria");
      when(usuarios.buscaUsuario("maria")).thenReturn(u);

      when(caixas.save(any(Caixa.class))).thenAnswer(inv -> {
        Caixa saved = inv.getArgument(0);
        saved.setCodigo(1L);
        return saved;
      });

      Long id = caixaService.cadastro(c);

      assertEquals(1L, id);
      verify(lancamentos).lancamento(argThat(l -> 
          l.getTipo().equals(TipoLancamento.SALDOINICIAL) &&
          l.getEstilo().equals(EstiloLancamento.ENTRADA) &&
          Double.compare(l.getValor(), 150.0) == 0 &&
          l.getCaixa().orElse(null) == c &&
          l.getUsuario() == u
      ));
      verify(caixas).save(same(c));
    }
  }
  
  @Test
  @DisplayName("cadastro(): se salvar caixa der erro retorna 'Erro no processo de abertura, chame o suporte técnico'")
  void cadastro_FalhaAoSalvar_RetornaMensagem() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.COFRE);
    c.setValor_abertura(0.0);
    c.setDescricao("");

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("joao");

      when(usuarios.buscaUsuario("joao")).thenReturn(new Usuario());
      doThrow(new RuntimeException("boom")).when(caixas).save(any(Caixa.class));

      RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.cadastro(c));
      assertEquals("Erro no processo de abertura, chame o suporte técnico", ex.getMessage());
    }
  }
  
  @Test
  @DisplayName("cadastro(): se lançamento inicial falhar retorna 'Erro no processo, chame o suporte'")
  void cadastro_FalhaNoLancamentoInicial_RetornaMensagem() {
    Caixa c = new Caixa();
    c.setTipo(CaixaTipo.CAIXA);
    c.setValor_abertura(10.0);
    c.setDescricao("");

    when(caixas.caixaAberto()).thenReturn(Optional.empty());

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("bia");

      when(usuarios.buscaUsuario("bia")).thenReturn(new Usuario());
      when(caixas.save(any(Caixa.class))).thenAnswer(inv -> inv.getArgument(0));

      doThrow(new RuntimeException("lancamento falhou")).when(lancamentos).lancamento(any(CaixaLancamento.class));

      RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.cadastro(c));
      assertEquals("Erro no processo, chame o suporte", ex.getMessage());
    }
  }
  
  @Test
  @DisplayName("fechaCaixa(): se senha vazia retorna 'Favor, informe a senha'")
  void fechaCaixa_SenhaVazia_RetornaMensagem() {
    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("user");
      when(usuarios.buscaUsuario("user")).thenReturn(new Usuario());

      String msg = caixaService.fechaCaixa(1L, "");
      assertEquals("Favor, informe a senha", msg);
      verify(caixas, never()).findById(anyLong());
    }
  }

  @Test
  @DisplayName("fechaCaixa(): se senha correta e caixa em aberto deve fechar e salvar")
  void fechaCaixa_SenhaCorreta_FechaESalva() {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    String senha = "123";
    String hash = enc.encode(senha);

    Usuario u = new Usuario();
    u.setSenha(hash);

    Caixa c = new Caixa();
    c.setValor_total(123.45);
    when(caixas.findById(5L)).thenReturn(Optional.of(c));

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("user1");
      when(usuarios.buscaUsuario("user1")).thenReturn(u);

      when(caixas.save(any(Caixa.class))).thenAnswer(inv -> inv.getArgument(0));

      String msg = caixaService.fechaCaixa(5L, "123");

      assertEquals("Caixa fechado com sucesso", msg);
      assertNotNull(c.getData_fechamento());
      assertEquals(123.45, c.getValor_fechamento());
      verify(caixas).save(same(c));
    }
  }
  
  @Test
  @DisplayName("fechaCaixa(): se senha correta mas caixa já fechado retorna 'Caixa já esta fechado'")
  void fechaCaixa_JaFechado_RetornaErro() {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    Usuario u = new Usuario();
    u.setSenha(enc.encode("abc"));

    Caixa c = new Caixa();
    c.setValor_total(10.0);
    c.setData_fechamento(new java.sql.Timestamp(System.currentTimeMillis()));
    when(caixas.findById(3L)).thenReturn(Optional.of(c));

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("user2");
      when(usuarios.buscaUsuario("user2")).thenReturn(u);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.fechaCaixa(3L, "abc"));
      assertEquals("Caixa já esta fechado", ex.getMessage());
      verify(caixas, never()).save(any());
    }
  }
  
  @Test
  @DisplayName("fechaCaixa(): se senha incorreta deve retornar 'Senha incorreta, favor verifique'")
  void fechaCaixa_SenhaIncorreta_RetornaMensagem() {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    Usuario u = new Usuario();
    u.setSenha(enc.encode("certa"));

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("user3");
      when(usuarios.buscaUsuario("user3")).thenReturn(u);

      String msg = caixaService.fechaCaixa(10L, "errada");
      assertEquals("Senha incorreta, favor verifique", msg);
      verify(caixas, never()).findById(anyLong());
    }
  }
  
  @Test
  @DisplayName("fechaCaixa(): se ocorrer erro ao salvar caixa retorna 'Ocorreu um erro ao fechar o caixa, chame o suporte'")
  void fechaCaixa_FalhaAoSalvar_RetornaMensagem() {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    Usuario u = new Usuario();
    u.setSenha(enc.encode("pw"));

    Caixa c = new Caixa();
    when(caixas.findById(11L)).thenReturn(Optional.of(c));
    doThrow(new RuntimeException("x")).when(caixas).save(any(Caixa.class));

    try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
           mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
      net.originmobi.pdv.singleton.Aplicacao aplicacao = mock(net.originmobi.pdv.singleton.Aplicacao.class);
      app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacao);
      when(aplicacao.getUsuarioAtual()).thenReturn("user4");
      when(usuarios.buscaUsuario("user4")).thenReturn(u);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> caixaService.fechaCaixa(11L, "pw"));
      assertEquals("Ocorreu um erro ao fechar o caixa, chame o suporte", ex.getMessage());
    }
  }
  
  @Test
  @DisplayName("caixaIsAberto(): retorna true quando há caixa aberto")
  void caixaIsAberto_HaCaixaAberto_retornaTrue() {
    when(caixas.caixaAberto()).thenReturn(Optional.of(new Caixa()));
    assertTrue(caixaService.caixaIsAberto());
  }

  @Test
  @DisplayName("caixaIsAberto(): retorna false quando não há caixa aberto")
  void caixaIsAberto_NaoHaCaixaAberto_retornaFalse() {
    when(caixas.caixaAberto()).thenReturn(Optional.empty());
    assertFalse(caixaService.caixaIsAberto());
  }


  @Test
  @DisplayName("listaTodos(): retorna lista ordenada por código")
  void listaTodos_retornaListaOrdenadaPorCodigo() {
    List<Caixa> esperado = List.of(new Caixa());
    when(caixas.findByCodigoOrdenado()).thenReturn(esperado);
    assertEquals(esperado, caixaService.listaTodos());
  }

  @Test
  @DisplayName("caixaAberto(): retorna Optional do caixa aberto")
  void caixaAberto_retornaOptionalDoCaixa() {
    Optional<Caixa> opt = Optional.of(new Caixa());
    when(caixas.caixaAberto()).thenReturn(opt);
    assertEquals(opt, caixaService.caixaAberto());
  }

  @Test
  @DisplayName("caixasAbertos(): retorna lista de caixas em aberto")
  void caixasAbertos_retornaListaDeCaixasEmAberto() {
    List<Caixa> lista = List.of(new Caixa());
    when(caixas.caixasAbertos()).thenReturn(lista);
    assertEquals(lista, caixaService.caixasAbertos());
  }

  @Test
  @DisplayName("busca(): ao consultar por código retorna o Optional")
  void busca_CodigoInformado_retornaOptional() {
    Optional<Caixa> opt = Optional.of(new Caixa());
    when(caixas.findById(77L)).thenReturn(opt);

    assertEquals(opt, caixaService.busca(77L));
  }

  @Test
  @DisplayName("listaBancos(): retorna caixas do tipo BANCO")
  void listaBancos_retornaCaixasTipoBanco() {
    List<Caixa> bancos = List.of(new Caixa());
    when(caixas.buscaBancos(CaixaTipo.BANCO)).thenReturn(bancos);
    assertEquals(bancos, caixaService.listaBancos());
  }


  @Test
  @DisplayName("listaCaixasAbertosTipo(): retorna caixas do tipo informado")
  void listaCaixasAbertosTipo_retornaCaixasDoTipoInformado() {
    List<Caixa> lista = List.of(new Caixa());
    when(caixas.buscaCaixaTipo(CaixaTipo.COFRE)).thenReturn(lista);
    assertEquals(lista, caixaService.listaCaixasAbertosTipo(CaixaTipo.COFRE));
  }

  @Test
  @DisplayName("listarCaixas(): com data deve buscar por data normalizada")
  void listarCaixas_FiltroTemData_buscaPorData() {
    CaixaFilter f = new CaixaFilter();
    f.setData_cadastro("2025/10/05");
    List<Caixa> porData = List.of(new Caixa(), new Caixa());
    when(caixas.buscaCaixasPorDataAbertura(Date.valueOf("2025-10-05"))).thenReturn(porData);

    assertEquals(porData, caixaService.listarCaixas(f));
    verify(caixas).buscaCaixasPorDataAbertura(Date.valueOf("2025-10-05"));
    verify(caixas, never()).listaCaixasAbertos();
  }

  @Test
  @DisplayName("listarCaixas(): sem data deve retornar abertos")
  void listarCaixas_FiltroSemData_listaAbertos() {
    CaixaFilter f = new CaixaFilter(); 
    List<Caixa> abertos = List.of(new Caixa());
    when(caixas.listaCaixasAbertos()).thenReturn(abertos);

    assertEquals(abertos, caixaService.listarCaixas(f));
    verify(caixas).listaCaixasAbertos();
    verify(caixas, never()).buscaCaixasPorDataAbertura(any());
  }


  @Test
  @DisplayName("buscaCaixaUsuario(): se existir caixa aberto do usuário deve retornar Optional")
  void buscaCaixaUsuario_Existe_RetornaOptional() {
    Usuario u = new Usuario();
    u.setCodigo(888L);
    when(usuarios.buscaUsuario("rafa")).thenReturn(u);

    Caixa c = new Caixa();
    when(caixas.findByCaixaAbertoUsuario(888L)).thenReturn(c);

    Optional<Caixa> r = caixaService.buscaCaixaUsuario("rafa");
    assertTrue(r.isPresent());
    assertSame(c, r.get());
  }

  @Test
  @DisplayName("buscaCaixaUsuario(): se não existir caixa do usuário deve retornar vazio")
  void buscaCaixaUsuario_NaoExiste_RetornaVazio() {
    Usuario u = new Usuario();
    u.setCodigo(777L);
    when(usuarios.buscaUsuario("lia")).thenReturn(u);
    when(caixas.findByCaixaAbertoUsuario(777L)).thenReturn(null);

    Optional<Caixa> r = caixaService.buscaCaixaUsuario("lia");
    assertTrue(r.isEmpty());
  }


@Test
  @DisplayName("listaBancosAbertosTipoFilterBanco(): com data deve buscar por tipo/data")
  void listaBancosAbertosTipoFilterBanco_TemData_buscaPorTipoEData() {
    BancoFilter f = new BancoFilter();
    f.setData_cadastro("2025/01/02");
    List<Caixa> porData = List.of(new Caixa());

    when(caixas.buscaCaixaTipoData(CaixaTipo.BANCO, Date.valueOf("2025-01-02")))
        .thenReturn(porData);

    assertEquals(porData, caixaService.listaBancosAbertosTipoFilterBanco(CaixaTipo.BANCO, f));
    verify(caixas).buscaCaixaTipoData(CaixaTipo.BANCO, Date.valueOf("2025-01-02"));
    verify(caixas, never()).buscaCaixaTipo(any());
  }

  @Test
  @DisplayName("listaBancosAbertosTipoFilterBanco(): sem data deve buscar por BANCO")
  void listaBancosAbertosTipoFilterBanco_SemData_buscaPorBanco() {
    BancoFilter f = new BancoFilter();
    List<Caixa> porTipo = List.of(new Caixa(), new Caixa());

    when(caixas.buscaCaixaTipo(CaixaTipo.BANCO)).thenReturn(porTipo);

    assertEquals(porTipo, caixaService.listaBancosAbertosTipoFilterBanco(CaixaTipo.BANCO, f));
    verify(caixas).buscaCaixaTipo(CaixaTipo.BANCO);
    verify(caixas, never()).buscaCaixaTipoData(any(), any());
  }
  
}
