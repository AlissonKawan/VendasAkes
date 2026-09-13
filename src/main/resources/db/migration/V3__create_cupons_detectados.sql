CREATE TABLE cupons_detectados (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(100) NOT NULL UNIQUE,
    desconto VARCHAR(100) NOT NULL,
    compra_minima VARCHAR(100),
    desconto_maximo VARCHAR(100),
    valido_ate DATE,
    categoria VARCHAR(255),
    url_campanha VARCHAR(2000) NOT NULL,
    url_desencurtada VARCHAR(2000),
    status VARCHAR(50) NOT NULL,
    data_deteccao TIMESTAMP NOT NULL,
    data_processamento TIMESTAMP,
    mensagem_erro VARCHAR(2000)
);

CREATE INDEX idx_cupons_detectados_status ON cupons_detectados(status);
