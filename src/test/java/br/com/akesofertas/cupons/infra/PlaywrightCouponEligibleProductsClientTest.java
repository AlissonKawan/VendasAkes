package br.com.akesofertas.cupons.infra;

import br.com.akesofertas.cupons.client.CouponClientException;
import br.com.akesofertas.cupons.client.TipoErroInfraestruturaCupom;
import br.com.akesofertas.cupons.domain.FonteDescobertaProdutoCupom;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PlaywrightCouponEligibleProductsClientTest {
    private static final long COUPON_ID = 13558453L;
    private static final String PRODUCTS = "https://lista.mercadolivre.com.br/campanha?coupon_campaign_id=13558453";
    private static final String PREVIOUS = "https://www.mercadolivre.com.br/afiliados/hub";
    private Page page;
    private Locator next;
    private PlaywrightCouponEligibleProductsClient client;

    @BeforeEach
    void setUp() {
        page = mock(Page.class);
        next = mock(Locator.class);
        when(page.url()).thenReturn(PREVIOUS, PRODUCTS, PRODUCTS);
        when(page.locator(anyString())).thenReturn(next);
        client = new PlaywrightCouponEligibleProductsClient(page);
    }

    @Test void extraiItemIdExatoDoWid() {
        snapshot(cards(card("Produto", "https://produto.mercadolivre.com.br/item?wid=MLB4812130742&sid=search")), null, true, false);
        var product = client.buscarProdutos(COUPON_ID, PRODUCTS).getFirst();
        assertEquals("MLB4812130742", product.itemId());
        assertEquals(COUPON_ID, product.couponId());
        assertEquals("Produto", product.title());
    }

    @Test void extraiItemHistoricoConhecidoDoSsrSemDependerDeWid() {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("ssrCards", List.of(Map.of(
                "itemId", "MLB4639510787",
                "productId", "MLB60012839",
                "title", "Produto conhecido da campanha",
                "href", "https://www.mercadolivre.com.br/produto/p/MLB60012839",
                "price", "78.90")));
        value.put("cards", List.of());
        value.put("hasNext", false);
        value.put("hasResults", true);
        value.put("emptyState", false);
        when(page.evaluate(anyString())).thenReturn(value);

        var product = client.buscarProdutos(COUPON_ID, PRODUCTS).getFirst();

        assertEquals("MLB4639510787", product.itemId());
        assertEquals("MLB60012839", product.productId());
        assertEquals(new java.math.BigDecimal("78.90"), product.precoAtual());
        assertEquals(FonteDescobertaProdutoCupom.SSR_STRUCTURED, product.fonteItemId());
    }

    @Test void urlSemWidNaoProduzProduto() {
        snapshot(cards(card("Produto", "https://produto.mercadolivre.com.br/item")), null, true, false);
        assertTrue(client.buscarProdutos(COUPON_ID, PRODUCTS).isEmpty());
    }

    @Test void productIdNoPathNaoViraItemId() {
        snapshot(cards(card("Catálogo", "https://www.mercadolivre.com.br/produto/p/MLB60012839")), null, true, false);
        assertTrue(client.buscarProdutos(COUPON_ID, PRODUCTS).isEmpty());
    }

    @Test void extraiMultiplosProdutos() {
        snapshot(cards(
                card("Um", "https://produto.mercadolivre.com.br/a?wid=MLB1&sid=search"),
                card("Dois", "https://produto.mercadolivre.com.br/b?wid=MLB2&sid=search")), null, true, false);
        assertEquals(List.of("MLB1", "MLB2"), client.buscarProdutos(COUPON_ID, PRODUCTS)
                .stream().map(p -> p.itemId()).toList());
    }

    @Test void deduplicaPorItemIdMantendoPrimeiraOcorrencia() {
        snapshot(cards(
                card("Primeiro", "https://produto.mercadolivre.com.br/a?wid=MLB1&sid=search"),
                card("Duplicado", "https://produto.mercadolivre.com.br/b?wid=MLB1&sid=search")), null, true, false);
        var result = client.buscarProdutos(COUPON_ID, PRODUCTS);
        assertEquals(1, result.size());
        assertEquals("Primeiro", result.getFirst().title());
    }

    @Test void percorreDuasPaginasESomaProdutos() {
        String next = "https://lista.mercadolivre.com.br/campanha_Desde_49";
        when(page.url()).thenReturn(PREVIOUS, PRODUCTS, next, next);
        when(page.evaluate(anyString())).thenReturn(
                snapshotValue(cards(card("Um", "https://produto/a?wid=MLB1")), next, true, false),
                snapshotValue(cards(card("Dois", "https://produto/b?wid=MLB2")), null, true, false));

        var result = client.buscarProdutos(COUPON_ID, PRODUCTS);

        assertEquals(2, result.size());
        verify(this.next).click(any(Locator.ClickOptions.class));
        verify(page, times(2)).evaluate(anyString());
    }

    @Test void ultimaPaginaSemSeguinteEncerraColeta() {
        snapshot(cards(card("Único", "https://produto/a?wid=MLB1")), null, true, false);
        assertEquals(1, client.buscarProdutos(COUPON_ID, PRODUCTS).size());
        verify(page, times(1)).evaluate(anyString());
    }

    @Test void paginaSemProdutosLegitimaRetornaListaVazia() {
        snapshot(List.of(), null, false, true);
        assertTrue(client.buscarProdutos(COUPON_ID, PRODUCTS).isEmpty());
    }

    @Test void indicacaoDeResultadosSemCardsSinalizaEstruturaAlterada() {
        snapshot(List.of(), null, true, false);
        var error = assertThrows(CouponClientException.class,
                () -> client.buscarProdutos(COUPON_ID, PRODUCTS));
        assertEquals(TipoErroInfraestruturaCupom.ESTRUTURA_HTML_ALTERADA, error.tipo());
    }

    @Test void restauraUrlAnteriorAposSucesso() {
        snapshot(List.of(), null, false, true);
        client.buscarProdutos(COUPON_ID, PRODUCTS);
        verify(page).navigate(eq(PREVIOUS), any(Page.NavigateOptions.class));
    }

    @Test void restauraUrlAnteriorMesmoQuandoColetaFalha() {
        snapshot(List.of(), null, true, false);
        assertThrows(CouponClientException.class, () -> client.buscarProdutos(COUPON_ID, PRODUCTS));
        verify(page).navigate(eq(PREVIOUS), any(Page.NavigateOptions.class));
    }

    @Test void falhaNaRestauracaoNaoEscondeFalhaPrincipal() {
        snapshot(List.of(), null, true, false);
        when(page.navigate(eq(PREVIOUS), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("falha de restauração"));
        var error = assertThrows(CouponClientException.class,
                () -> client.buscarProdutos(COUPON_ID, PRODUCTS));
        assertEquals(TipoErroInfraestruturaCupom.ESTRUTURA_HTML_ALTERADA, error.tipo());
    }

    @Test void cicloNaPaginacaoEInterrompido() {
        when(page.url()).thenReturn(PREVIOUS, PRODUCTS, PRODUCTS, PRODUCTS);
        when(page.evaluate(anyString())).thenReturn(
                snapshotValue(List.of(), PRODUCTS, false, true));
        var error = assertThrows(CouponClientException.class,
                () -> client.buscarProdutos(COUPON_ID, PRODUCTS));
        assertEquals(TipoErroInfraestruturaCupom.ESTRUTURA_HTML_ALTERADA, error.tipo());
    }

    @Test void rejeitaEntradasInvalidasAntesDeNavegar() {
        assertThrows(IllegalArgumentException.class, () -> client.buscarProdutos(0, PRODUCTS));
        assertThrows(IllegalArgumentException.class, () -> client.buscarProdutos(COUPON_ID, "produto"));
        verify(page, never()).navigate(anyString(), any(Page.NavigateOptions.class));
    }

    @Test void extratorAceitaWidEmQualquerPosicaoDaQuery() {
        assertEquals("MLB4812130742", PlaywrightCouponEligibleProductsClient.extractItemId(
                "https://produto/item?foo=1&wid=MLB4812130742#detalhes"));
    }

    @Test void extraiItemIdDeUrlDiretaDoAnuncio() {
        assertEquals("MLB5314205168", PlaywrightCouponEligibleProductsClient.extractItemId(
                "https://produto.mercadolivre.com.br/MLB-5314205168-produto-_JM"));
    }

    @Test void extraiProdutoDiretoDeLinkDeVerificacao() {
        assertEquals("https://produto.mercadolivre.com.br/MLB-5314205168",
                PlaywrightCouponEligibleProductsClient.directProductUrl(
                        "https://www.mercadolivre.com.br/gz/account-verification?go=https%3A%2F%2Fproduto.mercadolivre.com.br%2FMLB-5314205168&tid=x"));
    }

    @Test void abreDestinoDeListaSemPararNaVerificacaoDeConta() {
        assertEquals("https://lista.mercadolivre.com.br/_Container_aff-list-26?matt_tool=56979161",
                PlaywrightCouponEligibleProductsClient.mercadoLivreDestination(
                        "https://www.mercadolivre.com.br/gz/account-verification?go=https%3A%2F%2Flista.mercadolivre.com.br%2F_Container_aff-list-26%3Fmatt_tool%3D56979161&tid=x"));
    }

    @Test void extraiPrecoAtualRealDoCard() {
        snapshot(cards(Map.of("title", "Produto", "href", "https://produto/item?wid=MLB1", "fraction", "159", "cents", "90")),
                null, true, false);
        var product = client.buscarProdutos(COUPON_ID, PRODUCTS).getFirst();
        assertEquals(new java.math.BigDecimal("159.90"), product.precoAtual());
    }

    @Test void parsePrecoTrataMilharESemCentavos() {
        assertEquals(new java.math.BigDecimal("1599.00"), PlaywrightCouponEligibleProductsClient.parsePreco("1.599", ""));
        assertEquals(new java.math.BigDecimal("1599.50"), PlaywrightCouponEligibleProductsClient.parsePreco("1.599", "50"));
        assertNull(PlaywrightCouponEligibleProductsClient.parsePreco("", ""));
        assertNull(PlaywrightCouponEligibleProductsClient.parsePreco(null, null));
    }

    private void snapshot(List<Map<String, Object>> cards, String nextUrl,
                          boolean hasResults, boolean emptyState) {
        when(page.evaluate(anyString())).thenReturn(snapshotValue(cards, nextUrl, hasResults, emptyState));
    }

    private static Map<String, Object> snapshotValue(List<Map<String, Object>> cards, String nextUrl,
                                                      boolean hasResults, boolean emptyState) {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("cards", cards);
        value.put("hasNext", nextUrl != null);
        value.put("hasResults", hasResults);
        value.put("emptyState", emptyState);
        return value;
    }

    @SafeVarargs
    private static List<Map<String, Object>> cards(Map<String, Object>... values) {
        return List.of(values);
    }

    private static Map<String, Object> card(String title, String href) {
        return Map.of("title", title, "href", href);
    }
}
