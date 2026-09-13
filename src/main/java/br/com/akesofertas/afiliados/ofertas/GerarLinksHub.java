package br.com.akesofertas.afiliados.ofertas;

import br.com.akesofertas.afiliados.AffiliateLinkRequest;
import br.com.akesofertas.afiliados.AffiliateLinkResponse;
import br.com.akesofertas.afiliados.MercadoLivreAffiliateClient;
import br.com.akesofertas.afiliados.NavegadorAfiliados;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Path;
import java.util.List;

public final class GerarLinksHub {
    public static void main(String[] args) {
        String canal = System.getenv("AFILIADOS_NAVEGADOR");
        if (canal == null || canal.isBlank()) canal = "chrome";
        try (var navegador = new NavegadorAfiliados(Path.of(".local/mercadolivre"), canal, false)) {
            navegador.pagina().navigate(MercadoLivreAffiliateOffersClient.HUB,
                    new com.microsoft.playwright.Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            
            var offersClient = new MercadoLivreAffiliateOffersClient(navegador.pagina(), new br.com.akesofertas.afiliados.ofertas.MercadoLivreAffiliateOfferMapper());
            var clientLinks = new MercadoLivreAffiliateClient(navegador.pagina());
            
            var lote = offersClient.buscarLote();
            List<String> todasUrls = lote.stream()
                .filter(o -> o.url() != null)
                .map(br.com.akesofertas.afiliados.ofertas.OfertaAfiliado::url)
                .toList();
                
            System.out.println("Polycards recebidos (total): " + lote.size());
            System.out.println("URLs extraidas do Hub: " + todasUrls.size());
            
            List<Integer> batches = List.of(2, 5, 10);
            
            for (int batchSize : batches) {
                if (todasUrls.size() < batchSize) break;
                System.out.println("\n==================================");
                System.out.println("TESTANDO LOTE COM " + batchSize + " URLs");
                System.out.println("==================================");
                
                List<String> urls = todasUrls.subList(0, batchSize);
                var pedido = new AffiliateLinkRequest(urls, "telegram");
                AffiliateLinkResponse resposta = clientLinks.criarLink(pedido);
                
                System.out.println("Status HTTP/Business: " + resposta.status());
                System.out.println("Total Items: " + resposta.totalItems());
                System.out.println("Total Success: " + resposta.totalSuccess());
                System.out.println("Total Error: " + resposta.totalError());
                
                if (resposta.urls() != null) {
                    System.out.println("\nLinks retornados: " + resposta.urls().size());
                    for (var link : resposta.urls()) {
                        System.out.println("- origin_url: " + (link.originUrl() != null && link.originUrl().length() > 60 ? link.originUrl().substring(0, 60) + "..." : link.originUrl()));
                        System.out.println("  created: " + link.created());
                        System.out.println("  short_url: " + link.shortUrl());
                        System.out.println("  tag: " + link.tag());
                    }
                } else {
                    System.out.println("urls = null");
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }
}

