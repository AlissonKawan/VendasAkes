package br.com.vendas.akes.vendasakes.dto;

import br.com.vendas.akes.vendasakes.model.Marketplace;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OfertaResponse(
        Long id,
        Marketplace marketplace,
        String marketplaceProductId,
        String titulo,
        BigDecimal precoOriginal,
        BigDecimal precoAtual,
        BigDecimal percentualDesconto,
        String urlProduto,
        String urlAfiliado,
        String imagemUrl,
        LocalDateTime dataEncontrada,
        LocalDateTime dataPublicacao,
        boolean publicado
) {
}
