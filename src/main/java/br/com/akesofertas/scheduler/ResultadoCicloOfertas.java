package br.com.akesofertas.scheduler;

public record ResultadoCicloOfertas(int ofertasHub, int jaPublicadas, int cooldown,
                                   int tentadasCreateLink, int elegiveis, int selecionadas,
                                   int enviadas, int errosEnvio, boolean ignoradoPorConcorrencia,
                                   boolean sessaoExpirada) {
    static ResultadoCicloOfertas concorrente() {
        return new ResultadoCicloOfertas(0, 0, 0, 0, 0, 0, 0, 0, true, false);
    }
}
