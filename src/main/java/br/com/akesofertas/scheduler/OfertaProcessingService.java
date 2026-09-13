package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.cupons.evidence.OfferSnapshot;
import br.com.akesofertas.cupons.service.CouponEligibilityService;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.telegram.PublicadorOfertaService;
import br.com.akesofertas.telegram.ResultadoPublicacaoOferta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;

/** Pipeline comum às fontes HUB_AFILIADOS e CUPOM. */
@Service
@ConditionalOnProperty(name = "akes.scheduler.enabled", havingValue = "true")
public class OfertaProcessingService {
    private static final Logger log = LoggerFactory.getLogger(OfertaProcessingService.class);
    private static final String FORNECEDOR = "MERCADO_LIVRE";
    private static final int TAMANHO_LOTE_LINKS = 10;

    private final OfertaPublicadaService publicadas;
    private final OfertaAfiliadoRejeitadaService rejeitadas;
    private final PublicadorOfertaService publicador;
    private final CouponEligibilityService couponEligibility;
    private final Duration validationMaxAge;
    private final Clock clock;
    private final long delayEntreEnviosMs;
    private final LongConsumer pausa;

    @Autowired
    public OfertaProcessingService(OfertaPublicadaService publicadas,
                                   OfertaAfiliadoRejeitadaService rejeitadas,
                                   PublicadorOfertaService publicador,
                                   CouponEligibilityService couponEligibility,
                                   @Value("${akes.scheduler.delay-entre-envios-ms:2000}") long delayEntreEnviosMs,
                                   @Value("${akes.cupons.max-candidatos-compartilhamento:${akes.cupons.max-candidatos-create-link:20}}")
                                   int maxCandidatosCupomCompartilhamento,
                                   @Value("${akes.mercadolivre.validation.max-age-seconds:120}") long validationMaxAgeSeconds) {
        this(publicadas, rejeitadas, publicador, couponEligibility, delayEntreEnviosMs,
                Duration.ofSeconds(validationMaxAgeSeconds), Clock.systemUTC(),
                OfertaProcessingService::sleep);
    }

    OfertaProcessingService(OfertaPublicadaService publicadas,
                             OfertaAfiliadoRejeitadaService rejeitadas,
                             PublicadorOfertaService publicador,
                             CouponEligibilityService couponEligibility,
                             long delayEntreEnviosMs,
                             int maxCandidatosCupomCompartilhamento,
                             Duration validationMaxAge,
                             Clock clock,
                             LongConsumer pausa) {
        this(publicadas, rejeitadas, publicador, couponEligibility, delayEntreEnviosMs,
                validationMaxAge, clock, pausa);
    }

    OfertaProcessingService(OfertaPublicadaService publicadas,
                             OfertaAfiliadoRejeitadaService rejeitadas,
                             PublicadorOfertaService publicador,
                             CouponEligibilityService couponEligibility,
                             long delayEntreEnviosMs,
                             Duration validationMaxAge,
                             Clock clock,
                             LongConsumer pausa) {
        this.publicadas = Objects.requireNonNull(publicadas);
        this.rejeitadas = Objects.requireNonNull(rejeitadas);
        this.publicador = Objects.requireNonNull(publicador);
        this.couponEligibility = Objects.requireNonNull(couponEligibility);
        if (delayEntreEnviosMs < 0) throw new IllegalArgumentException("delayEntreEnviosMs inválido");
        this.delayEntreEnviosMs = delayEntreEnviosMs;
        this.validationMaxAge = Objects.requireNonNull(validationMaxAge);
        this.clock = Objects.requireNonNull(clock);
        this.pausa = Objects.requireNonNull(pausa);
    }

    public ResultadoProcessamento processar(MercadoLivreAfiliadosSessionFactory.Sessao sessao,
                                             List<OfertaAfiliado> hub,
                                             int alvo) {
        return processar(sessao, hub, List.of(), alvo);
    }

