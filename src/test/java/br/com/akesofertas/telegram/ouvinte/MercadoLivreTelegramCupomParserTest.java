package br.com.akesofertas.telegram.ouvinte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLivreTelegramCupomParserTest {

    private static final String MENSAGEM_PRINT = """
            SIM, A COMPRA MÍNIMA É DE R$1. 👀

            🎟️ SAINDOBARRATO 👉 30% OFF
            Compra mínima: R$1 | Desconto máx: R$500
            Válido até 05.10, ou enquanto durarem os estoques, em produtos elegíveis.
            🔗 https://bit.ly/4gCA4Xq

            🎟️ OPAECONOMIZEI 👉 30% OFF
            Compra mínima: R$1 | Desconto máx: R$500
            Válido até 05.10, ou enquanto durarem os estoques, em produtos elegíveis.
            🔗 https://bit.ly/4rb15q4

            🎟️ MANTECORP14 👉 14% OFF
            Beleza | Compra mínima: R$5 | Desconto máx: R$150
            Válido até 16.09, ou enquanto durarem os estoques, em produtos elegíveis.
            🔗 https://bit.ly/4iZZKQy

            🎟️ ISDIN15 👉 15% OFF
            Beleza | Compra mínima: R$5 | Desconto máx: R$150
            Válido até 16.09, ou enquanto durarem os estoques, em produtos elegíveis.
            🔗 https://bit.ly/4xkyjVp

            🎟️ MELIACHAPROMO 👉 10% OFF
            KaBuM! | Compra mínima: R$99 | Desconto máx: R$300
            Válido até 13.09, ou enquanto durarem os estoques, em produtos elegíveis.
            🔗 https://bit.ly/4xJInYY

            Agora escolhe os FAVs e bora fazer esses links rodarem. 🔗
            """;

    @Test
    @DisplayName("Deve extrair todos os 5 cupons do print do canal oficial")
    void deveExtrairTodosCuponsDoPrint() {
        LocalDate referencia = LocalDate.of(2026, 9, 10);
        List<CupomDetectado> cupons = MercadoLivreTelegramCupomParser.extrairCupons(MENSAGEM_PRINT, referencia);

        assertEquals(5, cupons.size(), "Deve ter encontrado 5 cupons");

        CupomDetectado cupom1 = cupons.get(0);
        assertEquals("SAINDOBARRATO", cupom1.codigo());
        assertEquals("30% OFF", cupom1.desconto());
        assertEquals("R$1", cupom1.compraMinima());
        assertEquals("R$500", cupom1.descontoMaximo());
        assertEquals(LocalDate.of(2026, 10, 5), cupom1.validoAte());
        assertEquals("https://bit.ly/4gCA4Xq", cupom1.urlCampanha());

        CupomDetectado cupom3 = cupons.get(2);
        assertEquals("MANTECORP14", cupom3.codigo());
        assertEquals("14% OFF", cupom3.desconto());
        assertEquals("Beleza", cupom3.categoria());
        assertEquals("R$5", cupom3.compraMinima());
        assertEquals(LocalDate.of(2026, 9, 16), cupom3.validoAte());
        assertEquals("https://bit.ly/4iZZKQy", cupom3.urlCampanha());

        CupomDetectado cupom5 = cupons.get(4);
        assertEquals("MELIACHAPROMO", cupom5.codigo());
        assertEquals("10% OFF", cupom5.desconto());
        assertEquals("KaBuM!", cupom5.categoria());
        assertEquals("R$99", cupom5.compraMinima());
        assertEquals("R$300", cupom5.descontoMaximo());
        assertEquals(LocalDate.of(2026, 9, 13), cupom5.validoAte());
        assertEquals("https://bit.ly/4xJInYY", cupom5.urlCampanha());
    }

    @Test
    @DisplayName("Deve filtrar e descartar cupons já vencidos")
    void deveFiltrarCuponsVencidos() {
        // Se a data de referência for 15/09/2026:
        // MELIACHAPROMO venceu em 13/09 -> deve ser descartado
        // MANTECORP14 (16/09), ISDIN15 (16/09), SAINDOBARRATO (05/10) e OPAECONOMIZEI (05/10) -> devem permanecer válidos
        LocalDate referencia = LocalDate.of(2026, 9, 15);
        List<CupomDetectado> validos = MercadoLivreTelegramCupomParser.extrairCuponsValidos(MENSAGEM_PRINT, referencia);

        assertEquals(4, validos.size());
        assertTrue(validos.stream().noneMatch(c -> c.codigo().equals("MELIACHAPROMO")));
        assertTrue(validos.stream().anyMatch(c -> c.codigo().equals("SAINDOBARRATO")));
        assertTrue(validos.stream().anyMatch(c -> c.codigo().equals("MANTECORP14")));
    }

    @Test
    void aceitaLinkComSubdominioDoMercadoLivre() {
        String mensagem = """
                CUPOM10 👉 10% OFF
                Válido até 16.09
                https://www.mercadolivre.com.br/ofertas/cupom
                """;

        List<CupomDetectado> cupons = MercadoLivreTelegramCupomParser.extrairCupons(
                mensagem, LocalDate.of(2026, 9, 11));

        assertEquals(1, cupons.size());
        assertEquals("https://www.mercadolivre.com.br/ofertas/cupom", cupons.getFirst().urlCampanha());
    }

    @Test
    void naoConsideraHistoricoSemValidadeExplicitaComoCupomVigente() {
        List<CupomDetectado> cupons = MercadoLivreTelegramCupomParser.extrairCuponsValidos(
                "ANTIGO10 👉 10% OFF\nhttps://bit.ly/exemplo", LocalDate.of(2026, 9, 11));

        assertTrue(cupons.isEmpty());
    }

    @Test
    void naoInterpretaPreposicaoComComoCodigoDeCupom() {
        List<CupomDetectado> cupons = MercadoLivreTelegramCupomParser.extrairCuponsValidos("""
                Produtos selecionados com 18% OFF
                Válido até 16.09
                https://www.mercadolivre.com.br/ofertas/cupom
                """, LocalDate.of(2026, 9, 11));

        assertTrue(cupons.isEmpty());
    }
}
