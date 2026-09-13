# Mercado Livre: cupons como fonte de produtos

Captura autenticada realizada em 2026-09-10 (America/Sao_Paulo), usando a sessão que o usuário abriu manualmente no Brave. Nenhum cookie, token, header de autorização ou credencial foi copiado para este documento ou para o código.

## 1. Escopo e conclusão

O Mercado Livre fornece duas fontes de descoberta diferentes:

- o Hub de Afiliados fornece ofertas do feed do afiliado;
- a página central de cupons fornece campanhas e links/containers de produtos participantes.

Estar na listagem oficial de uma campanha é a prova de associação usada pelo fluxo CUPOM. O PDP ainda atualiza a identidade e o preço, mas a ausência de uma confirmação `HIGH` no PDP não apaga o cupom comprovado pela listagem oficial.

O fluxo de cupons não é mais um enriquecimento oportunista de itens encontrados no Hub. Ele navega `cupom -> container/listagem -> produtos -> candidatos CUPOM` independentemente do Hub.

## 2. Arquitetura anterior

Antes desta refatoração, `ExecutarCicloOfertasService` concentrava coleta do Hub, tentativa de associar cupons, busca subsidiária de produtos de container, geração de links, validação PDP e publicação. A fonte CUPOM era acionada como complemento do Hub, e o cliente de produtos dependia essencialmente do DOM e de `wid=MLB...` no `href`.

Fluxo anterior simplificado:

```text
OfertaScheduler
  -> ExecutarCicloOfertasService
      -> Sessao.buscarOfertas (Hub)
      -> tentar enriquecer itens do Hub com cache de cupons
      -> se necessário, navegar containers
      -> createLink para todos os candidatos
      -> PDP
      -> deduplicação/publicação/Telegram
```

O banco é PostgreSQL em produção e H2 nos testes. A chave única e a regra-base de deduplicação persistida são `(fornecedor, item_id)`. `OfertaPublicadaService` também aplica a janela configurável de republicação; rejeições de link código 111 usam cooldown separado.

## 3. Investigação autenticada da página central

Página aberta:

```text
GET https://www.mercadolivre.com.br/cupons
tipo observado: navegação DOCUMENT com dados SSR
```

A interface indicou 2.812 cupons. No documento foi encontrado `__NORDIC_RENDERING_CTX__` com aproximadamente 429 KiB. O caminho real usado pela página para a lista estruturada foi:

```text
tracking.view.eventData.coupons_list
```

Na captura havia 74 entradas nessa lista SSR. Para `campaign_id=13558453`, foram observados os campos reais:

```json
{
  "campaign_id": 13558453,
  "title": "R$ 20 OFF",
  "code": "",
  "discount_type": "FIXED",
  "discount_value": 20,
  "min_amount": 150,
  "cap_amount": 20,
  "expiration_date": "2026-10-01T02:59:59Z",
  "status_id": "ACTIVE",
  "item_ids": [
    "MLB4812130742",
    "MLB3800842215",
    "MLB4997939720",
    "MLB5144311038"
  ]
}
```

Em `segmentations.containers[0]`:

```json
{
  "id": "MLB1775107",
  "name": "20 ACIMA DE 150 SET seller 1788199941"
}
```

O endpoint `/cupons/api/main-data/filtered?page=N` **não foi confirmado como chamado pelo frontend atual**. Existe um desserializador legado no projeto, mas o cliente não chama esse endpoint. Não há base observada para inventar ou promover esse caminho a contrato.

## 4. Investigação autenticada dos produtos de um cupom

O container histórico da campanha 13558453 foi reaberto em:

```text
GET https://lista.mercadolivre.com.br/_Container_MLB1775107
```

Na captura de 2026-09-10 ele respondeu visualmente “Não encontramos resultados”. Portanto, a associação histórica `13558453 -> MLB4639510787` não pôde ser reproduzida ao vivo nessa data. Isso é deriva temporal dos dados da campanha, não justificativa para fabricar um resultado. O caso histórico foi preservado como fixture de teste do parser SSR.

