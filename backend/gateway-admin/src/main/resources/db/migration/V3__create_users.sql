-- V3__create_users.sql
-- Таблица пользователей с поддержкой ролей

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'developer',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('developer', 'security', 'admin'))
);

-- Индексы для быстрого поиска по username и email
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER users_updated_at_trigger
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE users IS 'Пользователи API Gateway с поддержкой ролей';
COMMENT ON COLUMN users.username IS 'Уникальный логин пользователя';
COMMENT ON COLUMN users.email IS 'Уникальный email пользователя';
COMMENT ON COLUMN users.password_hash IS 'Хеш пароля в формате BCrypt';
COMMENT ON COLUMN users.role IS 'Роль пользователя: developer, security, admin';
COMMENT ON COLUMN users.is_active IS 'Флаг активности учётной записи';
