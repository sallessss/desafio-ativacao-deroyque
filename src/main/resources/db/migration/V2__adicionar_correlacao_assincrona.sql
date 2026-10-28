ALTER TABLE solicitacoes_ativacao
    ADD COLUMN correlation_id VARCHAR(64);

UPDATE solicitacoes_ativacao
SET correlation_id = id::text
WHERE correlation_id IS NULL;

ALTER TABLE solicitacoes_ativacao
    ALTER COLUMN correlation_id SET NOT NULL;
