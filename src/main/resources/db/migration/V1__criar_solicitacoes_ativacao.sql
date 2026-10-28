CREATE TABLE solicitacoes_ativacao (
    id UUID PRIMARY KEY,
    external_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    consumer_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_solicitacoes_external_id UNIQUE (external_id),
    CONSTRAINT ck_solicitacoes_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_solicitacoes_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_solicitacoes_consumer_status_created
    ON solicitacoes_ativacao (consumer_id, status, created_at DESC, id DESC);

CREATE INDEX idx_solicitacoes_consumer_customer_created
    ON solicitacoes_ativacao (consumer_id, customer_id, created_at DESC, id DESC);

CREATE INDEX idx_solicitacoes_pending_work
    ON solicitacoes_ativacao (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_solicitacoes_processing_recovery
    ON solicitacoes_ativacao (processing_started_at)
    WHERE status = 'PROCESSING';
