package br.com.akesofertas.cupons.domain;

import br.com.akesofertas.cupons.evidence.ValidationConfidence;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enriquecimento de oferta por cupom (do consumidor ou de afiliado).
 * O preço após cupom é sempre uma estimativa calculada.
 */
public record CupomAplicavel(
        Long couponId,
        String codigoExibivel,
        String title,
        CondicoesCupom condicoes,
        BigDecimal descontoAplicado,
        BigDecimal precoEstimado,
        BigDecimal compraMinima,
        String instrucaoAtivacao,
        ValidationConfidence confidence,
        Instant verifiedAt,
        String paymentContext,
        String rawType) {

    public CupomAplicavel {
        if (couponId == null || couponId <= 0) {
            throw new IllegalArgumentException("couponId deve ser positivo");
        }
        if (descontoAplicado == null || descontoAplicado.signum() <= 0
                || precoEstimado == null || precoEstimado.signum() < 0) {
            throw new IllegalArgumentException("Estimativa de cupom inválida");
        }
        confidence = confidence == null ? ValidationConfidence.LOW : confidence;
    }

    /** Construtor legado para compatibilidade total com testes existentes. */
    public CupomAplicavel(Long couponId, String alias, String title, CondicoesCupom condicoes,
                          BigDecimal descontoAplicado, BigDecimal precoEstimado) {
        this(couponId, alias, title, condicoes, descontoAplicado, precoEstimado,
                condicoes != null ? condicoes.minimumPurchase() : null,
                (alias == null || alias.isBlank()) ? "Ative o cupom no Mercado Livre" : null,
                ValidationConfidence.LOW, null, null, null);
    }

    public CupomAplicavel(Long couponId, String alias, String title, CondicoesCupom condicoes,
                          BigDecimal descontoAplicado, BigDecimal precoEstimado,
                          BigDecimal compraMinima, String instrucaoAtivacao) {
        this(couponId, alias, title, condicoes, descontoAplicado, precoEstimado,
                compraMinima, instrucaoAtivacao, ValidationConfidence.LOW, null, null, null);
    }

    /** Construtor para cupom normal do consumidor. */
    public static CupomAplicavel doConsumidor(
            MercadoLivreCupom cupom,
            BigDecimal descontoAplicado,
            BigDecimal precoEstimado) {
        Objects.requireNonNull(cupom, "cupom é obrigatório");
        long id;
        try {
            id = Long.parseLong(cupom.id());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("campaignId inválido: " + cupom.id(), exception);
        }
        if (id <= 0) {
            throw new IllegalArgumentException("campaignId deve ser positivo: " + cupom.id());
        }

        String codigo = cupom.temCodigoExibivel() ? cupom.codigoExibivel() : null;
        String instrucao = cupom.temCodigoExibivel() ? null : "Ative o cupom no Mercado Livre";

        BigDecimal percent = cupom.tipoDesconto() == TipoDescontoCupom.PERCENTUAL ? cupom.valorDesconto() : null;
        BigDecimal fixo = cupom.tipoDesconto() == TipoDescontoCupom.VALOR_FIXO ? cupom.valorDesconto() : null;
        CondicoesCupom condicoes = new CondicoesCupom(id, null, null, cupom.compraMinima(),
                percent, fixo, cupom.descontoMaximo(), null, null, null, null, null);

        return new CupomAplicavel(
                id,
                codigo,
                cupom.titulo(),
                condicoes,
                descontoAplicado,
                precoEstimado,
                cupom.compraMinima(),
                instrucao,
                ValidationConfidence.LOW,
                null,
                null,
                null
        );
    }

    /** Retorna o código legível ou alias para compatibilidade. */
    public String alias() {
        return codigoExibivel;
    }

    public boolean temCodigoExibivel() {
        return codigoExibivel != null && !codigoExibivel.isBlank();
    }

    public boolean confirmadoParaPublicacao(Instant agora, Duration maxAge) {
        return confidence == ValidationConfidence.HIGH
                && verifiedAt != null
                && agora != null
                && maxAge != null
                && !maxAge.isNegative()
                && !verifiedAt.isAfter(agora)
                && !verifiedAt.plus(maxAge).isBefore(agora);
    }
}
