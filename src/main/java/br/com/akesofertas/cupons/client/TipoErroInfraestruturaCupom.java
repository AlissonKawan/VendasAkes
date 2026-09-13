package br.com.akesofertas.cupons.client;

/** Categorias observáveis de falha na comunicação com o portal de cupons. */
public enum TipoErroInfraestruturaCupom {
    SESSAO_INVALIDA,
    ACESSO_NEGADO,
    ESTRUTURA_HTML_ALTERADA,
    RATE_LIMIT,
    ENDPOINT_NAO_ENCONTRADO,
    RESPOSTA_INVALIDA,
    CONFLITO_CODIGO,
    INFRAESTRUTURA
}
