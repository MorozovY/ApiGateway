-- V4__create_audit_logs.sql
-- Таблица аудит-логов для отслеживания изменений

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    changes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Индексы для быстрого поиска
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);

COMMENT ON TABLE audit_logs IS 'Аудит-лог изменений в системе';
COMMENT ON COLUMN audit_logs.entity_type IS 'Тип сущности (user, route, rate_limit)';
COMMENT ON COLUMN audit_logs.entity_id IS 'ID изменённой сущности';
COMMENT ON COLUMN audit_logs.action IS 'Действие (created, updated, deleted, role_changed)';
COMMENT ON COLUMN audit_logs.user_id IS 'ID пользователя, выполнившего действие';
COMMENT ON COLUMN audit_logs.username IS 'Username пользователя для удобства просмотра';
COMMENT ON COLUMN audit_logs.changes IS 'JSON с изменёнными полями (old/new values)';
