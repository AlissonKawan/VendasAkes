package br.com.akesofertas.cupons.evidence;

import java.util.List;

public record CampaignParticipation(
        String campaignId,
        String title,
        String activationType,
        String statusId,
        String benefitMode,
        String code,
        List<String> collectorIds,
        List<String> containerIds,
        boolean hasItems,
        CouponRules rules) {

    public CampaignParticipation {
        collectorIds = collectorIds == null ? List.of() : List.copyOf(collectorIds);
        containerIds = containerIds == null ? List.of() : List.copyOf(containerIds);
    }
}
