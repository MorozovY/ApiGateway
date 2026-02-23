-- seed-demo-data.sql
-- Демо-данные для разработки и тестирования
-- Запуск: docker exec -i gateway-postgres psql -U gateway -d gateway < scripts/seed-demo-data.sql

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

    -- Published routes (опубликованные)
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, approved_by, approved_at, rate_limit_id)
    VALUES
        ('/api/users', 'https://jsonplaceholder.typicode.com/users', ARRAY['GET', 'POST'], 'published',
         'API пользователей (JSONPlaceholder)', developer_id, security_id, NOW() - INTERVAL '5 days', rate_limit_standard_id),

        ('/api/posts', 'https://jsonplaceholder.typicode.com/posts', ARRAY['GET', 'POST', 'PUT', 'DELETE'], 'published',
         'API постов (JSONPlaceholder)', developer_id, security_id, NOW() - INTERVAL '3 days', rate_limit_standard_id),

        ('/api/comments', 'https://jsonplaceholder.typicode.com/comments', ARRAY['GET'], 'published',
         'API комментариев (только чтение)', developer_id, admin_id, NOW() - INTERVAL '2 days', rate_limit_premium_id)
    ON CONFLICT (path) DO NOTHING;

    -- Pending routes (ожидают одобрения)
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, submitted_at)
    VALUES
        ('/api/albums', 'https://jsonplaceholder.typicode.com/albums', ARRAY['GET', 'POST'], 'pending',
         'API альбомов — ожидает проверки', developer_id, NOW() - INTERVAL '1 day'),

        ('/api/photos', 'https://jsonplaceholder.typicode.com/photos', ARRAY['GET'], 'pending',
         'API фотографий — только чтение', developer_id, NOW() - INTERVAL '12 hours')
    ON CONFLICT (path) DO NOTHING;

    -- Draft routes (черновики)
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by)
    VALUES
        ('/api/todos', 'https://jsonplaceholder.typicode.com/todos', ARRAY['GET', 'POST', 'PATCH', 'DELETE'], 'draft',
         'API задач — в разработке', developer_id),

        ('/api/weather', 'https://api.openweathermap.org/data/2.5/weather', ARRAY['GET'], 'draft',
         'API погоды (требуется API ключ)', admin_id)
    ON CONFLICT (path) DO NOTHING;

    -- Rejected route (отклонённый)
    INSERT INTO routes (path, upstream_url, methods, status, description, created_by, submitted_at, rejected_by, rejected_at, rejection_reason)
    VALUES
        ('/api/internal', 'http://internal-service:8080/api', ARRAY['GET', 'POST'], 'rejected',
         'Внутренний сервис', developer_id, NOW() - INTERVAL '4 days', security_id, NOW() - INTERVAL '3 days',
         'Внутренние сервисы не должны быть доступны через публичный gateway')
    ON CONFLICT (path) DO NOTHING;

    -- ==========================================
    -- Audit Logs (история изменений)
    -- ==========================================

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
        json_build_object('path', path, 'upstreamUrl', upstream_url, 'methods', methods, 'status', 'draft')::text,
        created_at
    FROM routes
    WHERE NOT EXISTS (
        SELECT 1 FROM audit_logs al
        WHERE al.entity_type = 'route'
        AND al.entity_id = routes.id::text
        AND al.action = 'created'
    );

    -- Audit logs для routes (submitted) — pending routes
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

    RAISE NOTICE 'Демо-данные успешно созданы!';
    RAISE NOTICE 'Rate Limits: 3 политики';
    RAISE NOTICE 'Routes: 8 маршрутов (3 published, 2 pending, 2 draft, 1 rejected)';
    RAISE NOTICE 'Audit Logs: созданы для всех сущностей';
END $$;

-- Вывод результатов
SELECT 'Rate Limits:' AS info, COUNT(*) AS count FROM rate_limits
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
SELECT 'Audit Logs:', COUNT(*) FROM audit_logs;