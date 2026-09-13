package br.com.akesofertas.afiliados;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/** O login do portal de afiliados é separado do OAuth da API de vendedores. */
public final class NavegadorAfiliados implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(NavegadorAfiliados.class);
    private final Playwright playwright;
    private final BrowserContext contexto;
    private final Page pagina;

    public NavegadorAfiliados(Path perfil, String navegador, boolean oculto) {
        // Usa o Chrome/Edge instalado; não baixa outros navegadores na inicialização.
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        try {
            // O perfil exclusivo conserva a sessão entre execuções. Não use o perfil pessoal.
            contexto = playwright.chromium().launchPersistentContext(perfil.toAbsolutePath(),
                    new BrowserType.LaunchPersistentContextOptions()
                            .setChannel(navegador).setHeadless(oculto).setLocale("pt-BR"));
            pagina = contexto.pages().isEmpty() ? contexto.newPage() : contexto.pages().getFirst();
            int antes = contexto.pages().size();
            int depois = fecharAbasExcedentes(contexto, pagina);
            if (antes != depois) {
                log.info("Playwright: abas antigas removidas ao abrir o contexto={}", antes - depois);
            }
            pagina.setDefaultTimeout(15_000);
            pagina.setDefaultNavigationTimeout(45_000);
        } catch (RuntimeException exception) {
            playwright.close();
            throw exception;
        }
    }

    // Todas as operações do Playwright devem acontecer na mesma thread.
    public Page pagina() {
        return pagina;
    }

    public BrowserContext contexto() { return contexto; }

    static int fecharAbasExcedentes(BrowserContext contexto, Page principal) {
        for (Page aberta : new ArrayList<>(contexto.pages())) {
            if (aberta != principal && !aberta.isClosed()) aberta.close();
        }
        return contexto.pages().size();
    }

    @Override
    public void close() {
        try {
            int abertas = fecharAbasExcedentes(contexto, pagina);
            log.info("Playwright: abas abertas ao final do ciclo={}", abertas);
        } finally {
            try {
                contexto.close();
            } finally {
                playwright.close();
            }
        }
    }

    public static void main(String[] args) {
        Path perfil = Path.of(variavel("AFILIADOS_PERFIL", ".local/mercadolivre"));
        String canal = variavel("AFILIADOS_NAVEGADOR", "chrome");
        try (NavegadorAfiliados navegador = new NavegadorAfiliados(perfil, canal, false)) {
            Page pagina = navegador.pagina();
            if (java.util.Arrays.asList(args).contains("--diagnostico")) {
                DiagnosticoAfiliados.observar(pagina, Path.of(".local/diagnostico-afiliados.jsonl"));
                System.out.println("Diagnostico: apos entrar, gere um link pelo portal. So a estrutura HTTP sera registrada.");
            }
            pagina.navigate("https://www.mercadolivre.com.br/afiliados/linkbuilder");
            System.out.println("Entre na conta pelo navegador. Nenhuma oferta sera publicada neste modo.");
            System.out.println("Quando terminar, feche a janela. A sessao fica no perfil local, ignorado pelo Git.");
            while (!pagina.isClosed()) {
                try {
                    pagina.waitForTimeout(1000);
                } catch (PlaywrightException exception) {
                    if (!pagina.isClosed()) throw exception;
                }
            }
        }
    }

    private static String variavel(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
