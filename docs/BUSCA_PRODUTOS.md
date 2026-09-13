# Etapa 1 — busca oficial de produtos

## O que foi implementado

```http
GET /api/produtos?fornecedor=mercadolivre&q=mouse&limite=5
```

O backend consulta apenas a API oficial, com o token OAuth da sessão:

```http
GET https://api.mercadolibre.com/products/search?status=active&site_id=MLB&q=mouse&limit=5
Authorization: Bearer ACCESS_TOKEN
```

O endpoint é o [Buscador de produtos documentado pelo Mercado Livre](https://developers.mercadolivre.com.br/buscador-de-produtos).
Não há scraping ou busca de produtos pelo portal de afiliados. Cookies de `.local/mercadolivre`
não são usados na API oficial. O token OAuth não é retornado no JSON nem enviado à rota de afiliados.

## Produto de catálogo não é uma oferta

Cada resultado contém `fornecedor`, `id`, `titulo`, `urlProduto` e `imagemUrl`.
A URL normal é a página de catálogo `https://www.mercadolivre.com.br/p/{id}`, derivada do ID
retornado pela API. Ainda não representa a seleção de um anúncio específico de um vendedor.

Esta busca não comprova preço atual, preço antigo, estoque ou desconto. Esses valores não
são inventados. Antes da etapa 2, será necessário obter dados de anúncios/preços por um
recurso oficial autorizado e confirmar sua disponibilidade para esta conta/aplicação.

## Testar com a conta real

1. Pare a instância antiga da aplicação com Ctrl+C no terminal onde ela está rodando.
2. Na pasta do projeto, execute `./iniciar-oauth.ps1` para compilar/iniciar o código novo.
   Informe credenciais somente no terminal, se o script solicitar. Mantenha o ngrok apontando para 8080.
3. No navegador, abra:
   `https://droop-juncture-june.ngrok-free.dev/mercadolivre/auth`
4. Depois de autorizar, na mesma sessão do navegador, abra:
   `https://droop-juncture-june.ngrok-free.dev/api/produtos?q=mouse&limite=5`

Não use localhost em uma etapa e ngrok em outra: o cookie da sessão precisa acompanhar a busca.
Reiniciar a aplicação apaga os tokens em memória e exige nova autorização. Não envie tokens
na query string ou no payload da busca.

Resposta de sucesso: HTTP 200 e lista de produtos. Uma lista vazia é válida, mas para validar
a etapa com produtos reais precisamos ver pelo menos um ID/título real retornado pela API.
Um HTTP 403 não deve ser contornado com outra identidade, cookies do site ou scraping.

| Resposta | Significado |
| --- | --- |
| 400 | Termo inválido, limite fora de 1–20 ou fornecedor desconhecido |
| 401 | Sessão ausente/expirada ou token recusado; autorizar novamente |
| 502 mencionando HTTP 403 | API recusou acesso; verificar permissões/disponibilidade do recurso |
| 502 mencionando HTTP 429 | Limite da API atingido; aguardar |
| 502 | Falha remota, timeout ou resposta inválida |
| 404 | Possível instância antiga sem o novo endpoint; conferir a reinicialização |

## Extensão futura

`ProdutoController → ProdutoBuscaService → ProdutoProvider → MercadoLivreProvider → MercadoLivreProdutosClient`

`ProdutoProvider` define `codigo()` e `buscar(termo, limite)`. Novos beans como
`ShopeeProvider` ou `AmazonProvider` poderão retornar `ProdutoEncontrado`; o serviço
de busca descobre os providers por injeção de lista, sem um if por marketplace.

O cliente do Mercado Livre concentra a autenticação. Por enquanto seu fornecedor de token
consulta a HttpSession do pedido. Um futuro agendador precisará de armazenamento/renovação
de tokens independente da sessão web; ainda não há execução automática em segundo plano.

`AffiliateLinkService → AffiliateLinkClient` permanece separado e não é chamado nesta etapa.
Não foram adicionados filtro de oferta, banco, deduplicação ou envio automático ao Telegram.

## Validação

`./mvnw.cmd test` executa testes simulados: endpoint oficial, Bearer sem cookies, codificação
de parâmetros, mapeamento do catálogo, erros 401/403/429/500, timeout sem retry e seleção
de providers. Esses testes não substituem a consulta autenticada com a conta real.

O usuário confirmou a validação da etapa 1 em ambiente real, com produtos de catálogo
retornados pela API. A próxima verificação de anúncios/preços está descrita em
[Etapa 2](ETAPA_2_ANUNCIOS_PRECOS.md); afiliados, persistência e publicação continuam posteriores.
