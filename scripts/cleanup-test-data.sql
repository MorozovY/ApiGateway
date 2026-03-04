-- Story 15.5: Очистка тестовых данных из БД
-- Удаляет все данные с префиксами e2e-* и diagnostic-*
--
-- Использование:
--   docker exec -i infra-postgres psql -U gateway -d gateway < scripts/cleanup-test-data.sql
--
-- ВАЖНО: Скрипт НЕ удаляет демо-пользователей (admin, security, developer)

BEGIN;

-- 1. Удаляем audit_logs связанные с тестовыми сущностями
-- (по changes содержащим тестовые паттерны или по тестовым username)
DELETE FROM audit_logs
WHERE changes LIKE '%/e2e-%'
   OR changes LIKE '%/diagnostic-%'
   OR changes LIKE '%"e2e-%'
   OR username IN ('test-developer', 'test-admin', 'test-security');

-- 2. Удаляем route_versions для тестовых маршрутов (FK constraint)
-- Используем DO block для безопасной проверки существования таблицы
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'route_versions') THEN
    DELETE FROM route_versions
    WHERE route_id IN (
      SELECT id FROM routes WHERE path LIKE '/e2e-%' OR path LIKE '/diagnostic-%'
    );
  END IF;
END $$;

-- 3. Удаляем тестовые маршруты
DELETE FROM routes WHERE path LIKE '/e2e-%' OR path LIKE '/diagnostic-%';

-- 4. Удаляем маршруты которые ссылаются на тестовые rate limit политики
DELETE FROM routes
WHERE rate_limit_id IN (SELECT id FROM rate_limits WHERE name LIKE 'e2e-%');

-- 5. Удаляем тестовые rate limit политики
DELETE FROM rate_limits WHERE name LIKE 'e2e-%';

-- 6. Удаляем audit_logs для тестовых пользователей (FK constraint)
DELETE FROM audit_logs
WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e-%');

-- 7. Обнуляем FK ссылки на тестовых пользователей в routes
UPDATE routes SET approved_by = NULL
WHERE approved_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%');

UPDATE routes SET rejected_by = NULL
WHERE rejected_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%');

-- 8. Удаляем rate_limits созданные тестовыми пользователями
DELETE FROM rate_limits
WHERE created_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%');

-- 9. Удаляем тестовых пользователей (кроме демо-пользователей)
DELETE FROM users
WHERE username LIKE 'e2e-%'
  AND username NOT IN ('admin', 'security', 'developer');

COMMIT;

-- Показываем результат очистки
SELECT 'Cleanup completed. Remaining test data:' as status;

SELECT 'routes (e2e/diagnostic)' as table_name, COUNT(*) as remaining
FROM routes WHERE path LIKE '/e2e-%' OR path LIKE '/diagnostic-%'
UNION ALL
SELECT 'rate_limits (e2e)', COUNT(*)
FROM rate_limits WHERE name LIKE 'e2e-%'
UNION ALL
SELECT 'users (e2e)', COUNT(*)
FROM users WHERE username LIKE 'e2e-%';
