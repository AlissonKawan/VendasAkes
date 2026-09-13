CREATE TABLE ofertas_publicadas (
    id BIGSERIAL PRIMARY KEY,
    fornecedor VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    produto_id VARCHAR(255),
    titulo VARCHAR(1000) NOT NULL,
    url_original VARCHAR(2000) NOT NULL,
    url_afiliado VARCHAR(2000) NOT NULL,
    preco_anterior DECIMAL(19, 2),
    preco_atual DECIMAL(19, 2) NOT NULL,
    desconto VARCHAR(255),
    comissao VARCHAR(255),
    destaque VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    data_criacao TIMESTAMP NOT NULL,
    data_envio TIMESTAMP,
    mensagem_erro VARCHAR(2000),
    CONSTRAINT uk_ofertas_publicadas_forn_item UNIQUE (fornecedor, item_id)
);