    public ResultadoProcessamento processar(MercadoLivreAfiliadosSessionFactory.Sessao sessao,
                                             List<OfertaAfiliado> hub,
                                             List<OfertaAfiliado> coupons,
                                             int alvo) {
        List<OfertaAfiliado> consolidadas = consolidar(hub, coupons);
        var aptas = new ArrayList<OfertaAfiliado>();
        int jaPublicadas = 0;
        int cooldown = 0;

        for (OfertaAfiliado oferta : consolidadas) {
            if (!valida(oferta)) continue;
            if (publicadas.bloqueadaParaPublicacao(FORNECEDOR, oferta.itemId())) {
                jaPublicadas++;
                log.info("Produto {} já publicado: ignorando", oferta.itemId());
                continue;
            }
            if (rejeitadas.emCooldown(FORNECEDOR, oferta.itemId())) {
                cooldown++;
                continue;
            }
            aptas.add(oferta);
        }

        var candidatos = new ArrayList<CandidatoComLink>();
        LinkMetrics linkMetrics = gerarLinks(sessao, aptas, candidatos, alvo);
        int tentadas = linkMetrics.tentadas();
        int elegiveis = linkMetrics.elegiveis();

        int enviadas = 0;
        int erros = 0;
        int selecionadas = 0;
        for (CandidatoComLink candidato : candidatos) {
            OfertaAfiliado validada = validarPdp(sessao, candidato.oferta(), false);
            OfertaAfiliado comImagem = sessao.resolverImagem(validada);
            if (comImagem == null) comImagem = validada;
            log.info("Produto {} selecionado via {}", comImagem.itemId(), comImagem.origem());
            ResultadoPublicacaoOferta resultado = publicador.publicarComLink(FORNECEDOR, comImagem, candidato.shortUrl());
            selecionadas++;
            if (resultado == ResultadoPublicacaoOferta.ENVIADA) enviadas++;
            else if (resultado == ResultadoPublicacaoOferta.ERRO_ENVIO) erros++;
            if (selecionadas < candidatos.size()) pausa.accept(delayEntreEnviosMs);
        }
        return new ResultadoProcessamento(jaPublicadas, cooldown, tentadas, elegiveis,
                selecionadas, enviadas, erros, consolidadas.size());
    }

    /** Mesma chave persistida: fornecedor + itemId. Dentro do ciclo, a variante com cupom prevalece. */
    static List<OfertaAfiliado> consolidar(List<OfertaAfiliado> hub, List<OfertaAfiliado> coupons) {
        Map<String, OfertaAfiliado> porItem = new LinkedHashMap<>();
        if (hub != null) hub.stream().filter(OfertaProcessingService::valida)
                .forEach(oferta -> porItem.putIfAbsent(oferta.itemId(), oferta));
        if (coupons != null) coupons.stream().filter(OfertaProcessingService::valida)
                .forEach(oferta -> porItem.merge(oferta.itemId(), oferta, OfertaProcessingService::preferirCupom));
        return porItem.values().stream()
                .sorted(Comparator.comparing((OfertaAfiliado o) -> o.origem() != OrigemOferta.CUPOM))
                .toList();
    }

    private static OfertaAfiliado preferirCupom(OfertaAfiliado atual, OfertaAfiliado nova) {
        if (nova.temCupom() && !atual.temCupom()) return nova;
        if (nova.origem() == OrigemOferta.CUPOM && atual.origem() != OrigemOferta.CUPOM) return nova;
        return atual;
    }

    private LinkMetrics gerarLinks(MercadoLivreAfiliadosSessionFactory.Sessao sessao,
                                   List<OfertaAfiliado> ofertas,
                                   List<CandidatoComLink> candidatos,
                                   int alvo) {
        int tentadas = 0;
        int elegiveis = 0;
        for (int inicio = 0; inicio < ofertas.size() && candidatos.size() < alvo; inicio += TAMANHO_LOTE_LINKS) {
            int fim = Math.min(inicio + TAMANHO_LOTE_LINKS, ofertas.size());
            List<OfertaAfiliado> lote = ofertas.subList(inicio, fim);
            lote = lote.stream().filter(o -> candidatos.stream()
                    .noneMatch(c -> c.oferta().itemId().equals(o.itemId()))).toList();
            if (lote.isEmpty()) continue;
            tentadas += lote.size();
            Map<String, OfertaAfiliado> porUrl = new HashMap<>();
            lote.forEach(o -> porUrl.put(o.url(), o));
            List<ResultadoLinkAfiliado> resultados = sessao.gerarLinks(lote.stream().map(OfertaAfiliado::url).toList());
            if (resultados == null) continue;
            for (ResultadoLinkAfiliado resultado : resultados) {
                OfertaAfiliado oferta = porUrl.get(resultado.originUrl());
                if (oferta == null) continue;
                int antes = candidatos.size();
                if (tratarResultadoAfiliado(oferta, resultado, candidatos)) elegiveis++;
                if (candidatos.size() >= alvo) break;
                if (candidatos.size() == antes && Thread.currentThread().isInterrupted()) break;
            }
        }
        return new LinkMetrics(tentadas, elegiveis);
    }

