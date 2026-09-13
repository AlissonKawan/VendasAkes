package br.com.akesofertas.cupons.domain;

/** Motivos que impedem um cálculo seguro do desconto. */
public enum MotivoIneligibilidadeCupom {
    ABAIXO_COMPRA_MINIMA,
    SEM_REGRA_DE_DESCONTO,
    CONDICOES_INCONSISTENTES
}
