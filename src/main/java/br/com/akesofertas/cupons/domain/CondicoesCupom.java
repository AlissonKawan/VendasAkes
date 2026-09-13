package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Condições reconhecidas do texto do portal; campos desconhecidos permanecem nulos. */
public record CondicoesCupom(
        Long couponId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minimumPurchase,
        BigDecimal discountPercent,
        BigDecimal fixedDiscount,
        BigDecimal maximumDiscount,
        Integer usesPerCpf,
        Integer availableStock,
        Boolean automaticApplication,
        Boolean cumulative,
        String rawTerms) {
}
