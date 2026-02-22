# Story 10.5: Nginx Health Check on Metrics Page

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **DevOps Engineer**,
I want to see Nginx health status on the Metrics page,
so that I can monitor the reverse proxy.

## Feature Context

**Source:** Epic 9 Retrospective (2026-02-22) — FR-02 feedback from Yury (Project Lead)

**Business Value:** Nginx является критическим компонентом инфраструктуры — это reverse proxy, через который проходят ВСЕ запросы к системе. Мониторинг его состояния необходим для быстрого обнаружения проблем с доступностью.

**Текущее состояние:** HealthService уже проверяет 6 сервисов:
- gateway-core (HTTP к /actuator/health)
- gateway-admin (всегда UP)
- postgresql (R2DBC connection test)
- redis (PING команда)
- prometheus (HTTP к /-/healthy)
- grafana (HTTP к /api/health)

**Что нужно добавить:** Проверка Nginx через HTTP GET к `/nginx-health` endpoint (уже настроен в nginx.conf).

## Acceptance Criteria

### AC1: Nginx status displayed in Health Check section
**Given** user navigates to `/metrics`
**When** Health Check section loads
**Then** Nginx status is displayed alongside other services
**And** status shows UP when Nginx responds to /nginx-health

### AC2: Nginx DOWN status shown correctly
**Given** Nginx is not responding
**When** Health Check section loads
**Then** Nginx shows DOWN status with red indicator
**And** error details available on hover

