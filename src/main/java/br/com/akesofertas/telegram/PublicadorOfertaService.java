package br.com.akesofertas.telegram;

import br.com.akesofertas.afiliados.AffiliateLinkService;
import br.com.akesofertas.afiliados.ResultadoLinkAfiliado;
import br.com.akesofertas.afiliados.rejeicao.OfertaAfiliadoRejeitadaService;
import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import br.com.akesofertas.publicacao.OfertaPublicada;
import br.com.akesofertas.publicacao.OfertaPublicadaService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class PublicadorOfertaService {

    private final OfertaPublicadaService ofertaPublicadaService;
    private final AffiliateLinkService linkService;
    private final TelegramService telegramService;
    private final OfertaAfiliadoRejeitadaService rejeitadaService;
    private final Duration validationMaxAge;
    private final Clock clock;

    @Autowired
    public PublicadorOfertaService(OfertaPublicadaService ofertaPublicadaService,
                                   AffiliateLinkService linkService,
                                   TelegramService telegramService,
                                   OfertaAfiliadoRejeitadaService rejeitadaService,
                                   @Value("${akes.mercadolivre.validation.max-age-seconds:120}") long validationMaxAgeSeconds) {
        this(ofertaPublicadaService, linkService, telegramService, rejeitadaService,
                Duration.ofSeconds(validationMaxAgeSeconds), Clock.systemUTC());
    }

    public PublicadorOfertaService(OfertaPublicadaService ofertaPublicadaService,
                                   AffiliateLinkService linkService,
                                   TelegramService telegramService,
                                   OfertaAfiliadoRejeitadaService rejeitadaService) {
        this(ofertaPublicadaService, linkService, telegramService, rejeitadaService,
                Duration.ofSeconds(120), Clock.systemUTC());
    }

    PublicadorOfertaService(OfertaPublicadaService ofertaPublicadaService,
                            AffiliateLinkService linkService,
                            TelegramService telegramService,
                            OfertaAfiliadoRejeitadaService rejeitadaService,
                            Duration validationMaxAge, Clock clock) {
        this.ofertaPublicadaService = ofertaPublicadaService;
        this.linkService = linkService;
        this.telegramService = telegramService;
        this.rejeitadaService = rejeitadaService;
        this.validationMaxAge = validationMaxAge;
        this.clock = clock;
    }

    public ResultadoPublicacaoOferta processarOferta(String fornecedor, OfertaAfiliado ofertaAfiliado) {
        if (ofertaAfiliado == null || ofertaAfiliado.itemId() == null || ofertaAfiliado.itemId().isBlank()) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }

        // 1. Deduplicação prévia
        if (ofertaPublicadaService.bloqueadaParaPublicacao(fornecedor, ofertaAfiliado.itemId())) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }
        if (rejeitadaService.emCooldown(fornecedor, ofertaAfiliado.itemId())) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }

        ResultadoLinkAfiliado resultado = linkService.gerarResultados(List.of(ofertaAfiliado.url())).stream()
                .filter(r -> ofertaAfiliado.url().equals(r.originUrl())).findFirst().orElse(null);
        if (resultado != null && Integer.valueOf(OfertaAfiliadoRejeitadaService.URL_NAO_PERMITIDA)
                .equals(resultado.errorCode())) {
            rejeitadaService.registrarCodigo111(fornecedor, ofertaAfiliado);
            return ResultadoPublicacaoOferta.REJEITADA_AFILIADO;
        }
        if (resultado == null || !resultado.sucesso()) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }

        return publicarComLink(fornecedor, ofertaAfiliado, resultado.shortUrl());
    }

    public ResultadoPublicacaoOferta publicarComLink(String fornecedor, OfertaAfiliado ofertaAfiliado,
                                                      String linkAfiliado) {
        if (ofertaPublicadaService.bloqueadaParaPublicacao(fornecedor, ofertaAfiliado.itemId())) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }
        boolean cupomPublicavel = ofertaAfiliado.cupom() != null
                && (ofertaAfiliado.origem() == OrigemOferta.CUPOM
                || ofertaAfiliado.cupom().confirmadoParaPublicacao(clock.instant(), validationMaxAge));
        OfertaAfiliado ofertaSegura = cupomPublicavel ? ofertaAfiliado : ofertaAfiliado.comCupom(null);
        org.slf4j.LoggerFactory.getLogger(PublicadorOfertaService.class).info(
                "[TELEGRAM] itemId={} cupom publicado={}", ofertaAfiliado.itemId(), cupomPublicavel);

        OfertaPublicada pendente = ofertaPublicadaService.registrarPendente(
                fornecedor, ofertaSegura, linkAfiliado);
        if (pendente == null) {
            return ResultadoPublicacaoOferta.IGNORADA;
        }

        // 4. Enviar ao Telegram
        try {
            org.slf4j.LoggerFactory.getLogger(PublicadorOfertaService.class).info(
                    "Rastreamento imagem: itemId={} etapa=publicador imagemPresente={} imagemUrl={}",
                    ofertaSegura.itemId(), ofertaSegura.imagemUrl() != null, ofertaSegura.imagemUrl());
            telegramService.enviarOferta(pendente.comCupom(ofertaSegura.cupom()), ofertaSegura.imagemUrl());
            // 5. Marcar como ENVIADA (sucesso)
            ofertaPublicadaService.marcarComoEnviada(pendente.id());
            return ResultadoPublicacaoOferta.ENVIADA;
        } catch (Exception e) {
            // 6. Marcar como ERRO_ENVIO (falha)
            ofertaPublicadaService.marcarErroEnvio(pendente.id(), e.getMessage());
            return ResultadoPublicacaoOferta.ERRO_ENVIO;
        }
    }
}

