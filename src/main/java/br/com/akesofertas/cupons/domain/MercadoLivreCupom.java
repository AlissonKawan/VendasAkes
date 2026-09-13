package br.com.akesofertas.cupons.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Modelo de domínio para cupons normais do consumidor do Mercado Livre.
 */
public record MercadoLivreCupom(
        String id,
        String titulo,
        String codigoExibivel,
        String tokenAtivacao,
        TipoAtivacaoCupom tipoAtivacao,
        TipoDescontoCupom tipoDesconto,
        BigDecimal valorDesconto,
        BigDecimal compraMinima,
        BigDecimal descontoMaximo,
        Instant dataExpiracao,
        String status,
        String urlProdutos,
        Set<String> itemIdsElegiveis,
        List<String> itensPreview) {

    public MercadoLivreCupom {
        Objects.requireNonNull(id, "id da campanha é obrigatório");
        Objects.requireNonNull(titulo, "titulo é obrigatório");
        Objects.requireNonNull(tipoAtivacao, "tipoAtivacao é obrigatório");
        Objects.requireNonNull(tipoDesconto, "tipoDesconto é obrigatório");
        Objects.requireNonNull(valorDesconto, "valorDesconto é obrigatório");
        itemIdsElegiveis = itemIdsElegiveis != null ? Set.copyOf(itemIdsElegiveis) : Set.of();
        itensPreview = itensPreview != null ? List.copyOf(itensPreview) : List.of();
    }

    public boolean temCodigoExibivel() {
        return codigoExibivel != null && !codigoExibivel.isBlank();
    }

    public boolean ativo() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean expirado(Instant agora) {
        return dataExpiracao != null && agora != null && agora.isAfter(dataExpiracao);
    }

    /** Helper para sanitizar códigos: aceita como exibível apenas códigos curtos e humanos (ex: MELI20). */
    public static String sanitizarCodigoExibivel(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return null;
        String limpo = rawCode.trim();
        // Tokens internos têm mais de 20 caracteres ou caracteres de base64 (=, +, /)
        if (limpo.length() > 20 || limpo.contains("==") || limpo.contains("+") || limpo.contains("/")) {
            return null;
        }
        return limpo.toUpperCase();
    }
}
