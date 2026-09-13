package br.com.akesofertas.diagnostico;

import br.com.akesofertas.afiliados.NavegadorAfiliados;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Programa manual e passivo, fora do Spring. Não coleta para negócio, não repete HTTP e não clica em Gerar. */
public final class DiagnosticoOfertasNetwork {
    private static final String HUB = "https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user";
    private static final String CREATE_LINK = "/affiliate-program/api/v2/affiliates/createLink";
    private final Path destino;
    private boolean pausado;
    private int registrados;

    private DiagnosticoOfertasNetwork(Path destino) { this.destino = destino; }

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : HUB;
        int segundos;
        try {
            segundos = args.length > 1 ? Integer.parseInt(args[1]) : 600;
            if (!paginaPermitida(url) || segundos < 30 || segundos > 1800) throw new IllegalArgumentException();
        } catch (Exception exception) {
            System.err.println("Use uma URL HTTPS do hub, ofertas ou gerador do Mercado Livre e duração entre 30 e 1800 segundos.");
            return;
        }
        Path arquivo = Path.of(".local/diagnosticos/ofertas-" + Instant.now().toEpochMilli() + ".jsonl");
        var diagnostico = new DiagnosticoOfertasNetwork(arquivo);
        String canal = System.getenv("AFILIADOS_NAVEGADOR");
        if (canal == null || canal.isBlank()) canal = "chrome";
        if (!List.of("chrome", "msedge").contains(canal)) {
            System.err.println("AFILIADOS_NAVEGADOR deve ser chrome ou msedge.");
            return;
        }
        // Perfil fixo desta investigação; não lê ou exporta cookies e não usa tokens OAuth.
        try (var navegador = new NavegadorAfiliados(Path.of(".local/mercadolivre"), canal, false)) {
            var contexto = navegador.contexto();
            contexto.onRequestFinished(diagnostico::registrar);
            System.out.println("Diagnostico passivo iniciado. Relatorio sanitizado: " + arquivo);
            System.out.println("Use a janela para abrir ofertas, paginar/rolar e gerar links manualmente. Analise de lote: ate 20 URLs.");
            System.out.println("Se houver login, entre manualmente. Em desafio ou recusa, a captura para: resolva manualmente e reinicie o diagnostico.");
            diagnostico.gravar(java.util.Map.of("evento", "inicio", "modo", "passivo", "maxUrlsAnalisadas", 20,
                    "perfil", ".local/mercadolivre", "duracaoSegundos", segundos));
            try {
                navegador.pagina().navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception exception) {
                System.out.println("A navegacao nao concluiu no prazo. Confira a janela; nenhuma requisicao sera repetida pelo diagnostico.");
            }
            long fim = System.nanoTime() + segundos * 1_000_000_000L;
            boolean loginAvisado = false;
            while (System.nanoTime() < fim && !contexto.pages().isEmpty()) {
                Page pagina = contexto.pages().getFirst();
                for (Page aba : contexto.pages()) {
                    if (aba.isClosed()) continue;
                    if (login(aba.url()) && !loginAvisado) {
                        System.out.println("Login manual necessario neste perfil. O diagnostico nao registra a pagina de login.");
                        loginAvisado = true;
                    }
                    if (!diagnostico.pausado && desafioVisivel(aba)) diagnostico.pausar("Desafio de seguranca: acesso manual necessario.");
                }
                // Bombeia eventos na mesma thread do Playwright; não existe tarefa agendada no backend.
                pagina.waitForTimeout(500);
            }
        } catch (Exception exception) {
            // Exceções do navegador podem conter URLs e headers: nunca imprimir exception/stack trace.
            System.err.println("Diagnostico encerrado: janela fechada ou navegador indisponivel. Confira o relatorio e feche outras instancias deste perfil antes de tentar novamente.");
        } finally {
            diagnostico.gravar(java.util.Map.of("evento", "fim", "requisicoesRegistradas", diagnostico.registrados, "capturaPausada", diagnostico.pausado));
            System.out.println("Diagnostico encerrado. Requisicoes registradas: " + diagnostico.registrados);
        }
    }

    private void registrar(Request pedido) {
        if (pausado) return;
        try {
            Response resposta = pedido.response();
            if (resposta == null || !elegivel(pedido.frame().page().url(), pedido.url(), pedido.resourceType())) return;
            if (List.of(401, 403, 429).contains(resposta.status())) {
                pausar("HTTP " + resposta.status() + ": acesso manual necessario; nenhuma tentativa automatica.");
                return;
            }
            if (desafioVisivel(pedido.frame().page())) { pausar("Desafio de seguranca: acesso manual necessario."); return; }
            if (registrados >= 100) { pausar("Limite de 100 respostas atingido."); return; }
            String contentType = resposta.headerValue("content-type");
            String corpo = null;
            String formato = "nao JSON; corpo omitido";
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
                String tamanho = resposta.headerValue("content-length");
                if (tamanho == null || Long.parseLong(tamanho) <= ResumoNetworkSeguro.MAX_BODY) {
                    byte[] bytes = resposta.body();
                    if (bytes.length <= ResumoNetworkSeguro.MAX_BODY) {
                        corpo = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        formato = "JSON";
                    } else formato = "JSON omitido por tamanho";
                } else formato = "JSON omitido por tamanho";
            }
            var registro = new LinkedHashMap<String, Object>();
            registro.put("evento", "requisicao");
            registro.put("numero", ++registrados);
            registro.put("instante", Instant.now().toString());
            registro.put("endpoint", ResumoNetworkSeguro.endpoint(pedido.url()));
            registro.put("parametrosQuerySemValores", ResumoNetworkSeguro.parametros(pedido.url()));
            registro.put("metodo", List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD").contains(pedido.method()) ? pedido.method() : "omitido");
            registro.put("recurso", pedido.resourceType());
            registro.put("httpStatus", resposta.status());
            registro.put("formato", formato);
            registro.put("pedido", ResumoNetworkSeguro.resumir(pedido.postData()));
            registro.put("resposta", ResumoNetworkSeguro.resumir(corpo));
            if (CREATE_LINK.equals(URI.create(pedido.url()).getPath()) && "POST".equals(pedido.method())) {
                registro.put("loteCreateLink", ResumoNetworkSeguro.lote(pedido.postData(), corpo));
            }
            gravar(registro);
            System.out.println("Resposta " + registrados + " registrada (HTTP " + resposta.status() + ").");
        } catch (Exception exception) {
            System.err.println("Uma resposta nao pode ser analisada; corpo e detalhes privados omitidos.");
        }
    }

    private void pausar(String motivo) {
        if (pausado) return;
        pausado = true;
        System.out.println("Captura pausada. " + motivo);
        gravar(java.util.Map.of("evento", "pausa", "motivo", motivo));
    }

    private void gravar(Object registro) {
        try {
            Files.createDirectories(destino.toAbsolutePath().getParent());
            Files.writeString(destino, new ObjectMapper().writeValueAsString(registro) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) { pausado = true; System.err.println("Nao foi possivel salvar o relatorio. Captura interrompida."); }
    }

    static boolean paginaPermitida(String endereco) {
        try {
            URI uri = URI.create(endereco);
            return "https".equals(uri.getScheme()) && "www.mercadolivre.com.br".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && List.of("/afiliados/hub", "/afiliados/linkbuilder", "/ofertas").stream()
                    .anyMatch(p -> uri.getPath().equals(p) || uri.getPath().startsWith(p + "/"));
        } catch (IllegalArgumentException exception) { return false; }
    }

    static boolean elegivel(String pagina, String endpoint, String recurso) {
        try {
            URI uri = URI.create(endpoint);
            return paginaPermitida(pagina) && "https".equals(uri.getScheme()) && ResumoNetworkSeguro.dominioPermitido(uri.getHost())
                    && List.of("xhr", "fetch").contains(recurso) && !sensivel(uri.getPath());
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static boolean sensivel(String path) {
        String valor = path.toLowerCase(Locale.ROOT);
        return List.of("login", "logout", "oauth", "auth", "password", "session", "account", "users", "profile",
                "tracking", "telemetry", "metrics", "captcha", "challenge", "security").stream().anyMatch(valor::contains);
    }

    private static boolean login(String endereco) {
        try { return URI.create(endereco).getPath().toLowerCase(Locale.ROOT).contains("login"); }
        catch (Exception exception) { return false; }
    }

    private static boolean desafioVisivel(Page pagina) {
        try {
            String path = URI.create(pagina.url()).getPath().toLowerCase(Locale.ROOT);
            if (path.contains("captcha") || path.contains("challenge") || path.contains("security-check")) return true;
            var elementos = pagina.locator("iframe[src*='recaptcha'], iframe[src*='hcaptcha'], iframe[src*='challenges.cloudflare.com'], #captcha, [id*='captcha-challenge']");
            for (int i = 0; i < elementos.count(); i++) if (elementos.nth(i).isVisible()) return true;
            return false;
        } catch (Exception exception) { return false; }
    }
}
