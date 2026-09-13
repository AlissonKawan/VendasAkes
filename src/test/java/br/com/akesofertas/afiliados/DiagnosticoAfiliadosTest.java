package br.com.akesofertas.afiliados;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticoAfiliadosTest {
    @Test
    void ignoraNavegacaoInicialLoginEOutrosDominios() {
        String pagina = "https://www.mercadolivre.com.br/afiliados/linkbuilder";
        String endpoint = "https://www.mercadolivre.com.br/exemplo/link";
        assertFalse(DiagnosticoAfiliados.elegivel("about:blank", endpoint, "POST", "fetch"));
        assertFalse(DiagnosticoAfiliados.elegivel("https://www.mercadolivre.com.br/login", endpoint, "POST", "fetch"));
        assertFalse(DiagnosticoAfiliados.elegivel(pagina, "https://example.com/link", "POST", "fetch"));
        assertFalse(DiagnosticoAfiliados.elegivel(pagina, endpoint, "GET", "fetch"));
        assertTrue(DiagnosticoAfiliados.elegivel(pagina, endpoint, "POST", "fetch"));
    }
    @Test
    void registraSomenteNomesDosCookies() {
        assertEquals(List.of("_csrf", "ssid"),
                DiagnosticoAfiliados.nomesCookies("ssid=segredo==; _csrf=outro-segredo"));
    }

    @Test
    void eliminaValoresInclusiveSegredosAninhados() {
        Object estrutura = DiagnosticoAfiliados.estrutura("""
                {"urls":["https://produto.mercadolivre.com.br/MLB-123-produto"],
                 "token":"segredo", "data":{"ssid":"outro", "user_id":123}, "ok":true}
                """);
        assertEquals(Map.of("urls", List.of("[string]"), "token", "[string]",
                "data", Map.of("ssid", "[string]", "user_id", "[number]"), "ok", "[boolean]"), estrutura);
    }

    @Test
    void naoGravaHtmlNemErrosTextuaisQuePodemConterCredenciais() {
        assertEquals("[corpo nao JSON omitido]", DiagnosticoAfiliados.estrutura("token=segredo"));
    }
}
