package br.com.akesofertas.cupons.evidence;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponRules(
        BigDecimal minAmount,
        BigDecimal capAmount,
        String currency,
        Instant expirationDate,
        boolean installments) {
}
