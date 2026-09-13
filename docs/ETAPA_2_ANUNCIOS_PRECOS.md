# Etapa 2 — ofertas pela buy box e preços oficiais

## Fluxo implementado

`GET /api/ofertas/buscar?q=mouse&limite=10`

1. `ProdutoBuscaService` busca vários candidatos em `/products/search` (etapa 1).
2. `OfertaBuscaService` encaminha cada candidato ao `OfertaProvider` correspondente.
3. `MercadoLivreOfertaProvider` consulta `/products/{PRODUCT_ID}` com OAuth.
4. `buy_box_winner: null` descarta somente esse candidato e continua. Campo ausente
   ou malformado gera descarte com motivo diferente.
5. Exige catálogo ativo, `item_id` brasileiro, `seller_id` válido e permalink oficial
   HTTPS utilizável. Quantidade zero/negativa descarta; quantidade ausente fica nula.
6. Consulta `/items/{ITEM_ID}/prices` com o ID do vencedor, nunca o ID do catálogo.
7. Confirma valor/contexto e cria `OfertaEncontrada`, ou informa o motivo do descarte.

`limite` controla candidatos, não garante essa quantidade de ofertas: aceita 1–20,
com padrão 10. Não há paginação automática, repetição de requisições ou ranking novo:
a seleção do vencedor é do Mercado Livre. Cada catálogo é consultado uma vez por
requisição. Novos marketplaces implementam `ProdutoProvider` e `OfertaProvider`.

A resposta contém `candidatosAvaliados`, `ofertas` e `candidatos` (ID,
`ofertaUtilizavel`, `motivo`). Se todos forem descartados, retorna HTTP 200 com
`ofertas: []` e os motivos. Isso não é uma falha geral da busca.

## Preços e campos

Cada oferta contém IDs do catálogo/anúncio/vendedor, título, imagem, URL oficial da
página de produto, preço atual, moeda, referência regular quando comprovada, preço
original da buy box, indicador promocional, desconto e ID do preço confirmado.
A URL é o permalink oficial do catálogo; não se afirma que ela fixe o vendedor no
checkout. `anuncioId` e `vendedorId` identificam separadamente o anúncio avaliado.

`entrega` mapeia `shipping.free_shipping`, `store_pick_up`, `mode`, `logistic_type`
e `tags`. `quantidadeDisponivel` preserva `available_quantity` quando informado.
Quantidades públicas podem ser referenciais, não contagem exata de estoque.

Regras conservadoras de `MercadoLivrePrecoOferta`:

- Preço positivo em BRL; resposta de preços deve corresponder ao anúncio solicitado.
  Exige uma única entrada aplicável com valor e moeda iguais aos da buy box.
- Aceita `standard` ou `promotion`, com `conditions.context_restrictions` vazio ou
  somente `channel_marketplace`. Não escolhe o menor valor da lista.
- Não aceita preços de fidelidade, outros canais, empresas, quantidade mínima maior
  que uma unidade, preços líquidos sem impostos ou condições desconhecidas.
- Respeita início/fim quando retornados. Datas podem ser ocultadas para terceiros;
  ausência não comprova vigência ilimitada. O valor também precisa coincidir com a
  buy box consultada nesta requisição.
- Usa `regular_amount` do preço correspondente, ou `original_price` da mesma buy box
  quando não houver regular_amount. Se ambos existirem e divergirem, descarta por
  cautela: referências oficiais distintas podem ter bases diferentes.
- Nunca transforma outra entrada standard em preço anterior. Referências ausentes
  permanecem nulas; referências inválidas não geram desconto.
- Percentual = `(regular - atual) / regular * 100`, com duas casas, somente quando
  `regular > atual > 0`. Promotion sem referência pode ser promocional, com desconto
  nulo. Preço standard confiável também pode ser retornado; ainda não foi definido
  um percentual mínimo comercial de “boa oferta”.

Os valores são uma fotografia de consultas que não são atômicas: preço pode mudar
depois. Esta etapa não publica nem reserva um preço.

## Erros e diagnóstico opcional

404 no detalhe ou nos preços descarta apenas o candidato, identificando o recurso.
401 interrompe com HTTP 401; recusa 403, limite 429, falha remota/contrato inválido ou
comunicação interrompem com HTTP 502 e mensagem local. Não são convertidos em sucesso
vazio nem disparam tentativas de contornar o acesso. Nesses erros não é retornada uma
lista parcial como se a busca tivesse sido concluída.

