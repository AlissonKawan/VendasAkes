package br.com.akesofertas.afiliados.ofertas;

import br.com.akesofertas.afiliados.NavegadorAfiliados;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Path;
import java.util.List;

/** Executável manual: abre o perfil, lê um lote e imprime somente as ofertas normalizadas. */
public final class ConsultarOfertasHub {
    public static void main(String[] args) {
        String canal = System.getenv("AFILIADOS_NAVEGADOR");
        if (canal == null || canal.isBlank()) canal = "chrome";
        if (!List.of("chrome", "msedge").contains(canal)) {
            System.err.println("AFILIADOS_NAVEGADOR deve ser chrome ou msedge.");
            System.exit(1);
        }
        try (var navegador = new NavegadorAfiliados(Path.of(".local/mercadolivre"), canal, false)) {
            navegador.pagina().navigate(MercadoLivreAffiliateOffersClient.HUB,
                    new com.microsoft.playwright.Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            var client = new MercadoLivreAffiliateOffersClient(navegador.pagina(), new MercadoLivreAffiliateOfferMapper());
            var ofertas = client.buscarLote();
            
            System.out.println("Ofertas mapeadas: " + ofertas.size());
            
            int limit = Math.min(3, ofertas.size());
            for (int i = 0; i < limit; i++) {
                var o = ofertas.get(i);
                System.out.println("--- Oferta " + (i + 1) + " ---");
                System.out.println("itemId: " + o.itemId());
                System.out.println("produtoId: " + o.produtoId());
                System.out.println("titulo: " + o.titulo());
                System.out.println("precoAnterior: " + o.precoAnterior());
                System.out.println("precoAtual: " + o.precoAtual());
                System.out.println("desconto: " + o.desconto());
                System.out.println("comissao: " + o.comissao());
                System.out.println("destaque: " + o.destaque());
                System.out.println("imagemPresente: " + (o.imagemUrl() != null));
                System.out.println("imagemUrl: " + o.imagemUrl());
                System.out.println("url: " + o.url());
                System.out.println();
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Não foi possível consultar o Hub. Confira manualmente o acesso e se o perfil está aberto em outro processo.");
            System.exit(1);
        }
    }
}
