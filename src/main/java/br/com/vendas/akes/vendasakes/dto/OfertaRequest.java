package br.com.vendas.akes.vendasakes.dto;

import br.com.vendas.akes.vendasakes.model.Marketplace;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OfertaRequest(
        @NotNull(message = "O marketplace e obrigatorio")
        Marketplace marketplace,

        @NotBlank(message = "O identificador do produto no marketplace e obrigatorio")
        @Size(max = 120, message = "O identificador deve ter no maximo 120 caracteres")
        String marketplaceProductId,

        @NotBlank(message = "O titulo e obrigatorio")
        @Size(max = 300, message = "O titulo deve ter no maximo 300 caracteres")
        String titulo,

        @NotNull(message = "O preco original e obrigatorio")
        @DecimalMin(value = "0.00", message = "O preco original nao pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "O preco original deve ter no maximo 2 casas decimais")
        BigDecimal precoOriginal,

        @NotNull(message = "O preco atual e obrigatorio")
        @DecimalMin(value = "0.00", message = "O preco atual nao pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "O preco atual deve ter no maximo 2 casas decimais")
        BigDecimal precoAtual,

        @NotBlank(message = "A URL do produto e obrigatoria")
        @Size(max = 2048, message = "A URL do produto deve ter no maximo 2048 caracteres")
        String urlProduto,

        @Size(max = 2048, message = "A URL de afiliado deve ter no maximo 2048 caracteres")
        String urlAfiliado,

        @Size(max = 2048, message = "A URL da imagem deve ter no maximo 2048 caracteres")
        String imagemUrl
) {
}
