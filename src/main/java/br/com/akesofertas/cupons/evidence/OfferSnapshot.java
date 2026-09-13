package br.com.akesofertas.cupons.evidence;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record OfferSnapshot(
        String requestedItemId,
        String itemId,
        String productId,
        URI finalUrl,
        PriceEvidence price,
        List<CampaignParticipation> participations,
        List<CouponDecision> couponDecisions,
        Instant capturedAt) {

    public OfferSnapshot {
        participations = participations == null ? List.of() : List.copyOf(participations);
        couponDecisions = couponDecisions == null ? List.of() : List.copyOf(couponDecisions);
    }

    public boolean itemIdConfirmed() {
        return requestedItemId != null && requestedItemId.equalsIgnoreCase(itemId);
    }

    public boolean freshAt(Instant now, Duration maxAge) {
        if (capturedAt == null || now == null || maxAge == null || maxAge.isNegative()) return false;
        return !capturedAt.isAfter(now) && !capturedAt.plus(maxAge).isBefore(now);
    }
}
