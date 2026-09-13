package br.com.akesofertas.cupons.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CondicoesCupomParserTest {
    private static final String PERCENTUAL = "ID 14090458 - Cupom válido no Brasil, de 11/08/2026 a 10/09/2026, incluindo ambas as datas, para compras de produtos realizadas no site e no aplicativo Mercado Livre. Válido apenas para os produtos selecionados e enquanto durarem os estoques. O cupom será aplicado automaticamente no carrinho elegível, sem necessidade de ativação pelo usuário. O cupom é aplicável apenas para compras mínimas de produtos selecionados cujo valor seja igual ou superior a R$ 19. O cupom consiste em 20% de desconto sobre o valor da compra dos produtos selecionados. Não será aplicado sobre o custo de envio e/ou qualquer outro custo associado. O cupom é limitado a 1 (um) uso por CPF, não é transferível e não poderá ser convertido em outro benefício. Máximo de desconto de R$ 100. Válido enquanto durar o estoque de 3157 cupons. Cupom acumulável com outros descontos em meios de pagamento, cashback em Mercado Coin e descontos diretos no preço de venda estabelecido pelo vendedor. Este cupom é de responsabilidade do vendedor dos produtos participantes.";
    private static final String FIXO = "ID 13558453 - Cupom válido no Brasil, de 01/09/2026 a 30/09/2026, incluindo ambas as datas, para compras de produtos realizadas no site e no aplicativo Mercado Livre. Válido apenas para os produtos selecionados e enquanto durarem os estoques. O cupom será aplicado automaticamente no carrinho elegível, sem necessidade de ativação pelo usuário. O cupom é aplicável apenas para compras mínimas de produtos selecionados cujo valor seja igual ou superior a R$ 150. O cupom consiste em um desconto total de R$ 20 sobre o valor da compra dos produtos selecionados. Não será aplicado sobre o custo de envio e/ou qualquer outro custo associado. O cupom é limitado a 1 (um) uso por CPF, não é transferível e não poderá ser convertido em outro benefício. Máximo de desconto de R$ 20. Válido enquanto durar o estoque de 50000 cupons. Cupom acumulável com outros descontos em meios de pagamento, cashback em Mercado Coin e descontos diretos no preço de venda estabelecido pelo vendedor. Este cupom é de responsabilidade do vendedor dos produtos participantes.";

    private CondicoesCupomParser parser;

    @BeforeEach
    void setUp() {
        parser = new CondicoesCupomParser();
    }

    @Test void fixturePercentualCompleta() {
        var result = parser.parse(14090458L, PERCENTUAL);
        assertAll(
                () -> assertEquals(14090458L, result.couponId()),
                () -> assertEquals(LocalDate.of(2026, 8, 11), result.startDate()),
                () -> assertEquals(LocalDate.of(2026, 9, 10), result.endDate()),
                () -> assertEquals(new BigDecimal("19"), result.minimumPurchase()),
                () -> assertEquals(new BigDecimal("20"), result.discountPercent()),
                () -> assertNull(result.fixedDiscount()),
                () -> assertEquals(new BigDecimal("100"), result.maximumDiscount()),
                () -> assertEquals(1, result.usesPerCpf()),
                () -> assertEquals(3157, result.availableStock()),
                () -> assertEquals(true, result.automaticApplication()),
                () -> assertEquals(true, result.cumulative()),
                () -> assertSame(PERCENTUAL, result.rawTerms()));
    }

    @Test void fixtureFixaCompleta() {
        var result = parser.parse(13558453L, FIXO);
        assertAll(
                () -> assertEquals(13558453L, result.couponId()),
                () -> assertEquals(LocalDate.of(2026, 9, 1), result.startDate()),
                () -> assertEquals(LocalDate.of(2026, 9, 30), result.endDate()),
                () -> assertEquals(new BigDecimal("150"), result.minimumPurchase()),
                () -> assertNull(result.discountPercent()),
                () -> assertEquals(new BigDecimal("20"), result.fixedDiscount()),
                () -> assertEquals(new BigDecimal("20"), result.maximumDiscount()),
                () -> assertEquals(1, result.usesPerCpf()),
                () -> assertEquals(50000, result.availableStock()),
                () -> assertEquals(true, result.automaticApplication()),
                () -> assertEquals(true, result.cumulative()),
                () -> assertSame(FIXO, result.rawTerms()));
    }

    @Test void extraiCouponIdPercentual() { assertEquals(14090458L, parser.parse(14090458L, PERCENTUAL).couponId()); }
    @Test void extraiCouponIdFixo() { assertEquals(13558453L, parser.parse(13558453L, FIXO).couponId()); }
    @Test void extraiDatasPercentual() {
        var result = parser.parse(14090458L, PERCENTUAL);
        assertEquals(LocalDate.of(2026, 8, 11), result.startDate());
        assertEquals(LocalDate.of(2026, 9, 10), result.endDate());
    }
    @Test void extraiDatasFixo() {
        var result = parser.parse(13558453L, FIXO);
        assertEquals(LocalDate.of(2026, 9, 1), result.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), result.endDate());
    }
    @Test void extraiCompraMinimaDezenove() { assertMoney("19", parser.parse(14090458L, PERCENTUAL).minimumPurchase()); }
    @Test void extraiCompraMinimaCentoECinquenta() { assertMoney("150", parser.parse(13558453L, FIXO).minimumPurchase()); }
    @Test void extraiPercentualVinte() { assertMoney("20", parser.parse(14090458L, PERCENTUAL).discountPercent()); }
    @Test void percentualNaoProduzDescontoFixo() { assertNull(parser.parse(14090458L, PERCENTUAL).fixedDiscount()); }
    @Test void extraiDescontoFixoVinte() { assertMoney("20", parser.parse(13558453L, FIXO).fixedDiscount()); }
    @Test void fixoNaoProduzPercentual() { assertNull(parser.parse(13558453L, FIXO).discountPercent()); }
    @Test void extraiMaximoCem() { assertMoney("100", parser.parse(14090458L, PERCENTUAL).maximumDiscount()); }
    @Test void extraiMaximoVinte() { assertMoney("20", parser.parse(13558453L, FIXO).maximumDiscount()); }
    @Test void extraiUmUsoPorCpf() { assertEquals(1, parser.parse(14090458L, PERCENTUAL).usesPerCpf()); }
    @Test void extraiEstoque3157() { assertEquals(3157, parser.parse(14090458L, PERCENTUAL).availableStock()); }
    @Test void extraiEstoque50000() { assertEquals(50000, parser.parse(13558453L, FIXO).availableStock()); }
    @Test void reconheceAplicacaoAutomatica() { assertEquals(true, parser.parse(14090458L, PERCENTUAL).automaticApplication()); }
    @Test void reconheceCupomAcumulavel() { assertEquals(true, parser.parse(14090458L, PERCENTUAL).cumulative()); }

    @Test void preservaRawTermsSemTrimOuNormalizacao() {
        String raw = "  Condições ÁÉÍ com espaços finais.  ";
        assertSame(raw, parser.parse(123L, raw).rawTerms());
    }

    @Test void campoAusentePermaneceNull() {
        assertNull(parser.parse(123L, "ID 123 - Máximo de desconto desconhecido.").maximumDiscount());
    }

    @Test void aceitaParsingParcial() {
        String raw = "ID 123 - Cupom válido no Brasil, de 01/09/2026 a 30/09/2026. "
                + "O cupom consiste em 20% de desconto.";
        var result = parser.parse(123L, raw);
        assertEquals(LocalDate.of(2026, 9, 1), result.startDate());
        assertMoney("20", result.discountPercent());
        assertNull(result.minimumPurchase());
        assertNull(result.availableStock());
    }

    @Test void textoDesconhecidoRetornaSomenteIdERawTerms() {
        String raw = "Condições comerciais ainda não reconhecidas.";
        var result = parser.parse(123L, raw);
        assertEquals(123L, result.couponId());
        assertSame(raw, result.rawTerms());
        assertAll(
                () -> assertNull(result.startDate()), () -> assertNull(result.endDate()),
                () -> assertNull(result.minimumPurchase()), () -> assertNull(result.discountPercent()),
                () -> assertNull(result.fixedDiscount()), () -> assertNull(result.maximumDiscount()),
                () -> assertNull(result.usesPerCpf()), () -> assertNull(result.availableStock()),
                () -> assertNull(result.automaticApplication()), () -> assertNull(result.cumulative()));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejeitaExpectedCouponIdNaoPositivo(long couponId) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(couponId, "Termos"));
    }

    @Test void rejeitaRawTermsNull() { assertThrows(IllegalArgumentException.class, () -> parser.parse(1L, null)); }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void rejeitaRawTermsBlank(String raw) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(1L, raw));
    }

    @Test void rejeitaIdTextualDivergente() {
        assertThrows(CondicoesCupomParsingException.class,
                () -> parser.parse(123L, "ID 456 - Condições da campanha."));
    }

    @Test void validaIdTextualMesmoQuandoHaUmPrefixo() {
        assertThrows(CondicoesCupomParsingException.class,
                () -> parser.parse(123L, "Termos públicos: ID 456 - Condições da campanha."));
    }

    @Test void aceitaTextoSemIdUsandoExpectedCouponId() {
        assertEquals(123L, parser.parse(123L, "Condições sem identificador textual.").couponId());
    }

    @Test void converteMoedaComCentavos() { assertMoney("19.90", CondicoesCupomParser.parseBrazilianMoney("19,90")); }
    @Test void converteMoedaComMilhar() { assertMoney("1000", CondicoesCupomParser.parseBrazilianMoney("1.000")); }
    @Test void converteMoedaComMilharECentavos() { assertMoney("1000.50", CondicoesCupomParser.parseBrazilianMoney("1.000,50")); }

    @Test void extraiMoedaBrasileiraDoContextoDaCompraMinima() {
        var result = parser.parse(1L, "O valor seja igual ou superior a R$ 1.000,50.");
        assertMoney("1000.50", result.minimumPurchase());
    }

    @Test void naoConfundeDescontoFixoComDescontoMaximo() {
        var result = parser.parse(1L, "O cupom consiste em um desconto total de R$ 20. "
                + "Máximo de desconto de R$ 100.");
        assertMoney("20", result.fixedDiscount());
        assertMoney("100", result.maximumDiscount());
    }

    @Test void naoConfundeCompraMinimaComOutrosValores() {
        var result = parser.parse(1L, "Oferta de R$ 999. O valor seja igual ou superior a R$ 150. "
                + "Máximo de desconto de R$ 20.");
        assertMoney("150", result.minimumPurchase());
    }

    @Test void ausenciaDeAutomaticoNaoSignificaFalse() {
        assertNull(parser.parse(1L, "Cupom aplicado no carrinho.").automaticApplication());
    }

    @Test void ausenciaDeAcumulavelNaoSignificaFalse() {
        assertNull(parser.parse(1L, "Cupom com condições comerciais.").cumulative());
    }

    @Test void estoqueComOverflowPermaneceNull() {
        assertNull(parser.parse(1L, "Estoque de 999999999999999999999 cupons.").availableStock());
    }

    @Test void usoPorCpfNaoDependeDoNumeroPorExtenso() {
        assertEquals(2, parser.parse(1L, "Limitado a 2 usos por CPF.").usesPerCpf());
    }

    @Test void dataInvalidaReconhecidaPermaneceNull() {
        var result = parser.parse(1L, "Cupom válido no Brasil, de 31/02/2026 a 10/09/2026.");
        assertNull(result.startDate());
        assertNull(result.endDate());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