Para observar uma listagem ativa, a ação “Conferir produtos” da campanha 13618999 levou a:

```text
GET https://lista.mercadolivre.com.br/pagina/quantum_x/?coupon_campaign_id=13618999
tipo observado: navegação DOCUMENT com dados SSR
```

Resultados observados:

- 198 resultados informados pela interface;
- 48 cards de produto no DOM da primeira página;
- 49 entradas em `initialState.results` no SSR;
- paginação presente no SSR e na interface.

O caminho estruturado real foi:

```text
__NORDIC_RENDERING_CTX__
  .appProps.pageProps.initialState.results[]
  .appProps.pageProps.initialState.pagination.pagination_nodes_url
```

Cada resultado de produto relevante apresentou a estrutura real:

```text
result.id
result.state
result.polycard.metadata.id                    -> itemId/anúncio
result.polycard.metadata.product_id            -> productId de catálogo
result.polycard.metadata.signal.item_id        -> itemId alternativo
result.polycard.metadata.signal.price          -> preço
result.polycard.metadata.url
result.polycard.metadata.url_params
result.polycard.metadata.url_fragments
result.polycard.metadata.tracks.price.promotions[].campaign_id
result.polycard.components[type=title].title.text
result.polycard.pictures.pictures[].id
```

Exemplo real de card:

```text
productId no path: /p/MLB26638960
itemId no fragmento: wid=MLB6181752186
SSR metadata.product_id: MLB26638960
SSR metadata.id: MLB6181752186
```

Consequência: o identificador `/p/MLB...` não pode ser tratado como `itemId`. Ele é `productId`; o anúncio é `metadata.id`, `metadata.signal.item_id` ou, apenas como fallback, `wid`.

A imagem não apareceu como URL pronta no bloco SSR inspecionado; havia IDs em `pictures`. Por isso o coletor mantém o resolvedor de imagem existente e usa imagem do DOM somente quando disponível.

## 5. Requisições e endpoints confirmados

| Método | Path/URL | Ação | Resultado observado |
|---|---|---|---|
| GET | `/cupons` | abrir central de cupons | documento SSR com `coupons_list` |
| GET | `/_Container_MLB1775107` | abrir container da campanha 13558453 | estado vazio em 2026-09-10 |
| GET | `/pagina/quantum_x/?coupon_campaign_id=13618999` | “Conferir produtos” | listagem SSR com 198 resultados |
| GET | PDP do produto | abrir o item com a sessão autenticada | SSR com `initialState.components.share.share_actions` |
| GET | `/noindex/share/{productId}` | abrir “Compartilhar” | modal SSR de afiliado com ação `copy_action` |
| POST | `/affiliate-program/api/v2/affiliates/createLink` | geração de link do fluxo HUB | mantido somente para ofertas originadas no Hub |

Não foi confirmada uma resposta Fetch/XHR específica para obter os produtos do cupom: na versão observada, campanha e listagem vieram no HTML/SSR. Em particular, `/cupons/api/main-data/filtered` não foi observado. Os formatos SSR, compartilhamento e `createLink` são internos/privados e podem mudar sem aviso.

## 6. Link afiliado

Os links dos cards são URLs normais de produto, com `productId` no path e `wid`/tracking no fragmento. Na sessão autenticada de afiliado, a ação “Compartilhar” seguida de “Copiar link” exibiu a confirmação “Você copiou o link. Compartilhe para monetizar.”

O estado SSR do PDP expôs a mesma ação em `initialState.components.share.share_actions`. A ação `COPY_LINK` trouxe um destino personalizado do Mercado Livre e tracking `user_type=affiliates`. A política implementada é:

```text
origem HUB -> AffiliateLinkService/createLink
origem CUPOM -> abrir PDP autenticado e extrair a ação COPY_LINK marcada como afiliado
se a ação afiliada não existir -> tentar o próximo produto de cupom
```

