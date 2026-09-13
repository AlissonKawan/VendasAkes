package br.com.akesofertas.telegram.ouvinte;

import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.cupons.client.CouponEligibleProductsClient;
import br.com.akesofertas.cupons.domain.ProdutoElegivelCupom;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.scheduler.MercadoLivreAfiliadosSessionFactory;
import br.com.akesofertas.telegram.PublicadorOfertaService;
import br.com.akesofertas.telegram.ResultadoPublicacaoOferta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampanhaProcessingServiceTest {

    @Test
    void naoMarcaCupomComoProcessadoQuandoNenhumaOfertaFoiPublicada() {
        var sessoes = mock(MercadoLivreAfiliadosSessionFactory.class);
        var sessao = mock(MercadoLivreAfiliadosSessionFactory.Sessao.class);
        var produtos = mock(CouponEligibleProductsClient.class);
        var publicador = mock(PublicadorOfertaService.class);
        var publicadas = mock(OfertaPublicadaService.class);
        var storage = mock(CupomStorageService.class);
        var cupom = new CupomDetectadoEntity("CUPOM10", "10% OFF", "R$100", "R$50",
                LocalDate.now().plusDays(1), null, "https://bit.ly/campanha",
                "https://www.mercadolivre.com.br/ofertas/campanha");

        when(storage.buscarPendentesValidos()).thenReturn(List.of(cupom));
        when(sessoes.abrir()).thenReturn(sessao);
        when(sessao.produtosCupons()).thenReturn(produtos);
        when(produtos.buscarProdutos(anyLong(), anyString())).thenReturn(List.of());

        var service = new CampanhaProcessingService(sessoes, publicador, publicadas, storage, 3);

        assertEquals(0, service.processarCuponsPendentes());
        verify(storage, never()).marcarComoProcessado(any());
        verify(storage, never()).marcarComoErro(any(), anyString());
    }

    @Test
    void limitaATresOfertasNoTotalMesmoComVariosCuponsPendentes() {
        var sessoes = mock(MercadoLivreAfiliadosSessionFactory.class);
        var sessao = mock(MercadoLivreAfiliadosSessionFactory.Sessao.class);
        var produtos = mock(CouponEligibleProductsClient.class);
        var publicador = mock(PublicadorOfertaService.class);
        var publicadas = mock(OfertaPublicadaService.class);
        var storage = mock(CupomStorageService.class);

        var cupom1 = cupom("CUPOM1", "https://www.mercadolivre.com.br/ofertas/1");
        var cupom2 = cupom("CUPOM2", "https://www.mercadolivre.com.br/ofertas/2");
        var cupom3 = cupom("CUPOM3", "https://www.mercadolivre.com.br/ofertas/3");
        when(storage.buscarPendentesValidos()).thenReturn(List.of(cupom1, cupom2, cupom3));
        when(sessoes.abrir()).thenReturn(sessao);
        when(sessao.produtosCupons()).thenReturn(produtos);
        when(produtos.buscarProdutos(anyLong(), anyString())).thenReturn(
                List.of(produto("MLB1"), produto("MLB2")),
                List.of(produto("MLB3"), produto("MLB4")));
        when(sessao.resolverImagem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessao.gerarLinks(anyList())).thenAnswer(invocation -> {
            String url = ((List<String>) invocation.getArgument(0)).getFirst();
            return List.of(new ResultadoLinkAfiliado(url, true, "https://meli.la/teste", true, null, null));
        });
        when(publicador.publicarComLink(anyString(), any(), anyString()))
                .thenReturn(ResultadoPublicacaoOferta.ENVIADA);

        var service = new CampanhaProcessingService(sessoes, publicador, publicadas, storage, 3);

        assertEquals(3, service.processarCuponsPendentes());
        verify(publicador, times(3)).publicarComLink(anyString(), any(), anyString());
        verify(produtos, times(2)).buscarProdutos(anyLong(), anyString());
        verify(storage, times(2)).marcarComoProcessado(any());
    }

    private static CupomDetectadoEntity cupom(String codigo, String url) {
        return new CupomDetectadoEntity(codigo, "10% OFF", "R$1", "R$50",
                LocalDate.now().plusDays(1), null, url, url);
    }

    private static ProdutoElegivelCupom produto(String itemId) {
        return new ProdutoElegivelCupom(1L, itemId, "Produto " + itemId,
                "https://produto.mercadolivre.com.br/" + itemId, new BigDecimal("100.00"));
    }
}
