package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calcula uma estimativa monetária usando somente as condições já estruturadas. */
public final class CalculadoraDescontoCupom {
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int ESCALA_MONETARIA = 2;
    private static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;

    public ResultadoCalculoCupom calcular(BigDecimal preco, CondicoesCupom condicoes) {
        validarEntrada(preco, condicoes);
        BigDecimal precoOriginal = monetario(preco);

        if (condicoes.minimumPurchase() != null
                && preco.compareTo(condicoes.minimumPurchase()) < 0) {
            return inelegivel(precoOriginal, MotivoIneligibilidadeCupom.ABAIXO_COMPRA_MINIMA);
        }

        BigDecimal percentual = condicoes.discountPercent();
        BigDecimal fixo = condicoes.fixedDiscount();
        if (percentual == null && fixo == null) {
            return inelegivel(precoOriginal, MotivoIneligibilidadeCupom.SEM_REGRA_DE_DESCONTO);
        }
        if (percentual != null && fixo != null) {
            return inelegivel(precoOriginal, MotivoIneligibilidadeCupom.CONDICOES_INCONSISTENTES);
        }
        if (valorNaoPositivo(percentual) || valorNaoPositivo(fixo)
                || valorNegativo(condicoes.minimumPurchase())
                || valorNegativo(condicoes.maximumDiscount())) {
            return inelegivel(precoOriginal, MotivoIneligibilidadeCupom.CONDICOES_INCONSISTENTES);
        }

        BigDecimal desconto = percentual != null
                ? precoOriginal.multiply(percentual).divide(CEM, ESCALA_MONETARIA, ARREDONDAMENTO)
                : monetario(fixo);

        if (condicoes.maximumDiscount() != null) {
            desconto = desconto.min(monetario(condicoes.maximumDiscount()));
        }
        desconto = desconto.min(precoOriginal).setScale(ESCALA_MONETARIA, ARREDONDAMENTO);
        BigDecimal estimado = precoOriginal.subtract(desconto).setScale(ESCALA_MONETARIA, ARREDONDAMENTO);

        return new ResultadoCalculoCupom(true, precoOriginal, desconto, estimado, null);
    }

    private static void validarEntrada(BigDecimal preco, CondicoesCupom condicoes) {
        if (preco == null || preco.signum() <= 0) {
            throw new IllegalArgumentException("preço deve ser maior que zero");
        }
        if (condicoes == null) throw new IllegalArgumentException("condições são obrigatórias");
    }

    private static boolean valorNaoPositivo(BigDecimal valor) {
        return valor != null && valor.signum() <= 0;
    }

    private static boolean valorNegativo(BigDecimal valor) {
        return valor != null && valor.signum() < 0;
    }

    private static BigDecimal monetario(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, ARREDONDAMENTO);
    }

    private static ResultadoCalculoCupom inelegivel(
            BigDecimal precoOriginal, MotivoIneligibilidadeCupom motivo) {
        return new ResultadoCalculoCupom(false, precoOriginal, null, null, motivo);
    }
}
