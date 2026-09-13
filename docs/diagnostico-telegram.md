# Diagnóstico Telegram — 06/09/2026

Escopo: Telegram e seu executável de diagnóstico. Mapper do Hub, createLink,
serviços de persistência, deduplicação, Flyway e API oficial não foram alterados.

## Evidências do 404 anterior

O registro antigo no PostgreSQL contém `https://api.telegram.org/bot/sendMessage`.
Porém, o código recebido já concatenava o token e rejeitava token vazio antes
na chamada HTTP. Também substituía o token por uma máscara que não aparece
na mensagem persistida. O arquivo boot_log.txt estava vazio.
Assim, a origem exata da URL antiga (configuração ou versão executada) não
pode ser comprovada com os artefatos disponíveis. Não atribuir o erro a uma
variável vazia como fato confirmado.

## Correções

- TelegramService valida token e chat na construção; campos ausentes geram
  `TELEGRAM_BOT_TOKEN não configurado` ou `TELEGRAM_CHAT_ID não configurado`.
- A URI é construída uma vez como `https://api.telegram.org/bot<TOKEN>/sendMessage`.
  Caracteres que alterariam caminho, query ou fragmento são rejeitados.
- HTTP só confirma envio com status 2xx e JSON booleano `ok=true`.
- Erros HTTP preservam apenas o código; erros de transporte/parser não
  propagam URL, corpo remoto ou causa. Não há repetição automática.
- TelegramService e PublicadorOfertaService respeitam `telegram.enabled=false`
  para manter o perfil OAuth utilizável sem credenciais Telegram.
- O diagnóstico abre a URL do Hub, não o endpoint createLink; não usa perfil
  de teste, oferta mock nem link fallback. Correlaciona por origin_url exata.
- Seleciona no máximo uma oferta inédita, gera o link real, registra pendente,
  envia e consulta o estado final. Usa os métodos de persistência existentes.
- A saída do processo Java contém somente campos públicos e avisos seguros.

## Execução local

Na pasta do projeto, com PostgreSQL disponível e bot administrador do canal:

```powershell
.\entrar-afiliados.ps1
# Concluir login manual e fechar a janela do navegador.
.\enviar-oferta-telegram.ps1
```

O segundo script pede um NOVO token do BotFather via Read-Host -AsSecureString,
injeta TELEGRAM_BOT_TOKEN no processo Java e define TELEGRAM_CHAT_ID=@vendasakes.
application.properties e @Value entregam esses valores ao TelegramService.
Não há token no código, argumento Java/Maven ou arquivo de configuração.
A variável do token é removida ao final. Não reutilizar o token comprometido.
O classpath contém somente classes/dependências de produção, sem recursos H2.

Não repetir após sucesso. Se o banco falhar após o envio, verificar canal e
registro antes de qualquer nova execução. Login e desafios permanecem manuais.

## Validação

- Antes das alterações: 136 testes passaram.
- Depois: 158 testes passaram, zero falhas/erros/ignorados.
- TelegramServiceTest: 19 casos, sem rede real; URI, null/vazio/blank,
  chat ausente, caracteres inválidos, 401/404, respostas inválidas, ok=false,
  timeout e ausência de credencial em stack trace.
- TelegramPublicacaoIntegrationTest: 4 casos com transporte simulado e JPA/H2
  real verificando pendente antes do HTTP, status final e dataEnvio.
- Os demais testes foram preservados, inclusive os de OAuth e persistência.
- Script PowerShell passou na análise sintática.
- Diagnóstico sem token: mensagem segura e código de saída 1, antes do Spring.
- A compilação de testes no isolamento não resolvia classes locais; a mesma
  execução Maven fora desse isolamento concluiu normalmente.

## Estado real observado antes da etapa manual

PostgreSQL foi iniciado via pg_ctl, sem modificar o serviço Windows.
Único registro observado: MLB6797156948 / ERRO_ENVIO / dataEnvio nula.
Nenhum envio real foi feito pelo agente nesta etapa. A confirmação do envio
com novo token e sessão renovada depende da execução local pelo usuário.

## Correção após a execução local

A interrupção antes de consultar o Hub foi reproduzida: o contexto completo da
aplicação, iniciado como não web, tentava injetar HttpServletRequest nos clientes
OAuth e falhava com NoSuchBeanDefinitionException. O diagnóstico passou a
registrar apenas a configuração necessária a JPA e Telegram, mantendo a
aplicação normal e o OAuth sem alterações. Não inicia servidor HTTP.

A saída de erro agora identifica a etapa e o tipo da exceção, sem imprimir sua
mensagem original ou stack trace. O script foi salvo em UTF-8 com BOM para
preservar os acentos no Windows PowerShell.

Validação atual: 159 testes passaram, incluindo a inicialização real do contexto
do diagnóstico em teste. A mesma configuração também iniciou com PostgreSQL
real, usando credenciais Telegram fictícias e sem chamar Hub ou Telegram.
A consulta confirmou somente MLB6797156948 / ERRO_ENVIO / dataEnvio null.
As duas tentativas locais relatadas não criaram novos registros.

## Retorno createLink sem link — 06/09/2026

Na verificação real seguinte, a oferta MLB3807070759 retornou status=200,
total_error=1 e total_success=0. Havia uma entrada com origin_url exatamente
igual à URL solicitada, mas sem short_url, created ou tag. Portanto não se
tratava de falha na comparação da URL. A causa específica da recusa individual
não foi determinada pelo DTO atual. Nenhuma oferta foi gravada ou enviada
nessa verificação.

O diagnóstico agora diferencia mapa sem short_url válido de divergência de
origin_url e mostra os contadores de erro retornados. Cliente createLink,
mapper, persistência e TelegramService não foram alterados nesta etapa.
