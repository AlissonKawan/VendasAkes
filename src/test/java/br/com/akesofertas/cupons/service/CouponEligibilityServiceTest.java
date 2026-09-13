package br.com.akesofertas.cupons.service;

import br.com.akesofertas.cupons.domain.CondicoesCupom;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.cupons.evidence.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CouponEligibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-09T14:00:00Z");
    private final CouponEligibilityService service = new CouponEligibilityService();

    @Test
    void activeParticipationDoesNotMeanEligibleWhenGivenDiscountIsZero() {
        OfferSnapshot snapshot = snapshot(decision("ACTIVE", BigDecimal.ZERO, null, false, futureRules()), NOW);
        assertEquals(ValidationConfidence.LOW,
                service.confidence(snapshot, "13558453", NOW, Duration.ofMinutes(2)));
        assertTrue(service.confirmedCoupon(candidate(), snapshot, NOW, Duration.ofMinutes(2)).isEmpty());
    }

    @Test
    void campaignAppliedWithGivenDiscountProducesHighConfidence() {
        OfferSnapshot snapshot = snapshot(decision("ACTIVE", new BigDecimal("20.00"), null, true, futureRules()), NOW);
        CupomAplicavel confirmed = service.confirmedCoupon(candidate(), snapshot, NOW, Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ValidationConfidence.HIGH, confirmed.confidence());
        assertEquals(new BigDecimal("20.00"), confirmed.descontoAplicado());
        assertEquals("PIX_PLUS_COUPON", confirmed.paymentContext());
        assertFalse(confirmed.temCodigoExibivel(), "cupom automático sem código continua válido");
    }

    @Test
    void preservesRawTypeAndNeverOverridesMercadoLivreWithLocalMath() {
        CouponDecision decision = decision("redeemed", BigDecimal.ZERO,
                "COUPON_NOT_MIN_PURCHASE_AMOUNT", false, futureRules());
        OfferSnapshot snapshot = snapshot(decision, NOW);
        assertEquals("COUPON_NOT_MIN_PURCHASE_AMOUNT", snapshot.couponDecisions().getFirst().rawType());
        assertEquals(ValidationConfidence.LOW,
                service.confidence(snapshot, "13558453", NOW, Duration.ofMinutes(2)));
    }

    @Test
    void rejectsExpiredCampaignStaleSnapshotDivergentItemAndUnavailablePdp() {
        OfferSnapshot expiredCampaign = snapshot(decision("ACTIVE", new BigDecimal("20"), null,
                true, new CouponRules(new BigDecimal("150"), new BigDecimal("20"), "BRL",
                        NOW.minusSeconds(1), false)), NOW);
        assertEquals(ValidationConfidence.LOW,
                service.confidence(expiredCampaign, "13558453", NOW, Duration.ofMinutes(2)));

        OfferSnapshot stale = snapshot(decision("ACTIVE", new BigDecimal("20"), null, true, futureRules()),
                NOW.minus(Duration.ofMinutes(3)));
        assertEquals(ValidationConfidence.LOW,
                service.confidence(stale, "13558453", NOW, Duration.ofMinutes(2)));

        OfferSnapshot divergent = new OfferSnapshot("MLB1", "MLB2", "MLB9", URI.create("https://produto"),
                price(), List.of(), List.of(decision("ACTIVE", new BigDecimal("20"), null, true, futureRules())), NOW);
        assertEquals(ValidationConfidence.LOW,
                service.confidence(divergent, "13558453", NOW, Duration.ofMinutes(2)));

        OfferSnapshot unavailable = new OfferSnapshot("MLB1", null, null, null,
                new PriceEvidence(null, null, null, "UNAVAILABLE", "BRL", Set.of()),
                List.of(), List.of(), NOW);
        assertEquals(ValidationConfidence.LOW,
                service.confidence(unavailable, "13558453", NOW, Duration.ofMinutes(2)));
    }

    @Test
    void modelsActivationSeparatelyForEuQueroAndAutomatic() {
        CampaignParticipation euQuero = new CampaignParticipation("1", "10%", "EU_QUERO", "INACTIVE",
                "PERCENT", null, List.of(), List.of("C1"), false, futureRules());
        CampaignParticipation automatic = new CampaignParticipation("2", "R$ 20", "AUTOMATIC", "ACTIVE",
                "FIXED", "", List.of(), List.of("C2"), false, futureRules());
        assertEquals("INACTIVE", euQuero.statusId());
        assertEquals("EU_QUERO", euQuero.activationType());
        assertEquals("AUTOMATIC", automatic.activationType());
        assertTrue(automatic.code().isEmpty());
    }

    private OfferSnapshot snapshot(CouponDecision decision, Instant capturedAt) {
        return new OfferSnapshot("MLB4639510787", "MLB4639510787", "MLB26643506",
                URI.create("https://produto/p/MLB26643506?wid=MLB4639510787"), price(),
                List.of(new CampaignParticipation("13558453", "R$ 20 OFF", "AUTOMATIC", "ACTIVE",
                        "FIXED", "", List.of("1"), List.of("MLB1775107"), false, futureRules())),
                List.of(decision), capturedAt);
    }

    private PriceEvidence price() {
        return new PriceEvidence(new BigDecimal("499.90"), new BigDecimal("179.90"),
                new BigDecimal("151.90"), "PIX_PLUS_COUPON", "BRL",
                Set.of(EvidenceSource.SSR_STATE, EvidenceSource.JSON_LD));
    }

    private CouponDecision decision(String status, BigDecimal given, String rawType,
                                    boolean breakdown, CouponRules rules) {
        return new CouponDecision("13558453", status, given.signum() == 0 ? BigDecimal.ZERO : given,
                rawType, "FIXED", new BigDecimal("20.00"), given, rules, 1, NOW, breakdown);
    }

    private CouponRules futureRules() {
        return new CouponRules(new BigDecimal("150.00"), new BigDecimal("20.00"), "BRL",
                NOW.plus(Duration.ofDays(1)), false);
    }

    private CupomAplicavel candidate() {
        return new CupomAplicavel(13558453L, null, "R$ 20 OFF",
                new CondicoesCupom(13558453L, null, null, new BigDecimal("150"), null,
                        new BigDecimal("20"), new BigDecimal("20"), null, null, null, null, null),
                new BigDecimal("20"), new BigDecimal("159.90"));
    }
}