O fluxo CUPOM nunca chama `createLink`, portanto a resposta 111 e seu cooldown pertencem exclusivamente ao fluxo HUB. O link completo não é registrado em log, pois contém identificadores personalizados da sessão/afiliado.

## 7. Arquitetura implementada

Foram reaproveitados:

- `MercadoLivreAffiliateOffersClient` e o mapeador do Hub;
- `PlaywrightConsumerCouponClient` como catálogo de cupons SSR;
- `MercadoLivreCouponService` como cache/orquestrador cupom-produtos;
- `MercadoLivrePdpValidator` para atualizar identidade e preço;
- `AffiliateLinkService` para o Hub, além de `PublicadorOfertaService`, `OfertaPublicadaService` e `OfertaAfiliadoRejeitadaService`;
- uma única `MercadoLivreAfiliadosSessionFactory.Sessao` por ciclo.

Responsabilidades novas/separadas:

- `OrigemOferta`: `HUB_AFILIADOS` ou `CUPOM`;
- `HubAffiliateOfferCollector`: somente coleta/normaliza o Hub;
- `CouponOfferCollector`: somente inicia o caminho catálogo -> containers -> candidatos CUPOM;
- `OfertaProcessingService`: consolida, deduplica, valida PDP, gera link, resolve imagem e publica;
- `MercadoLivreShareLinkResolver`: obtém o link de compartilhamento afiliado do PDP autenticado para candidatos CUPOM;
- `MercadoLivreShareLinkParser`: valida a ação `COPY_LINK`, o host e o tracking de afiliado, além de extrair o itemId final;
- `FonteDescobertaProdutoCupom`: rastreia `SSR_STRUCTURED`, `DOM_METADATA` ou `HREF_WID`.

O scheduler continua único. Os dois coletores rodam sequencialmente na mesma Page autenticada porque Playwright/Page e navegação compartilhada não são seguros para execução concorrente nesse desenho.

## 8. Extração de produtos e prioridade das fontes

Ordem implementada no `PlaywrightCouponEligibleProductsClient`:

```text
SSR estruturado (metadata.id / signal.item_id)
  -> metadata data-item-id do card no DOM
      -> wid=MLB... no href
```

O SSR prevalece em caso de repetição; DOM completa campos ausentes; `href/wid` é apenas fallback. A paginação é seguida até um dos limites configurados, e a URL anterior da Page é restaurada ao fim da coleta.

Logs resumidos, sem HTML ou sessão:

```text
[CONTAINER] campaignId=... containerId=...
[CONTAINER] páginas=... cards encontrados=...
[CONTAINER] itemIds SSR=... itemIds href=... itemIds finais=...
[CONTAINER] productId=... itemId=... href=<sanitizado> fonte=...
```

## 9. Deduplicação e gate PDP

A chave persistida `(fornecedor, itemId)` foi preservada. Dentro do ciclo, candidatos HUB e CUPOM são consolidados por `itemId`:

- se existe versão CUPOM e versão HUB, a CUPOM é avaliada primeiro;
- a participação comprovada pela listagem oficial mantém o cupom mesmo que o PDP não repita a campanha com confiança `HIGH`;
- o itemId final extraído do compartilhamento é usado para uma segunda verificação contra itens já selecionados/publicados;
- jamais são publicadas duas mensagens do mesmo item no mesmo ciclo;
- uma participação em container nunca substitui `CouponDecision` do PDP.

Não foi alterado o schema do banco nesta etapa. `origem` e `couponId/codigo` viajam no candidato/publicação, mas ainda não são persistidos em `ofertas_publicadas`. Isso é uma limitação documentada para evolução futura.

## 10. Configurações e controle de carga

```properties
akes.cupons.enabled=true
akes.cupons.cache-ttl-segundos=900
akes.cupons.max-paginas=5
akes.cupons.max-cupons-por-ciclo=10
akes.cupons.max-containers-por-ciclo=5
akes.cupons.max-ofertas-diretas-por-ciclo=20
akes.cupons.max-candidatos-compartilhamento=20
akes.cupons.container-error-cooldown-segundos=300
akes.cupons.max-paginas-produtos=4
akes.cupons.max-produtos-por-cupom=200
akes.cupons.timeout-ms=30000
akes.cupons.max-retries=1
akes.cupons.retry-backoff-ms=500
```

