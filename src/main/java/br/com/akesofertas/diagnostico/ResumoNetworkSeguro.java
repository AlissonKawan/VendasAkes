package br.com.akesofertas.diagnostico;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projeção de diagnóstico, nunca um DTO de negócio. Nenhum JSON bruto vai para o disco. */
public final class ResumoNetworkSeguro {
    public static final int MAX_URLS = 20;
    public static final int MAX_BODY = 2_000_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CAMPOS = Set.of(
            "data", "results", "items", "products", "offers", "deals", "promotions", "coupons", "coupon", "content",
            "components", "type", "id", "item_id", "product_id", "catalog_product_id", "seller_id", "site_id", "status",
            "name", "title", "url", "permalink", "url_product", "product_url", "origin_url", "short_url", "urls", "tag",
            "created", "price", "prices", "amount", "value", "original_price", "regular_amount", "original_amount",
            "previous_price", "current_price", "sale_price", "discount", "discount_percentage", "percentage", "currency_id",
            "paging", "pagination", "page", "offset", "limit", "total", "count", "size", "has_more", "next", "cursor",
            "next_cursor", "next_page", "previous_page", "code", "shipping", "free_shipping", "available_quantity",
            "attributes", "pictures", "thumbnail", "metadata", "conditions", "context_restrictions", "start_time", "end_time",
            "buy_box_winner", "tracking", "error", "message", "success", "errors", "filters", "available_filters",
            "state", "initial_state", "props", "children", "label", "text", "link", "links", "benefits", "benefit",
            "price_data", "price_info", "original_value", "decimal_places", "fraction", "cents", "symbol", "decimals",
            "csrf", "token", "access_token", "refresh_token", "authorization", "cookie", "password", "session", "ssid");
    private static final Set<String> SEGMENTOS = Set.of(
            "api", "v1", "v2", "v3", "v4", "affiliate-program", "affiliates", "afiliados", "createLink", "linkbuilder",
            "ofertas", "offers", "deals", "deal", "promotions", "promociones", "products", "items", "search", "feed",
            "recommendations", "recommendation", "recommendations-api", "catalog", "catalogs", "catalogue", "product",
            "campaigns", "campaign", "coupons", "cupons", "discounts", "list", "listing", "listings", "results", "page",
            "pages", "home", "content", "frontend", "navigation", "components", "backend", "sites", "MLB", "p", "l");
    private static final Set<String> IDS = Set.of("id", "item_id", "product_id", "catalog_product_id");
    private static final Set<String> PRECOS = Set.of("price", "amount", "original_price", "regular_amount", "original_amount",
            "previous_price", "current_price", "sale_price", "discount", "discount_percentage", "percentage");
    private static final Set<String> PAGINACAO = Set.of("page", "offset", "limit", "total", "count", "size", "has_more",
            "next", "cursor", "next_cursor", "next_page", "previous_page");

    private ResumoNetworkSeguro() {}

    public static boolean dominioPermitido(String host) {
        return host != null && (host.equals("mercadolivre.com.br") || host.endsWith(".mercadolivre.com.br")
                || host.equals("mercadolibre.com") || host.endsWith(".mercadolibre.com"));
    }

    public static String endpoint(String endereco) {
        try {
            URI uri = URI.create(endereco);
            if (!"https".equals(uri.getScheme()) || !dominioPermitido(uri.getHost())) return "[endpoint omitido]";
            // Nem segmentos desconhecidos nem subdomínios arbitrários podem carregar uma sessão para o relatório.
            String host = Set.of("www.mercadolivre.com.br", "api.mercadolibre.com", "www.mercadolibre.com").contains(uri.getHost())
                    ? uri.getHost() : "[subdominio omitido]";
            var partes = new ArrayList<String>();
            for (String parte : uri.getRawPath().split("/")) {
                if (parte.isEmpty()) continue;
                partes.add(SEGMENTOS.contains(parte) ? parte : "{segmento_omitido}");
                if (partes.size() >= 20) break;
            }
            return "https://" + host + "/" + String.join("/", partes);
        } catch (IllegalArgumentException exception) { return "[endpoint omitido]"; }
    }

