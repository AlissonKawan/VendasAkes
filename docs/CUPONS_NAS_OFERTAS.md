# Cupons nas ofertas (Consumidor)

O ciclo mantém duas fontes independentes: o Hub de Afiliados e a página oficial de cupons.
A identidade da oferta continua sendo fornecedor + itemId. Quando as duas fontes encontram o mesmo item, a versão CUPOM vence e a mensagem inclui o cupom.

O Hub continua usando `createLink`. Produtos descobertos na página de cupons não passam por esse endpoint: o bot abre cada produto com a sessão autenticada e extrai o link da ação `Compartilhar` / `Copiar link`, depois resolve imagem, deduplica e envia normalmente.

## Arquitetura e Fluxo

1. **Origem dos Cupons:**
   - Obtidos diretamente da página oficial de cupons (`https://www.mercadolivre.com.br/cupons`) via SSR hydration (`__NORDIC_RENDERING_CTX__`).
   - Cliente Playwright: `PlaywrightConsumerCouponClient` implementando `MercadoLivreConsumerCouponClient`.

2. **Elegibilidade Objetiva:**
   - `CupomElegibilidadeService`: um cupom é associado à oferta quando houver prova objetiva de participação:
     - `item_ids` explícitos retornados pelo Mercado Livre contêm o itemId da oferta; ou
     - a listagem oficial de produtos da campanha (`container_url`) contém o itemId da oferta (resolvido via `PlaywrightCouponEligibleProductsClient`, até quatro páginas por padrão).
   - Nenhuma correspondência é feita por inferência de título ou categoria.

3. **Cálculo de Desconto:**
   - `CalculadoraDescontoCupom`: calcula o desconto aplicado respeitando valor mínimo de compra (`minimumPurchase`) e teto máximo de desconto (`maxDiscount`).
   - Suporta descontos percentuais e fixos.

4. **Formatação de Mensagens:**
   - `MensagemOfertaService`: formata o cupom na mensagem do Telegram:
     - Com código: `🎟 Cupom: <b>CODIGO</b>`
     - Sem código (ativação no checkout/clique): `🎟 <b>CUPOM DISPONÍVEL</b>` + `👉 Ative o cupom no Mercado Livre`
     - Desconto: `💸 10% OFF` ou `💸 R$ 20,00 OFF`
     - Estimativa: `Com cupom (estimado): <b>R$ 180,00</b>`

5. **Cache em Memória:**
   - `MercadoLivreCouponService` gerencia cache com TTL configurável (`akes.cupons.cache-ttl-segundos`, padrão 900s).

6. **Link do produto de cupom:**
   - `MercadoLivreShareLinkResolver` abre o PDP preservando `wid`, lê o estado SSR do compartilhamento e aceita somente a ação `COPY_LINK` marcada pelo Mercado Livre como `user_type=affiliates`.
   - O `itemId` final resolvido no PDP é usado na deduplicação, pois ele pode ser diferente do anúncio presente na URL inicial.

## Diagnóstico

Para testar ponta a ponta sem enviar Telegram e sem alterar banco:
```powershell
powershell -ExecutionPolicy Bypass -File .\consultar-cupons-consumidor.ps1
```
