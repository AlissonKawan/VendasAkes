package br.com.akesofertas.afiliados.rejeicao;

import java.time.Instant;

public record OfertaAfiliadoRejeitada(Long id, String fornecedor, String itemId, String produtoId,
                                      Integer errorCode, String motivo, Instant dataRejeicao,
                                      Instant reprocessarApos) {
}