`GET /api/produtos/{PRODUCT_ID}/diagnostico-ofertas?limite=3` permanece apenas como
diagnóstico de concorrentes. 404 em `/products/{id}/items` significa “produto sem
listagem consultável por esse recurso”. A busca principal não chama essa rota.

## Evidência real e validação

Respostas fornecidas pelo usuário antes desta alteração:

| Catálogo | Detalhe oficial | buy_box_winner | permalink | /products/{id}/items |
| --- | --- | --- | --- | --- |
| MLB47622919 | 200, active | null | vazio | 404 not_found |
| MLB73798525 | 200, active | null | vazio | 404 not_found |

Esses candidatos não têm oferta utilizável pelos dados observados. O 404 não comprova
falha em todos os catálogos ou no OAuth. Não foram obtidos ITEM_IDs nem preços reais.

Os testes automatizados usam respostas simuladas. Cobrem candidato nulo seguido de
vencedor válido, todos descartados, 404 individual, preços/contextos divergentes,
ausência de referência/estoque/frete, ausência de consultas aos concorrentes e
interrupção em 401/403/429/500. Ainda falta validar uma buy box positiva com esta
conta real. Preços dos testes não representam ofertas reais.

```powershell
.\mvnw.cmd -B -ntp test
```

Para testar a aplicação atualizada:

1. Pare a instância antiga com Ctrl+C no terminal onde ela roda e execute
   `.\iniciar-oauth.ps1`. Mantenha o ngrok apontando para a porta 8080.
2. Abra `https://droop-juncture-june.ngrok-free.dev/mercadolivre/auth` no navegador.
   O consentimento pode ser reaproveitado sem nova tela, mas o reinício perde os
   tokens da sessão Spring. Não abra uma segunda aplicação na mesma porta.
3. No mesmo navegador, abra
   `https://droop-juncture-june.ngrok-free.dev/api/ofertas/buscar?q=mouse&limite=10`.
4. Examine ofertas e descartes. Uma lista vazia com motivos é válida. Para conferir
   os dois casos conhecidos, use q=MLB47622919 ou q=MLB73798525 e limite=1.

Nenhum token/cookie é retornado por esse endpoint. Não é necessário compartilhá-los.

## Investigação separada: /sites/MLB/search?q=TERMO

Consulta documental em 05/09/2026. Não adicionado ao código nem usado como alternativa.

A documentação atual de [Itens e buscas](https://developers.mercadolivre.com.br/en_us/items-and-searches),
com atualização indicada em 04/04/2025, apresenta substituições para consultas por
vendedor: seller_id (com ou sem categoria) por `/users/{user_id}/items/search`;
a busca por nickname aparece sem substituição. O recurso privado de usuário consulta
anúncios da conta do vendedor, não um catálogo global de ofertas para afiliados.

Isso não comprova a descontinuação de toda variante q=TERMO. Também não encontramos
confirmação atual de suporte dessa busca global para o tipo de aplicação deste bot.
Exemplos antigos de GitHub não estabelecem contrato nem permissão atual.

**Conclusão: suporte ao nosso caso não confirmado.** Não adotar como dependência
definitiva. É necessária confirmação atual do Mercado Livre para este aplicativo e,
separadamente, validação de acesso autenticado. Não foi feito teste dessa rota com a
conta do usuário nesta alteração. Erro sem token não comprovaria disponibilidade ou
indisponibilidade para esta conta.

## Fontes oficiais

- [Buscador de produtos](https://developers.mercadolivre.com.br/buscador-de-produtos):
  busca de catálogo, detalhes e significado de buy box nula.
- [Competição em catálogo](https://developers.mercadolivre.com.br/pt_br/enderecos-do-usuario/concorrencia-em-catalogo):
  vencedor em /products/{id} e campos do anúncio.
- [Preços de produtos](https://developers.mercadolivre.com.br/pt_br/api-de-precos):
  preços, referências e contextos. Também documenta sale_price para resolução por
  contexto, não adicionado ao fluxo solicitado de /prices.
- [Preços por quantidade](https://developers.mercadolivre.com.br/pt_br/categorizacao-de-produtos/precos-por-quantidade):
  restrições de quantidade e público.

Esta alteração termina na consulta de ofertas. Não aciona afiliados, Telegram,
cupons, banco ou scheduler.
