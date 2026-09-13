package br.com.akesofertas.cupons.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CalculadoraDescontoCupomTest {
    private CalculadoraDescontoCupom calculadora;

    @BeforeEach
    void setUp() {
        calculadora = new CalculadoraDescontoCupom();
    }

    @Test void calculaVintePorCentoDeOitenta() {
        assertMoney("16.00", calculadora.calcular(bd("80"), percentual("19", "20", "100")).descontoAplicado());
    }

    @Test void calculaPrecoFinalSessentaEQuatro() {
        assertMoney("64.00", calculadora.calcular(bd("80"), percentual("19", "20", "100")).precoEstimado());
    }

    @Test void limitaVintePorCentoDeMilAoTetoDeCem() {
        assertMoney("100.00", calculadora.calcular(bd("1000"), percentual("19", "20", "100")).descontoAplicado());
    }

    @Test void calculaPrecoFinalNovecentosAposTeto() {
        assertMoney("900.00", calculadora.calcular(bd("1000"), percentual("19", "20", "100")).precoEstimado());
    }

    @Test void aplicaDescontoFixoVinteEmDuzentos() {
        assertMoney("20.00", calculadora.calcular(bd("200"), fixo("150", "20", "20")).descontoAplicado());
    }

    @Test void calculaPrecoFinalCentoEOitenta() {
        assertMoney("180.00", calculadora.calcular(bd("200"), fixo("150", "20", "20")).precoEstimado());
    }

    @Test void precoAbaixoDoMinimoFicaInelegivel() {
        var result = calculadora.calcular(bd("149.99"), fixo("150", "20", "20"));
        assertFalse(result.elegivel());
        assertEquals(MotivoIneligibilidadeCupom.ABAIXO_COMPRA_MINIMA, result.motivo());
        assertNull(result.descontoAplicado());
        assertNull(result.precoEstimado());
    }

    @Test void precoExatamenteNoMinimoEElegivel() {
        assertTrue(calculadora.calcular(bd("150"), fixo("150", "20", "20")).elegivel());
    }

    @Test void minimoAusenteNaoImpedeCalculo() {
        assertTrue(calculadora.calcular(bd("80"), percentual(null, "20", "100")).elegivel());
    }

    @Test void maximoAusenteNaoCriaTeto() {
        assertMoney("200.00", calculadora.calcular(bd("1000"), percentual("19", "20", null)).descontoAplicado());
    }

    @Test void calculaPercentualSemTeto() {
        assertMoney("40.00", calculadora.calcular(bd("200"), percentual(null, "20", null)).descontoAplicado());
    }

    @Test void calculaFixoSemTeto() {
        assertMoney("20.00", calculadora.calcular(bd("200"), fixo(null, "20", null)).descontoAplicado());
    }

    @Test void ambosDescontosAusentesRetornamMotivoExplicito() {
        var result = calculadora.calcular(bd("200"), condicoes(null, null, null, null, 1, true, true));
        assertFalse(result.elegivel());
        assertEquals(MotivoIneligibilidadeCupom.SEM_REGRA_DE_DESCONTO, result.motivo());
    }

    @Test void percentualEFixoSimultaneosSaoInconsistentes() {
        var result = calculadora.calcular(bd("200"), condicoes(null, "20", "10", null, 1, true, true));
        assertFalse(result.elegivel());
        assertEquals(MotivoIneligibilidadeCupom.CONDICOES_INCONSISTENTES, result.motivo());
    }

    @Test void rejeitaPrecoNull() {
        assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(null, fixo(null, "20", null)));
    }

    @Test void rejeitaPrecoZero() {
        assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(BigDecimal.ZERO, fixo(null, "20", null)));
    }

    @Test void rejeitaPrecoNegativo() {
        assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(bd("-0.01"), fixo(null, "20", null)));
    }

    @Test void limitaDescontoFixoAoPreco() {
        var result = calculadora.calcular(bd("30"), fixo(null, "50", null));
        assertMoney("30.00", result.descontoAplicado());
        assertMoney("0.00", result.precoEstimado());
    }

    @Test void maximoMenorQueFixoLimitaDesconto() {
        assertMoney("20.00", calculadora.calcular(bd("100"), fixo(null, "50", "20")).descontoAplicado());
    }

    @Test void maximoMenorQuePercentualCalculadoLimitaDesconto() {
        assertMoney("10.00", calculadora.calcular(bd("100"), percentual(null, "20", "10")).descontoAplicado());
    }

    @Test void maximoMaiorQuePercentualCalculadoNaoAlteraDesconto() {
        assertMoney("20.00", calculadora.calcular(bd("100"), percentual(null, "20", "50")).descontoAplicado());
    }

    @Test void arredondaPercentualComCentavosHalfUp() {
        var result = calculadora.calcular(bd("33.33"), percentual(null, "15", null));
        assertMoney("5.00", result.descontoAplicado());
        assertMoney("28.33", result.precoEstimado());
    }

    @Test void todosResultadosMonetariosTemDuasCasas() {
        var result = calculadora.calcular(bd("80"), percentual(null, "20", null));
        assertEquals(2, result.precoOriginal().scale());
        assertEquals(2, result.descontoAplicado().scale());
        assertEquals(2, result.precoEstimado().scale());
    }

    @Test void automaticApplicationNullNaoImpedeCalculo() {
        assertTrue(calculadora.calcular(bd("80"), condicoes(null, "20", null, null, 1, null, true)).elegivel());
    }

    @Test void cumulativeNullNaoImpedeCalculo() {
        assertTrue(calculadora.calcular(bd("80"), condicoes(null, "20", null, null, 1, true, null)).elegivel());
    }

    @Test void estoqueZeroNaoAlteraMatematica() {
        var result = calculadora.calcular(bd("80"), condicoes(null, "20", null, null, 0, true, true));
        assertMoney("16.00", result.descontoAplicado());
    }

    @Test void usosPorCpfNaoAlteramMatematica() {
        var semUso = calculadora.calcular(bd("80"), condicoes(null, "20", null, null, 1, true, true));
        var muitosUsos = calculadora.calcular(bd("80"), condicoes(null, "20", null, null, 999, true, true));
        assertEquals(semUso.descontoAplicado(), muitosUsos.descontoAplicado());
        assertEquals(semUso.precoEstimado(), muitosUsos.precoEstimado());
    }

    @Test void rejeitaCondicoesNull() {
        assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(bd("80"), null));
    }

    @Test void percentualNaoPositivoEInconsistente() {
        var result = calculadora.calcular(bd("80"), percentual(null, "0", null));
        assertEquals(MotivoIneligibilidadeCupom.CONDICOES_INCONSISTENTES, result.motivo());
    }

    @Test void maximoNegativoEInconsistente() {
        var result = calculadora.calcular(bd("80"), percentual(null, "20", "-1"));
        assertEquals(MotivoIneligibilidadeCupom.CONDICOES_INCONSISTENTES, result.motivo());
    }

    private static CondicoesCupom percentual(String minimum, String percent, String maximum) {
        return condicoes(minimum, percent, null, maximum, 1, true, true);
    }

    private static CondicoesCupom fixo(String minimum, String fixed, String maximum) {
        return condicoes(minimum, null, fixed, maximum, 1, true, true);
    }

    private static CondicoesCupom condicoes(String minimum, String percent, String fixed,
                                             String maximum, Integer stock,
                                             Boolean automatic, Boolean cumulative) {
        return new CondicoesCupom(1L, null, null, bdOrNull(minimum), bdOrNull(percent),
                bdOrNull(fixed), bdOrNull(maximum), 1, stock, automatic, cumulative, "Termos");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal bdOrNull(String value) {
        return value == null ? null : bd(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
