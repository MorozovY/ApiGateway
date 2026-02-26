-- seed-demo-data.sql
-- Демо-данные для разработки и тестирования
-- Запуск: docker exec -i gateway-postgres psql -U gateway -d gateway < scripts/seed-demo-data.sql

-- ==========================================
-- Создание пользователей (developer, security)
-- ==========================================
-- Миграция V3_1 создаёт только admin, добавляем остальных
-- Пароли: placeholder (не используются при Keycloak auth)

INSERT INTO users (username, email, password_hash, role, is_active)
VALUES
    ('developer', 'developer@gateway.local', '$2a$10$placeholder.developer.hash.not.used.with.keycloak', 'developer', true),
    ('security', 'security@gateway.local', '$2a$10$placeholder.security.hash.not.used.with.keycloak', 'security', true)
ON CONFLICT (username) DO NOTHING;

-- Получаем UUID пользователей
DO $$
DECLARE
    developer_id UUID;
    security_id UUID;
    admin_id UUID;
    rate_limit_standard_id UUID;
    rate_limit_premium_id UUID;
    rate_limit_burst_id UUID;
BEGIN
    -- Получаем ID пользователей
    SELECT id INTO developer_id FROM users WHERE username = 'developer';
    SELECT id INTO security_id FROM users WHERE username = 'security';
    SELECT id INTO admin_id FROM users WHERE username = 'admin';

    -- Проверяем что пользователи существуют
    IF developer_id IS NULL OR security_id IS NULL OR admin_id IS NULL THEN
        RAISE EXCEPTION 'Не найдены базовые пользователи. Запустите миграции сначала.';
    END IF;

    -- ==========================================
    -- Rate Limits (политики ограничения)
    -- ==========================================

    -- Standard (10 req/s, burst 20)
    INSERT INTO rate_limits (id, name, description, requests_per_second, burst_size, created_by)
    VALUES (
        gen_random_uuid(),
        'Standard',
        'Стандартная политика для большинства API',
        10,
        20,
        admin_id
    )
    ON CONFLICT (name) DO NOTHING
    RETURNING id INTO rate_limit_standard_id;

    IF rate_limit_standard_id IS NULL THEN
        SELECT id INTO rate_limit_standard_id FROM rate_limits WHERE name = 'Standard';
    END IF;

    -- Premium (100 req/s, burst 200)
    INSERT INTO rate_limits (id, name, description, requests_per_second, burst_size, created_by)
    VALUES (
        gen_random_uuid(),
        'Premium',
        'Премиум политика для высоконагруженных API',
        100,
        200,
        admin_id
    )
    ON CONFLICT (name) DO NOTHING
    RETURNING id INTO rate_limit_premium_id;

    IF rate_limit_premium_id IS NULL THEN
        SELECT id INTO rate_limit_premium_id FROM rate_limits WHERE name = 'Premium';
    END IF;

    -- Burst (5 req/s, burst 50)
    INSERT INTO rate_limits (id, name, description, requests_per_second, burst_size, created_by)
    VALUES (
        gen_random_uuid(),
        'Burst',
        'Политика с высоким burst для редких пиковых нагрузок',
        5,
        50,
        admin_id
    )
    ON CONFLICT (name) DO NOTHING
    RETURNING id INTO rate_limit_burst_id;

    IF rate_limit_burst_id IS NULL THEN
        SELECT id INTO rate_limit_burst_id FROM rate_limits WHERE name = 'Burst';
    END IF;

    -- ==========================================
    -- Routes (маршруты)
    -- ==========================================
    -- Группировка по интеграциям (upstream сервисам)
    -- auth_required: true = требуется JWT, false = публичный доступ
    -- allowed_consumers: NULL = все consumers, ARRAY['id1','id2'] = whitelist

    -- ==========================================
    -- ИНТЕГРАЦИЯ 1: JSONPlaceholder Users API (published, 3 маршрута)
    -- Upstream: https://jsonplaceholder.typicode.com
    -- Статус: полностью опубликовано, защищено JWT
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, approved_by, approved_at, rate_limit_id, auth_required, allowed_consumers)
    VALUES
        ('/api/users', 'https://jsonplaceholder.typicode.com/users', ARRAY['GET', 'POST'], 'published',
         'Users API — список пользователей', developer_id, security_id, NOW() - INTERVAL '10 days',
         rate_limit_standard_id, true, NULL),

        ('/api/users/{id}', 'https://jsonplaceholder.typicode.com/users', ARRAY['GET', 'PUT', 'DELETE'], 'published',
         'Users API — операции с пользователем по ID', developer_id, security_id, NOW() - INTERVAL '10 days',
         rate_limit_standard_id, true, NULL),

        ('/api/users/{id}/posts', 'https://jsonplaceholder.typicode.com/users', ARRAY['GET'], 'published',
         'Users API — посты конкретного пользователя', developer_id, security_id, NOW() - INTERVAL '9 days',
         rate_limit_standard_id, true, NULL)
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- ИНТЕГРАЦИЯ 2: JSONPlaceholder Posts API (published, 3 маршрута)
    -- Upstream: https://jsonplaceholder.typicode.com
    -- Статус: опубликовано, whitelist для company-a и company-b
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, approved_by, approved_at, rate_limit_id, auth_required, allowed_consumers)
    VALUES
        ('/api/posts', 'https://jsonplaceholder.typicode.com/posts', ARRAY['GET', 'POST'], 'published',
         'Posts API — список постов (только company-a, company-b)', developer_id, security_id, NOW() - INTERVAL '7 days',
         rate_limit_premium_id, true, ARRAY['company-a', 'company-b']),

        ('/api/posts/{id}', 'https://jsonplaceholder.typicode.com/posts', ARRAY['GET', 'PUT', 'PATCH', 'DELETE'], 'published',
         'Posts API — операции с постом по ID', developer_id, security_id, NOW() - INTERVAL '7 days',
         rate_limit_premium_id, true, ARRAY['company-a', 'company-b']),

        ('/api/posts/{id}/comments', 'https://jsonplaceholder.typicode.com/posts', ARRAY['GET', 'POST'], 'published',
         'Posts API — комментарии к посту', developer_id, admin_id, NOW() - INTERVAL '6 days',
         rate_limit_premium_id, true, ARRAY['company-a', 'company-b'])
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- ИНТЕГРАЦИЯ 3: JSONPlaceholder Albums API (pending, 3 маршрута)
    -- Upstream: https://jsonplaceholder.typicode.com
    -- Статус: ожидает одобрения security team
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, submitted_at, auth_required, allowed_consumers)
    VALUES
        ('/api/albums', 'https://jsonplaceholder.typicode.com/albums', ARRAY['GET', 'POST'], 'pending',
         'Albums API — список альбомов (ожидает проверки)', developer_id, NOW() - INTERVAL '2 days',
         true, NULL),

        ('/api/albums/{id}', 'https://jsonplaceholder.typicode.com/albums', ARRAY['GET', 'PUT', 'DELETE'], 'pending',
         'Albums API — операции с альбомом по ID', developer_id, NOW() - INTERVAL '2 days',
         true, NULL),

        ('/api/albums/{id}/photos', 'https://jsonplaceholder.typicode.com/albums', ARRAY['GET'], 'pending',
         'Albums API — фотографии альбома', developer_id, NOW() - INTERVAL '1 day',
         true, ARRAY['company-c'])
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- ИНТЕГРАЦИЯ 4: Todo Service (draft, 2 маршрута)
    -- Upstream: https://jsonplaceholder.typicode.com
    -- Статус: в разработке
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, auth_required, allowed_consumers)
    VALUES
        ('/api/todos', 'https://jsonplaceholder.typicode.com/todos', ARRAY['GET', 'POST'], 'draft',
         'Todo API — список задач (в разработке)', developer_id, true, NULL),

        ('/api/todos/{id}', 'https://jsonplaceholder.typicode.com/todos', ARRAY['GET', 'PUT', 'PATCH', 'DELETE'], 'draft',
         'Todo API — операции с задачей по ID', developer_id, true, NULL)
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- ИНТЕГРАЦИЯ 5: Weather Service (draft, 2 маршрута)
    -- Upstream: https://api.openweathermap.org
    -- Статус: в разработке, публичный доступ (без JWT)
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, auth_required, allowed_consumers)
    VALUES
        ('/api/weather/current', 'https://api.openweathermap.org/data/2.5/weather', ARRAY['GET'], 'draft',
         'Weather API — текущая погода (публичный)', admin_id, false, NULL),

        ('/api/weather/forecast', 'https://api.openweathermap.org/data/2.5/forecast', ARRAY['GET'], 'draft',
         'Weather API — прогноз погоды (публичный)', admin_id, false, NULL)
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- ИНТЕГРАЦИЯ 6: Internal Service (rejected, 2 маршрута)
    -- Upstream: http://internal-service:8080
    -- Статус: отклонено — внутренние сервисы не публикуются
    -- ==========================================
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, submitted_at, rejected_by, rejected_at, rejection_reason, auth_required, allowed_consumers)
    VALUES
        ('/api/internal/health', 'http://internal-service:8080/health', ARRAY['GET'], 'rejected',
         'Internal API — health check', developer_id, NOW() - INTERVAL '5 days', security_id, NOW() - INTERVAL '4 days',
         'Внутренние endpoints не должны быть доступны через публичный gateway. Используйте internal load balancer.', true, NULL),

        ('/api/internal/metrics', 'http://internal-service:8080/metrics', ARRAY['GET'], 'rejected',
         'Internal API — метрики Prometheus', developer_id, NOW() - INTERVAL '5 days', security_id, NOW() - INTERVAL '4 days',
         'Метрики содержат sensitive данные. Доступ только через internal network.', true, NULL)
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- Consumer Rate Limits (для E2E тестов)
    -- ==========================================
    -- Test consumers из Keycloak realm-export.json
    -- Используются в E2E тестах для multi-tenant scenarios

    -- company-a: Standard rate limit (10 req/s, burst 20)
    INSERT INTO consumer_rate_limits (consumer_id, requests_per_second, burst_size, created_by, created_at, updated_at)
    VALUES (
        'company-a',
        10,
        20,
        admin_id,
        NOW() - INTERVAL '7 days',
        NOW() - INTERVAL '7 days'
    )
    ON CONFLICT (consumer_id) DO NOTHING;

    -- company-b: Premium rate limit (50 req/s, burst 100)
    INSERT INTO consumer_rate_limits (consumer_id, requests_per_second, burst_size, created_by, created_at, updated_at)
    VALUES (
        'company-b',
        50,
        100,
        admin_id,
        NOW() - INTERVAL '5 days',
        NOW() - INTERVAL '5 days'
    )
    ON CONFLICT (consumer_id) DO NOTHING;

    -- company-c: Low rate limit (5 req/s, burst 10) для rate limit тестов
    INSERT INTO consumer_rate_limits (consumer_id, requests_per_second, burst_size, created_by, created_at, updated_at)
    VALUES (
        'company-c',
        5,
        10,
        admin_id,
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days'
    )
    ON CONFLICT (consumer_id) DO NOTHING;

    -- ==========================================
    -- Audit Logs (история изменений)
    -- ==========================================

    -- Audit logs для создания пользователей developer и security
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'user',
        id::text,
        'created',
        admin_id,
        'admin',
        json_build_object('username', username, 'email', email, 'role', role)::text,
        created_at
    FROM users
    WHERE username IN ('developer', 'security')
    AND NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'user'
        AND al.entity_id = users.id::text
        AND al.action = 'created'
    );

    -- Audit logs для rate limits
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'rate_limit',
        id::text,
        'created',
        admin_id,
        'admin',
        json_build_object('name', name, 'requestsPerSecond', requests_per_second, 'burstSize', burst_size)::text,
        created_at
    FROM rate_limits
    WHERE NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'rate_limit'
        AND al.entity_id = rate_limits.id::text
        AND al.action = 'created'
    );

    -- Audit logs для routes (created)
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'route',
        id::text,
        'created',
        created_by,
        (SELECT username FROM users WHERE id = routes.created_by),
        json_build_object(
            'path', path,
            'upstreamUrl', upstream_url,
            'methods', methods,
            'status', 'draft',
            'authRequired', auth_required,
            'allowedConsumers', allowed_consumers
        )::text,
        created_at
    FROM routes
    WHERE NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'route'
        AND al.entity_id = routes.id::text
        AND al.action = 'created'
    );

    -- Audit logs для routes (submitted) — pending и rejected routes
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'route',
        id::text,
        'submitted',
        created_by,
        (SELECT username FROM users WHERE id = routes.created_by),
        json_build_object('path', path, 'status', 'pending')::text,
        submitted_at
    FROM routes
    WHERE status IN ('pending', 'published', 'rejected')
    AND submitted_at IS NOT NULL
    AND NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'route'
        AND al.entity_id = routes.id::text
        AND al.action = 'submitted'
    );

    -- Audit logs для routes (approved) — published routes
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'route',
        id::text,
        'approved',
        approved_by,
        (SELECT username FROM users WHERE id = routes.approved_by),
        json_build_object('path', path, 'status', 'published')::text,
        approved_at
    FROM routes
    WHERE status = 'published'
    AND approved_at IS NOT NULL
    AND NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'route'
        AND al.entity_id = routes.id::text
        AND al.action = 'approved'
    );

    -- Audit logs для routes (rejected) — rejected routes
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'route',
        id::text,
        'rejected',
        rejected_by,
        (SELECT username FROM users WHERE id = routes.rejected_by),
        json_build_object('path', path, 'status', 'rejected', 'reason', rejection_reason)::text,
        rejected_at
    FROM routes
    WHERE status = 'rejected'
    AND rejected_at IS NOT NULL
    AND NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'route'
        AND al.entity_id = routes.id::text
        AND al.action = 'rejected'
    );

    -- Audit logs для consumer rate limits
    INSERT INTO audit_logs (entity_type, entity_id, action, user_id, username, changes, created_at)
    SELECT
        'consumer_rate_limit',
        id::text,
        'created',
        admin_id,
        'admin',
        json_build_object('consumerId', consumer_id, 'requestsPerSecond', requests_per_second, 'burstSize', burst_size)::text,
        created_at
    FROM consumer_rate_limits
    WHERE NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'consumer_rate_limit'
        AND al.entity_id = consumer_rate_limits.id::text
        AND al.action = 'created'
    );

    RAISE NOTICE 'Демо-данные успешно созданы!';
    RAISE NOTICE 'Users: 2 пользователя (developer, security)';
    RAISE NOTICE 'Rate Limits: 3 политики (Standard, Premium, Burst)';
    RAISE NOTICE 'Routes: 15 маршрутов в 6 интеграциях';
    RAISE NOTICE '  - Users API: 3 published';
    RAISE NOTICE '  - Posts API: 3 published';
    RAISE NOTICE '  - Albums API: 3 pending';
    RAISE NOTICE '  - Todo API: 2 draft';
    RAISE NOTICE '  - Weather API: 2 draft';
    RAISE NOTICE '  - Internal API: 2 rejected';
    RAISE NOTICE 'Consumer Rate Limits: 3 consumers (company-a, company-b, company-c)';
    RAISE NOTICE 'Audit Logs: созданы для всех сущностей';
