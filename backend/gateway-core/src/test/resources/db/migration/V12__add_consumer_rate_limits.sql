-- V12__add_consumer_rate_limits.sql
-- Story 12.8: Per-consumer Rate Limits
-- Таблица для per-consumer rate limiting policies
-- Упрощённая версия для gateway-core тестов (без FK на users)

CREATE TABLE consumer_rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_id VARCHAR(255) NOT NULL,
    requests_per_second INTEGER NOT NULL,
    burst_size INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID,

    -- UNIQUE constraint на consumer_id
    CONSTRAINT uq_consumer_rate_limits_consumer UNIQUE (consumer_id),
    -- Валидация: requests_per_second > 0
    CONSTRAINT chk_consumer_rate_limits_rps CHECK (requests_per_second > 0),
    -- Валидация: burst_size > 0
    CONSTRAINT chk_consumer_rate_limits_burst CHECK (burst_size > 0)
);

COMMENT ON TABLE consumer_rate_limits IS 'Per-consumer rate limiting policies';
COMMENT ON COLUMN consumer_rate_limits.consumer_id IS 'Consumer ID (Keycloak client_id / azp claim)';
COMMENT ON COLUMN consumer_rate_limits.requests_per_second IS 'Лимит запросов в секунду';
COMMENT ON COLUMN consumer_rate_limits.burst_size IS 'Максимальный burst (пик запросов)';
COMMENT ON COLUMN consumer_rate_limits.created_by IS 'ID пользователя, создавшего лимит';

-- Индекс для быстрого lookup по consumer_id
CREATE INDEX idx_consumer_rate_limits_consumer_id ON consumer_rate_limits(consumer_id);

-- Триггер для auto-update updated_at (используем существующую функцию из V2)
CREATE TRIGGER consumer_rate_limits_updated_at
    BEFORE UPDATE ON consumer_rate_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
