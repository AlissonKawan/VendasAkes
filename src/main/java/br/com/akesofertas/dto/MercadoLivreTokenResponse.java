package br.com.akesofertas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Os nomes JSON da API usam snake_case; no Java usamos camelCase.
public record MercadoLivreTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        String scope,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("refresh_token") String refreshToken
) {
    // Evita revelar credenciais se alguém imprimir este objeto durante o estudo.
    @Override
    public String toString() {
        return "MercadoLivreTokenResponse[tokens=REDACTED]";
    }
}
