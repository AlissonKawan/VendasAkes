package br.com.akesofertas.afiliados;

/** Isola o contrato privado do portal do restante da aplicação. */
@FunctionalInterface
public interface AffiliateLinkClient {
    AffiliateLinkResponse criarLink(AffiliateLinkRequest pedido);
}
