package br.com.vendas.akes.vendasakes.service;

import br.com.vendas.akes.vendasakes.dto.OfertaRequest;
import br.com.vendas.akes.vendasakes.dto.OfertaResponse;
import br.com.vendas.akes.vendasakes.exception.OfertaDuplicadaException;
import br.com.vendas.akes.vendasakes.mapper.OfertaMapper;
import br.com.vendas.akes.vendasakes.model.Marketplace;
import br.com.vendas.akes.vendasakes.model.Oferta;
import br.com.vendas.akes.vendasakes.repository.OfertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfertaServiceTest {

    @Mock
    private OfertaRepository ofertaRepository;

    private OfertaService ofertaService;

    @BeforeEach
    void setUp() {
        ofertaService = new OfertaService(ofertaRepository, new OfertaMapper());
    }

    @Test
    void deveCalcularDescontoAoCriarOferta() {
        OfertaRequest request = criarRequest();
        when(ofertaRepository.findByMarketplaceAndMarketplaceProductId(
                request.marketplace(), request.marketplaceProductId()
        )).thenReturn(Optional.empty());
        when(ofertaRepository.saveAndFlush(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = ofertaService.criar(request);

        assertThat(response.percentualDesconto()).isEqualByComparingTo("20.00");
        assertThat(response.publicado()).isFalse();
        assertThat(response.dataEncontrada()).isNotNull();
    }

    @Test
    void naoDeveCriarProdutoDuplicadoNoMesmoMarketplace() {
        OfertaRequest request = criarRequest();
        Oferta existente = new Oferta(
                request.marketplace(),
                request.marketplaceProductId(),
                request.titulo(),
                request.precoOriginal(),
                request.precoAtual(),
                new BigDecimal("20.00"),
                request.urlProduto(),
                request.urlAfiliado(),
                request.imagemUrl(),
                java.time.LocalDateTime.now()
        );
        when(ofertaRepository.findByMarketplaceAndMarketplaceProductId(
                request.marketplace(), request.marketplaceProductId()
        )).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> ofertaService.criar(request))
                .isInstanceOf(OfertaDuplicadaException.class);
    }

    private OfertaRequest criarRequest() {
        return new OfertaRequest(
                Marketplace.MERCADO_LIVRE,
                "MLB123456",
                "Fone Bluetooth",
                new BigDecimal("250.00"),
                new BigDecimal("200.00"),
                "https://produto.mercadolivre.com.br/MLB123456",
                null,
                "https://http2.mlstatic.com/imagem.jpg"
        );
    }
}
