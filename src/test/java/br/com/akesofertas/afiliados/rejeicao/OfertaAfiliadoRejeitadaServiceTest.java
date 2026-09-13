package br.com.akesofertas.afiliados.rejeicao;

import br.com.akesofertas.afiliados.ofertas.OfertaAfiliado;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfertaAfiliadoRejeitadaServiceTest {
    private static final Instant AGORA = Instant.parse("2026-09-06T03:00:00Z");

    @Test
    void codigo111PersisteMotivoSeguroECooldownDe24Horas() {
        var repository = mock(OfertaAfiliadoRejeitadaRepository.class);
        when(repository.findByFornecedorAndItemId("ML", "MLB1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new OfertaAfiliadoRejeitadaService(repository, Clock.fixed(AGORA, ZoneOffset.UTC));

        var rejeicao = service.registrarCodigo111("ML", oferta());

        assertEquals(111, rejeicao.errorCode());
        assertEquals("URL não permitida no programa de afiliados", rejeicao.motivo());
        assertEquals(AGORA, rejeicao.dataRejeicao());
        assertEquals(AGORA.plus(Duration.ofHours(24)), rejeicao.reprocessarApos());
    }

    @Test
    void cooldownAtivoBloqueiaENoVencimentoPermiteNovaTentativa() {
        var repository = mock(OfertaAfiliadoRejeitadaRepository.class);
        var ativa = new OfertaAfiliadoRejeitadaEntity("ML", "MLB1", null, 111, "seguro",
                AGORA.minusSeconds(60), AGORA.plusSeconds(60));
        when(repository.findByFornecedorAndItemId("ML", "MLB1")).thenReturn(Optional.of(ativa));
        assertTrue(new OfertaAfiliadoRejeitadaService(repository, Clock.fixed(AGORA, ZoneOffset.UTC))
                .emCooldown("ML", "MLB1"));

        var expirada = new OfertaAfiliadoRejeitadaEntity("ML", "MLB1", null, 111, "seguro",
                AGORA.minus(Duration.ofHours(25)), AGORA);
        when(repository.findByFornecedorAndItemId("ML", "MLB1")).thenReturn(Optional.of(expirada));
        assertFalse(new OfertaAfiliadoRejeitadaService(repository, Clock.fixed(AGORA, ZoneOffset.UTC))
                .emCooldown("ML", "MLB1"));
    }

    private OfertaAfiliado oferta() {
        return new OfertaAfiliado("MLB1", "MLBP1", "Produto", null, BigDecimal.TEN,
                null, null, null, "https://www.mercadolivre.com.br/produto/p/MLBP1");
    }
}
