-- V13__fix_rate_limits_fk_cascade.sql
-- Исправление FK constraint для rate_limits.created_by
-- Добавляет ON DELETE SET NULL чтобы удаление пользователя не блокировалось

-- Удаляем старый constraint
ALTER TABLE rate_limits
    DROP CONSTRAINT IF EXISTS rate_limits_created_by_fkey;

-- Изменяем колонку на nullable (для SET NULL)
ALTER TABLE rate_limits
    ALTER COLUMN created_by DROP NOT NULL;

-- Добавляем новый constraint с ON DELETE SET NULL
ALTER TABLE rate_limits
    ADD CONSTRAINT rate_limits_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

-- Аналогично для consumer_rate_limits если существует
ALTER TABLE consumer_rate_limits
    DROP CONSTRAINT IF EXISTS consumer_rate_limits_created_by_fkey;

ALTER TABLE consumer_rate_limits
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE consumer_rate_limits
    ADD CONSTRAINT consumer_rate_limits_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

COMMENT ON COLUMN rate_limits.created_by IS 'ID пользователя, создавшего политику (NULL если пользователь удалён)';
COMMENT ON COLUMN consumer_rate_limits.created_by IS 'ID пользователя, создавшего политику (NULL если пользователь удалён)';
