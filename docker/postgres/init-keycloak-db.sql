-- Init script для создания базы данных Keycloak
-- Выполняется при первом запуске PostgreSQL контейнера
-- PostgreSQL init scripts запускаются только если /var/lib/postgresql/data пуст

-- Создаём базу данных keycloak для Keycloak Identity Provider
-- Примечание: GRANT не требуется, т.к. пользователь gateway является владельцем БД
-- и автоматически получает все права на созданную базу
CREATE DATABASE keycloak;
