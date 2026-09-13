package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import br.com.akesofertas.afiliados.ofertas.OrigemOferta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Fonte A: descobre candidatos exclusivamente no Hub de Afiliados. */
@Component
@ConditionalOnProperty(name = "akes.scheduler.enabled", havingValue = "true")
public class HubAffiliateOfferCollector {
    private static final Logger log = LoggerFactory.getLogger(HubAffiliateOfferCollector.class);

    public List<OfertaAfiliado> coletar(MercadoLivreAfiliadosSessionFactory.Sessao sessao) {
        List<OfertaAfiliado> ofertas = sessao.buscarOfertas().stream()
                .map(oferta -> oferta.comOrigem(OrigemOferta.HUB_AFILIADOS))
                .toList();
        log.info("[HUB_AFILIADOS] Ofertas encontradas: {}", ofertas.size());
        return ofertas;
    }
}
