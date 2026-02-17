-- V5__add_description_to_routes.sql
-- Добавление поля description в таблицу routes (Story 3.1)

ALTER TABLE routes ADD COLUMN description VARCHAR(1000);

COMMENT ON COLUMN routes.description IS 'Описание маршрута (до 1000 символов)';
