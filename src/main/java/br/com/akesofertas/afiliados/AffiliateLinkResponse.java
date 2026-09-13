package br.com.akesofertas.afiliados;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AffiliateLinkResponse(
        Integer status,
        List<Link> urls,
        @JsonProperty("total_items") Integer totalItems,
        @JsonProperty("total_success") Integer totalSuccess,
        @JsonProperty("total_error") Integer totalError) {
        
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(
            Boolean created,
            String tag,
            @JsonProperty("short_url") String shortUrl,
            @JsonProperty("origin_url") String originUrl,
            @JsonProperty("error_code") Integer errorCode,
            String message,
            Integer status) {}
}
