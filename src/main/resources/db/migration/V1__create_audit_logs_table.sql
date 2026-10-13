CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    created_by BIGINT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    data TEXT
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
