package br.com.akesofertas.provider;

/** Cada marketplace resolve seus próprios anúncios e preços, sem mudar o fluxo da busca. */
public interface OfertaProvider {
    String codigo();
    Avaliacao avaliar(ProdutoEncontrado produto);

    record Avaliacao(OfertaEncontrada oferta, String motivo) {
        public static Avaliacao descartar(String motivo) { return new Avaliacao(null, motivo); }
    }
}
