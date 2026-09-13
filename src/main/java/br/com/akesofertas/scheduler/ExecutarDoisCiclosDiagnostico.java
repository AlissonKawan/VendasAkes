package br.com.akesofertas.scheduler;

import br.com.akesofertas.afiliados.*;
import br.com.akesofertas.afiliados.rejeicao.*;
import br.com.akesofertas.publicacao.*;
import br.com.akesofertas.telegram.*;
import org.springframework.boot.SpringApplication;

/** Diagnóstico real limitado a dois ciclos; não habilita o @Scheduled. */
public final class ExecutarDoisCiclosDiagnostico {
    public static void main(String[] args) {
        if (System.getenv("TELEGRAM_BOT_TOKEN") == null || System.getenv("TELEGRAM_BOT_TOKEN").isBlank()) {
            System.err.println("TELEGRAM_BOT_TOKEN não configurado");
            System.exit(1);
        }
        if (System.getenv("TELEGRAM_CHAT_ID") == null || System.getenv("TELEGRAM_CHAT_ID").isBlank()) {
            System.err.println("TELEGRAM_CHAT_ID não configurado");
            System.exit(1);
        }
        long intervalo = intervaloDiagnostico();
        try (var context = SpringApplication.run(Configuracao.class,
                "--spring.main.web-application-type=none", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN", "--logging.level.br.com.akesofertas.scheduler=INFO",
                "--akes.scheduler.enabled=true", "--akes.scheduler.min-ofertas=2",
                "--akes.scheduler.max-ofertas=2", "--akes.scheduler.delay-entre-envios-ms=2000")) {
            var ciclos = context.getBean(ExecutarCicloOfertasService.class);
            for (int numero = 1; numero <= 2; numero++) {
                System.out.println("CICLO CONTROLADO " + numero + " DE 2");
                ResultadoCicloOfertas resultado = ciclos.executarCiclo();
                if (resultado.sessaoExpirada()) break;
                if (numero == 1) Thread.sleep(intervalo);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            System.err.println("Teste controlado interrompido (" + exception.getClass().getSimpleName()
                    + "). Nenhuma repetição automática realizada.");
            System.exit(1);
        }
    }

    private static long intervaloDiagnostico() {
        String valor = System.getenv("AKES_DIAGNOSTICO_INTERVAL_MS");
        if (valor == null || valor.isBlank()) return 10_000;
        try { return Math.max(1_000, Long.parseLong(valor)); }
        catch (NumberFormatException exception) { return 10_000; }
    }

    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackageClasses = {
            OfertaPublicadaEntity.class, OfertaAfiliadoRejeitadaEntity.class})
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = {
            SpringDataOfertaPublicadaRepository.class, OfertaAfiliadoRejeitadaRepository.class})
    @org.springframework.context.annotation.Import({OfertaPublicadaService.class,
            JpaOfertaPublicadaRepositoryAdapter.class, OfertaAfiliadoRejeitadaService.class,
            TelegramService.class, MensagemOfertaService.class, PublicadorOfertaService.class,
            AffiliateLinkService.class, DummyAffiliateLinkClient.class,
            MercadoLivreAfiliadosSessionFactory.class, ExecutarCicloOfertasService.class})
    static class Configuracao {}
}
