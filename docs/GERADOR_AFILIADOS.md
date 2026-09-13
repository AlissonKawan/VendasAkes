# Gerador de afiliados — investigação

## Escopo

Receber uma URL normal de produto e devolver o link do próprio afiliado, normalmente
`https://meli.la/...`. Não coletar produtos, cupons, preços ou enviar ao Telegram.
A integração está isolada no pacote `br.com.akesofertas.afiliados`.

## Estrutura implementada

O restante do sistema injeta `AffiliateLinkService` pelo construtor e chama:

```java
String linkAfiliado = affiliateLinkService.gerarLink(urlProduto);
```

- `AffiliateLinkService`: valida a URL, monta o pedido e extrai o link da resposta.
- `AffiliateLinkClient`: contrato pequeno que isola o portal privado.
- `MercadoLivreAffiliateClient`: guarda a URI observada; **ainda não faz HTTP**.
- `AffiliateLinkRequest` / `AffiliateLinkResponse`: DTOs do contrato observado.
- `AffiliateLinkException`: erro legível sem dados privados da requisição.

Hoje o cliente real lança um erro informando que a autenticação está pendente.
Não devolve um link fictício e não reutiliza tokens OAuth. O serviço não chama o navegador.
Os testes de geração usam respostas simuladas.

Configuração: `afiliados.tag=${AFILIADOS_TAG:telegram}`. A etiqueta não é uma
credencial nem substitui a sessão do afiliado. Nenhuma variável de cookies/tokens
foi criada antes de confirmar a autenticação exigida.

## Contrato informado pelo usuário

O usuário forneceu a seguinte captura do Gerador de Links. É uma rota interna,
não uma API pública documentada; seu contrato pode mudar.

```http
POST https://www.mercadolivre.com.br/affiliate-program/api/v2/affiliates/createLink
```

Pedido:

```json
{"urls":["URL_DO_PRODUTO"],"tag":"telegram"}
```

Resposta:

```json
{
  "status": 200,
  "urls": [{
    "created": true,
    "tag": "telegram",
    "short_url": "https://meli.la/EXEMPLO",
    "origin_url": "URL_DO_PRODUTO"
  }]
}
```

Extração: `urls[0].short_url`. O serviço exige status de negócio 200, um único
resultado, `created=true`, a etiqueta pedida e URL HTTPS do domínio exato `meli.la`
com caminho não vazio. A semântica de `created=false` (erro ou link já existente)
não foi confirmada; por enquanto é rejeitado. O cliente HTTP futuro deverá verificar
também o status HTTP, que é diferente do campo `status` no JSON.

## Evidência confirmada em 05/09/2026

