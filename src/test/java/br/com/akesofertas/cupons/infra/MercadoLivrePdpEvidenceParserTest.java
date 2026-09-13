package br.com.akesofertas.cupons.infra;

import br.com.akesofertas.cupons.evidence.ValidationConfidence;
import br.com.akesofertas.cupons.service.CouponEligibilityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLivrePdpEvidenceParserTest {
    private static final Instant NOW = Instant.parse("2026-09-09T14:00:00Z");

    @Test
    void parsesObservedApplicablePixPlusCouponCase() {
        String main = """
                _n.ctx.r={"price":179.9,"original_price":499.9,
                "item_id":"MLB4639510787","catalog_product_id":"MLB26643506",
                "pricing":{"campaigns":[{"id":"13558453","type":"SELLER_COUPON",
                "amount":{"type":"price","value":20}},{"id":"14181618","type":"PIX",
                "amount":{"type":"price","value":8}}]},
                "price_decrement":{"wording_label":"no Pix com Cupom","campaign_id":"13558453-14181618","discount_percentage":15}};
                """;
        String coupon = """
                _n.ctx.r={"coupons_list":[{"campaign_id":"13558453","title":"R$ 20 OFF",
                "status_id":"ACTIVE","currency":"BRL","discount_type":"FIXED","discount_value":20,
                "given_discount":20,"cap_amount":20,"min_amount":150,
                "expiration_date":"2026-10-01T02:59:59Z","installments":false}],
                "rawCoupons":[{"campaign_id":"13558453","title":{"text":"R$ 20 OFF"},
                "amount":{"min_amount":"Compra mínima R$ 150","cap_amount":"Limite de R$ 20"},
                "benefit_mode":"FIXED","status":{"id":"ACTIVE"},"code":"",
                "expiration_date":"2026-10-01T02:59:59Z","segmentations":{"collectors":["1867843782"],
                "containers":[{"id":"MLB1775107"}],"has_items":false}}]};
                """;
        String ld = "{\"@type\":\"Product\",\"productID\":\"MLB26643506\",\"offers\":{\"price\":151.90}}";

        var snapshot = MercadoLivrePdpEvidenceParser.parse("MLB4639510787", null,
                "https://produto/p/MLB26643506?wid=MLB4639510787", main, coupon, ld,
                "151.90", "151.90", 1, NOW);

        assertTrue(snapshot.itemIdConfirmed());
        assertEquals(new BigDecimal("499.90"), snapshot.price().originalPrice());
        assertEquals(new BigDecimal("179.90"), snapshot.price().basePrice());
        assertEquals(new BigDecimal("151.90"), snapshot.price().currentPrice());
        assertEquals("PIX_PLUS_COUPON", snapshot.price().paymentContext());
        assertEquals(new BigDecimal("20.00"), snapshot.couponDecisions().getFirst().givenDiscount());
        assertTrue(snapshot.couponDecisions().getFirst().presentInPriceBreakdown());
        assertEquals(ValidationConfidence.HIGH, new CouponEligibilityService().confidence(
                snapshot, "13558453", NOW, Duration.ofMinutes(2)));
    }

    @Test
    void preservesObservedNotMinimumPurchaseDecisionEvenAtQuantityTwo() {
        String main = """
                _n.ctx.r={"price":78.9,"original_price":159.9,"item_id":"MLB4812130742",
                "catalog_product_id":"MLB25929487","coupons":{"coupons":[{
                "label":"Compre R$ 150 e ganhe R$ 20 OFF","status":"redeemed","amount":0,
                "campaign_id":"13558453","type":"COUPON_NOT_MIN_PURCHASE_AMOUNT","scarcity":"N/A"}]}};
                """;
        String coupon = """
                _n.ctx.r={"coupons_list":[{"campaign_id":"13558453","title":"R$ 20 OFF",
                "status_id":"ACTIVE","currency":"BRL","discount_type":"FIXED","discount_value":20,
                "given_discount":0,"cap_amount":20,"min_amount":150,
                "expiration_date":"2026-10-01T02:59:59Z","installments":false}],
                "pdpData":{"itemId":"MLB4812130742","unitPrice":"78.9","quantity":"2"}};
                """;

        var snapshot = MercadoLivrePdpEvidenceParser.parse("MLB4812130742", "MLB25929487",
                "https://produto/p/MLB25929487?quantity=2", main, coupon,
                "{\"@type\":\"Product\",\"productID\":\"MLB25929487\",\"offers\":{\"price\":78.9}}",
                "78.90", "78.90", 2, NOW);
        var decision = snapshot.couponDecisions().getFirst();

        assertEquals(2, decision.quantity());
        assertEquals(new BigDecimal("0.00"), decision.givenDiscount());
        assertEquals("COUPON_NOT_MIN_PURCHASE_AMOUNT", decision.rawType());
        assertEquals("redeemed", decision.status());
        assertEquals(ValidationConfidence.LOW, new CouponEligibilityService().confidence(
                snapshot, "13558453", NOW, Duration.ofMinutes(2)));
    }

    @Test
    void detectsRedirectToDifferentItemAndPreservesUrlContexts() {
        var snapshot = MercadoLivrePdpEvidenceParser.parse("MLB1", null, "https://produto/?wid=MLB2",
                "{\"item_id\":\"MLB2\",\"price\":10,\"original_price\":20}", null,
                "{\"@type\":\"Product\",\"productID\":\"MLB9\",\"offers\":{\"price\":10}}",
                "10", "10", 1, NOW);
        assertFalse(snapshot.itemIdConfirmed());
        assertEquals("https://produto/p/MLB9?wid=MLB1",
                MercadoLivrePdpValidator.preserveItemContext("https://produto/p/MLB9?wid=MLB1", "MLB1"));
        assertTrue(MercadoLivrePdpValidator.preserveItemContext(
                "https://produto/p/MLB9", "MLB1").contains("pdp_filters=item_id%3AMLB1"));
        assertTrue(MercadoLivrePdpValidator.withQuantity(
                "https://www.mercadolivre.com.br/cupons/pdp?quantity=1", 2).contains("quantity=2"));
    }
}
