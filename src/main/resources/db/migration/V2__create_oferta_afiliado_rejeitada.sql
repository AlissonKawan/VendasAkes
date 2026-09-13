CREATE TABLE ofertas_afiliado_rejeitadas (
    id BIGSERIAL PRIMARY KEY,
    fornecedor VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    produto_id VARCHAR(255),
    error_code INTEGER NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    data_rejeicao TIMESTAMP NOT NULL,
    reprocessar_apos TIMESTAMP NOT NULL,
    CONSTRAINT uk_ofertas_afiliado_rejeitadas_forn_item UNIQUE (fornecedor, item_id)
);
