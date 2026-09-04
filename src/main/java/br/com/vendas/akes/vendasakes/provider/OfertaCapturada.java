package br.com.vendas.akes.vendasakes.provider;

import java.math.BigDecimal;

public record OfertaCapturada(
        String marketplaceProductId,
        String titulo,
        BigDecimal precoOriginal,
        BigDecimal precoAtual,
        String urlProduto,
        String urlAfiliado,
        String imagemUrl
) {
}
