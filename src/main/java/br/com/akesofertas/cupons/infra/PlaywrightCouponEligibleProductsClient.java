package br.com.akesofertas.cupons.infra;

import br.com.akesofertas.cupons.client.CouponClientException;
import br.com.akesofertas.cupons.client.CouponEligibleProductsClient;
import br.com.akesofertas.cupons.client.TipoErroInfraestruturaCupom;
import br.com.akesofertas.cupons.domain.FonteDescobertaProdutoCupom;
import br.com.akesofertas.cupons.domain.ProdutoElegivelCupom;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

/** Percorre a listagem oficial de uma campanha usando a Page autenticada recebida. */
public final class PlaywrightCouponEligibleProductsClient implements CouponEligibleProductsClient {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightCouponEligibleProductsClient.class);
    private static final Pattern ITEM_ID_WID = Pattern.compile("[?&#]wid=(MLB\\d+)(?=[&#]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_ID_PATH = Pattern.compile("/(MLB)-(\\d+)(?=\\D|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRODUCT_ID_PATH = Pattern.compile("/p/(MLB\\d+)(?=[/?#]|$)", Pattern.CASE_INSENSITIVE);
    private static final String NEXT_SELECTOR = "li.andes-pagination__button--next"
            + ":not(.andes-pagination__button--disabled) a.andes-pagination__link";

    private final Page page;
    private final Object pageLock;
    private final ObjectMapper json = new ObjectMapper();
    private final int maxPaginas;
    private final int maxProdutos;
    private final int timeoutMs;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final LongConsumer pausa;

    public PlaywrightCouponEligibleProductsClient(Page page) {
        this(page, new Object());
    }

    public PlaywrightCouponEligibleProductsClient(Page page, Object pageLock) {
        this(page, pageLock, 20, Integer.MAX_VALUE, 30_000, 1, 500,
                PlaywrightCouponEligibleProductsClient::sleep);
    }

    public PlaywrightCouponEligibleProductsClient(Page page, Object pageLock, int maxPaginas, int maxProdutos,
                                                   int timeoutMs, int maxRetries, long retryBackoffMs,
                                                   LongConsumer pausa) {
        if (page == null) throw new IllegalArgumentException("page é obrigatória");
        if (pageLock == null) throw new IllegalArgumentException("pageLock é obrigatório");
        if (maxPaginas < 1) throw new IllegalArgumentException("maxPaginas deve ser positivo");
        if (maxProdutos < 1) throw new IllegalArgumentException("maxProdutos deve ser positivo");
        if (timeoutMs < 1) throw new IllegalArgumentException("timeoutMs deve ser positivo");
        if (maxRetries < 0 || maxRetries > 3) throw new IllegalArgumentException("maxRetries deve estar entre 0 e 3");
        if (retryBackoffMs < 0) throw new IllegalArgumentException("retryBackoffMs não pode ser negativo");
        this.page = page;
        this.pageLock = pageLock;
        this.maxPaginas = maxPaginas;
        this.maxProdutos = maxProdutos;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
        this.pausa = pausa != null ? pausa : ignored -> {};
    }

    @Override
    public List<ProdutoElegivelCupom> buscarProdutos(long couponId, String productsUrl) {
        validarEntrada(couponId, productsUrl);
        synchronized (pageLock) {
            return buscarComRestauracao(couponId, productsUrl);
        }
    }

    private List<ProdutoElegivelCupom> buscarComRestauracao(long couponId, String productsUrl) {
        String previousUrl = page.url();
        RuntimeException primaryFailure = null;
        try {
            var products = new LinkedHashMap<String, ProdutoElegivelCupom>();
            var visited = new HashSet<String>();
            int pages = 0;
            int totalSsr = 0;
            int totalHref = 0;
            int totalCards = 0;
            String detectedContainerId = null;
            String navigationUrl = mercadoLivreDestination(productsUrl);
            withRetry(() -> navigate(navigationUrl), "abrir container");
            String currentUrl = page.url();
            ProdutoElegivelCupom produtoDireto = readDirectProduct(couponId, navigationUrl, currentUrl);
            if (produtoDireto != null) {
                log.info("[CONTAINER] campaignId={} produto direto itemId={} fonte={}",
                        couponId, produtoDireto.itemId(), produtoDireto.fonteItemId());
                return List.of(produtoDireto);
            }
            while (pages < maxPaginas && products.size() < maxProdutos) {
                if (!visited.add(currentUrl)) throw structureChanged("Paginação de produtos entrou em ciclo");
                PageSnapshot snapshot = readPage();
                pages++;
                totalSsr += snapshot.ssrCards();
                totalHref += snapshot.hrefCards();
                totalCards += snapshot.cards().size();
                if (detectedContainerId == null) detectedContainerId = snapshot.containerId();
                if (snapshot.cards().isEmpty() && snapshot.rawCards() == 0
                        && snapshot.hasResults() && !snapshot.emptyState()) {
                    throw structureChanged("Página indica resultados, mas não contém cards estruturados nem hrefs reconhecidos");
                }
                for (CardSnapshot card : snapshot.cards()) {
                    if (card.itemId() == null || products.size() >= maxProdutos) continue;
                    products.putIfAbsent(card.itemId(), new ProdutoElegivelCupom(
                            couponId, card.itemId(), card.productId(), card.title(), card.href(), card.imageUrl(),
                            card.precoAtual(), card.source()));
                }
                if (!snapshot.hasNext() || pages >= maxPaginas || products.size() >= maxProdutos) break;
                withRetry(this::clickNext, "avançar paginação do container");
                currentUrl = page.url();
            }
            log.info("[CONTAINER] campaignId={} containerId={} páginas={} cards encontrados={} itemIds SSR={} itemIds href={} itemIds finais={}",
                    couponId, detectedContainerId != null ? detectedContainerId : containerId(productsUrl),
                    pages, totalCards, totalSsr, totalHref, products.size());
            products.values().stream().limit(5).forEach(p -> log.info(
                    "[CONTAINER] productId={} itemId={} href={} fonte usada para descobrir itemId={}",
                    p.productId(), p.itemId(), urlSeguraParaLog(p.productUrl()), p.fonteItemId()));
            return List.copyOf(products.values());
        } catch (RuntimeException exception) {
            primaryFailure = sanitize(exception);
            throw primaryFailure;
        } finally {
            try {
                restore(previousUrl);
            } catch (RuntimeException restoreFailure) {
                log.warn("[COUPONS] Não foi possível restaurar a Page após consultar produtos");
                if (primaryFailure == null) throw infrastructure("Falha ao restaurar a Page da sessão");
            }
        }
    }

    private ProdutoElegivelCupom readDirectProduct(long couponId, String originalUrl, String navigatedUrl) {
        String href = directProductUrl(navigatedUrl);
        if (href == null) href = directProductUrl(originalUrl);
        if (href == null) return null;
        String directHref = href;
        if (!directHref.equals(page.url())) {
            withRetry(() -> navigate(directHref), "abrir produto indicado pela campanha");
        }

        String itemId = extractItemId(directHref);
        if (itemId == null) itemId = extractItemId(page.url());
        if (itemId == null) return null;

        Object result = page.evaluate("""
                () => ({
                    title: (document.querySelector('h1')?.textContent
                        || document.querySelector('meta[property="og:title"]')?.content || '').trim(),
                    imageUrl: document.querySelector('meta[property="og:image"]')?.content || '',
                    price: document.querySelector('meta[itemprop="price"]')?.content || '',
                    fraction: (document.querySelector('.ui-pdp-price__second-line .andes-money-amount__fraction')?.textContent
                        || document.querySelector('.andes-money-amount__fraction')?.textContent || '').trim(),
                    cents: (document.querySelector('.ui-pdp-price__second-line .andes-money-amount__cents')?.textContent
                        || document.querySelector('.andes-money-amount__cents')?.textContent || '').trim()
                })
                """);
        JsonNode root = json.valueToTree(result);
        BigDecimal price = decimal(root.path("price"));
        if (price == null) price = parsePreco(root.path("fraction").asText(""), root.path("cents").asText(""));
        return new ProdutoElegivelCupom(couponId, itemId, extractProductId(page.url()),
                root.path("title").asText(""), page.url(), root.path("imageUrl").asText(null), price,
                FonteDescobertaProdutoCupom.DIRECT_URL);
    }

    private PageSnapshot readPage() {
        Object result = page.evaluate("""
                () => {
                    const money = value => {
                        if (typeof value === 'number' && Number.isFinite(value)) return value;
                        if (typeof value === 'string' && value.trim() !== '') {
                            const parsed = Number(value.replace(/\\./g, '').replace(',', '.'));
                            return Number.isFinite(parsed) ? parsed : null;
                        }
                        return null;
                    };
                    const buildUrl = metadata => {
                        const base = metadata?.url || '';
                        if (!base) return '';
                        const absolute = /^https?:\\/\\//i.test(base) ? base : `https://${base.replace(/^\\/\\//, '')}`;
                        return absolute + (metadata.url_params || '') + (metadata.url_fragments || '');
                    };
                    const parseRenderingContext = () => {
                        try {
                            if (window._n?.ctx?.r) return window._n.ctx.r;
                            const text = document.getElementById('__NORDIC_RENDERING_CTX__')?.textContent || '';
                            const start = text.indexOf('{');
                            if (start < 0) return null;
                            let depth = 0, inString = false, escape = false, end = -1;
                            for (let i = start; i < text.length; i++) {
                                const ch = text[i];
                                if (inString) {
                                    if (escape) escape = false;
                                    else if (ch === '\\\\') escape = true;
                                    else if (ch === '"') inString = false;
                                    continue;
                                }
                                if (ch === '"') { inString = true; continue; }
                                if (ch === '{') depth++;
                                else if (ch === '}' && --depth === 0) { end = i + 1; break; }
                            }
                            return end > start ? JSON.parse(text.substring(start, end)) : null;
                        } catch (_) { return null; }
                    };
                    const rendering = parseRenderingContext();
                    const results = rendering?.appProps?.pageProps?.initialState?.results;
                    const serializedResults = Array.isArray(results) ? JSON.stringify(results) : '';
                    const containerMatch = serializedResults.match(/deal(?:%3A|:)(MLB\\d+)/i);
                    const ssrCards = Array.isArray(results) ? results.map(result => {
                        const polycard = result?.polycard || {};
                        const metadata = polycard.metadata || {};
                        const titleComponent = (polycard.components || []).find(c => c?.type === 'title' || c?.id === 'title');
                        const priceComponent = (polycard.components || []).find(c => c?.type === 'price');
                        return {
                            itemId: metadata.id || metadata.signal?.item_id || '',
                            productId: metadata.product_id || '',
                            title: titleComponent?.title?.text || '',
                            href: buildUrl(metadata),
                            imageUrl: '',
                            price: money(metadata.signal?.price ?? priceComponent?.price?.current_price?.value)
                        };
                    }).filter(card => /^MLB\\d+$/i.test(card.itemId) && card.href) : [];

                    const cards = [...document.querySelectorAll('a.poly-component__title')].map(a => {
                        const container = a.closest('.poly-card, .ui-search-result, .ui-search-layout__item, li') || a.parentElement;
                        const metadataItem = container?.getAttribute('data-item-id')
                            || container?.querySelector('[data-item-id]')?.getAttribute('data-item-id') || '';
                        const fraction = (container?.querySelector('.andes-money-amount__fraction')?.textContent || '').trim();
                        const cents = (container?.querySelector('.andes-money-amount__cents')?.textContent || '').trim();
                        const image = container?.querySelector('img');
                        return {
                            itemId: metadataItem,
                            title: (a.textContent || '').trim(),
                            href: a.href || '',
                            imageUrl: image?.currentSrc || image?.src || '',
                            fraction,
                            cents
                        };
                    });
                    const next = document.querySelector(
                        'li.andes-pagination__button--next:not(.andes-pagination__button--disabled) a.andes-pagination__link');
                    const bodyText = (document.body?.innerText || '').toLocaleLowerCase('pt-BR');
                    const hasResults = /\\b\\d[\\d.]*\\s+resultados?\\b/i.test(bodyText)
                        || Boolean(document.querySelector('.ui-search-results, .ui-search-layout, [class*="search-results"]'));
                    const emptyState = /não (?:encontramos|há) resultados|nenhum resultado/i.test(bodyText)
                        || Boolean(document.querySelector('.ui-search-rescue, [class*="empty-state"]'));
                    return {ssrCards, cards, hasNext: Boolean(next), hasResults, emptyState,
                        containerId: containerMatch ? containerMatch[1].toUpperCase() : null};
                }
                """);
        try {
            JsonNode root = json.valueToTree(result);
            if (root == null || (!root.path("cards").isArray() && !root.path("ssrCards").isArray())) {
                throw structureChanged("Página não retornou uma estrutura de coleta válida");
            }
            var merged = new LinkedHashMap<String, CardSnapshot>();
            int ssrCards = addSsrCards(root.path("ssrCards"), merged);
            int hrefCards = addDomCards(root.path("cards"), merged);
            return new PageSnapshot(List.copyOf(merged.values()), root.path("hasNext").asBoolean(false),
                    root.path("hasResults").asBoolean(false), root.path("emptyState").asBoolean(false),
                    ssrCards, hrefCards, root.path("ssrCards").size() + root.path("cards").size(),
                    root.path("containerId").asText(null));
        } catch (CouponClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw structureChanged("Página não retornou uma estrutura de coleta válida");
        }
    }

    private int addSsrCards(JsonNode nodes, LinkedHashMap<String, CardSnapshot> merged) {
        if (!nodes.isArray()) return 0;
        int count = 0;
        for (JsonNode node : nodes) {
            String itemId = validMlb(node.path("itemId").asText(null));
            String href = node.path("href").asText("");
            if (itemId == null || href.isBlank()) continue;
            count++;
            merged.putIfAbsent(itemId, new CardSnapshot(itemId, validMlb(node.path("productId").asText(null)),
                    node.path("title").asText(""), href, node.path("imageUrl").asText(null),
                    decimal(node.path("price")), FonteDescobertaProdutoCupom.SSR_STRUCTURED));
        }
        return count;
    }

    private int addDomCards(JsonNode nodes, LinkedHashMap<String, CardSnapshot> merged) {
        if (!nodes.isArray()) return 0;
        int count = 0;
        for (JsonNode node : nodes) {
            if (!node.path("href").isString()) continue;
            String href = node.path("href").asText();
            String metadataItem = validMlb(node.path("itemId").asText(null));
            String itemId = metadataItem != null ? metadataItem : extractItemId(href);
            if (itemId == null) continue;
            count++;
            FonteDescobertaProdutoCupom source = metadataItem != null
                    ? FonteDescobertaProdutoCupom.DOM_METADATA : FonteDescobertaProdutoCupom.HREF_WID;
            CardSnapshot dom = new CardSnapshot(itemId, extractProductId(href), node.path("title").asText(""), href,
                    node.path("imageUrl").asText(null), parsePreco(node.path("fraction").asText(""),
                    node.path("cents").asText("")), source);
            merged.merge(itemId, dom, PlaywrightCouponEligibleProductsClient::preferStructured);
        }
        return count;
    }

    private static CardSnapshot preferStructured(CardSnapshot structured, CardSnapshot fallback) {
        return new CardSnapshot(structured.itemId(), first(structured.productId(), fallback.productId()),
                first(structured.title(), fallback.title()), first(structured.href(), fallback.href()),
                first(structured.imageUrl(), fallback.imageUrl()),
                structured.precoAtual() != null ? structured.precoAtual() : fallback.precoAtual(), structured.source());
    }

    private static String first(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
            return value.signum() > 0 ? value.setScale(2, java.math.RoundingMode.HALF_UP) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static BigDecimal parsePreco(String fraction, String cents) {
        if (fraction == null || fraction.isBlank()) return null;
        try {
            String limpo = fraction.replace(".", "").trim();
            if (limpo.isBlank()) return null;
            if (cents != null && !cents.isBlank()) limpo = limpo + "." + cents.trim();
            BigDecimal preco = new BigDecimal(limpo).setScale(2, java.math.RoundingMode.HALF_UP);
            return preco.signum() > 0 ? preco : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String extractItemId(String url) {
        if (url == null || url.isBlank()) return null;
        var matcher = ITEM_ID_WID.matcher(url);
        if (matcher.find()) return matcher.group(1).toUpperCase(Locale.ROOT);
        matcher = ITEM_ID_PATH.matcher(url);
        return matcher.find() ? (matcher.group(1) + matcher.group(2)).toUpperCase(Locale.ROOT) : null;
    }

    static String directProductUrl(String value) {
        String destination = mercadoLivreDestination(value);
        return extractItemId(destination) != null ? destination : null;
    }

    static String mercadoLivreDestination(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if (query == null) return value;
            for (String part : query.split("&")) {
                int equals = part.indexOf('=');
                if (equals <= 0 || !"go".equalsIgnoreCase(part.substring(0, equals))) continue;
                String decoded = URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8);
                URI destination = URI.create(decoded);
                String host = destination.getHost();
                if (validHttpUrl(decoded) && host != null
                        && (host.equalsIgnoreCase("mercadolivre.com.br")
                        || host.toLowerCase(Locale.ROOT).endsWith(".mercadolivre.com.br"))) {
                    return decoded;
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return value;
    }

    static String extractProductId(String url) {
        if (url == null || url.isBlank()) return null;
        var matcher = PRODUCT_ID_PATH.matcher(url);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private void withRetry(Runnable action, String operation) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                return;
            } catch (PlaywrightException exception) {
                last = exception;
                if (attempt >= maxRetries || Thread.currentThread().isInterrupted()) break;
                log.warn("[COUPONS] Falha transitória ao {}; tentativa {}/{}", operation, attempt + 1, maxRetries + 1);
                pausa.accept(retryBackoffMs * (attempt + 1L));
            }
        }
        throw last != null ? last : infrastructure("Operação do navegador não concluída");
    }

    private void navigate(String url) {
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD).setTimeout((double) timeoutMs));
    }

    private void clickNext() {
        Locator next = page.locator(NEXT_SELECTOR);
        next.click(new Locator.ClickOptions().setTimeout((double) timeoutMs));
    }

    private void restore(String previousUrl) {
        if (previousUrl == null || previousUrl.isBlank() || previousUrl.equals(page.url())) return;
        page.navigate(previousUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout((double) timeoutMs));
    }

    private static void validarEntrada(long couponId, String productsUrl) {
        if (couponId <= 0) throw new IllegalArgumentException("couponId deve ser positivo");
        if (!validHttpUrl(productsUrl)) throw new IllegalArgumentException("productsUrl deve ser uma URL HTTP válida");
    }

    private static boolean validHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String validMlb(String value) {
        if (value == null || !value.matches("(?i)MLB\\d+")) return null;
        return value.toUpperCase(Locale.ROOT);
    }

    private static String containerId(String url) {
        if (url == null) return null;
        var matcher = Pattern.compile("(?i)_Container_([^/?#]+)").matcher(url);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static String urlSeguraParaLog(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            String itemId = extractItemId(value);
            return uri.getScheme() + "://" + uri.getHost() + uri.getPath()
                    + (itemId != null ? "?wid=" + itemId : "");
        } catch (IllegalArgumentException exception) {
            return "URL_INVALIDA";
        }
    }

    private static RuntimeException sanitize(RuntimeException exception) {
        if (exception instanceof CouponClientException) return exception;
        if (exception instanceof IllegalArgumentException) return exception;
        if (exception instanceof PlaywrightException) return infrastructure("Navegador falhou ao consultar produtos do cupom");
        return infrastructure("Consulta de produtos do cupom não foi concluída");
    }

    private static CouponClientException structureChanged(String message) {
        return new CouponClientException(TipoErroInfraestruturaCupom.ESTRUTURA_HTML_ALTERADA, message);
    }

    private static CouponClientException infrastructure(String message) {
        return new CouponClientException(TipoErroInfraestruturaCupom.INFRAESTRUTURA, message);
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record CardSnapshot(String itemId, String productId, String title, String href, String imageUrl,
                                BigDecimal precoAtual, FonteDescobertaProdutoCupom source) {}
    private record PageSnapshot(List<CardSnapshot> cards, boolean hasNext, boolean hasResults, boolean emptyState,
                                int ssrCards, int hrefCards, int rawCards, String containerId) {}
}
