# OAuth 2.0 do Mercado Livre — primeira etapa

## Endereço configurado neste ambiente

Callback: `https://droop-juncture-june.ngrok-free.dev/mercadolivre/callback`.
Cadastre exatamente esse valor no Mercado Livre, sem `?code` e sem barra final.

Na pasta do projeto, execute `./iniciar-oauth.ps1` no PowerShell. O script pede
App ID e Client Secret quando faltarem no ambiente e inicia o perfil OAuth.
O Client Secret é digitado de forma oculta e não é salvo em arquivo.
Mantenha o ngrok aberto em outro terminal, encaminhando para `http://127.0.0.1:8080`.
Depois abra `https://droop-juncture-june.ngrok-free.dev/mercadolivre/auth`.
Se o endereço mudar, atualize tanto o cadastro quanto `MERCADOLIVRE_REDIRECT_URI`.

## O que foi implementado

- `GET /mercadolivre/auth`: cria uma sessão e redireciona o navegador para a autorização oficial.
- `GET /mercadolivre/callback`: recebe `code` e `state`, valida o retorno e troca o código por tokens.
- `GET /mercadolivre/me`: usa o access token salvo para consultar `https://api.mercadolibre.com/users/me`.

Esta etapa não busca produtos, não gera links de afiliado, não publica no Telegram e não usa banco.
O perfil `oauth` desativa os componentes Telegram que já existiam no projeto.

## Os três arquivos principais

- `controller/MercadoLivreAuthController.java`: recebe as requisições e envia os redirecionamentos HTTP.
- `service/MercadoLivreAuthService.java`: cria state/PKCE, chama a API e mantém os tokens na sessão.
- `dto/MercadoLivreTokenResponse.java`: representa o JSON retornado pelo endpoint de tokens.

Os arquivos têm comentários explicando as decisões. Não foi necessário adicionar dependências ao Maven:
Spring MVC já fornece RestClient e o suporte às sessões.

## 1. Cadastrar a aplicação

