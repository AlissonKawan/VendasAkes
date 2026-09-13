package br.com.akesofertas.cupons.evidence;

import java.math.BigDecimal;
import java.util.Set;

public record PriceEvidence(
        BigDecimal originalPrice,
        BigDecimal basePrice,
        BigDecimal currentPrice,
        String paymentContext,
        String currency,
        Set<EvidenceSource> sources) {

    public PriceEvidence {
        sources = sources == null ? Set.of() : Set.copyOf(sources);
    }

    public boolean confirmed() {
        return basePrice != null && basePrice.signum() > 0
                && currentPrice != null && currentPrice.signum() > 0
                && !sources.isEmpty();
    }
}
