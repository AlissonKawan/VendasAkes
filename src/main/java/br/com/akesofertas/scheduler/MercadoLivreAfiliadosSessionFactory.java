package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.AffiliateLinkService;
import br.com.akesofertas.afiliados.MercadoLivreAffiliateClient;
import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.NavegadorAfiliados;
import br.com.akesofertas.afiliados.ofertas.*;
import br.com.akesofertas.cupons.client.CouponEligibleProductsClient;
import br.com.akesofertas.cupons.infra.PlaywrightCouponEligibleProductsClient;
import br.com.akesofertas.cupons.infra.MercadoLivrePdpValidator;
import br.com.akesofertas.cupons.evidence.OfferSnapshot;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "akes.scheduler.enabled", havingValue = "true")
public class MercadoLivreAfiliadosSessionFactory {
    private final Path perfil;
    private final String navegador;
    private final String tag;
    private final int maximoPaginas;
    private final int couponMaxPaginas;
    private final int couponMaxProdutos;
    private final int couponTimeoutMs;
    private final int couponMaxRetries;
    private final long couponRetryBackoffMs;
    private final Semaphore sessaoExclusiva = new Semaphore(1, true);

    @org.springframework.beans.factory.annotation.Autowired
    public MercadoLivreAfiliadosSessionFactory(
            @Value("${AFILIADOS_PERFIL:.local/mercadolivre}") String perfil,
            @Value("${AFILIADOS_NAVEGADOR:chrome}") String navegador,
            @Value("${afiliados.tag:telegram}") String tag,
            @Value("${akes.ofertas.hub-max-paginas:5}") int maximoPaginas,
            @Value("${akes.cupons.max-paginas-produtos:4}") int couponMaxPaginas,
            @Value("${akes.cupons.max-produtos-por-cupom:200}") int couponMaxProdutos,
            @Value("${akes.cupons.timeout-ms:30000}") int couponTimeoutMs,
            @Value("${akes.cupons.max-retries:1}") int couponMaxRetries,
            @Value("${akes.cupons.retry-backoff-ms:500}") long couponRetryBackoffMs) {
        if (maximoPaginas < 1 || maximoPaginas > 20) {
            throw new IllegalArgumentException("akes.ofertas.hub-max-paginas deve estar entre 1 e 20");
        }
        this.perfil = Path.of(perfil);
        this.navegador = navegador;
        this.tag = tag;
        this.maximoPaginas = maximoPaginas;
        this.couponMaxPaginas = couponMaxPaginas;
        this.couponMaxProdutos = couponMaxProdutos;
        this.couponTimeoutMs = couponTimeoutMs;
        this.couponMaxRetries = couponMaxRetries;
        this.couponRetryBackoffMs = couponRetryBackoffMs;
    }

    public MercadoLivreAfiliadosSessionFactory(String perfil, String navegador, String tag, int maximoPaginas) {
        this(perfil, navegador, tag, maximoPaginas, 4, 200, 30_000, 1, 500);
    }

    public Sessao abrir() {
        try {
            sessaoExclusiva.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Abertura da sessão de afiliados interrompida", exception);
        }
        NavegadorAfiliados browser = null;
        try {
            browser = new NavegadorAfiliados(perfil, navegador, false);
            browser.pagina().navigate(MercadoLivreAffiliateOffersClient.HUB,
                    new com.microsoft.playwright.Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            return new Sessao(browser, new MercadoLivreAffiliateOffersClient(browser.pagina(),
                    new MercadoLivreAffiliateOfferMapper(), maximoPaginas),
                    new AffiliateLinkService(new MercadoLivreAffiliateClient(browser.pagina()), tag),
                    couponMaxPaginas, couponMaxProdutos, couponTimeoutMs, couponMaxRetries, couponRetryBackoffMs,
                    sessaoExclusiva::release);
        } catch (RuntimeException exception) {
            if (browser != null) browser.close();
            sessaoExclusiva.release();
            throw exception;
        }
    }

    public static class Sessao implements AutoCloseable {
        private final NavegadorAfiliados navegador;
        private final MercadoLivreAffiliateOffersClient ofertas;
        private final AffiliateLinkService links;
        private final MercadoLivreImagemProdutoResolver imagens;
        private final CouponEligibleProductsClient produtosCupons;
        private final MercadoLivrePdpValidator pdpValidator;
        private final Object pageLock = new Object();
        private final Runnable aoFechar;
        private final AtomicBoolean fechada = new AtomicBoolean();

        Sessao(NavegadorAfiliados navegador, MercadoLivreAffiliateOffersClient ofertas,
               AffiliateLinkService links) {
            this(navegador, ofertas, links, 4, 200, 30_000, 1, 500, () -> {});
        }

        Sessao(NavegadorAfiliados navegador, MercadoLivreAffiliateOffersClient ofertas,
               AffiliateLinkService links, int couponMaxPaginas, int couponMaxProdutos,
               int couponTimeoutMs, int couponMaxRetries, long couponRetryBackoffMs) {
            this(navegador, ofertas, links, couponMaxPaginas, couponMaxProdutos, couponTimeoutMs,
                    couponMaxRetries, couponRetryBackoffMs, () -> {});
        }

        Sessao(NavegadorAfiliados navegador, MercadoLivreAffiliateOffersClient ofertas,
               AffiliateLinkService links, int couponMaxPaginas, int couponMaxProdutos,
               int couponTimeoutMs, int couponMaxRetries, long couponRetryBackoffMs, Runnable aoFechar) {
            this.navegador = navegador;
            this.ofertas = ofertas;
            this.links = links;
            this.imagens = new MercadoLivreImagemProdutoResolver(navegador.pagina());
            this.produtosCupons = new PlaywrightCouponEligibleProductsClient(navegador.pagina(), pageLock,
                    couponMaxPaginas, couponMaxProdutos, couponTimeoutMs, couponMaxRetries,
                    couponRetryBackoffMs, MercadoLivreAfiliadosSessionFactory::sleep);
            this.pdpValidator = new MercadoLivrePdpValidator(navegador.pagina(), pageLock);
            this.aoFechar = aoFechar;
        }

        public List<OfertaAfiliado> buscarOfertas() {
            synchronized (pageLock) { return ofertas.buscarLote(); }
        }
        public List<ResultadoLinkAfiliado> gerarLinks(List<String> urls) {
            synchronized (pageLock) { return links.gerarResultados(urls); }
        }
        public OfertaAfiliado resolverImagem(OfertaAfiliado oferta) {
            synchronized (pageLock) { return imagens.resolver(oferta); }
        }
        public CouponEligibleProductsClient produtosCupons() { return produtosCupons; }
        public OfferSnapshot validarPdp(OfertaAfiliado oferta, int quantidade) {
            return pdpValidator.validate(oferta.itemId(), oferta.produtoId(), oferta.url(), quantidade);
        }
        @Override public void close() {
            if (!fechada.compareAndSet(false, true)) return;
            try {
                synchronized (pageLock) { navegador.close(); }
            } finally {
                aoFechar.run();
            }
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
