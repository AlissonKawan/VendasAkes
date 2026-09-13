package br.com.akesofertas.afiliados.ofertas;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MercadoLivreImagemProdutoResolverTest {
    @Test
    void transportaImagemDiretaRenderizadaParaOfertaAfiliado() {
        Page pagina = mock(Page.class);
        Locator imagens = mock(Locator.class);
        when(pagina.locator("img[src*='mlstatic.com']")).thenReturn(imagens);
        when(imagens.evaluateAll(anyString())).thenReturn(
                "https://http2.mlstatic.com/D_NQ_NP_753073-MLA110946060098_052026-O.webp");
        var oferta = new OfertaAfiliado("MLB1", "MLB2", "Produto", BigDecimal.TEN,
                BigDecimal.ONE, "90% OFF", null, null, "https://www.mercadolivre.com.br/p/MLB2");

        OfertaAfiliado resolvida = new MercadoLivreImagemProdutoResolver(pagina).resolver(oferta);

        assertEquals("https://http2.mlstatic.com/D_NQ_NP_753073-MLA110946060098_052026-O.webp",
                resolvida.imagemUrl());
        assertEquals(oferta.itemId(), resolvida.itemId());
        assertEquals(oferta.url(), resolvida.url());
        verify(pagina).navigate(eq(oferta.url()), any(Page.NavigateOptions.class));
    }

    @Test
    void resolverImagemPreservaCupomDaOferta() {
        Page pagina = mock(Page.class);
        Locator imagens = mock(Locator.class);
        when(pagina.locator("img[src*='mlstatic.com']")).thenReturn(imagens);
        when(imagens.evaluateAll(anyString())).thenReturn("https://http2.mlstatic.com/foto.jpg");
        var condicoes = new br.com.akesofertas.cupons.domain.CondicoesCupom(1L, null, null, BigDecimal.ONE,
                null, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, "termos");
        var cupom = new br.com.akesofertas.cupons.domain.CupomAplicavel(1L, "#A", "Cupom", condicoes,
                BigDecimal.ONE, new BigDecimal("9"));
        var oferta = new OfertaAfiliado("MLB1", null, "Produto", null, BigDecimal.TEN, null, null, null,
                "https://www.mercadolivre.com.br/p/MLB2").comCupom(cupom);
        var resolvida = new MercadoLivreImagemProdutoResolver(pagina).resolver(oferta);
        assertSame(cupom, resolvida.cupom());
        assertEquals("https://http2.mlstatic.com/foto.jpg", resolvida.imagemUrl());
    }

    @Test
    void rejeitaPaginaProdutoOuLinkAfiliadoComoImagem() {
        assertNull(MercadoLivreImagemProdutoResolver.validarDireta("https://produto.mercadolivre.com.br/MLB-1"));
        assertNull(MercadoLivreImagemProdutoResolver.validarDireta("https://meli.la/abc"));
        assertNull(MercadoLivreImagemProdutoResolver.validarDireta(null));
    }
}
