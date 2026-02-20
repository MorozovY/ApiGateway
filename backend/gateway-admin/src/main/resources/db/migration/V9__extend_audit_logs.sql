-- V9__extend_audit_logs.sql
-- Добавление полей для IP адреса и correlation ID (Story 7.1, AC1)

ALTER TABLE audit_logs
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN correlation_id VARCHAR(128);

-- Индекс для поиска по correlation ID
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);

COMMENT ON COLUMN audit_logs.ip_address IS 'IP адрес клиента (X-Forwarded-For или remote)';
COMMENT ON COLUMN audit_logs.correlation_id IS 'Correlation ID запроса для трассировки';
