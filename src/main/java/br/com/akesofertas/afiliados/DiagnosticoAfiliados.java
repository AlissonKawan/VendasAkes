package br.com.akesofertas.afiliados;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Observa uma geração feita pelo usuário; não repete requisições nem exporta credenciais. */
public final class DiagnosticoAfiliados {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DiagnosticoAfiliados() {}

    public static void observar(Page pagina, Path destino) {
        pagina.onResponse(resposta -> registrar(pagina, resposta, destino));
    }

    private static void registrar(Page pagina, Response resposta, Path destino) {
        Request pedido = resposta.request();
        if (!elegivel(pagina.url(), pedido.url(), pedido.method(), pedido.resourceType())) return;
        try {
            URI uri = URI.create(pedido.url());
            Map<String, String> cabecalhos = pedido.allHeaders();
            Map<String, Object> registro = new LinkedHashMap<>();
            registro.put("endpoint", uri.getScheme() + "://" + uri.getHost() + uri.getPath());
            registro.put("metodo", pedido.method());
            registro.put("headersObservados", cabecalhos.keySet().stream().sorted().toList());
            registro.put("contentType", cabecalhos.get("content-type"));
            // Somente os nomes; ssid, CSRF, Authorization e demais valores nunca são gravados.
            registro.put("cookiesObservados", nomesCookies(cabecalhos.get("cookie")));
            registro.put("bodyEstrutura", estrutura(pedido.postData()));
            registro.put("httpStatus", resposta.status());
            registro.put("respostaEstrutura", estrutura(resposta.text()));
            Files.createDirectories(destino.toAbsolutePath().getParent());
            Files.writeString(destino, JSON.writeValueAsString(registro) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("Chamada do gerador registrada sem valores de credenciais: " + destino);
        } catch (Exception exception) {
            // A falha do diagnóstico não interrompe o portal e não imprime bodies ou tokens.
            System.err.println("Nao foi possivel registrar a estrutura da chamada do gerador.");
        }
    }

    static boolean elegivel(String pagina, String endpoint, String metodo, String recurso) {
        try {
            URI tela = URI.create(pagina);
            URI uri = URI.create(endpoint);
            // Durante a navegação, a página pode ainda ser about:blank, sem um path.
            return "/afiliados/linkbuilder".equals(tela.getPath())
                    && "www.mercadolivre.com.br".equals(tela.getHost())
                    && "www.mercadolivre.com.br".equals(uri.getHost())
                    && "POST".equals(metodo) && List.of("xhr", "fetch").contains(recurso)
                    && uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).contains("link");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static List<String> nomesCookies(String cookie) {
        if (cookie == null || cookie.isBlank()) return List.of();
        return Arrays.stream(cookie.split(";"))
                .map(parte -> parte.split("=", 2)[0].trim()).filter(nome -> !nome.isBlank())
                .distinct().sorted().toList();
    }

    static Object estrutura(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) return null;
        try {
            return estrutura(JSON.readTree(conteudo), 0);
        } catch (Exception exception) {
            return "[corpo nao JSON omitido]";
        }
    }

    private static Object estrutura(JsonNode node, int nivel) {
        if (nivel > 12) return "[estrutura profunda omitida]";
        if (node.isObject()) {
            Map<String, Object> campos = new LinkedHashMap<>();
            node.properties().forEach(campo -> campos.put(campo.getKey(), estrutura(campo.getValue(), nivel + 1)));
            return campos;
        }
        if (node.isArray()) {
            List<Object> exemplos = new ArrayList<>();
            for (JsonNode valor : node) {
                if (exemplos.size() == 3) break;
                exemplos.add(estrutura(valor, nivel + 1));
            }
            return exemplos;
        }
        if (node.isNull()) return null;
        if (node.isNumber()) return "[number]";
        if (node.isBoolean()) return "[boolean]";
        // Inclusive links: o diagnóstico descreve o contrato, sem copiar dados da sessão.
        return "[string]";
    }
}
