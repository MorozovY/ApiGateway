-- V5__fix_audit_logs_fk_cascade.sql
-- Исправление FK constraint для audit_logs: ON DELETE CASCADE → ON DELETE RESTRICT
--
-- Обоснование: аудит-лог должен переживать удаление пользователей.
-- CASCADE удалял бы доказательства действий при физическом удалении user записи.
-- RESTRICT запрещает удаление пользователя пока есть связанные аудит-записи,
-- что является стандартным поведением для систем аудита.

ALTER TABLE audit_logs
    DROP CONSTRAINT fk_audit_logs_user;

ALTER TABLE audit_logs
    ADD CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_audit_logs_user ON audit_logs IS
    'RESTRICT: удаление пользователя запрещено пока существуют аудит-записи';
