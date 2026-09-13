package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.cupons.service.CouponEligibilityService;
import br.com.akesofertas.cupons.service.MercadoLivreCouponService;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import br.com.akesofertas.telegram.PublicadorOfertaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

/** Orquestra as duas fontes sem executar operações de Page em paralelo. */
@Service
@ConditionalOnProperty(name = "akes.scheduler.enabled", havingValue = "true")
public class ExecutarCicloOfertasService {
    private static final Logger log = LoggerFactory.getLogger(ExecutarCicloOfertasService.class);

    private final MercadoLivreAfiliadosSessionFactory sessoes;
    private final HubAffiliateOfferCollector hubCollector;
    private final OfertaProcessingService processing;
    private final int minimo;
    private final int maximo;
    private final IntSupplier quantidadeAleatoria;
    private final AtomicBoolean executando = new AtomicBoolean();

    @Autowired
    public ExecutarCicloOfertasService(MercadoLivreAfiliadosSessionFactory sessoes,
                                       HubAffiliateOfferCollector hubCollector,
                                       OfertaProcessingService processing,
                                       @Value("${akes.scheduler.min-ofertas:2}") int minimo,
                                       @Value("${akes.scheduler.max-ofertas:3}") int maximo) {
        this(sessoes, hubCollector, processing, minimo, maximo,
                () -> ThreadLocalRandom.current().nextInt(minimo, maximo + 1));
    }

    ExecutarCicloOfertasService(MercadoLivreAfiliadosSessionFactory sessoes,
                                HubAffiliateOfferCollector hubCollector,
                                OfertaProcessingService processing,
                                int minimo,
                                int maximo,
                                IntSupplier quantidadeAleatoria) {
        if (minimo < 1 || maximo < minimo) throw new IllegalArgumentException("Quantidade do scheduler inválida");
        this.sessoes = Objects.requireNonNull(sessoes);
        this.hubCollector = Objects.requireNonNull(hubCollector);
        this.processing = Objects.requireNonNull(processing);
        this.minimo = minimo;
        this.maximo = maximo;
        this.quantidadeAleatoria = Objects.requireNonNull(quantidadeAleatoria);
    }

    ExecutarCicloOfertasService(MercadoLivreAfiliadosSessionFactory sessoes,
                                OfertaPublicadaService publicadas,
                                OfertaAfiliadoRejeitadaService rejeitadas,
                                PublicadorOfertaService publicador,
                                int minimo,
                                int maximo,
                                long delayEntreEnviosMs,
                                IntSupplier quantidadeAleatoria,
                                LongConsumer pausa) {
        this(sessoes, publicadas, rejeitadas, publicador, minimo, maximo, delayEntreEnviosMs,
                quantidadeAleatoria, pausa, null, new CouponEligibilityService(),
                Duration.ofSeconds(120), Clock.systemUTC());
    }

    public ExecutarCicloOfertasService(MercadoLivreAfiliadosSessionFactory sessoes,
                                       OfertaPublicadaService publicadas,
                                       OfertaAfiliadoRejeitadaService rejeitadas,
                                       PublicadorOfertaService publicador,
                                       int minimo,
                                       int maximo,
                                       long delayEntreEnviosMs,
                                       IntSupplier quantidadeAleatoria,
                                       LongConsumer pausa,
                                       MercadoLivreCouponService coupons) {
        this(sessoes, publicadas, rejeitadas, publicador, minimo, maximo, delayEntreEnviosMs,
                quantidadeAleatoria, pausa, coupons, new CouponEligibilityService(),
                Duration.ofSeconds(120), Clock.systemUTC());
    }

    ExecutarCicloOfertasService(MercadoLivreAfiliadosSessionFactory sessoes,
                                OfertaPublicadaService publicadas,
                                OfertaAfiliadoRejeitadaService rejeitadas,
                                PublicadorOfertaService publicador,
                                int minimo,
                                int maximo,
                                long delayEntreEnviosMs,
                                IntSupplier quantidadeAleatoria,
                                LongConsumer pausa,
                                MercadoLivreCouponService coupons,
                                CouponEligibilityService couponEligibility,
                                Duration validationMaxAge,
                                Clock clock) {
        this(sessoes, new HubAffiliateOfferCollector(),
                new OfertaProcessingService(publicadas, rejeitadas, publicador, couponEligibility,
                        delayEntreEnviosMs, 20,
                        validationMaxAge, clock, pausa), minimo, maximo, quantidadeAleatoria);
    }

    public ResultadoCicloOfertas executarCiclo() {
        if (!executando.compareAndSet(false, true)) {
            log.warn("Ciclo ignorado: outro ciclo ainda está em execução.");
            return ResultadoCicloOfertas.concorrente();
        }
        try {
            return executarProtegido();
        } finally {
            executando.set(false);
        }
    }

    private ResultadoCicloOfertas executarProtegido() {
        int alvo = Math.max(minimo, Math.min(maximo, quantidadeAleatoria.getAsInt()));
        int hubCount = 0;
        log.info("===============================\nCICLO AKES OFERTAS: CATÁLOGO HUB\n===============================");
        try (var sessao = sessoes.abrir()) {
            var hub = hubCollector.coletar(sessao);
            hubCount = hub.size();

            OfertaProcessingService.ResultadoProcessamento resultado =
                    processing.processar(sessao, hub, alvo);
            log.info("""
                    ===============================
                    Ofertas Hub: {}
                    Candidatos consolidados: {}
                    Selecionadas: {}
                    Enviadas: {}
                    Erros de envio: {}
                    ===============================""",
                    hubCount,
                    resultado.candidatosConsolidados(), resultado.selecionadas(), resultado.enviadas(),
                    resultado.errosEnvio());
            return new ResultadoCicloOfertas(hubCount, resultado.jaPublicadas(), resultado.cooldown(),
                    resultado.tentadasCreateLink(), resultado.elegiveis(), resultado.selecionadas(),
                    resultado.enviadas(), resultado.errosEnvio(), false, false);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("SESSAO_AFILIADOS_EXPIRADA")) {
                log.warn("Sessão Mercado Livre expirada. Faça login manual; nenhuma oferta foi publicada neste ciclo.");
                return new ResultadoCicloOfertas(hubCount, 0, 0, 0, 0, 0, 0, 0, false, true);
            }
            throw exception;
        }
    }
}
