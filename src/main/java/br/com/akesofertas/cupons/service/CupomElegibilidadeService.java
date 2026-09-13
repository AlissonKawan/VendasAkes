package br.com.akesofertas.cupons.service;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.cupons.domain.CupomAplicavel;
import br.com.akesofertas.cupons.domain.MercadoLivreCupom;
import br.com.akesofertas.cupons.domain.TipoDescontoCupom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Avalia de forma puramente determinística e objetiva a elegibilidade de uma oferta a um cupom normal.
 * NUNCA infere elegibilidade por casamento de palavras-chave do título.
 */
@Service
public class CupomElegibilidadeService {
    private static final Logger log = LoggerFactory.getLogger(CupomElegibilidadeService.class);

    public Optional<CupomAplicavel> avaliar(OfertaAfiliado oferta, MercadoLivreCupom cupom, Instant agora) {
        if (oferta == null || cupom == null) return Optional.empty();
        if (oferta.itemId() == null || oferta.itemId().isBlank()) return Optional.empty();
        if (oferta.precoAtual() == null || oferta.precoAtual().signum() <= 0) return Optional.empty();
        try {
            if (Long.parseLong(cupom.id()) <= 0) return Optional.empty();
        } catch (NumberFormatException exception) {
            log.debug("Cupom ignorado: campaignId inválido ({})", cupom.id());
            return Optional.empty();
        }

        // 1. Validação de status ativo
        if (!cupom.ativo()) {
            log.debug("Cupom {} ignorado: status não ativo ({})", cupom.id(), cupom.status());
            return Optional.empty();
        }

        // 2. Validação de expiração
        if (cupom.expirado(agora)) {
            log.debug("Cupom {} ignorado: já expirado em {}", cupom.id(), cupom.dataExpiracao());
            return Optional.empty();
        }

        // 3. Validação de compra mínima
        BigDecimal preco = oferta.precoAtual();
        if (cupom.compraMinima() != null && cupom.compraMinima().signum() > 0) {
            if (preco.compareTo(cupom.compraMinima()) < 0) {
                log.debug("Cupom {} não elegível para {}: preço {} abaixo da compra mínima {}",
                        cupom.id(), oferta.itemId(), preco, cupom.compraMinima());
                return Optional.empty();
            }
        }

        // 4. Validação de Associação REAL/OBJETIVA (item_ids explícitos ou container confirmado)
        if (!pertenceObjetivamente(oferta.itemId(), cupom)) {
            log.debug("Cupom {} sem comprovação objetiva para item {}", cupom.id(), oferta.itemId());
            return Optional.empty();
        }

        // 5. Cálculo do benefício (respeitando teto máximo)
        BigDecimal desconto = calcularDesconto(preco, cupom);
        if (desconto.signum() <= 0 || desconto.compareTo(preco) >= 0) {
            return Optional.empty();
        }

        BigDecimal precoEstimado = preco.subtract(desconto).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(CupomAplicavel.doConsumidor(cupom, desconto, precoEstimado));
    }

    /**
     * Comprovação objetiva estrita:
     * - Item ID explícito na lista de participantes do cupom.
     */
    public boolean pertenceObjetivamente(String itemId, MercadoLivreCupom cupom) {
        if (itemId == null || cupom == null) return false;
        return cupom.itemIdsElegiveis().contains(itemId);
    }

    private BigDecimal calcularDesconto(BigDecimal preco, MercadoLivreCupom cupom) {
        BigDecimal desconto;
        if (cupom.tipoDesconto() == TipoDescontoCupom.VALOR_FIXO) {
            desconto = cupom.valorDesconto();
        } else {
            desconto = preco.multiply(cupom.valorDesconto())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        if (cupom.descontoMaximo() != null && cupom.descontoMaximo().signum() > 0) {
            desconto = desconto.min(cupom.descontoMaximo());
        }

        return desconto.setScale(2, RoundingMode.HALF_UP);
    }
}
