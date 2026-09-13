package br.com.akesofertas.afiliados.ofertas;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLivreAffiliateOffersClientTest {
    private Page pagina;
    private Locator elementos;
    private MercadoLivreAffiliateOffersClient client;

    @BeforeEach
    void setup() {
        pagina = mock(Page.class);
        elementos = mock(Locator.class);
        when(pagina.url()).thenReturn(MercadoLivreAffiliateOffersClient.HUB);
        when(pagina.locator(anyString())).thenReturn(elementos);
        when(elementos.innerText()).thenReturn("Hub de Afiliados");
        client = new MercadoLivreAffiliateOffersClient(pagina, new MercadoLivreAffiliateOfferMapper());
    }

    @Test
    void umUnicoPostComPayloadObservadoESessaoDoBrowser() {
        when(pagina.evaluate(anyString(), any())).thenReturn(Map.of("status", 200, "body", MercadoLivreAffiliateOfferMapperTest.RESPOSTA));
        assertEquals(1, client.buscarLote().size());
        assertThrows(IllegalStateException.class, client::buscarLote);
        var script = ArgumentCaptor.forClass(String.class);
        var argumento = ArgumentCaptor.forClass(Object.class);
        verify(pagina, times(1)).evaluate(script.capture(), argumento.capture());
        assertTrue(script.getValue().contains("credentials: 'same-origin'"));
        assertTrue(script.getValue().contains("redirect: 'error'"));
        assertFalse(script.getValue().contains("Authorization"));
        assertFalse(script.getValue().contains("document.cookie"));
        assertEquals(Map.of("endpoint", MercadoLivreAffiliateOffersClient.ENDPOINT,
                "payload", Map.of("search", "", "sort", "relevance", "filters", List.of(), "offset", 0)), argumento.getValue());
        verify(pagina, never()).context();
        verify(pagina, never()).request();
    }

    @Test
    void percorrePaginasAvancandoPelaQuantidadeRealDePolycards() {
        client = new MercadoLivreAffiliateOffersClient(pagina, new MercadoLivreAffiliateOfferMapper(), 5);
        String segunda = MercadoLivreAffiliateOfferMapperTest.RESPOSTA
                .replace("MLB4363134873", "MLB5363134873")
                .replace("MLB63137487", "MLB73137487");
        when(pagina.evaluate(anyString(), any())).thenReturn(
                Map.of("status", 200, "body", MercadoLivreAffiliateOfferMapperTest.RESPOSTA),
                Map.of("status", 200, "body", segunda),
                Map.of("status", 200, "body", "{\"polycard_client_model\":{\"polycards\":[]}}"));

        var ofertas = client.buscarLote();

        assertEquals(List.of("MLB4363134873", "MLB5363134873"),
                ofertas.stream().map(OfertaAfiliado::itemId).toList());
        var argumentos = ArgumentCaptor.forClass(Object.class);
        verify(pagina, times(3)).evaluate(anyString(), argumentos.capture());
        assertEquals(List.of(0, 1, 2), argumentos.getAllValues().stream()
                .map(valor -> ((Map<?, ?>) ((Map<?, ?>) valor).get("payload")).get("offset"))
                .toList());
    }

    @Test
    void interrompeQuandoEndpointRepetePaginaEDeduplicaItemId() {
        client = new MercadoLivreAffiliateOffersClient(pagina, new MercadoLivreAffiliateOfferMapper(), 5);
        when(pagina.evaluate(anyString(), any())).thenReturn(
                Map.of("status", 200, "body", MercadoLivreAffiliateOfferMapperTest.RESPOSTA));

        assertEquals(1, client.buscarLote().size());

        verify(pagina, times(2)).evaluate(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void recusasSaoLegiveisSemRepetirOuExporCorpo(int status) {
        when(pagina.evaluate(anyString(), any())).thenReturn(Map.of("status", status, "body", "SEGREDO"));
        var erro = assertThrows(IllegalStateException.class, client::buscarLote);
        if (status == 401 || status == 403) {
            assertTrue(erro.getMessage().contains("SESSAO_AFILIADOS_EXPIRADA"));
        } else {
            assertTrue(erro.getMessage().contains("HTTP " + status));
        }
        assertFalse(erro.getMessage().contains("SEGREDO"));
        assertThrows(IllegalStateException.class, client::buscarLote);
        verify(pagina, times(1)).evaluate(anyString(), any());
    }

    @Test
    void naoFazPostNaPaginaDeLoginOuComDesafioVisivel() {
        when(pagina.url()).thenReturn("https://www.mercadolivre.com.br/login");
        assertThrows(IllegalStateException.class, client::buscarLote);
        verify(pagina, never()).evaluate(anyString(), any());
        setup();
        when(elementos.count()).thenReturn(1);
        when(elementos.nth(0)).thenReturn(elementos);
        when(elementos.isVisible()).thenReturn(true);
        assertThrows(IllegalStateException.class, client::buscarLote);
        verify(pagina, never()).evaluate(anyString(), any());
    }

    @Test
    void identificaLimiteDeTentativasSemPost() {
        when(elementos.innerText()).thenReturn("Você alcançou o limite de tentativas");
        assertThrows(IllegalStateException.class, client::buscarLote);
        verify(pagina, never()).evaluate(anyString(), any());
    }

    @Test
    void htmlTimeoutERedirecionamentoNaoViraramListaVazia() {
        when(pagina.evaluate(anyString(), any())).thenReturn(Map.of("status", 200, "invalid", true));
        assertThrows(IllegalStateException.class, client::buscarLote);
        setup();
        when(pagina.evaluate(anyString(), any())).thenReturn(Map.of("failed", true));
        assertThrows(IllegalStateException.class, client::buscarLote);
    }
}
