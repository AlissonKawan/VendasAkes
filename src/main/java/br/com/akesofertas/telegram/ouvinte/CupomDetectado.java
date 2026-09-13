package br.com.akesofertas.telegram.ouvinte;

import java.time.LocalDate;

/**
 * Representa um cupom extraído das mensagens do canal oficial de afiliados do Mercado Livre.
 */
public record CupomDetectado(
        String codigo,
        String desconto,
        String compraMinima,
        String descontoMaximo,
        LocalDate validoAte,
        String categoria,
        String urlCampanha
) {
    /**
     * Verifica se o cupom ainda está dentro da validade considerando a data de referência (hoje).
     */
    public boolean estaValido(LocalDate hoje) {
        // Para publicação automática, ausência de validade não é evidência de que
        // uma campanha histórica continua ativa.
        return validoAte != null && !validoAte.isBefore(hoje);
    }
}
