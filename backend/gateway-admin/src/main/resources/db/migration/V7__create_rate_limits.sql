-- V7__create_rate_limits.sql
-- Создаёт таблицу для политик rate limiting (Epic 5)
-- Story 5.1: Rate Limit Policy CRUD API

CREATE TABLE rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    requests_per_second INTEGER NOT NULL CHECK (requests_per_second > 0),
    burst_size INTEGER NOT NULL CHECK (burst_size > 0),
    created_by UUID NOT NULL REFERENCES users(id),
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

-- Комментарии к таблице и колонкам
COMMENT ON TABLE rate_limits IS 'Политики rate limiting для маршрутов';
COMMENT ON COLUMN rate_limits.id IS 'Уникальный идентификатор политики (UUID)';
COMMENT ON COLUMN rate_limits.name IS 'Уникальное имя политики';
COMMENT ON COLUMN rate_limits.description IS 'Описание политики (опционально)';
COMMENT ON COLUMN rate_limits.requests_per_second IS 'Лимит запросов в секунду (> 0)';
COMMENT ON COLUMN rate_limits.burst_size IS 'Максимальный размер burst (>= requests_per_second)';
COMMENT ON COLUMN rate_limits.created_by IS 'ID пользователя, создавшего политику (FK на users)';
COMMENT ON COLUMN rate_limits.created_at IS 'Дата и время создания политики';
COMMENT ON COLUMN rate_limits.updated_at IS 'Дата и время последнего обновления политики';
