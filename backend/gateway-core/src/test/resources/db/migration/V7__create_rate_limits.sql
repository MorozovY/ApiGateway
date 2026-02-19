-- V7__create_rate_limits.sql
-- Создаёт таблицу для политик rate limiting (Epic 5)
-- Story 5.3: Упрощённая версия для тестов gateway-core (без FK на users)

CREATE TABLE rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    requests_per_second INTEGER NOT NULL CHECK (requests_per_second > 0),
    burst_size INTEGER NOT NULL CHECK (burst_size > 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Trigger для автоматического обновления updated_at (функция создана в V2)
CREATE TRIGGER update_rate_limits_updated_at
    BEFORE UPDATE ON rate_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Индекс для быстрого поиска по имени
CREATE INDEX idx_rate_limits_name ON rate_limits(name);