END $$;

-- Вывод результатов
SELECT 'Users:' AS info, COUNT(*) AS count FROM users
UNION ALL
SELECT 'Rate Limits:', COUNT(*) FROM rate_limits
UNION ALL
SELECT 'Routes:', COUNT(*) FROM routes
UNION ALL
SELECT 'Published:', COUNT(*) FROM routes WHERE status = 'published'
UNION ALL
SELECT 'Pending:', COUNT(*) FROM routes WHERE status = 'pending'
UNION ALL
SELECT 'Draft:', COUNT(*) FROM routes WHERE status = 'draft'
UNION ALL
SELECT 'Rejected:', COUNT(*) FROM routes WHERE status = 'rejected'
UNION ALL
SELECT 'Consumer Rate Limits:', COUNT(*) FROM consumer_rate_limits
UNION ALL
SELECT 'Audit Logs:', COUNT(*) FROM audit_logs;

-- Группировка по интеграциям (upstream)
SELECT
    CASE
        WHEN upstream_url LIKE '%jsonplaceholder%/users%' THEN 'Users API'
        WHEN upstream_url LIKE '%jsonplaceholder%/posts%' THEN 'Posts API'
        WHEN upstream_url LIKE '%jsonplaceholder%/albums%' THEN 'Albums API'
        WHEN upstream_url LIKE '%jsonplaceholder%/todos%' THEN 'Todo API'
        WHEN upstream_url LIKE '%openweathermap%' THEN 'Weather API'
        WHEN upstream_url LIKE '%internal-service%' THEN 'Internal API'
        ELSE 'Other'
    END AS integration,
    status,
    COUNT(*) AS routes_count
FROM routes
GROUP BY integration, status
ORDER BY integration, status;
