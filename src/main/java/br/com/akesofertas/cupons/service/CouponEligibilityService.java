package br.com.akesofertas.cupons.service;

import br.com.akesofertas.cupons.domain.CondicoesCupom;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.cupons.evidence.CampaignParticipation;
import br.com.akesofertas.cupons.evidence.CouponDecision;
import br.com.akesofertas.cupons.evidence.CouponRules;
import br.com.akesofertas.cupons.evidence.OfferSnapshot;
import br.com.akesofertas.cupons.evidence.ValidationConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Gate final: participação em campanha nunca substitui a decisão item-específica do PDP. */
@Service
public class CouponEligibilityService {
    private static final Logger log = LoggerFactory.getLogger(CouponEligibilityService.class);

    public ValidationConfidence confidence(OfferSnapshot snapshot, String campaignId,
                                           Instant now, Duration maxAge) {
        if (snapshot == null || campaignId == null || campaignId.isBlank()) return ValidationConfidence.LOW;
        if (!snapshot.itemIdConfirmed() || !snapshot.freshAt(now, maxAge)) return ValidationConfidence.LOW;
        if (snapshot.price() == null || !snapshot.price().confirmed()) return ValidationConfidence.MEDIUM;

        Optional<CouponDecision> decision = decision(snapshot, campaignId);
        if (decision.isEmpty()) return ValidationConfidence.MEDIUM;
        CouponDecision value = decision.get();
        if (value.explicitlyDenied() || value.givenDiscountOrZero().signum() <= 0
                && !value.presentInPriceBreakdown()) {
            return ValidationConfidence.LOW;
        }
        if (expired(value.rules(), now)) return ValidationConfidence.LOW;
        return value.givenDiscountOrZero().signum() > 0 || value.presentInPriceBreakdown()
                ? ValidationConfidence.HIGH
                : ValidationConfidence.MEDIUM;
    }

    public Optional<CupomAplicavel> confirmedCoupon(CupomAplicavel candidate, OfferSnapshot snapshot,
                                                     Instant now, Duration maxAge) {
        if (candidate == null || candidate.couponId() == null || snapshot == null) return Optional.empty();
        String campaignId = candidate.couponId().toString();
        ValidationConfidence confidence = confidence(snapshot, campaignId, now, maxAge);
        Optional<CouponDecision> decision = decision(snapshot, campaignId);
        CouponDecision value = decision.orElse(null);

        logDecision(snapshot, campaignId, value, confidence);
        if (confidence != ValidationConfidence.HIGH || value == null) return Optional.empty();

        BigDecimal discount = value.givenDiscountOrZero().signum() > 0
                ? value.givenDiscountOrZero() : value.discountValue();
        if (discount == null || discount.signum() <= 0) return Optional.empty();

        CampaignParticipation participation = snapshot.participations().stream()
                .filter(p -> campaignId.equals(p.campaignId())).findFirst().orElse(null);
        CouponRules rules = value.rules();
        BigDecimal currentPrice = snapshot.price().currentPrice();
        if (currentPrice == null || currentPrice.signum() <= 0) return Optional.empty();

        BigDecimal percent = "PERCENT".equalsIgnoreCase(value.discountType())
                ? value.discountValue() : null;
        BigDecimal fixed = "FIXED".equalsIgnoreCase(value.discountType())
                ? value.discountValue() : null;
        if (percent == null && fixed == null) fixed = discount;

        CondicoesCupom conditions = new CondicoesCupom(
                candidate.couponId(), null, null,
                rules != null ? rules.minAmount() : candidate.compraMinima(),
                percent, fixed, rules != null ? rules.capAmount() : null,
                null, null, null, null, null);
        String title = participation != null && participation.title() != null
                ? participation.title() : candidate.title();
        String code = participation != null && shortHumanCode(participation.code())
                ? participation.code() : candidate.codigoExibivel();

        return Optional.of(new CupomAplicavel(
                candidate.couponId(), code, title, conditions, discount, currentPrice,
                rules != null ? rules.minAmount() : candidate.compraMinima(),
                code == null || code.isBlank() ? "Cupom aplicado automaticamente no Mercado Livre" : null,
                ValidationConfidence.HIGH, snapshot.capturedAt(), snapshot.price().paymentContext(), value.rawType()));
    }

    private Optional<CouponDecision> decision(OfferSnapshot snapshot, String campaignId) {
        return snapshot.couponDecisions().stream()
                .filter(d -> campaignId.equals(d.campaignId()))
                .sorted((a, b) -> Boolean.compare(b.presentInPriceBreakdown(), a.presentInPriceBreakdown()))
                .findFirst();
    }

    private boolean expired(CouponRules rules, Instant now) {
        return rules != null && rules.expirationDate() != null && now != null
                && now.isAfter(rules.expirationDate());
    }

    private boolean shortHumanCode(String code) {
        return code != null && !code.isBlank() && code.length() <= 20
                && !code.contains("+") && !code.contains("/") && !code.contains("=");
    }

    private void logDecision(OfferSnapshot snapshot, String campaignId, CouponDecision decision,
                             ValidationConfidence confidence) {
        String requested = snapshot != null ? snapshot.requestedItemId() : null;
        String confirmed = snapshot != null ? snapshot.itemId() : null;
        log.info("[PDP] itemId solicitado={} itemId confirmado={} productId={} basePrice={} currentPrice={} paymentContext={}",
                requested, confirmed, snapshot != null ? snapshot.productId() : null,
                snapshot != null && snapshot.price() != null ? snapshot.price().basePrice() : null,
                snapshot != null && snapshot.price() != null ? snapshot.price().currentPrice() : null,
                snapshot != null && snapshot.price() != null ? snapshot.price().paymentContext() : null);
        log.info("[COUPON] campaignId={} activationStatus={} rawType={} givenDiscount={} minAmount={} capAmount={} confidence={}",
                campaignId, decision != null ? decision.status() : null,
                decision != null ? decision.rawType() : null,
                decision != null ? decision.givenDiscount() : null,
                decision != null && decision.rules() != null ? decision.rules().minAmount() : null,
                decision != null && decision.rules() != null ? decision.rules().capAmount() : null,
                confidence);
    }
}
