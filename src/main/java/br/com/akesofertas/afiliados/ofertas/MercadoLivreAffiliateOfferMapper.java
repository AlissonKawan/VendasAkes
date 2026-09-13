package br.com.akesofertas.afiliados.ofertas;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Único lugar que conhece polycards. Não depende de navegador ou do formato da API oficial. */
public final class MercadoLivreAffiliateOfferMapper {
    private final ObjectMapper json = new ObjectMapper();

    public List<OfertaAfiliado> mapear(String resposta) {
        JsonNode raiz;
        try { raiz = json.readTree(resposta); }
        catch (Exception exception) { throw new IllegalArgumentException("O Hub não retornou JSON válido."); }
        if (raiz == null || !raiz.isObject()) throw new IllegalArgumentException("O Hub não retornou um objeto JSON.");
        var cards = raiz.path("polycard_client_model").path("polycards");
        if (!cards.isArray()) throw new IllegalArgumentException("Resposta do Hub sem polycard_client_model.polycards como lista.");
        
        System.out.println("Polycards recebidos (total): " + cards.size());
        
        var ofertas = new ArrayList<OfertaAfiliado>();
        for (var card : cards) {
            var metadata = card.path("metadata");
            String itemId = texto(metadata.path("id"));
            // Um card editorial ou um PRODUCT_ID não deve ser convertido em anúncio por suposição.
            if (itemId == null || !itemId.matches("MLB[0-9]{1,20}")) continue;
            
            String produtoId = texto(metadata.path("product_id"));
            
            var titulo = componente(card, "title");
            var destaqueNode = componente(card, "highlight");
            String destaque = texto(destaqueNode.path("text"));
            var preco = componente(card, "price");
            // O endpoint do Hub não fornece a foto da oferta. Ela é enriquecida depois,
            // a partir da página original, sem interferir na geração do link afiliado.
            String imagemUrl = null;
            
            var chipComissao = comissao(card);
            String textoComissao = extrairTextoComissao(chipComissao);
            String type = texto(metadata.path("type"));
            
            String url = urlProduto(texto(metadata.path("url")), texto(metadata.path("url_params")), texto(metadata.path("url_fragments")), itemId, type);
            
            if (url == null) {
                System.out.println("--- DIAGNÓSTICO URL NULA ---");
                System.out.println("id: " + itemId);
                System.out.println("product_id: " + produtoId);
                System.out.println("type: " + type);
                System.out.println("url (presente?): " + (texto(metadata.path("url")) != null ? "Sim" : "Não"));
                System.out.println("url_params (presente?): " + (texto(metadata.path("url_params")) != null ? "Sim" : "Não"));
                System.out.println("url_fragments (presente?): " + (texto(metadata.path("url_fragments")) != null ? "Sim" : "Não"));
                System.out.println("----------------------------");
            }

            ofertas.add(new OfertaAfiliado(itemId, produtoId, texto(titulo.path("text")),
                    valor(preco.path("previous_price").path("value")),
                    valor(preco.path("current_price").path("value")),
                    texto(preco.path("discount_label").path("text")), textoComissao,
                    destaque, url, imagemUrl));
        }
        return List.copyOf(ofertas);
    }

    private static String extrairTextoComissao(JsonNode chip) {
        if (chip == null || chip.isNull()) return null;
        String label = texto(chip.path("label").path("text"));
        if (label != null && label.contains("%")) return label;
        String pill = texto(chip.path("pill").path("text"));
        if (pill != null && pill.contains("%")) return pill;
        return null;
    }

    private JsonNode componente(JsonNode card, String tipo) {
        JsonNode resultado = json.nullNode();
        if (!card.path("components").isArray()) return resultado;
        for (var componente : card.path("components")) {
            if (tipo.equals(componente.path("type").asText())) {
                if (!resultado.isNull()) return json.nullNode();
                resultado = componente.path(tipo);
            }
        }
        return resultado;
    }

    private JsonNode comissao(JsonNode card) {
        JsonNode resultado = json.nullNode();
        if (!card.path("components").isArray()) return resultado;
        for (var componente : card.path("components")) {
            if ("chip".equals(componente.path("type").asText())
                    && "affiliates_commission_chip".equals(componente.path("id").asText())) {
                if (!resultado.isNull()) return json.nullNode();
                resultado = componente.path("chip");
            }
        }
        return resultado;
    }

    private static BigDecimal valor(JsonNode valor) {
        return valor.isNumber() && valor.decimalValue().signum() >= 0 ? valor.decimalValue() : null;
    }

    private static String texto(JsonNode valor) {
        return valor.isString() && !valor.asText().isBlank() ? valor.asText() : null;
    }

    static String urlProduto(String endereco, String parametros, String fragmentos, String itemId, String type) {
        if (endereco == null) return null;
        try {
            String normalizado = endereco.startsWith("www.mercadolivre.com.br/") || endereco.startsWith("produto.mercadolivre.com.br/")
                    ? "https://" + endereco : endereco;
            URI uri = URI.create(normalizado);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                    || !Set.of("www.mercadolivre.com.br", "produto.mercadolivre.com.br").contains(uri.getHost())
                    || uri.getPath() == null || uri.getPath().length() < 2) return null;
            
            String base = new URI("https", null, uri.getHost(), -1, uri.getPath(), null, null).toASCIIString();
            
            String urlFinal = base;
            if (parametros != null && !parametros.isBlank()) {
                if (!urlFinal.contains("?") && !parametros.startsWith("?")) {
                    urlFinal += "?";
                } else if (urlFinal.contains("?") && parametros.startsWith("?")) {
                    parametros = parametros.substring(1);
                }
                urlFinal += parametros;
            }
            if (fragmentos != null && !fragmentos.isBlank()) {
                if (!urlFinal.contains("#") && !fragmentos.startsWith("#")) {
                    urlFinal += "#";
                } else if (urlFinal.contains("#") && fragmentos.startsWith("#")) {
                    fragmentos = fragmentos.substring(1);
                }
                urlFinal += fragmentos;
            }
            
            boolean itemConfirmado = false;
            
            String decodificada = URLDecoder.decode(urlFinal, StandardCharsets.UTF_8);
            if (decodificada.contains("item_id:" + itemId) || decodificada.contains("wid=" + itemId)) {
                itemConfirmado = true;
            }
            
            if (!itemConfirmado && "item".equals(type) && uri.getPath() != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/(MLB)-?(\\d+)").matcher(uri.getPath());
                if (matcher.find()) {
                    String extractedId = matcher.group(1) + matcher.group(2);
                    if (extractedId.equals(itemId)) {
                        itemConfirmado = true;
                    }
                }
            }
            
            if (!itemConfirmado) {
                return null;
            }
            
            return urlFinal;
        } catch (Exception exception) { return null; }
    }
}
