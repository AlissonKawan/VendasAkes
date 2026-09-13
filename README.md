# Akes Ofertas

## OAuth do Mercado Livre (nova etapa)

O fluxo de autenticação está implementado e comentado. Consulte o
[guia de OAuth](docs/MERCADO_LIVRE_OAUTH.md) para configurar App ID, Client Secret,
Redirect URI e testar a conta com `/mercadolivre/me`.
Execute com o perfil `oauth` para estudar essa etapa sem ativar o Telegram.


MVP em Java 21 + Spring Boot 4.1.1 + Maven para publicar ofertas em um canal do Telegram.
O envio acontece ao receber `POST /api/ofertas/publicar`. Não há agendamento nem busca automática de ofertas.

## Estrutura

Pacote base: `br.com.akesofertas`. Classe principal: `AkesOfertasApplication`.

```text
src/main/java/br/com/akesofertas/
├── AkesOfertasApplication.java
├── controller/
├── service/
├── dto/
├── client/
└── exception/
```

Os testes seguem a mesma organização em `src/test/java/br/com/akesofertas/`.
O Maven gera `target/akes-ofertas-0.0.1-SNAPSHOT.jar`.

- `controller/OfertaController`: endpoint REST.
- `dto/OfertaRequest` e `OfertaResponse`: entrada validada e confirmação.
- `service/OfertaService`: valida URLs, formata preços e escapa HTML.
- `client/TelegramClient`: chamada HTTP síncrona com RestClient.
- `exception/`: respostas legíveis de validação e falha do Telegram.

Sem banco, JPA, Lombok, autenticação, filas ou integrações com lojas. O campo `loja` é texto livre:
novas lojas podem usar o mesmo endpoint. O antigo CRUD com persistência foi substituído por este fluxo.

## Preparar o Telegram

