# pdv
Sistema de ERP web desenvolvido em Java com Spring Framework 

# Link do documento do Plano de Teste
https://docs.google.com/document/d/1yJ1HbryH37ubZKkmaHUzn8jFX23Ox9zNgjQn83tilfg/edit?usp=sharing

# Link do documento de Testes Manuais
https://docs.google.com/document/d/1yudkPkBs9H3UMxCwu5Lg5rLogHO5h49KsCxGhzkSer0/edit?usp=sharing

O teste manual realizado no Testlink está localizado dentro da pasta "artefatos" do projeto.

# Recursos
- Cadastro produtos/clientes/fornecedor
- Controle de estoque
- Gerenciar comandas
- Realizar venda
- Controle de fluxo de caixa
- Controle de pagar e receber
- Venda com cartões
- Gerenciar permissões de usuários por grupos
- Cadastrar novas formas de pagamentos
- Relatórios

# Instalação
Para instalar o sistema, você deve criar o banco de dado "pdv" no mysql e configurar o arquivo application.properties
com os dados do seu usuário root do mysql e rodar o projeto pelo Eclipse ou gerar o jar do mesmo e execultar.

# Logando no sistema
Para logar no sistema, use o usuário "gerente" e a senha "123".

# Tecnologias utilizadas
- Spring Framework 5
- Thymeleaf 3
- MySQL
- Hibernate
- FlyWay

# Execução com Docker
Para executar a aplicação utilizando o docker, utilize o seguinte comando na raiz do projeto:
```sh
docker compose up -d
```

# Link do repositório com o histórico dos commits:
https://github.com/izabel-souza/pdv-qualidade-teste
