package br.com.akesofertas.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OfertaRequest(
        @NotBlank(message = "titulo é obrigatório") @Size(max = 300) String titulo,
        @NotNull(message = "precoAtual é obrigatório")
        @DecimalMin(value = "0.00", message = "precoAtual não pode ser negativo")
        @Digits(integer = 10, fraction = 2) BigDecimal precoAtual,
        @DecimalMin(value = "0.00", message = "precoAntigo não pode ser negativo")
        @Digits(integer = 10, fraction = 2) BigDecimal precoAntigo,
        @NotBlank(message = "loja é obrigatória") @Size(max = 100) String loja,
        @NotBlank(message = "urlProduto é obrigatória") @Size(max = 2048) String urlProduto,
        @Size(max = 100) String cupom,
        @Size(max = 2048) String imagemUrl
) {}