O catálogo e os containers usam cache; erros de container entram em cooldown. Navegações têm timeout, uma repetição moderada e backoff. Limites de cupons, containers, páginas, produtos, candidatos e links evitam varredura indiscriminada. A validação de PDP agora é sob demanda: assim que as 2–3 ofertas do ciclo obtêm compartilhamento, o pipeline para de abrir produtos e começa a publicar.

## 11. Testes

Os testes não dependem do Mercado Livre real. Cobrem:

- extração SSR, metadata DOM e fallback `wid`;
- distinção `productId`/`itemId`;
- fixture histórica `MLB4639510787` resolvida pelo SSR sem `wid`;
- paginação, deduplicação e preço do card;
- container vazio, estrutura alterada e restauração da Page;
- cache, erro/cooldown, cupom expirado e limite de cupons/containers;
- associação produto -> cupom e origem CUPOM;
- independência das fontes mesmo quando o Hub tem ofertas;
- prioridade CUPOM sobre HUB para o mesmo item;
- preservação do cupom oficial quando o PDP não repete confirmação `HIGH`;
- extração e validação do compartilhamento afiliado `COPY_LINK`;
- item já publicado, rejeição 111, falha do coletor e falha de publicação.

Resultado em 2026-09-10:

```text
Tests run: 356, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O diagnóstico autenticado, sem Telegram e sem escrita no banco, também foi executado após a implementação. Ele encontrou 44 cupons ativos, reuniu 200 candidatos oficiais em três campanhas e obteve no primeiro candidato uma ação `COPY_LINK` marcada como afiliado. A prévia da mensagem preservou o cupom da campanha 13618999.

## 12. Fluxo final

```text
OfertaScheduler (um ciclo, sem concorrência)
  -> abre uma Sessao/Page autenticada
  |
  +-> HubAffiliateOfferCollector
  |     -> MercadoLivreAffiliateOffersClient
  |     -> OfertaAfiliado(origem=HUB_AFILIADOS)
  |
  +-> CouponOfferCollector
        -> PlaywrightConsumerCouponClient
        -> campanhas SSR
        -> MercadoLivreCouponService
        -> PlaywrightCouponEligibleProductsClient
             SSR_STRUCTURED -> DOM_METADATA -> HREF_WID
        -> OfertaAfiliado(origem=CUPOM, cupom candidato)
  |
  v
OfertaProcessingService
  -> consolida por itemId (CUPOM tem prioridade de avaliação)
  -> OfertaPublicadaService / cooldown de rejeição
  -> para CUPOM: MercadoLivrePdpValidator
       -> atualiza itemId/preço
       -> preserva a associação da listagem oficial
  -> para CUPOM: MercadoLivreShareLinkResolver
       -> ação COPY_LINK autenticada e marcada como afiliado
  -> para HUB: AffiliateLinkService/createLink
  -> para HUB: validação PDP normal
  -> resolve imagem
  -> PublicadorOfertaService
  -> banco
  -> Telegram
```

## 13. Riscos e dívida técnica

- SSR, compartilhamento e `createLink` são contratos privados/instáveis do Mercado Livre.
- Campanhas, containers e itens participantes mudam ao longo do tempo; 13558453 é um exemplo comprovado de deriva.
- O parser legado de `/cupons/api/main-data/filtered` deve continuar inativo enquanto esse request não for observado novamente.
- O link afiliado não está no card: ele depende da sessão autenticada e do estado de compartilhamento do PDP.
- `origem`, `couponId`, código e `verifiedAt` não são persistidos na tabela de publicações.
- O cache de campanhas continua em memória e com TTL de 15 minutos.
- A coleta depende de uma sessão manual válida e deve parar diante de login, CAPTCHA, MFA ou bloqueio; não existe bypass.