- O [guia oficial](https://www.mercadolivre.com.br/l/afiliados-gere-seus-links)
  orienta abrir o Portal do Afiliado, acessar o Gerador de Links, colar a URL e gerar.
- O guia aponta para `https://www.mercadolivre.com.br/afiliados/linkbuilder`.
- Abrir esse endereço no navegador sem sessão redirecionou para o login.
- O OAuth da aplicação não autentica automaticamente esse perfil de navegador.
- Nas fontes oficiais consultadas, não foi encontrada documentação pública de um
  endpoint de geração de links de afiliado que aceite o access token OAuth.
  Isso não prova a inexistência de uma API privada ou de um acesso para parceiros.

## O que ainda falta comprovar

Endpoint, body e resposta foram informados pelo usuário. Não há captura local dos
headers de autenticação. Não sabemos quais cookies/tokens/headers são exigidos.

| Informação pedida | Situação |
| --- | --- |
| Endpoint e método | POST `createLink`, conforme captura fornecida acima |
| Content-Type | Body informado é JSON; confirmar o header enviado pelo portal |
| Accept, Origin e Referer | Não informados; obrigatoriedade desconhecida |
| Cookies | Nomes e conjunto mínimo ainda desconhecidos |
| Token/header CSRF | Presença, nome e relação com cookie não confirmados |
| Authorization | Presença desconhecida; não presumir Bearer OAuth |
| Tokens necessários | Não confirmados; não presumir que sejam os tokens OAuth |
| Expiração e renovação da sessão | Não determinadas |
| Body e resposta | DTOs criados a partir da captura fornecida |
| Campo que contém `meli.la` | `urls[0].short_url` |
| Reprodução direta permitida e suportada | Não confirmada pelas fontes oficiais consultadas |

Uma captura mostra o que o navegador enviou, mas não prova quais headers/cookies
são estritamente necessários nem autoriza transformar uma chamada interna em API pública.
A investigação atual permite um observador de navegador separado; o cliente HTTP
continua incompleto até esclarecer a autenticação e a adequação dessa rota privada.
Consulte [o diagnóstico de feed e lotes](DIAGNOSTICO_FEED_AFILIADOS.md). O serviço de
negócio continua recebendo uma URL; suporte a lote é apenas observado pelo diagnóstico.

## Próxima verificação no navegador (manual)

No DevTools → Network → Fetch/XHR, gere um link e abra a requisição `createLink`.
Anote os **nomes** dos headers e cookies enviados. Substitua os valores de Cookie,
Authorization e qualquer token por `[REDACTED]` antes de compartilhar. Não cole
um “Copy as cURL” bruto, que pode conter a sessão inteira.

Headers observados não são necessariamente obrigatórios. Determinar o conjunto
mínimo exige testes controlados na própria sessão, removendo um candidato por vez,
caso a reprodução direta seja considerada adequada. Não contorne desafios de segurança.
Se houver dependência de sessão privada, ela ficará apenas no cliente de afiliados,
sem circular pelo serviço de ofertas, Telegram ou OAuth. Segredos futuros deverão
vir de configuração privada fora do Git, nunca de fallbacks no código.

## Diagnóstico anterior (opcional, separado do serviço)

Com Chrome instalado e Java disponível, execute na pasta do projeto:

```powershell
.\entrar-afiliados.ps1 -Diagnostico
```

1. Entre na sua conta diretamente na janela aberta pelo programa.
2. No Gerador de Links, insira uma URL normal de produto e clique em Gerar.
3. Aguarde a mensagem `Chamada do gerador registrada...` no terminal.
4. Deixe o resultado aberto para conferir o comportamento ou feche a janela ao terminar.

O arquivo `.local/diagnostico-afiliados.jsonl` registra somente chamadas POST de
XHR/fetch no domínio `www.mercadolivre.com.br`, com `link` no caminho, enquanto a
tela do Gerador de Links está aberta. Outros domínios/métodos não são capturados
por esse filtro; ausência de registro não significa ausência de uma chamada HTTP.

O relatório contém endpoint sem query string, método, content type, nomes de
headers/cookies, status HTTP e estruturas JSON. Todos os valores JSON são
substituídos por tipos (`[string]`, `[number]`, `[boolean]`). Corpos não JSON são
omitidos. Não são exportados valores de cookies, tokens, senha nem um HAR completo.

## Sessão local

- `.local/mercadolivre` é um perfil exclusivo do robô; contém a sessão autenticada.
- `.local/` está no `.gitignore`. Não compartilhe essa pasta.
- `AFILIADOS_PERFIL` permite definir outro caminho privado, fora do controle de versão.
- `AFILIADOS_NAVEGADOR` aceita um canal instalado, como `chrome` (padrão) ou `msedge`.
- Feche o navegador de login antes de uma futura execução do serviço usar o mesmo perfil.
- O modo de diagnóstico não inicia Spring, não ocupa a porta 8080 e não publica ofertas.

## Estado da implementação

Prontos: fachada Spring `AffiliateLinkService`, contrato do cliente privado, DTOs,
validações e testes simulados. Nenhuma chamada HTTP real foi feita pelo serviço.
Não foi criado endpoint REST para geração nesta etapa.

O diagnóstico anterior com Playwright permanece separado, sem automação de clique
em Gerar e sem inicialização pelo Spring. O novo diagnóstico de feed/lote é outro
programa, no pacote `br.com.akesofertas.diagnostico`.
