package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;

/** Resultado explícito da estimativa; valores calculados ficam nulos quando o cupom não é elegível. */
public record ResultadoCalculoCupom(
        boolean elegivel,
        BigDecimal precoOriginal,
        BigDecimal descontoAplicado,
        BigDecimal precoEstimado,
        MotivoIneligibilidadeCupom motivo) {
}
