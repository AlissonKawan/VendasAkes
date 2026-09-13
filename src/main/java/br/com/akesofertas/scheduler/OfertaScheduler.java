package br.com.akesofertas.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "akes.scheduler.enabled", havingValue = "true")
public class OfertaScheduler {
    private static final Logger log = LoggerFactory.getLogger(OfertaScheduler.class);
    private final ExecutarCicloOfertasService ciclos;

    public OfertaScheduler(ExecutarCicloOfertasService ciclos) {
        this.ciclos = ciclos;
    }

    @Scheduled(fixedDelayString = "${akes.scheduler.interval-ms:300000}")
    public void executar() {
        try {
            ciclos.executarCiclo();
        } catch (Exception exception) {
            log.error("Ciclo não concluído ({}). O scheduler tentará novamente após o intervalo.",
                    exception.getClass().getSimpleName());
        }
    }
}
