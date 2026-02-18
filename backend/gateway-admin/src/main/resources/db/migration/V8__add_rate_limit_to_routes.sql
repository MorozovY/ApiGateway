-- V8__add_rate_limit_to_routes.sql
-- Добавляет поле rate_limit_id в таблицу routes (подготовка для Story 5.2)
-- Story 5.1: Task 8 — подготовка для назначения политик маршрутам

ALTER TABLE routes
    ADD COLUMN rate_limit_id UUID REFERENCES rate_limits(id);

COMMENT ON COLUMN routes.rate_limit_id IS 'ID политики rate limiting, назначенной маршруту (nullable)';
