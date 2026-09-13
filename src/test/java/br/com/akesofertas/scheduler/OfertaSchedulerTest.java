package br.com.akesofertas.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OfertaSchedulerTest {
    @Test void desabilitadoNaoCriaScheduler() {
        new ApplicationContextRunner()
                .withBean(ExecutarCicloOfertasService.class, () -> mock(ExecutarCicloOfertasService.class))
                .withUserConfiguration(OfertaScheduler.class)
                .withPropertyValues("akes.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OfertaScheduler.class));
    }

    @Test void desativadoPorPadraoNaoCriaScheduler() {
        new ApplicationContextRunner()
                .withBean(ExecutarCicloOfertasService.class, () -> mock(ExecutarCicloOfertasService.class))
                .withUserConfiguration(OfertaScheduler.class)
                .run(context -> assertThat(context).doesNotHaveBean(OfertaScheduler.class));
    }

    @Test void habilitadoExecutaCiclo() {
        var ciclos = mock(ExecutarCicloOfertasService.class);
        new ApplicationContextRunner()
                .withBean(ExecutarCicloOfertasService.class, () -> ciclos)
                .withUserConfiguration(OfertaScheduler.class)
                .withPropertyValues("akes.scheduler.enabled=true", "akes.scheduler.interval-ms=3600000")
                .run(context -> {
                    context.getBean(OfertaScheduler.class).executar();
                    verify(ciclos, atLeastOnce()).executarCiclo();
                });
    }

    @Test void falhaGeralNaoMataScheduler() {
        var ciclos = mock(ExecutarCicloOfertasService.class);
        doThrow(new IllegalStateException("indisponível")).when(ciclos).executarCiclo();
        var scheduler = new OfertaScheduler(ciclos);
        assertDoesNotThrow(scheduler::executar);
        assertDoesNotThrow(scheduler::executar);
        verify(ciclos, times(2)).executarCiclo();
    }
}