1. Crie um bot com [@BotFather](https://t.me/BotFather) e obtenha seu token.
2. Crie um canal e adicione o bot como administrador com permissão para publicar mensagens.
3. Para canal público, use `@nome_do_canal` como chat ID.
   Para canal privado, use o ID numérico completo (normalmente começa com `-100`).
   Você pode obtê-lo em `channel_post.chat.id` na resposta de
   `https://api.telegram.org/bot<TOKEN>/getUpdates`, após publicar manualmente no canal.
   Use essa consulta localmente; a URL contém o token. O bot não deve ter webhook ativo para usar getUpdates.

Se um token já ficou em código, logs ou conversa, revogue-o no BotFather antes de usar um novo.

## Rodar no Windows (PowerShell)

Requer JDK 21 ou superior compatível. Maven já vem pelo wrapper; a primeira execução requer internet.

```powershell
$env:TELEGRAM_BOT_TOKEN = "seu_novo_token"
$env:TELEGRAM_CHAT_ID = "@seu_canal"
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
export TELEGRAM_BOT_TOKEN='seu_novo_token'
export TELEGRAM_CHAT_ID='@seu_canal'
./mvnw spring-boot:run
```

O `.env.example` é uma referência, não é carregado automaticamente.
Sem token ou chat ID, a aplicação não inicia e informa qual variável está faltando.
A API escuta em `127.0.0.1:8080` por padrão. `PORT` altera a porta;
`SERVER_ADDRESS` altera a interface. Como esta fase não tem autenticação, mantenha o acesso local ou privado.

## Publicar a primeira oferta

Edite `exemplos/oferta.json` com uma oferta e um link reais. O arquivo de exemplo envia apenas texto:

```json
{
  "titulo": "Mouse Gamer Attack Shark X3",
  "precoAtual": 139.90,
  "precoAntigo": 199.90,
  "loja": "Mercado Livre",
  "urlProduto": "https://exemplo.com/produto",
  "cupom": "DESCONTO10"
}
```

Em outro terminal PowerShell, na pasta do projeto:

```powershell
curl.exe -i -X POST "http://localhost:8080/api/ofertas/publicar" -H "Content-Type: application/json" --data-binary "@exemplos/oferta.json"
```

Linux/macOS:

```bash
curl -i -X POST 'http://localhost:8080/api/ofertas/publicar' \
  -H 'Content-Type: application/json' \
  --data-binary @exemplos/oferta.json
```

Resposta HTTP 200, somente após confirmação do Telegram:

```json
{"sucesso":true,"mensagem":"Oferta publicada no Telegram","messageId":42}
```

O ID varia conforme a mensagem criada. Confira também a publicação no canal.

Para enviar foto, adicione `"imagemUrl": "https://seu-site.com/foto.jpg"` ao JSON.
Há um payload completo em `exemplos/oferta-com-imagem.json`. Substitua `imagemUrl`
por uma URL real de imagem antes de executar (o endereço example.com é ilustrativo):

```powershell
curl.exe -i -X POST "http://localhost:8080/api/ofertas/publicar" -H "Content-Type: application/json" --data-binary "@exemplos/oferta-com-imagem.json"
```

O campo recebe uma URL, não um arquivo local nem Base64. A aplicação envia essa URL
ao Telegram, que baixa a foto e publica a oferta como legenda no mesmo post.
Use uma URL pública direta de uma imagem real acessível ao Telegram.
URLs de exemplo não garantem uma imagem disponível. Sem imagemUrl, com null ou em branco, envia texto.
Cupom e preço antigo ausentes não aparecem na mensagem. Cupom vazio também é omitido.

URLs devem ser strings HTTP/HTTPS simples, sem a sintaxe de link Markdown `[texto](url)`.
Preços aceitam zero, não aceitam negativos, têm até 10 dígitos inteiros e 2 casas decimais.
Título: até 300 caracteres; loja e cupom: até 100; URLs: até 2048.
O limite da mensagem visível é verificado conservadoramente em unidades UTF-16:
1024 com foto e 4096 sem foto. Se a legenda exceder o limite, retorna 400;
reduza os campos ou remova imagemUrl.

## Erros

- **400**: campos obrigatórios ausentes, JSON inválido, preços/URLs inválidos ou legenda longa.
- **502**: Telegram recusou o envio, retornou resposta inválida ou houve falha de conexão.

Exemplo de falha externa:

```json
{"sucesso":false,"mensagem":"Telegram: Bad Request: chat not found","erros":[]}
```

`chat not found`: confira chat ID e acesso do bot.
`Forbidden`: confira as permissões do bot no canal.
`Unauthorized`: confira o token.
Erros de foto: confira a URL pública e o formato da imagem.
O Telegram pode retornar 429 por excesso de envios; a descrição aparece na resposta 502.

A conexão tem timeout de 5 segundos e a leitura de 20 segundos.
Não há repetição automática: após timeout, confira o canal antes de reenviar,
pois a mensagem pode ter sido publicada. Cada POST é uma nova publicação; não há deduplicação.
A aplicação não registra o token nem retorna a URL da Bot API em seus erros.

## Testar e empacotar

```powershell
.\mvnw.cmd clean verify
java -jar target/akes-ofertas-0.0.1-SNAPSHOT.jar
```

No Linux/macOS, use `./mvnw clean verify`. O JAR precisa das variáveis do Telegram.
Os testes usam respostas simuladas e não publicam mensagens reais.
Cobrem payload inválido, HTML, preços brasileiros, opcionais, limite de legenda,
envio de texto/foto, erro HTTP, ok=false, resposta inesperada, timeout e configuração ausente.

## Próximo passo

Validar monetização com poucas ofertas selecionadas manualmente e links de afiliado aprovados:
acompanhar cliques e conversões pelo painel do programa e descobrir qual nicho vende.
Depois disso, automatizar a entrada de ofertas da loja que apresentar resultado.

## Etapa 1: busca oficial de produtos

Após autenticar pelo OAuth, use `GET /api/produtos?q=mouse&limite=5` na mesma sessão.
A busca retorna produtos de catálogo reais da API oficial quando o acesso é autorizado;
ainda não calcula descontos nem publica ofertas. Veja [como testar e as limitações](docs/BUSCA_PRODUTOS.md).

## Etapa 2: ofertas pela buy box e preços oficiais

Use `GET /api/ofertas/buscar?q=mouse&limite=10` na mesma sessão OAuth.
A busca avalia até 20 catálogos, descarta cada produto sem `buy_box_winner` e consulta
`/items/{item_id}/prices` para confirmar o preço do vencedor. Retorna ofertas e os
motivos dos descartes; preço regular e desconto ficam nulos quando desconhecidos.

O diagnóstico `/api/produtos/{id}/diagnostico-ofertas?limite=3` permanece opcional.
Um 404 em `/products/{id}/items` não interfere na busca principal, que não usa essa rota.
Veja [regras, teste real pendente e investigação do endpoint legado](docs/ETAPA_2_ANUNCIOS_PRECOS.md).

## Gerador de afiliados (estrutura isolada)

Para investigar o feed no hub autenticado sem alterar os serviços existentes:
execute `.\diagnosticar-ofertas.ps1`. Ele observa Fetch/XHR do navegador privado e
gera um relatório sanitizado, incluindo contagens de lotes createLink de até 20 URLs.
Login, navegação e geração são manuais. Veja [o diagnóstico do feed](docs/DIAGNOSTICO_FEED_AFILIADOS.md).

A geração de links está em investigação separada do OAuth e do envio ao Telegram.
Para entrar no portal e observar uma geração, sem gravar valores de credenciais:

```powershell
.\entrar-afiliados.ps1 -Diagnostico
```

Consulte [o diagnóstico do gerador](docs/GERADOR_AFILIADOS.md) para o estado atual,
o que já foi confirmado e o procedimento. Ainda não há um endpoint de geração
automática pronto. A fachada `AffiliateLinkService.gerarLink(urlProduto)` e os DTOs
estão criados, mas o cliente HTTP retorna um erro de autenticação pendente sem enviar
requisições. A etiqueta usa `AFILIADOS_TAG` (padrão `telegram`). O diagnóstico anterior
é separado do serviço; seu perfil privado fica em `.local/`, ignorado pelo Git.

## Referências técnicas

- [RestClient — Spring Framework](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Telegram Bot API](https://core.telegram.org/bots/api)
