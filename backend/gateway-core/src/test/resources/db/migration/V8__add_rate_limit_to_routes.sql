-- V8__add_rate_limit_to_routes.sql
-- Добавляет поле rate_limit_id в таблицу routes (для Story 5.3)

ALTER TABLE routes
    ADD COLUMN rate_limit_id UUID REFERENCES rate_limits(id);
