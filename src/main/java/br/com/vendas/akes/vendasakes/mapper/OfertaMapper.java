package br.com.vendas.akes.vendasakes.mapper;

import br.com.vendas.akes.vendasakes.dto.OfertaRequest;
import br.com.vendas.akes.vendasakes.dto.OfertaResponse;
import br.com.vendas.akes.vendasakes.model.Oferta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class OfertaMapper {

    public Oferta toEntity(
            OfertaRequest request,
            BigDecimal percentualDesconto,
            LocalDateTime dataEncontrada
    ) {
        return new Oferta(
                request.marketplace(),
                request.marketplaceProductId().trim(),
                request.titulo().trim(),
                request.precoOriginal(),
                request.precoAtual(),
                percentualDesconto,
                request.urlProduto().trim(),
                textoOpcional(request.urlAfiliado()),
                textoOpcional(request.imagemUrl()),
                dataEncontrada
        );
    }

    public void updateEntity(
            Oferta oferta,
            OfertaRequest request,
            BigDecimal percentualDesconto
    ) {
        oferta.atualizarDados(
                request.marketplace(),
                request.marketplaceProductId().trim(),
                request.titulo().trim(),
                request.precoOriginal(),
                request.precoAtual(),
                percentualDesconto,
                request.urlProduto().trim(),
                textoOpcional(request.urlAfiliado()),
                textoOpcional(request.imagemUrl())
        );
    }

    public OfertaResponse toResponse(Oferta oferta) {
        return new OfertaResponse(
                oferta.getId(),
                oferta.getMarketplace(),
                oferta.getMarketplaceProductId(),
                oferta.getTitulo(),
                oferta.getPrecoOriginal(),
                oferta.getPrecoAtual(),
                oferta.getPercentualDesconto(),
                oferta.getUrlProduto(),
                oferta.getUrlAfiliado(),
                oferta.getImagemUrl(),
                oferta.getDataEncontrada(),
                oferta.getDataPublicacao(),
                oferta.isPublicado()
        );
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
