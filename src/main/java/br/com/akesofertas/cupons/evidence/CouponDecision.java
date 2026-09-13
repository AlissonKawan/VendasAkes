package br.com.akesofertas.cupons.evidence;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponDecision(
        String campaignId,
        String status,
        BigDecimal amount,
        String rawType,
        String discountType,
        BigDecimal discountValue,
        BigDecimal givenDiscount,
        CouponRules rules,
        int quantity,
        Instant verifiedAt,
        boolean presentInPriceBreakdown) {

    public boolean explicitlyDenied() {
        return rawType != null && !rawType.isBlank() && givenDiscountOrZero().signum() <= 0;
    }

    public BigDecimal givenDiscountOrZero() {
        return givenDiscount == null ? BigDecimal.ZERO : givenDiscount;
    }
}
