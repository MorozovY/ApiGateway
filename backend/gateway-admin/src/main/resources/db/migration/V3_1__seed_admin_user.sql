-- V3_1__seed_admin_user.sql
-- Seed-запись для администратора (placeholder пароль)
-- ВАЖНО: Реальный пароль устанавливается через AdminUserDataLoader из ADMIN_PASSWORD env var
-- Используем невалидный BCrypt хеш (начинается с $PLACEHOLDER$) как маркер
-- AdminUserDataLoader обнаружит его и обновит при первом старте приложения

-- Вставляем admin только если его ещё нет
INSERT INTO users (username, email, password_hash, role, is_active)
SELECT 'admin', 'admin@gateway.local', '$PLACEHOLDER$will.be.replaced.by.AdminUserDataLoader', 'admin', true
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

COMMENT ON TABLE users IS 'Таблица пользователей. Требуется env var ADMIN_PASSWORD для установки пароля администратора.';