    public static List<String> parametros(String endereco) {
        try {
            String query = URI.create(endereco).getRawQuery();
            if (query == null) return List.of();
            return java.util.Arrays.stream(query.split("&")).limit(40)
                    .map(p -> p.split("=", 2)[0]).map(ResumoNetworkSeguro::campoSeguro).distinct().toList();
        } catch (IllegalArgumentException exception) { return List.of(); }
    }

    public static Map<String, Object> resumir(String corpo) {
        if (corpo == null || corpo.isBlank()) return Map.of("formato", "vazio");
        if (corpo.length() > MAX_BODY) return Map.of("formato", "omitido por tamanho");
        try {
            JsonNode raiz = JSON.readTree(corpo);
            var campos = new ArrayList<Map<String, Object>>();
            var listas = new ArrayList<Map<String, Object>>();
            var amostras = new ArrayList<Map<String, Object>>();
            var orcamento = new int[]{2000};
            Object estrutura = visitar(raiz, "$", "", 0, orcamento, campos, listas, amostras);
            var resultado = new LinkedHashMap<String, Object>();
            resultado.put("formato", "JSON");
            resultado.put("estrutura", estrutura);
            resultado.put("camposDeInteresse", campos);
            resultado.put("listas", listas);
            resultado.put("amostrasComId", amostras);
            resultado.put("limites", "Profundidade 10; 2.000 nós; até 20 elementos por lista/amostras. Nomes desconhecidos omitidos.");
            return resultado;
        } catch (Exception exception) { return Map.of("formato", "corpo não JSON ou inválido; omitido"); }
    }

    private static Object visitar(JsonNode node, String caminho, String nome, int nivel, int[] limite,
                                  List<Map<String, Object>> campos, List<Map<String, Object>> listas,
                                  List<Map<String, Object>> amostras) {
        if (--limite[0] < 0 || nivel > 10) return "[truncado]";
        if (node.isObject()) {
            if (amostras.size() < MAX_URLS) amostra(node, caminho, amostras);
            var estrutura = new LinkedHashMap<String, Object>();
            int indice = 0;
            for (var campo : node.properties()) {
                if (++indice > 80 || limite[0] < 0) break;
                String chave = campoSeguro(campo.getKey());
                if (chave.equals("[campo omitido]")) chave += indice;
                String local = caminho + "." + chave;
                if (campos.size() < 100 && (IDS.contains(chave) || PRECOS.contains(chave)
                        || PAGINACAO.contains(chave) || Set.of("url", "permalink", "product_url", "url_product", "coupon", "coupons", "currency_id").contains(chave))) {
                    campos.add(Map.of("caminho", local, "tipo", tipo(campo.getValue())));
                }
                // Não percorre contêineres de credenciais; nomes de chaves podem ser segredos também.
                if (Set.of("token", "csrf", "access_token", "refresh_token", "authorization", "cookie", "session", "ssid", "password").contains(chave)) {
                    estrutura.put(chave, "[omitido]");
                } else estrutura.put(chave, visitar(campo.getValue(), local, chave, nivel + 1, limite, campos, listas, amostras));
            }
            return estrutura;
        }
        if (node.isArray()) {
            if (listas.size() < 100) listas.add(Map.of("caminho", caminho, "quantidade", node.size()));
            var exemplos = new ArrayList<Object>();
            for (int i = 0; i < Math.min(node.size(), MAX_URLS) && limite[0] >= 0; i++) {
                exemplos.add(visitar(node.get(i), caminho + "[" + i + "]", nome, nivel + 1, limite, campos, listas, amostras));
            }
            return Map.of("quantidade", node.size(), "elementosAmostrados", exemplos);
        }
        if (PAGINACAO.contains(nome) && (node.isBoolean() || (node.isIntegralNumber() && node.canConvertToInt()))) {
            return node.isBoolean() ? node.booleanValue() : node.intValue();
        }
        return tipo(node); // Inclui cursores, cupons, URLs, IDs de usuário e valores desconhecidos.
    }