Na área de aplicações do [Mercado Livre Developers](https://developers.mercadolivre.com.br/),
cadastre a aplicação e obtenha **App ID** e **Client Secret**.
Eles são diferentes do token do bot do Telegram.

Cadastre uma Redirect URI HTTPS que termine em `/mercadolivre/callback`.
Ela deve ser exatamente igual à variável MERCADOLIVRE_REDIRECT_URI:
protocolo, domínio, porta, caminho e barra final fazem diferença.

Para desenvolvimento local, use um endereço HTTPS que encaminhe para sua aplicação
na porta 8080 (um proxy/túnel de desenvolvimento ou um domínio de desenvolvimento com TLS).
Este projeto não cria nem publica esse endereço automaticamente.

Configure as permissões necessárias para ler a conta e obter acesso offline (`read` e
`offline_access`), conforme as opções oferecidas no cadastro. Não é necessário pedir escrita para esta etapa.
Ative PKCE na aplicação; o projeto usa S256 por padrão.
Se sua aplicação tiver PKCE desativado, defina MERCADOLIVRE_PKCE_ENABLED=false.

## 2. Configurar as variáveis e executar

No PowerShell, na pasta do projeto:

```powershell
$env:MERCADOLIVRE_APP_ID = "seu_app_id"
$env:MERCADOLIVRE_CLIENT_SECRET = "seu_client_secret"
$env:MERCADOLIVRE_REDIRECT_URI = "https://seu-dominio/mercadolivre/callback"
$env:MERCADOLIVRE_PKCE_ENABLED = "true"

.\mvnw.cmd -B -ntp "-Dspring-boot.run.profiles=oauth" spring-boot:run
```

As aspas acima pertencem à sintaxe do PowerShell. No application.properties use apenas:

```properties
mercadolivre.app-id=${MERCADOLIVRE_APP_ID:}
mercadolivre.client-secret=${MERCADOLIVRE_CLIENT_SECRET:}
mercadolivre.redirect-uri=${MERCADOLIVRE_REDIRECT_URI:}
mercadolivre.pkce-enabled=${MERCADOLIVRE_PKCE_ENABLED:true}
```

Não coloque valores reais dentro de `${...}`. O nome dentro das chaves é o nome da variável.
O `.env.example` é uma referência; Spring não carrega arquivos .env automaticamente.
As variáveis `$env:` valem para o terminal atual e os processos iniciados por ele.
Se alterar alguma variável, reinicie a aplicação a partir desse terminal.

Neste Windows com Java 24, caso apareça `Unable to establish loopback connection`,
use a alternativa que funcionou no ambiente do projeto:

```powershell
New-Item -ItemType Directory -Force target/tmp | Out-Null
$oauthTmp = ((Resolve-Path target/tmp).Path).Replace('\', '/')
.\mvnw.cmd -B -ntp "-Dspring-boot.run.profiles=oauth" "-Dspring-boot.run.jvmArguments=-Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=$oauthTmp" spring-boot:run
```

## 3. Autorizar no navegador

Abra `https://seu-dominio/mercadolivre/auth` no navegador.
Use o mesmo domínio HTTPS configurado no callback desde o início.
Não inicie por localhost e retorne pelo domínio do túnel: o cookie da sessão não acompanha a troca de domínio.

Fluxo:

1. O controller chama o service, que cria um `state` aleatório e o guarda na sessão.
2. Com PKCE ativado, também cria um `code_verifier` e envia seu hash como `code_challenge`.
3. O navegador recebe HTTP 302 e vai para `https://auth.mercadolivre.com.br/authorization`.
4. Você faz login e autoriza a aplicação no próprio Mercado Livre.
5. O navegador retorna ao callback com `code` e `state`.
6. O service exige que state corresponda ao fluxo da sessão e tenha menos de 10 minutos.
7. O código é enviado uma única vez a `POST https://api.mercadolibre.com/oauth/token`.
8. O access token, refresh token e validade ficam na memória do servidor, associados à sessão.
9. O controller troca o ID da sessão e responde HTTP 303 para `/mercadolivre/me`.
10. O service envia `Authorization: Bearer ACCESS_TOKEN` à API oficial e retorna os dados da conta.

Na etapa 7, o corpo usa `application/x-www-form-urlencoded` com:
`grant_type=authorization_code`, `client_id`, `client_secret`, `code`,
`redirect_uri` e, quando PKCE estiver ativado, `code_verifier`.

Não abra o callback manualmente com um code copiado. O state e o cookie da mesma sessão são necessários.
Abrir /auth de novo substitui a tentativa anterior daquela sessão.

## 4. Testar /users/me

Depois da autorização, você já será direcionado a `/mercadolivre/me`.
Para repetir, visite essa URL no mesmo navegador e domínio. Exemplo de campos retornados:

```json
{"id":123456789,"nickname":"SUA_CONTA"}
```

A API pode retornar outros campos da sua conta. O endpoint local não devolve access_token nem refresh_token.
No service, a chamada equivalente é:

```java
http.get().uri("/users/me")
    .headers(headers -> headers.setBearerAuth(credenciais.tokens().accessToken()))
    .retrieve().body(Map.class);
```

Se você já tiver um access token obtido por outro cliente OAuth, o teste direto no PowerShell é:

```powershell
curl.exe "https://api.mercadolibre.com/users/me" -H "Authorization: Bearer $env:MERCADOLIVRE_ACCESS_TOKEN"
```

MERCADOLIVRE_ACCESS_TOKEN é apenas para esse teste externo. A aplicação não preenche a variável
do terminal; ela armazena seus próprios tokens na sessão. Um curl novo contra /mercadolivre/me
sem o cookie da sessão autenticada retorna 401, mesmo que o navegador esteja autenticado.

## Armazenamento e limites desta etapa

Não há banco nem arquivo de tokens. A sessão HTTP padrão armazena tudo na memória da aplicação.
Os tokens são perdidos ao reiniciar, ao expirar a sessão (30 minutos sem atividade) ou ao perder o cookie.
O access token também é verificado usando expires_in retornado pelo Mercado Livre.
Após expirar, é necessário autorizar novamente: o refresh token está armazenado,
mas sua renovação automática ainda não foi implementada.

As sessões são independentes: um navegador não usa os tokens de outro.
Essa escolha atende ao estudo interativo do OAuth; o futuro bot em segundo plano precisará de
armazenamento próprio persistente e renovação de token.

O cookie é HttpOnly, SameSite=Lax e não é colocado em URLs.
Ao disponibilizar por HTTPS, configure cookie Secure e a proteção de acesso apropriada ao ambiente.
Não habilite logs de corpos HTTP/cabeçalhos Authorization nem registro de query strings do callback.

## Erros

- **503 em /auth**: falta configuração da aplicação ou a Redirect URI é inválida.
- **400 no callback**: state ausente/inválido/expirado, sessão perdida, code ausente ou consentimento negado.
- **401 em /me**: não houve autenticação nessa sessão ou o token expirou.
- **502**: falha/resposta inválida do Mercado Livre; a mensagem informa a operação e o HTTP externo quando disponível.

HTTP 400 do endpoint de tokens: confira se o code já foi usado, Redirect URI e configuração PKCE.
HTTP 401: confira App ID/Client Secret ou refaça a autorização.
HTTP 403 em /users/me: confira permissões e restrições da conta/aplicação.
Os corpos de erros externos não são exibidos para evitar vazamento de credenciais.
Não há repetição automática da troca do code após timeout; inicie um novo fluxo.

## Testes

```powershell
.\mvnw.cmd -B -ntp verify
```

Os testes simulam a API oficial e cobrem redirecionamento, PKCE/formulário, callback,
troca única do code, isolamento de sessão, armazenamento, header Bearer, erros e inicialização sem Telegram.
O login real depende de suas credenciais, da Redirect URI cadastrada e do seu consentimento no Mercado Livre.

## Fontes oficiais

- [Autenticação e autorização](https://developers.mercadolivre.com.br/devcenter/autenticacao-e-autorizacao)
- [Criação de aplicação e HTTPS no redirect](https://global-selling.mercadolibre.com/devsite/en_us/deals-gs/create-application)
