package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.AffiliateLinkService;
import br.com.akesofertas.afiliados.NavegadorAfiliados;
import br.com.akesofertas.afiliados.ofertas.MercadoLivreAffiliateOffersClient;
import br.com.akesofertas.cupons.infra.PlaywrightCouponEligibleProductsClient;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

class MercadoLivreAfiliadosSessionFactoryTest {

    @Test
    void clienteDeCuponsUsaAPageDaSessaoExistente() {
        var navegador = mock(NavegadorAfiliados.class);
        var page = mock(Page.class);
        when(navegador.pagina()).thenReturn(page);
        var sessao = new MercadoLivreAfiliadosSessionFactory.Sessao(navegador,
                mock(MercadoLivreAffiliateOffersClient.class), mock(AffiliateLinkService.class));

        assertInstanceOf(PlaywrightCouponEligibleProductsClient.class, sessao.produtosCupons());

        verify(navegador, never()).contexto();
    }
}