    private static void amostra(JsonNode node, String caminho, List<Map<String, Object>> amostras) {
        var amostra = new LinkedHashMap<String, Object>();
        for (String campo : List.of("item_id", "product_id", "catalog_product_id", "id")) {
            String valor = node.path(campo).asText("");
            if (valor.matches("MLB[0-9]{1,20}")) amostra.put(campo, valor);
        }
        if (amostra.isEmpty()) return;
        amostra.put("caminho", caminho);
        for (String campo : PRECOS) {
            var valor = node.path(campo);
            if (valor.isNumber() && valor.decimalValue().signum() >= 0 && valor.decimalValue().precision() < 16) {
                amostra.put(campo, valor.decimalValue());
            }
        }
        if (node.path("currency_id").asText("").matches("BRL|USD|ARS|MXN|CLP|COP|PEN|UYU")) {
            amostra.put("currency_id", node.path("currency_id").asText());
        }
        // O endereço original não é gravado: pode conter tracking/sessão até fora da query.
        // Confirma host e ID público e apresenta uma referência derivada, rotulada como tal.
        for (String campo : List.of("url", "permalink", "product_url", "url_product")) {
            String referencia = referenciaProduto(node.path(campo).asText(""));
            if (referencia != null) amostra.put(campo, Map.of("referenciaDerivada", referencia, "urlOriginalOmitida", true));
        }
        amostras.add(amostra);
    }

    static String referenciaProduto(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                    || !Set.of("www.mercadolivre.com.br", "produto.mercadolivre.com.br").contains(uri.getHost())) return null;
            var produto = java.util.regex.Pattern.compile("(?:^|/)p/(MLB[0-9]{1,20})(?:/|$)").matcher(uri.getPath());
            if (produto.find()) return "https://www.mercadolivre.com.br/p/" + produto.group(1);
            var item = java.util.regex.Pattern.compile("^/(MLB-[0-9]{1,20})(?:-|/|$)").matcher(uri.getPath());
            if (item.find()) return "anuncio:" + item.group(1);
            return null;
        } catch (IllegalArgumentException | NullPointerException exception) { return null; }
    }

    public static Map<String, Object> lote(String pedido, String resposta) {
        try {
            if (pedido == null || resposta == null || pedido.length() > MAX_BODY || resposta.length() > MAX_BODY) return Map.of("avaliavel", false);
            var entrada = JSON.readTree(pedido).path("urls");
            var retorno = JSON.readTree(resposta).path("urls");
            if (!entrada.isArray() || !retorno.isArray()) return Map.of("avaliavel", false);
            var resultado = new LinkedHashMap<String, Object>();
            resultado.put("quantidadeEnviada", entrada.size());
            resultado.put("quantidadeRecebida", retorno.size());
            boolean limitado = entrada.size() > 0 && entrada.size() <= MAX_URLS && retorno.size() <= MAX_URLS;
            resultado.put("dentroDoLimiteDiagnostico", limitado);
            if (!limitado) return resultado;
            var entradas = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < retorno.size(); i++) {
                var registro = retorno.get(i);
                var item = new LinkedHashMap<String, Object>();
                item.put("indiceResposta", i);
                item.put("created", registro.path("created").isBoolean() ? registro.path("created").booleanValue() : "[ausente ou inválido]");
                item.put("shortUrlMeliLa", linkCurtoValido(registro.path("short_url").asText("")));
                var indices = new ArrayList<Integer>();
                for (int j = 0; j < entrada.size(); j++) {
                    if (entrada.get(j).isString() && entrada.get(j).asText().equals(registro.path("origin_url").asText(null))) indices.add(j);
                }
                item.put("indicesEntradaPorOriginUrl", indices);
                entradas.add(item);
            }
            resultado.put("resultados", entradas);
            resultado.put("conclusao", "Observação do portal; não confirma limite oficial, necessidade de headers ou suporte público.");
            return resultado;
        } catch (Exception exception) { return Map.of("avaliavel", false); }
    }

    static boolean linkCurtoValido(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "meli.la".equals(uri.getHost()) && uri.getUserInfo() == null
                    && uri.getPort() == -1 && uri.getRawQuery() == null && uri.getFragment() == null
                    && uri.getPath().matches("/[A-Za-z0-9_-]{1,128}");
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static String campoSeguro(String campo) { return CAMPOS.contains(campo) ? campo : "[campo omitido]"; }
    private static String tipo(JsonNode valor) {
        if (valor.isNull()) return "null";
        if (valor.isObject()) return "object";
        if (valor.isArray()) return "array";
        if (valor.isNumber()) return "number";
        if (valor.isBoolean()) return "boolean";
        return "string";
    }
}
