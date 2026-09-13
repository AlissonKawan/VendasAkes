package br.com.akesofertas.cupons.infra;

import br.com.akesofertas.cupons.evidence.OfferSnapshot;
import br.com.akesofertas.cupons.evidence.PriceEvidence;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Captura uma fotografia item-específica usando a mesma Page Playwright persistente da sessão. */
public final class MercadoLivrePdpValidator {
    private static final Logger log = LoggerFactory.getLogger(MercadoLivrePdpValidator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Page page;
    private final Object pageLock;
    private final Clock clock;

    public MercadoLivrePdpValidator(Page page, Object pageLock) {
        this(page, pageLock, Clock.systemUTC());
    }

    MercadoLivrePdpValidator(Page page, Object pageLock, Clock clock) {
        this.page = Objects.requireNonNull(page, "page é obrigatória");
        this.pageLock = Objects.requireNonNull(pageLock, "pageLock é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
    }

    public OfferSnapshot validate(String itemId, String productId, String productUrl, int quantity) {
        if (itemId == null || itemId.isBlank() || productUrl == null || productUrl.isBlank() || quantity < 1) {
            return unavailable(itemId, productId, productUrl);
        }
        synchronized (pageLock) {
            String previousUrl = page.url();
            try {
                String requestedUrl = preserveItemContext(productUrl, itemId);
                log.info("[PDP] itemId solicitado={} productId={} quantity={}", itemId, productId, quantity);
                page.navigate(requestedUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                String finalUrl = page.url();
                PagePayload main = capturePagePayload();

                String couponSsr = null;
                if (main.couponUrl() != null && !main.couponUrl().isBlank()) {
                    String couponUrl = withQuantity(main.couponUrl(), quantity);
                    page.navigate(couponUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    couponSsr = ssrText();
                }

                OfferSnapshot snapshot = MercadoLivrePdpEvidenceParser.parse(
                        itemId, productId, finalUrl, main.ssr(), couponSsr,
                        main.jsonLdProduct(), main.metaPrice(), main.domPrice(), quantity, clock.instant());
                log.info("[PDP] itemId solicitado={} itemId confirmado={} productId={} basePrice={} currentPrice={} paymentContext={}",
                        itemId, snapshot.itemId(), snapshot.productId(), snapshot.price().basePrice(),
                        snapshot.price().currentPrice(), snapshot.price().paymentContext());
                return snapshot;
            } catch (RuntimeException exception) {
                log.warn("[PDP] indisponível itemId={} tipo={} mensagem={}",
                        itemId, exception.getClass().getSimpleName(), exception.getMessage());
                return unavailable(itemId, productId, productUrl);
            } finally {
                restore(previousUrl);
            }
        }
    }

    private PagePayload capturePagePayload() {
        Object raw = page.evaluate("""
                () => JSON.stringify({
                  ssr: document.getElementById('__NORDIC_RENDERING_CTX__')?.textContent || '',
                  jsonLdProduct: (() => {
                    for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
                      try {
                        const value = JSON.parse(script.textContent || '{}');
                        if (value && value['@type'] === 'Product') return JSON.stringify(value);
                      } catch (_) {}
                    }
                    return null;
                  })(),
                  metaPrice: document.querySelector('meta[itemprop="price"]')?.content || null,
                  domPrice: document.querySelector('[itemprop="price"]')?.getAttribute('content') || null,
                  couponUrl: Array.from(document.querySelectorAll('a[href]'))
                    .map(a => a.href).find(href => href.includes('/cupons/pdp')) || null
                })
                """);
        try {
            JsonNode root = JSON.readTree(raw == null ? "{}" : raw.toString());
            return new PagePayload(root.path("ssr").asText(""), root.path("jsonLdProduct").asText(null),
                    root.path("metaPrice").asText(null), root.path("domPrice").asText(null),
                    root.path("couponUrl").asText(null));
        } catch (Exception exception) {
            throw new IllegalStateException("Resposta do PDP inválida", exception);
        }
    }

    private String ssrText() {
        Object raw = page.evaluate("() => document.getElementById('__NORDIC_RENDERING_CTX__')?.textContent || ''");
        return raw == null ? "" : raw.toString();
    }

    public static String preserveItemContext(String productUrl, String itemId) {
        String upperUrl = productUrl.toUpperCase();
        String upperItem = itemId.toUpperCase();
        if (upperUrl.contains("WID=" + upperItem)
                || upperUrl.contains("ITEM_ID:" + upperItem)
                || upperUrl.contains("ITEM_ID%3A" + upperItem)
                || upperItem.matches("MLB\\d+")
                && upperUrl.contains("/MLB-" + upperItem.substring(3))) {
            return productUrl;
        }
        String separator = productUrl.contains("?") ? "&" : "?";
        return productUrl + separator + "pdp_filters="
                + URLEncoder.encode("item_id:" + itemId, StandardCharsets.UTF_8);
    }

    static String withQuantity(String couponUrl, int quantity) {
        if (couponUrl.matches(".*([?&])quantity=\\d+.*")) {
            return couponUrl.replaceFirst("([?&])quantity=\\d+", "$1quantity=" + quantity);
        }
        return couponUrl + (couponUrl.contains("?") ? "&" : "?") + "quantity=" + quantity;
    }

    private void restore(String previousUrl) {
        if (previousUrl == null || previousUrl.isBlank() || previousUrl.equals(page.url())) return;
        try {
            page.navigate(previousUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        } catch (RuntimeException exception) {
            log.warn("[PDP] Não foi possível restaurar a página anterior");
        }
    }

    private OfferSnapshot unavailable(String itemId, String productId, String productUrl) {
        URI uri;
        try { uri = productUrl == null ? null : URI.create(productUrl); }
        catch (IllegalArgumentException exception) { uri = null; }
        return new OfferSnapshot(itemId, null, productId, uri,
                new PriceEvidence(null, null, null, "UNAVAILABLE", "BRL", Set.of()),
                List.of(), List.of(), Instant.now(clock));
    }

    private record PagePayload(String ssr, String jsonLdProduct, String metaPrice,
                               String domPrice, String couponUrl) {}
}