### AC3: Nginx positioned correctly in service list
**Given** Health Check section displays all services
**When** services are rendered
**Then** Nginx appears BEFORE gateway-core (as it's the entry point)
**And** order is: Nginx → Gateway Core → Gateway Admin → PostgreSQL → Redis → Prometheus → Grafana

## Analysis Summary

### Nginx Health Check Endpoint: ✅ ALREADY EXISTS

Из `docker/nginx/nginx.conf:71-76`:
```nginx
# Health check
location /nginx-health {
    access_log off;
    return 200 "healthy\n";
    add_header Content-Type text/plain;
}
```

**URL для проверки:** `http://nginx:80/nginx-health`
- Response 200: "healthy\n"
- Response body: text/plain

### Backend Changes Required

**1. HealthService.kt — добавить checkNginx():**

```kotlin
// Новая константа
const val SERVICE_NGINX = "nginx"

// Новый метод
fun checkNginx(): Mono<ServiceHealthDto> {
    logger.debug("Проверка Nginx: {}/nginx-health", nginxUrl)

    return webClient.get()
        .uri("$nginxUrl/nginx-health")
        .retrieve()
        .bodyToMono(String::class.java)
        .map {
            ServiceHealthDto(SERVICE_NGINX, ServiceStatus.UP, Instant.now())
        }
        .timeout(checkTimeout)
        .onErrorResume { error ->
            logger.warn("Nginx недоступен: {}", error.message)
            Mono.just(createDownStatus(SERVICE_NGINX, error))
        }
}
```

**2. application.yml — добавить nginx URL:**

```yaml
nginx:
  url: ${NGINX_URL:http://localhost:80}
```

**3. docker-compose.override.yml — добавить env var:**

```yaml
environment:
  - NGINX_URL=http://nginx:80
```

### Frontend Changes Required

**1. HealthCheckSection.tsx — обновить SERVICE_CONFIG:**

```typescript
const SERVICE_CONFIG: Record<string, { displayName: string; order: number }> = {
  'nginx': { displayName: 'Nginx', order: 0 },          // Новый — первый!
  'gateway-core': { displayName: 'Gateway Core', order: 1 },
  'gateway-admin': { displayName: 'Gateway Admin', order: 2 },
  'postgresql': { displayName: 'PostgreSQL', order: 3 },
  'redis': { displayName: 'Redis', order: 4 },
  'prometheus': { displayName: 'Prometheus', order: 5 },
  'grafana': { displayName: 'Grafana', order: 6 },
}
```

**2. Тесты — обновить mock data для 7 сервисов**

## Tasks / Subtasks

- [x] Task 1: Backend — Add checkNginx method to HealthService (AC: #1, #2)
  - [x] 1.1 Add `SERVICE_NGINX = "nginx"` constant
  - [x] 1.2 Add `nginxUrl` parameter with @Value annotation
  - [x] 1.3 Implement `checkNginx()` method (GET /nginx-health)
  - [x] 1.4 Add checkNginx() to getServicesHealth() healthChecks list
  - [x] 1.5 Update KDoc comment with Nginx check description

- [x] Task 2: Backend — Configuration for Nginx URL (AC: #1)
  - [x] 2.1 Add `nginx.url` to application.yml with default `http://localhost:80`
  - [x] 2.2 Add `NGINX_URL` env var to docker-compose.override.yml

- [x] Task 3: Backend — Unit tests for Nginx health check (AC: #1, #2)
  - [x] 3.1 Test: `возвращает UP для Nginx когда health endpoint отвечает`
  - [x] 3.2 Test: `возвращает DOWN для Nginx когда сервер недоступен`
  - [x] 3.3 Update `возвращает HealthResponse со всеми сервисами` test for 7 services

- [x] Task 4: Frontend — Update SERVICE_CONFIG for Nginx (AC: #3)
  - [x] 4.1 Add 'nginx' entry with order: 0 to SERVICE_CONFIG
  - [x] 4.2 Update comments to reflect 7 services

- [x] Task 5: Frontend — Update tests for 7 services (AC: #1, #2, #3)
  - [x] 5.1 Update mockHealthResponse with nginx service
  - [x] 5.2 Update mockHealthWithDown with nginx DOWN variant
  - [x] 5.3 Update test "отображает все 6 сервисов" → "отображает все 7 сервисов"
  - [x] 5.4 Update assertion for UP tags count (6 → 7)
  - [x] 5.5 Update test for shuffled services with nginx

- [x] Task 6: Integration test — Nginx health check (AC: #1)
  - [x] 6.1 Update HealthControllerIntegrationTest for 7 services
  - [x] 6.2 Fix test isolation (prometheus/grafana URLs to unreachable ports)

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `http://nginx:80/nginx-health` | GET | - | ✅ Существует (nginx.conf) |
| `/api/v1/health/services` | GET | - | ✅ Существует (будет расширен) |

**Проверки перед началом разработки:**

- [x] Nginx health endpoint существует в nginx.conf
- [x] HealthService уже использует WebClient для HTTP checks
- [x] Паттерн timeout + onErrorResume уже реализован
- [x] SERVICE_CONFIG в HealthCheckSection поддерживает order

## Dev Notes

### Архитектура решения

**Минимальные изменения — следуем существующему паттерну:**

| Компонент | Изменение |
|-----------|-----------|
| HealthService.kt | +1 метод checkNginx(), +1 параметр nginxUrl |
| application.yml | +2 строки (nginx.url) |
| docker-compose.override.yml | +1 env var (NGINX_URL) |
| HealthCheckSection.tsx | +1 entry в SERVICE_CONFIG |
| HealthServiceTest.kt | +2 теста, обновить 1 тест |
| HealthCheckSection.test.tsx | Обновить mock data |

### Паттерн из существующего кода

**checkGatewayCore() — шаблон для checkNginx():**

```kotlin
// Из HealthService.kt:92-112
fun checkGatewayCore(): Mono<ServiceHealthDto> {
    logger.debug("Проверка gateway-core: {}/actuator/health", gatewayCoreUrl)

    return webClient.get()
        .uri("$gatewayCoreUrl/actuator/health")
        .retrieve()
        .bodyToMono(Map::class.java)
        .map { response ->
            val status = response["status"] as? String
            if (status == "UP") {
                ServiceHealthDto(SERVICE_GATEWAY_CORE, ServiceStatus.UP, Instant.now())
            } else {
                ServiceHealthDto(SERVICE_GATEWAY_CORE, ServiceStatus.DOWN, Instant.now(), "Status: $status")
            }
        }
        .timeout(checkTimeout)
        .onErrorResume { error ->
            logger.warn("gateway-core недоступен: {}", error.message)
            Mono.just(createDownStatus(SERVICE_GATEWAY_CORE, error))
        }
}
```

**Для Nginx проще** — не нужно парсить JSON, достаточно получить 200 response.

### Nginx конфигурация (nginx.conf)

```nginx
# Health check endpoint
location /nginx-health {
    access_log off;
    return 200 "healthy\n";
    add_header Content-Type text/plain;
}
```

**Важно:** Endpoint возвращает plain text, НЕ JSON.

### Docker network

В docker-compose.override.yml Nginx имеет service name `nginx`, поэтому URL внутри Docker network: `http://nginx:80/nginx-health`

### Project Structure Notes

- Следуем паттерну Story 8.1 (Health Check on Metrics Page)
- Минимальные изменения — добавляем 1 сервис к существующим 6
- Nginx должен быть ПЕРВЫМ в списке (order: 0), так как это entry point

### References

- [Source: docker/nginx/nginx.conf:71-76] — существующий health endpoint
- [Source: backend/gateway-admin/src/main/kotlin/.../service/HealthService.kt] — существующие health checks
- [Source: frontend/admin-ui/src/features/metrics/components/HealthCheckSection.tsx:28-35] — SERVICE_CONFIG
- [Source: _bmad-output/implementation-artifacts/8-1-health-check-metrics-page.md] — паттерн реализации
- [Source: epics.md#Story 10.5] — acceptance criteria

## Previous Story Learnings (10.4)

**Из Story 10.4 (Author Draft Deletion):**

1. **Role check в lowercase** — `user?.role === 'admin'`, НЕ uppercase
2. **Тесты на русском языке** — все названия тестов на русском
3. **517 frontend тестов** проходят — не сломать существующие

**Применимо к текущей story:**
- Следовать naming convention для тестов (русские названия)
- Обновить все assertions для 7 сервисов вместо 6
- Не добавлять лишние изменения — минимальный scope

## Git Intelligence (последние коммиты)

```
d0fc778 feat: Story 10.4 — Author can delete own draft route + code review fixes
a23df66 feat: Story 10.3 — Security role route rollback + code review fixes
100a1b9 feat: Story 10.2 — Approvals page real-time updates
```

**Паттерн коммитов Epic 10:**
- Prefix: `feat:` для новых features
- Format: `feat: Story 10.X — краткое описание + code review fixes`

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- ✅ Task 1: Добавлен метод `checkNginx()` в HealthService — проверяет `/nginx-health` endpoint, возвращает UP/DOWN
- ✅ Task 2: Добавлена конфигурация `nginx.url` в application.yml и `NGINX_URL` env var в docker-compose.override.yml
- ✅ Task 3: Добавлены 2 unit теста для Nginx (UP и DOWN), обновлён тест getServicesHealth для 7 сервисов
- ✅ Task 4: Добавлен `nginx` в SERVICE_CONFIG с order: 0 (первый в списке, как entry point)
- ✅ Task 5: Обновлены все frontend тесты для 7 сервисов — 10 тестов HealthCheckSection прошли
- ✅ Task 6: Обновлён integration test для 7 сервисов, исправлена изоляция тестов (prometheus/grafana URLs)
- 📊 Все тесты прошли: 36 Health-related backend тестов, 517 frontend тестов

### Code Review Fixes (2026-02-22)

- 🔴 **H1 FIXED:** Обновлён `docker-compose.override.yml.example` — добавлены NGINX_URL, GATEWAY_CORE_URL, GRAFANA_URL env vars и nginx service
- 🟡 **M1 FIXED:** Исправлен Story File List — теперь указывает `docker-compose.override.yml.example` (tracked git) вместо локального файла
- 🟢 **L1 FIXED:** Добавлен тест `AC3: Nginx отображается первым в списке` — проверяет DOM порядок через compareDocumentPosition

### Change Log

- 2026-02-22: Story 10.5 — Nginx health check on Metrics page (7 services total)
- 2026-02-22: Code review — 3 issues fixed (docker-compose.override.yml.example, File List, DOM order test)

### File List

- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/HealthService.kt (modified)
- backend/gateway-admin/src/main/resources/application.yml (modified)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/HealthServiceTest.kt (modified)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/HealthControllerIntegrationTest.kt (modified)
- docker-compose.override.yml.example (modified)
- frontend/admin-ui/src/features/metrics/components/HealthCheckSection.tsx (modified)
- frontend/admin-ui/src/features/metrics/components/HealthCheckSection.test.tsx (modified)
- _bmad-output/implementation-artifacts/sprint-status.yaml (modified)
