package br.com.akesofertas.cupons.infra;

import br.com.akesofertas.cupons.evidence.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MercadoLivrePdpEvidenceParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ITEM = Pattern.compile("\\\"item_id\\\":\\\"(MLB\\d+)\\\"");
    private static final Pattern PRODUCT = Pattern.compile("\\\"catalog_product_id\\\":\\\"(MLB\\d+)\\\"");
    private static final Pattern ROOT_PRICES = Pattern.compile("\\\"price\\\":([0-9]+(?:\\.[0-9]+)?),\\\"original_price\\\":([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PRICING = Pattern.compile("\\\"pricing\\\":\\{\\\"original_price\\\":([0-9]+(?:\\.[0-9]+)?),\\\"actual_price\\\":([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern WORDING = Pattern.compile("\\\"price_decrement\\\":\\{\\\"wording_label\\\":\\\"([^\\\"]+)\\\"");

    private MercadoLivrePdpEvidenceParser() {}

    static OfferSnapshot parse(String requestedItemId, String productIdHint, String finalUrl,
                               String mainSsr, String couponSsr, String jsonLdProduct,
                               String metaPrice, String domPrice, int requestedQuantity,
                               Instant capturedAt) {
        String itemId = firstGroup(ITEM, mainSsr);
        String productId = productIdFromJsonLd(jsonLdProduct);
        if (productId == null) productId = firstGroup(PRODUCT, mainSsr);
        if (productId == null) productId = blankToNull(productIdHint);

        BigDecimal original = null;
        BigDecimal base = null;
        Matcher rootPrices = ROOT_PRICES.matcher(nullToEmpty(mainSsr));
        if (rootPrices.find()) {
            base = decimal(rootPrices.group(1));
            original = decimal(rootPrices.group(2));
        } else {
            Matcher pricing = PRICING.matcher(nullToEmpty(mainSsr));
            if (pricing.find()) {
                original = decimal(pricing.group(1));
                base = decimal(pricing.group(2));
            }
        }

        BigDecimal jsonLdPrice = priceFromJsonLd(jsonLdProduct);
        BigDecimal current = jsonLdPrice != null ? jsonLdPrice
                : firstNonNull(decimal(metaPrice), decimal(domPrice), base);
        if (base == null) base = current;

        Set<EvidenceSource> priceSources = new LinkedHashSet<>();
        if (base != null || original != null) priceSources.add(EvidenceSource.SSR_STATE);
        if (jsonLdPrice != null) priceSources.add(EvidenceSource.JSON_LD);
        if (decimal(metaPrice) != null) priceSources.add(EvidenceSource.META);
        if (decimal(domPrice) != null) priceSources.add(EvidenceSource.DOM);
        priceSources.add(EvidenceSource.NETWORK_RESPONSE);

        String paymentContext = paymentContext(mainSsr, current, base);
        PriceEvidence price = new PriceEvidence(original, base, current, paymentContext, "BRL", priceSources);

        Set<String> priceBreakdownCampaigns = priceBreakdownCampaigns(mainSsr);
        Map<String, CouponDecision> decisions = detailedDecisions(couponSsr, requestedQuantity, capturedAt);
        mergeInlineDecisions(decisions, mainSsr, requestedQuantity, capturedAt, priceBreakdownCampaigns);
        for (String campaignId : priceBreakdownCampaigns) {
            CouponDecision existing = decisions.get(campaignId);
            if (existing != null && !existing.presentInPriceBreakdown()) {
                decisions.put(campaignId, copyWithPriceBreakdown(existing));
            } else if (existing == null) {
                decisions.put(campaignId, new CouponDecision(campaignId, null, null, null,
                        null, null, null, null, requestedQuantity, capturedAt, true));
            }
        }

        List<CampaignParticipation> participations = participations(couponSsr);
        URI uri;
        try { uri = finalUrl == null ? null : URI.create(finalUrl); }
        catch (IllegalArgumentException exception) { uri = null; }

        return new OfferSnapshot(requestedItemId, itemId, productId, uri, price,
                participations, List.copyOf(decisions.values()), capturedAt);
    }

    private static Map<String, CouponDecision> detailedDecisions(String couponSsr, int quantity, Instant capturedAt) {
        Map<String, CouponDecision> result = new LinkedHashMap<>();
        for (JsonNode node : arrayAfter(couponSsr, "\"coupons_list\":")) {
            String id = text(node, "campaign_id");
            if (id == null) continue;
            CouponRules rules = new CouponRules(
                    money(node, "min_amount"), money(node, "cap_amount"),
                    text(node, "currency"), instant(node, "expiration_date"),
                    node.path("installments").asBoolean(false));
            result.put(id, new CouponDecision(id, text(node, "status_id"), null, null,
                    text(node, "discount_type"), money(node, "discount_value"),
                    money(node, "given_discount"), rules, quantity, capturedAt, false));
        }
        return result;
    }

    private static void mergeInlineDecisions(Map<String, CouponDecision> result, String mainSsr,
                                             int quantity, Instant capturedAt,
                                             Set<String> priceBreakdownCampaigns) {
        String couponsObject = objectAfter(mainSsr, "\"coupons\":");
        if (couponsObject == null) return;
        try {
            JsonNode root = JSON.readTree(couponsObject);
            for (JsonNode node : root.path("coupons")) {
                String id = text(node, "campaign_id");
                if (id == null) continue;
                CouponDecision detailed = result.get(id);
                CouponRules rules = detailed != null ? detailed.rules() : null;
                String status = text(node, "status");
                result.put(id, new CouponDecision(id,
                        status != null ? status : detailed != null ? detailed.status() : null,
                        money(node, "amount"), text(node, "type"),
                        detailed != null ? detailed.discountType() : null,
                        detailed != null ? detailed.discountValue() : null,
                        detailed != null ? detailed.givenDiscount() : null,
                        rules, quantity, capturedAt, priceBreakdownCampaigns.contains(id)));
            }
        } catch (Exception ignored) {
        }
    }

    private static List<CampaignParticipation> participations(String couponSsr) {
        List<CampaignParticipation> result = new ArrayList<>();
        for (JsonNode node : arrayAfter(couponSsr, "\"rawCoupons\":")) {
            String id = text(node, "campaign_id");
            if (id == null) continue;
            List<String> collectors = strings(node.path("segmentations").path("collectors"), false);
            List<String> containers = strings(node.path("segmentations").path("containers"), true);
            CouponRules rules = new CouponRules(
                    brazilianMoney(node.path("amount").path("min_amount").asText(null)),
                    brazilianMoney(node.path("amount").path("cap_amount").asText(null)),
                    "BRL", instant(node, "expiration_date"), false);
            result.add(new CampaignParticipation(id, node.path("title").path("text").asText(null),
                    text(node, "activation_type"), node.path("status").path("id").asText(null),
                    text(node, "benefit_mode"), text(node, "code"), collectors, containers,
                    node.path("segmentations").path("has_items").asBoolean(false), rules));
        }
        return List.copyOf(result);
    }

    private static Set<String> priceBreakdownCampaigns(String mainSsr) {
        Set<String> ids = new LinkedHashSet<>();
        String pricing = objectAfter(mainSsr, "\"pricing\":");
        if (pricing == null) return ids;
        for (JsonNode node : arrayAfter(pricing, "\"campaigns\":")) {
            String id = text(node, "id");
            if (id != null && "SELLER_COUPON".equalsIgnoreCase(text(node, "type"))) ids.add(id);
        }
        return ids;
    }

    private static CouponDecision copyWithPriceBreakdown(CouponDecision d) {
        return new CouponDecision(d.campaignId(), d.status(), d.amount(), d.rawType(),
                d.discountType(), d.discountValue(), d.givenDiscount(), d.rules(), d.quantity(),
                d.verifiedAt(), true);
    }

    private static String paymentContext(String ssr, BigDecimal current, BigDecimal base) {
        Matcher matcher = WORDING.matcher(nullToEmpty(ssr));
        String label = matcher.find() ? matcher.group(1) : "";
        String normalized = label.toUpperCase(Locale.ROOT);
        if (normalized.contains("PIX") && normalized.contains("CUPOM")) return "PIX_PLUS_COUPON";
        if (normalized.contains("CUPOM")) return "COUPON";
        if (current != null && base != null && current.compareTo(base) < 0) return "PROMOTIONAL";
        return "STANDARD";
    }

    private static String productIdFromJsonLd(String json) {
        if (json == null || json.isBlank()) return null;
        try { return blankToNull(JSON.readTree(json).path("productID").asText(null)); }
        catch (Exception ignored) { return null; }
    }

    private static BigDecimal priceFromJsonLd(String json) {
        if (json == null || json.isBlank()) return null;
        try { return money(JSON.readTree(json).path("offers"), "price"); }
        catch (Exception ignored) { return null; }
    }

    private static JsonNode arrayAfter(String text, String marker) {
        String json = balancedAfter(text, marker, '[', ']');
        if (json == null) return JSON.createArrayNode();
        try {
            JsonNode node = JSON.readTree(json);
            return node != null && node.isArray() ? node : JSON.createArrayNode();
        } catch (Exception ignored) {
            return JSON.createArrayNode();
        }
    }

    private static String objectAfter(String text, String marker) {
        return balancedAfter(text, marker, '{', '}');
    }

    private static String balancedAfter(String text, String marker, char open, char close) {
        if (text == null) return null;
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) return null;
        int start = text.indexOf(open, markerIndex + marker.length());
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (ch == '\\') { escaped = true; continue; }
            if (ch == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (ch == open) depth++;
            if (ch == close && --depth == 0) return text.substring(start, i + 1);
        }
        return null;
    }

    private static List<String> strings(JsonNode array, boolean objectIds) {
        if (array == null || !array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String value = objectIds ? node.path("id").asText(null) : node.asText(null);
            if (value != null && !value.isBlank()) values.add(value);
        }
        return List.copyOf(values);
    }

    private static BigDecimal money(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        JsonNode value = node.path(field);
        try {
            BigDecimal decimal = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
            return decimal.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        catch (NumberFormatException ignored) { return null; }
    }

    private static BigDecimal brazilianMoney(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)").matcher(value.replace(".", "").replace(',', '.'));
        return matcher.find() ? decimal(matcher.group(1)) : null;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        return blankToNull(node.path(field).asText(null));
    }

    private static String firstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(nullToEmpty(value));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.trim()).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (NumberFormatException ignored) { return null; }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