    private boolean tratarResultadoAfiliado(OfertaAfiliado oferta, ResultadoLinkAfiliado resultado,
                                             List<CandidatoComLink> candidatos) {
        if (resultado == null) return false;
        if (Integer.valueOf(OfertaAfiliadoRejeitadaService.URL_NAO_PERMITIDA).equals(resultado.errorCode())) {
            rejeitadas.registrarCodigo111(FORNECEDOR, oferta);
            return false;
        }
        if (!resultado.sucesso()) return false;
        candidatos.add(new CandidatoComLink(oferta, resultado.shortUrl()));
        return true;
    }

    private static ResultadoLinkAfiliado resultadoDaUrl(List<ResultadoLinkAfiliado> resultados, String url) {
        if (resultados == null || resultados.isEmpty()) return null;
        return resultados.stream().filter(r -> url.equals(r.originUrl())).findFirst().orElse(resultados.get(0));
    }

    private OfertaAfiliado validarPdp(MercadoLivreAfiliadosSessionFactory.Sessao sessao,
                                      OfertaAfiliado candidata, boolean preservarCupomDaFonte) {
        OfferSnapshot snapshot = sessao.validarPdp(candidata, 1);
        Instant agora = clock.instant();
        var cupomConfirmado = couponEligibility.confirmedCoupon(
                candidata.cupom(), snapshot, agora, validationMaxAge).orElse(null);
        var cupomPublicado = cupomConfirmado != null ? cupomConfirmado
                : preservarCupomDaFonte ? candidata.cupom() : null;
        var price = snapshot != null ? snapshot.price() : null;
        var originalPrice = price != null && price.originalPrice() != null
                ? price.originalPrice() : candidata.precoAnterior();
        var basePrice = price != null && price.basePrice() != null
                ? price.basePrice() : candidata.precoAtual();
        var publishPrice = price != null && cupomPublicado == null
                && ("STANDARD".equals(price.paymentContext()) || "PROMOTIONAL".equals(price.paymentContext()))
                && price.currentPrice() != null ? price.currentPrice() : basePrice;
        log.info("[PDP] itemId={} origem={} cupom publicado={}", candidata.itemId(), candidata.origem(),
                cupomPublicado != null);
        String itemIdFinal = snapshot != null && snapshot.itemId() != null && !snapshot.itemId().isBlank()
                ? snapshot.itemId() : candidata.itemId();
        return new OfertaAfiliado(itemIdFinal,
                snapshot != null && snapshot.productId() != null ? snapshot.productId() : candidata.produtoId(),
                candidata.titulo(), originalPrice, publishPrice, candidata.desconto(), candidata.comissao(),
                candidata.destaque(), snapshot != null && snapshot.finalUrl() != null
                        ? snapshot.finalUrl().toString() : candidata.url(), candidata.imagemUrl(), cupomPublicado,
                candidata.origem());
    }

    private static boolean valida(OfertaAfiliado oferta) {
        return oferta != null && oferta.itemId() != null && !oferta.itemId().isBlank()
                && oferta.url() != null && !oferta.url().isBlank();
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record CandidatoComLink(OfertaAfiliado oferta, String shortUrl) {}
    private record LinkMetrics(int tentadas, int elegiveis) {}

    public record ResultadoProcessamento(int jaPublicadas, int cooldown, int tentadasCreateLink,
                                         int elegiveis, int selecionadas, int enviadas, int errosEnvio,
                                         int candidatosConsolidados) {}
}
