package br.com.akesofertas.afiliados;

/** Resultado individual correlacionado pela origin_url retornada pelo portal. */
public record ResultadoLinkAfiliado(
        String originUrl,
        boolean sucesso,
        String shortUrl,
        Boolean created,
        Integer errorCode,
        String message) {
}
