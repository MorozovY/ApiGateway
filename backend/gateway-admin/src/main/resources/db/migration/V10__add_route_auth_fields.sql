-- V10__add_route_auth_fields.sql
-- Story 12.4: Gateway Core — JWT Authentication Filter
-- Добавляем поля для JWT аутентификации маршрутов

-- Требуется ли JWT аутентификация для маршрута
-- По умолчанию true — новые маршруты защищены
ALTER TABLE routes
ADD COLUMN auth_required BOOLEAN NOT NULL DEFAULT true;

-- Whitelist consumer IDs для маршрута
-- NULL означает что все consumers разрешены
ALTER TABLE routes
ADD COLUMN allowed_consumers TEXT[] DEFAULT NULL;

COMMENT ON COLUMN routes.auth_required IS 'Требуется ли JWT аутентификация для маршрута';
COMMENT ON COLUMN routes.allowed_consumers IS 'Whitelist consumer IDs (NULL = все разрешены)';

-- Индекс для фильтрации по auth_required
CREATE INDEX idx_routes_auth_required ON routes(auth_required);
