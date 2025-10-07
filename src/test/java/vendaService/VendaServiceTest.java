package vendaService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.TituloTipo;
import net.originmobi.pdv.enumerado.VendaSituacao;
import net.originmobi.pdv.filter.VendaFilter;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.model.PagamentoTipo;
import net.originmobi.pdv.model.Pessoa;
import net.originmobi.pdv.model.Receber;
import net.originmobi.pdv.model.Titulo;
import net.originmobi.pdv.model.Usuario;
import net.originmobi.pdv.model.Venda;
import net.originmobi.pdv.model.VendaProduto;
import net.originmobi.pdv.repository.VendaRepository;
import net.originmobi.pdv.service.CaixaLancamentoService;
import net.originmobi.pdv.service.CaixaService;
import net.originmobi.pdv.service.PagamentoTipoService;
import net.originmobi.pdv.service.ParcelaService;
import net.originmobi.pdv.service.ProdutoService;
import net.originmobi.pdv.service.ReceberService;
import net.originmobi.pdv.service.UsuarioService;
import net.originmobi.pdv.service.VendaProdutoService;
import net.originmobi.pdv.service.VendaService;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendaService — testes unitarios")
class VendaServiceTest {

    @InjectMocks
    private VendaService vendaService;

    @Mock private VendaRepository vendas;
    @Mock private UsuarioService usuarios;
    @Mock private VendaProdutoService vendaProdutos;
    @Mock private PagamentoTipoService formaPagamentos;
    @Mock private CaixaService caixas;
    @Mock private ReceberService receberServ;
    @Mock private ParcelaService parcelas;
    @Mock private CaixaLancamentoService lancamentos;
    @Mock private TituloService tituloService;
    @Mock private CartaoLancamentoService cartaoLancamento;
    @Mock private ProdutoService produtos;

