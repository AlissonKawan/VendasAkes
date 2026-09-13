package br.com.akesofertas.afiliados.ofertas;

import com.microsoft.playwright.Page;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cliente privado do Portal, não da API OAuth. A sessão permanece dentro do navegador. */
public final class MercadoLivreAffiliateOffersClient {
    private static final Logger log = LoggerFactory.getLogger(MercadoLivreAffiliateOffersClient.class);
    public static final String HUB = "https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user";
    public static final String ENDPOINT = "https://www.mercadolivre.com.br/affiliate-program/api/hub/search?is_affiliate=true&device=desktop";
    private final Page pagina;
    private final MercadoLivreAffiliateOfferMapper mapper;
    private final int maximoPaginas;
    private boolean consultado;

    public MercadoLivreAffiliateOffersClient(Page pagina, MercadoLivreAffiliateOfferMapper mapper) {
        this(pagina, mapper, 1);
    }

    public MercadoLivreAffiliateOffersClient(Page pagina, MercadoLivreAffiliateOfferMapper mapper,
                                              int maximoPaginas) {
        if (maximoPaginas < 1 || maximoPaginas > 20) {
            throw new IllegalArgumentException("Máximo de páginas do Hub deve estar entre 1 e 20.");
        }
        this.pagina = pagina;
        this.mapper = mapper;
        this.maximoPaginas = maximoPaginas;
    }

    public List<OfertaAfiliado> buscarLote() {
        if (consultado) throw new IllegalStateException("Este cliente executa somente uma consulta paginada por ciclo.");
        consultado = true;
        try {
            verificarPagina();
            var ofertasPorItem = new LinkedHashMap<String, OfertaAfiliado>();
            int offset = 0;
            for (int numeroPagina = 0; numeroPagina < maximoPaginas; numeroPagina++) {
                PaginaHub paginaHub = buscarPagina(offset);
                int novas = 0;
                for (OfertaAfiliado oferta : paginaHub.ofertas()) {
                    if (ofertasPorItem.putIfAbsent(oferta.itemId(), oferta) == null) novas++;
                }
                log.info("Hub: página={} offset={} polycards={} ofertasNovas={}",
                        numeroPagina + 1, offset, paginaHub.quantidadePolycards(), novas);
                if (paginaHub.quantidadePolycards() == 0 || novas == 0) break;
                offset += paginaHub.quantidadePolycards();
            }
            return List.copyOf(ofertasPorItem.values());
        } catch (com.microsoft.playwright.PlaywrightException exception) {
            // A exceção original pode conter URLs/dados privados. Não propagamos body, headers ou stack do Playwright.
            throw new IllegalStateException("Navegador indisponível para consultar o Hub. Confira a sessão manualmente.");
        }
    }

    private PaginaHub buscarPagina(int offset) {
        var payload = Map.of("search", "", "sort", "relevance", "filters", List.of(), "offset", offset);
        Object retorno = pagina.evaluate("""
                async ({endpoint, payload}) => {
                    const controller = new AbortController();
                    const timer = setTimeout(() => controller.abort(), 20000);
                    try {
                        const response = await fetch(endpoint, {
                            method: 'POST', credentials: 'same-origin', redirect: 'error',
                            headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                            body: JSON.stringify(payload), signal: controller.signal
                        });
                        if (!response.ok) return {status: response.status};
                        if (!(response.headers.get('content-type') || '').includes('json')) return {status: response.status, invalid: true};
                        const text = await response.text();
                        if (text.length > 2000000) return {status: response.status, invalid: true};
                        return {status: response.status, body: text};
                    } catch (_) { return {failed: true}; }
                    finally { clearTimeout(timer); }
                }
                """, Map.of("endpoint", ENDPOINT, "payload", payload));
        verificarPagina();
        var resposta = new ObjectMapper().valueToTree(retorno);
        int status = resposta.path("status").asInt(0);
        if (status == 401 || status == 403) {
            throw new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA: É necessário fazer login manual novamente.");
        }
        if (status != 200) throw new IllegalStateException(status == 0
                ? "Consulta não concluída (comunicação, redirecionamento ou prazo). Nenhuma repetição foi feita."
                : "Hub retornou HTTP " + status + ". Confira o acesso manual; nenhuma repetição foi feita.");
        if (!resposta.path("body").isString()) throw new IllegalStateException("Hub não retornou JSON utilizável. Verifique a sessão ou desafio manualmente.");
        String corpo = resposta.path("body").asText();
        var raiz = new ObjectMapper().readTree(corpo);
        var cards = raiz.path("polycard_client_model").path("polycards");
        int quantidade = cards.isArray() ? cards.size() : 0;
        return new PaginaHub(mapper.mapear(corpo), quantidade);
    }

    private record PaginaHub(List<OfertaAfiliado> ofertas, int quantidadePolycards) {}

    private void verificarPagina() {
        URI uri = URI.create(pagina.url());
        if (!"https".equals(uri.getScheme()) || !"www.mercadolivre.com.br".equals(uri.getHost())
                || !"/afiliados/hub".equals(uri.getPath())) {
            throw new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA: Acesso manual necessário: o perfil não está autenticado no Hub ou foi redirecionado. Nenhum login automático será realizado.");
        }
        var desafios = pagina.locator("iframe[src*='recaptcha'], iframe[src*='hcaptcha'], iframe[src*='challenges.cloudflare.com'], #captcha, input[type='password']");
        for (int i = 0; i < desafios.count(); i++) if (desafios.nth(i).isVisible()) {
            throw new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA: Login ou desafio de segurança visível. Interrompido; acesso manual necessário.");
        }
        // Também identifica as recusas já vistas nesta conta, sem registrar o texto da página.
        String texto = pagina.locator("body").innerText().toLowerCase(java.util.Locale.ROOT);
        if (texto.contains("limite de tentativas") || texto.contains("não foi possível fazer o login")
                || texto.contains("verifique que você é humano") || texto.contains("confirme que você é humano")) {
            throw new IllegalStateException("SESSAO_AFILIADOS_EXPIRADA: Bloqueio de segurança no portal. Interrompido; acesso manual necessário.");
        }
    }
}
