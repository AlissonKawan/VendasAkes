package br.com.akesofertas.afiliados.ofertas;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/** Lê a foto principal já renderizada na página pública da oferta. */
public final class MercadoLivreImagemProdutoResolver {
    private static final Logger log = LoggerFactory.getLogger(MercadoLivreImagemProdutoResolver.class);
    private final Page pagina;

    public MercadoLivreImagemProdutoResolver(Page pagina) {
        this.pagina = pagina;
    }

    public OfertaAfiliado resolver(OfertaAfiliado oferta) {
        if (oferta == null || oferta.url() == null) return oferta;
        pagina.navigate(oferta.url(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        pagina.waitForTimeout(1_000);
        Object resultado = pagina.locator("img[src*='mlstatic.com']").evaluateAll("""
                imgs => {
                  const principais = imgs.filter(img => img.naturalWidth >= 200 && img.naturalHeight >= 200);
                  return (principais[0] || {}).currentSrc || (principais[0] || {}).src || null;
                }
                """);
        String imagem = resultado == null ? null : validarDireta(resultado.toString());
        log.info("Rastreamento imagem: itemId={} etapa=página-original imagemPresente={} imagemUrl={}",
                oferta.itemId(), imagem != null, imagem);
        return new OfertaAfiliado(oferta.itemId(), oferta.produtoId(), oferta.titulo(), oferta.precoAnterior(),
                oferta.precoAtual(), oferta.desconto(), oferta.comissao(), oferta.destaque(), oferta.url(), imagem, oferta.cupom());
    }

    static String validarDireta(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            URI uri = URI.create(valor.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (host.equals("mlstatic.com") || host.endsWith(".mlstatic.com"))
                    ? uri.toASCIIString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