    @Test
    @DisplayName("abreVenda(): se venda não tiver código deve preencher campos padrão, buscar usuário e salvar")
    void testAbreVenda_SemCodigo_PreencheCamposBuscaUsuarioESalva() {

        Venda venda = new Venda();

        try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
                 mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {

            net.originmobi.pdv.singleton.Aplicacao aplicacaoMock = mock(net.originmobi.pdv.singleton.Aplicacao.class);
            app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacaoMock);
            when(aplicacaoMock.getUsuarioAtual()).thenReturn("natalia");

            Usuario usuario = new Usuario();
            usuario.setUser("natalia");
            when(usuarios.buscaUsuario("natalia")).thenReturn(usuario);

            when(vendas.save(any(Venda.class))).thenAnswer(inv -> {
                Venda v = inv.getArgument(0);
                v.setCodigo(123L);
                return v;
            });

            Long idGerado = vendaService.abreVenda(venda);

            assertEquals(123L, idGerado, "deve retornar o código atribuído pelo repository");
            assertEquals(VendaSituacao.ABERTA, venda.getSituacao(), "deve marcar como ABERTA");
            assertEquals(0.00, venda.getValor_produtos(), 0.0001, "deve iniciar valor_produtos em 0.00");
            assertNotNull(venda.getData_cadastro(), "deve preencher data_cadastro");
            assertSame(usuario, venda.getUsuario(), "deve vincular o usuário atual");

            verify(usuarios).buscaUsuario("natalia");
            verify(vendas).save(same(venda));
            verify(vendas, never()).updateDadosVenda(any(), any(), any());
        }
    }

    @Test
    @DisplayName("abreVenda(): se venda já possuir código deve chamar updateDadosVenda")
    void testAbreVenda_ExisteCodigo_AtualizaDados() {
        Venda venda = new Venda();
        venda.setCodigo(10L);
        Pessoa pessoa = new Pessoa();
        venda.setPessoa(pessoa);
        venda.setObservacao("obs");

        Long id = vendaService.abreVenda(venda);

        assertEquals(10L, id);
        verify(vendas).updateDadosVenda(eq(pessoa), eq("obs"), eq(10L));
        verify(vendas, never()).save(any());
    }

    @Test
    @DisplayName("busca(): se filter tiver código deve chamar findByCodigoIn")
    void testBusca_FilterTemCodigo_ChamaFindByCodigoIn() {
        VendaFilter filter = new VendaFilter();
        filter.setCodigo(123L);
        Pageable pageable = mock(Pageable.class);
        Page<Venda> page = mock(Page.class);
        when(vendas.findByCodigoIn(123L, pageable)).thenReturn(page);

        Page<Venda> result = vendaService.busca(filter, "ABERTA", pageable);

        assertSame(page, result);
        verify(vendas, never()).findBySituacaoEquals(any(), any());
    }

    @Test
    @DisplayName("busca(): se filter não tiver código deve chamar findBySituacaoEquals(ABERTA)")
    void testBusca_FilterSemCodigo_ChamaFindBySituacao() {
        VendaFilter filter = new VendaFilter();
        Pageable pageable = mock(Pageable.class);
        Page<Venda> page = mock(Page.class);
        when(vendas.findBySituacaoEquals(VendaSituacao.ABERTA, pageable)).thenReturn(page);

        Page<Venda> result = vendaService.busca(filter, "ABERTA", pageable);

        assertSame(page, result);
        verify(vendas).findBySituacaoEquals(eq(VendaSituacao.ABERTA), eq(pageable));
    }

    @Test
    @DisplayName("busca(): se situacao != 'ABERTA' deve chamar findBySituacaoEquals(FECHADA)")
    void testBusca_SituacaoFechada_ChamaFindBySituacaoFechada() {
        VendaFilter filter = new VendaFilter(); // sem código
        Pageable pageable = mock(Pageable.class);
        Page<Venda> page = mock(Page.class);
        when(vendas.findBySituacaoEquals(VendaSituacao.FECHADA, pageable)).thenReturn(page);

        Page<Venda> result = vendaService.busca(filter, "FECHADA", pageable);

        assertSame(page, result);
        verify(vendas).findBySituacaoEquals(eq(VendaSituacao.FECHADA), eq(pageable));
    }

    @Test
    @DisplayName("addProduto(): se venda estiver ABERTA deve salvar produto e retornar 'ok'")
    void testAddProduto_VendaAberta_SalvaERetornaOk() {
        when(vendas.verificaSituacao(1L)).thenReturn(VendaSituacao.ABERTA.toString());

        String r = vendaService.addProduto(1L, 2L, 10.5);

        assertEquals("ok", r);
        verify(vendaProdutos).salvar(any(VendaProduto.class));
    }

    @Test
    @DisplayName("addProduto(): se venda estiver FECHADA deve retornar 'Venda fechada'")
    void testAddProduto_VendaFechada_RetornaMensagem() {
        when(vendas.verificaSituacao(1L)).thenReturn(VendaSituacao.FECHADA.toString());

        String r = vendaService.addProduto(1L, 2L, 10.5);

        assertEquals("Venda fechada", r);
        verify(vendaProdutos, never()).salvar(any());
    }

    @Test
    @DisplayName("removeProduto(): se venda estiver ABERTA deve remover produto e retornar 'ok'")
    void testRemoveProduto_VendaAberta_RemoveERetornaOk() {
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(99L)).thenReturn(venda);
        when(venda.getSituacao()).thenReturn(VendaSituacao.ABERTA);

        String r = vendaService.removeProduto(3L, 99L);

        assertEquals("ok", r);
        verify(vendaProdutos).removeProduto(3L);
    }

    @Test
    @DisplayName("removeProduto(): se venda estiver FECHADA deve retornar 'Venda fechada'")
    void testRemoveProduto_VendaFechada_RetornaMensagem() {
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(99L)).thenReturn(venda);
        when(venda.getSituacao()).thenReturn(VendaSituacao.FECHADA);

        String r = vendaService.removeProduto(3L, 99L);

        assertEquals("Venda fechada", r);
        verify(vendaProdutos, never()).removeProduto(anyLong());
    }

    @Test
    @DisplayName("lista(): deve retornar vendas do repository (findAll)")
    void testLista_RetornaVendas() {
        List<Venda> todas = Arrays.asList(new Venda(), new Venda());
        when(vendas.findAll()).thenReturn(todas);

        assertEquals(todas, vendaService.lista());
    }

    @Test
    @DisplayName("qtdAbertos(): deve retornar total de vendas em aberto")
    void testQtdAbertos_RetornaTotalDeVendasEmAberto() {
        when(vendas.qtdVendasEmAberto()).thenReturn(42);
        assertEquals(42, vendaService.qtdAbertos());
    }

    @Test
    @DisplayName("fechaVenda(): se for à vista (00) com DIN e caixa aberto deve fechar venda, lançar no caixa e movimentar estoque")
    void testFechaVenda_fluxoVistaDinheiro_FechaVenda() {
        Long codVenda = 7L;
        Long codForma = 100L;
        double vlProdutos = 200.00;
        double desconto = 20.00;
        double acrescimo = 5.00;
        String[] vlParcelas = new String[] {"200.00"};
        String[] titulos = new String[] {"1"};

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tituloTipo = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tituloTipo);
        when(tituloTipo.getSigla()).thenReturn(TituloTipo.DIN.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(true);
        when(caixas.caixaAberto()).thenReturn(Optional.of(new Caixa()));

        try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app = mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
            net.originmobi.pdv.singleton.Aplicacao aplicacaoMock = mock(net.originmobi.pdv.singleton.Aplicacao.class);
            app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacaoMock);
            when(aplicacaoMock.getUsuarioAtual()).thenReturn("natalia");

            Usuario u = new Usuario();
            u.setUser("natalia");
            when(usuarios.buscaUsuario("natalia")).thenReturn(u);

            String msg = vendaService.fechaVenda(codVenda, codForma, vlProdutos, desconto, acrescimo, vlParcelas, titulos);

            assertEquals("Venda finalizada com sucesso", msg);
            double vlFinal = (vlProdutos + acrescimo) - desconto;
            verify(receberServ).cadastrar(any(Receber.class));
            verify(vendas, atLeastOnce()).fechaVenda(eq(codVenda), eq(VendaSituacao.FECHADA), eq(vlFinal),
                    eq(desconto), eq(acrescimo), any(), eq(forma));
            verify(produtos).movimentaEstoque(eq(codVenda), eq(net.originmobi.pdv.enumerado.EntradaSaida.SAIDA));
            verify(lancamentos).lancamento(any(CaixaLancamento.class));
            verify(parcelas, never()).gerarParcela(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                    any(Receber.class), anyInt(), anyInt(), any(), any());
            verify(cartaoLancamento, never()).lancamento(anyDouble(), any());
        }
    }

    @Test
    @DisplayName("fechaVenda(): se venda não estiver aberta deve retornar 'venda fechada'")
    void testFechaVenda_VendaFechada_RetornaMensagem() {
        Long codVenda = 1L;
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(codVenda, 10L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"}));
        assertEquals("venda fechada", ex.getMessage());
    }

    @Test
    @DisplayName("fechaVenda(): se valor de produtos <= 0 deve retornar 'Venda sem valor, verifique'")
    void testFechaVenda_ValorProdutoInvalido_RetornaMensagem() {
        Long codVenda = 1L;
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(codVenda, 10L, 0.0, 0.0, 0.0, new String[]{"0.0"}, new String[]{"1"}));
        assertEquals("Venda sem valor, verifique", ex.getMessage());
    }

    @Test
    @DisplayName("fechaVenda(): se for à vista (00) com DIN e caixa fechado deve retornar 'nenhum caixa aberto'")
    void testFechaVenda_CaixaFechadoAVistaDinheiro_RetornaMensagem() {
        Long codVenda = 2L;
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(20L)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tituloTipo = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tituloTipo);
        when(tituloTipo.getSigla()).thenReturn(TituloTipo.DIN.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(codVenda, 20L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"}));
        assertEquals("nenhum caixa aberto", ex.getMessage());
    }

    @Test
    @DisplayName("fechaVenda(): se for a prazo e venda não tiver cliente deve retornar 'Venda sem cliente, verifique'")
    void testFechaVenda_AprazoSemCliente_RetornaMensagem() {
        Long codVenda = 10L, codForma = 400L;
        String[] vlParcelas = {"100.00"};
        String[] titulos = {"1"};

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(null);

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("30");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);
        when(tituloService.busca(1L)).thenReturn(Optional.of(mock(Titulo.class)));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            vendaService.fechaVenda(codVenda, codForma, 100.0, 0.0, 0.0, vlParcelas, titulos)
        );
        assertEquals("Venda sem cliente, verifique", ex.getMessage());
    }

    @Test
    @DisplayName("fechaVenda(): se for a prazo deve gerar parcelas e fechar venda")
    void testFechaVenda_Aprazo_GeraParcelasEFecha() {
        Long codVenda = 9L, codForma = 300L;
        String[] vlParcelas = {"100.00", "100.00"};
        String[] titulos = {"1","2"};
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("30/60");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        when(tituloService.busca(anyLong())).thenReturn(Optional.of(mock(Titulo.class)));

        String msg = vendaService.fechaVenda(codVenda, codForma, 200.00, 0.0, 0.0, vlParcelas, titulos);

        assertEquals("Venda finalizada com sucesso", msg);
        verify(parcelas, atLeast(2)).gerarParcela(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(Receber.class), anyInt(), anyInt(), any(), any());
        verify(vendas, atLeastOnce()).fechaVenda(eq(codVenda), eq(VendaSituacao.FECHADA), eq(Double.valueOf(200.00)),
                eq(Double.valueOf(0.0)), eq(Double.valueOf(0.0)), any(java.sql.Timestamp.class), eq(forma));
        verify(produtos).movimentaEstoque(eq(codVenda), eq(EntradaSaida.SAIDA));
    }

    @Test
    @DisplayName("fechaVenda(): se for à vista (00) com CARTDEB/CARTCRED deve lançar no cartão")
    void testFechaVenda_Cartao_ChamaCartaoLancamento() {
        Long codVenda = 8L, codForma = 200L;
        String[] vlParcelas = {"150.00"};
        String[] titulos = {"1"};

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tt = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tt);
        when(tt.getSigla()).thenReturn(TituloTipo.CARTDEB.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        String msg = vendaService.fechaVenda(codVenda, codForma, 150.00, 0.0, 0.0, vlParcelas, titulos);

        assertEquals("Venda finalizada com sucesso", msg);
        verify(cartaoLancamento).lancamento(eq(150.00), any());
        verify(lancamentos, never()).lancamento(any(CaixaLancamento.class));
        verify(produtos).movimentaEstoque(eq(codVenda), eq(EntradaSaida.SAIDA));
    }

    @Test
    @DisplayName("fechaVenda(): se falhar ao cadastrar o Receber deve retornar 'Erro ao fechar a venda, chame o suporte'")
    void testFechaVenda_FalhaAoCadastrarReceber_RetornaErro() {
        Long codVenda = 55L, codForma = 500L;
        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        doThrow(new RuntimeException("falha qualquer"))
            .when(receberServ).cadastrar(any(Receber.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            vendaService.fechaVenda(codVenda, codForma, 100.0, 0.0, 0.0,
                new String[]{"100.0"}, new String[]{"1"})
        );
        assertEquals("Erro ao fechar a venda, chame o suporte", ex.getMessage());
    }

    @Test
    @DisplayName("fechaVenda(): se falhar ao fechar a venda deve retornar 'Erro ao fechar a venda, chame o suporte'")
    void testFechaVenda_FalhaAoFechar_RetornaErro() {
        Long codVenda = 66L, codForma = 600L;
        String[] vlParcelas = {"200.00"};
        String[] titulos = {"1"};

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tt = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tt);
        when(tt.getSigla()).thenReturn(TituloTipo.DIN.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(true);
        when(caixas.caixaAberto()).thenReturn(Optional.of(new Caixa()));

        try (MockedStatic<net.originmobi.pdv.singleton.Aplicacao> app =
                 mockStatic(net.originmobi.pdv.singleton.Aplicacao.class)) {
            net.originmobi.pdv.singleton.Aplicacao aplicacaoMock = mock(net.originmobi.pdv.singleton.Aplicacao.class);
            app.when(net.originmobi.pdv.singleton.Aplicacao::getInstancia).thenReturn(aplicacaoMock);
            when(aplicacaoMock.getUsuarioAtual()).thenReturn("tester");
            when(usuarios.buscaUsuario("tester")).thenReturn(new Usuario());

            doThrow(new RuntimeException("falhou fechar"))
                .when(vendas).fechaVenda(eq(codVenda), eq(VendaSituacao.FECHADA),
                                         anyDouble(), anyDouble(), anyDouble(),
                                         any(java.sql.Timestamp.class), eq(forma));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(codVenda, codForma, 200.0, 0.0, 0.0, vlParcelas, titulos)
            );
            assertEquals("Erro ao fechar a venda, chame o suporte", ex.getMessage());
        }
    }

    @Test
    @DisplayName("aprazo(): parcela vazia deve retornar 'valor de recebimento invalido'")
    void testAprazo_ParcelaVazia_RetornaMensagem() {
        Long codVenda = 82L, codForma = 820L;
        String[] vlParcelas = { "" };
        String[] titulos = { "1" };

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);

        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("30");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        when(tituloService.busca(1L)).thenReturn(Optional.of(mock(Titulo.class)));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            vendaService.fechaVenda(codVenda, codForma, 100.0, 0.0, 0.0, vlParcelas, titulos)
        );
        assertEquals("valor de recebimento invalido", ex.getMessage());
    }

    @Test
    @DisplayName("avistaDinheiro(): soma das parcelas diferente do total deve retornar mensagem de diferença")
    void testAvistaDinheiro_SomaParcelasDiferenteTotal_RetornaMensagem() {
        Long codVenda = 81L, codForma = 810L;
        String[] vlParcelas = { "50.00" };
        String[] titulos = { "1" };

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tt = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tt);
        when(tt.getSigla()).thenReturn(TituloTipo.DIN.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            vendaService.fechaVenda(codVenda, codForma, 100.0, 0.0, 0.0, vlParcelas, titulos)
        );
        assertEquals("Valor das parcelas diferente do valor total de produtos, verifique", ex.getMessage());

        verifyNoInteractions(lancamentos, cartaoLancamento, parcelas);
    }

    @Test
    @DisplayName("avistaDinheiro(): parcela vazia deve retornar 'Parcela sem valor, verifique'")
    void testAvistaDinheiro_ParcelaVazia_RetornaMensagem() {
        Long codVenda = 80L, codForma = 800L;
        String[] vlParcelas = { "" };
        String[] titulos = { "1" };

        Venda venda = mock(Venda.class);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);
        when(venda.isAberta()).thenReturn(true);
        when(venda.getPessoa()).thenReturn(new Pessoa());

        PagamentoTipo forma = new PagamentoTipo();
        forma.setCodigo(codForma);
        forma.setFormaPagamento("00");
        when(formaPagamentos.busca(codForma)).thenReturn(forma);

        Titulo titulo = mock(Titulo.class);
        net.originmobi.pdv.model.TituloTipo tt = mock(net.originmobi.pdv.model.TituloTipo.class);
        when(titulo.getTipo()).thenReturn(tt);
        when(tt.getSigla()).thenReturn(TituloTipo.DIN.toString());
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            vendaService.fechaVenda(codVenda, codForma, 100.0, 0.0, 0.0, vlParcelas, titulos)
        );
        assertEquals("Parcela sem valor, verifique", ex.getMessage());

        verifyNoInteractions(lancamentos, cartaoLancamento, parcelas);
    }

}
