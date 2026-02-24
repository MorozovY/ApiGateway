# Story 12.4: Gateway Core — JWT Authentication Filter

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **System**,
I want Gateway Core to validate JWT tokens for protected routes,
So that only authenticated consumers can access protected APIs (FR35, FR36, FR40, FR41).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** JWT аутентификация на уровне Gateway Core обеспечивает защиту upstream сервисов от неавторизованных запросов. Маршруты могут быть публичными (без токена) или защищёнными (требуют валидный JWT от Keycloak).

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅
- Story 12.2 (Admin UI Keycloak Auth Migration) — DONE ✅
- Story 12.3 (Gateway Admin Keycloak JWT Validation) — DONE ✅

**Blocked By This Story:**
- Story 12.5 (Consumer Identity Filter) — использует consumer_id из JWT claims
- Story 12.6 (Multi-tenant Metrics) — использует consumer_id в метриках

## Acceptance Criteria

### AC1: Protected Route Without Token — 401 Unauthorized
**Given** route with `auth_required = true`
**When** request without Authorization header
**Then** response returns HTTP 401 Unauthorized
**And** response includes `WWW-Authenticate: Bearer` header
**And** response body в формате RFC 7807

### AC2: Protected Route With Valid JWT — Forward to Upstream
**Given** route with `auth_required = true`
**When** request with valid JWT token from Keycloak
**Then** request is forwarded to upstream
**And** consumer_id is extracted from JWT `azp` claim
**And** consumer_id доступен в exchange attributes

### AC3: Public Route Without Token — Forward to Upstream
**Given** route with `auth_required = false`
**When** request without Authorization header
**Then** request is forwarded to upstream (public route)
**And** consumer_id fallback to header or "anonymous"

### AC4: Consumer Whitelist — 403 Forbidden
**Given** route with `allowed_consumers = ["company-a", "company-b"]`
**When** request from consumer "company-c"
**Then** response returns HTTP 403 Forbidden
**And** detail: "Consumer not allowed for this route"
**And** response body в формате RFC 7807

### AC5: Consumer Whitelist Empty — Allow All
**Given** route with `allowed_consumers = null` (no restriction)
**When** request from any authenticated consumer
**Then** request is allowed

### AC6: JWKS Caching — Graceful Degradation
**Given** Keycloak is temporarily unavailable
**When** JWKS is cached
**Then** JWT validation continues using cached keys
**And** warning is logged about Keycloak unavailability

### AC7: Invalid/Expired JWT — 401 Unauthorized
**Given** request with invalid or expired JWT token
**When** request is made to protected endpoint
**Then** response returns HTTP 401 Unauthorized
**And** response body в формате RFC 7807

## Tasks / Subtasks

- [x] Task 0: Pre-flight Checklist (PA-09)
  - [x] 0.1 Проверить что gateway-core запускается и маршрутизация работает
  - [x] 0.2 Проверить что Keycloak запущен (`docker-compose ps keycloak`)
  - [x] 0.3 Feature flag `keycloak.enabled=false` по умолчанию — добавить в application.yml

- [x] Task 1: Database Migration — Route Auth Fields
  - [x] 1.1 Создать `V10__add_route_auth_fields.sql` в gateway-admin (flyway)
  - [x] 1.2 `ALTER TABLE routes ADD COLUMN auth_required BOOLEAN NOT NULL DEFAULT true`
  - [x] 1.3 `ALTER TABLE routes ADD COLUMN allowed_consumers TEXT[] DEFAULT NULL`
  - [x] 1.4 Создать индекс `idx_routes_auth_required`

- [x] Task 2: Update Route Entity
  - [x] 2.1 Добавить поля `authRequired` и `allowedConsumers` в `Route.kt` (gateway-common)
  - [x] 2.2 @Column("auth_required") для snake_case mapping
  - [x] 2.3 @Column("allowed_consumers") для array mapping

- [x] Task 3: Add OAuth2 Resource Server Dependency
  - [x] 3.1 Добавить `spring-boot-starter-oauth2-resource-server` в build.gradle.kts
  - [x] 3.2 Входит в starter (oauth2-jose включён автоматически)

- [x] Task 4: KeycloakProperties (gateway-core)
  - [x] 4.1 Создать `KeycloakProperties.kt` в properties/
  - [x] 4.2 Добавить keycloak.* properties в application.yml

- [x] Task 5: JwtAuthenticationFilter (AC: #1, #2, #3, #7)
  - [x] 5.1 Создать `JwtAuthenticationFilter.kt` в filter/
  - [x] 5.2 Implements GlobalFilter, Ordered (order = HIGHEST_PRECEDENCE + 5)
  - [x] 5.3 Получить route authRequired из exchange.attributes
  - [x] 5.4 Если authRequired=false и нет токена — chain.filter() (public route)
  - [x] 5.5 Если authRequired=true и нет токена — return 401
  - [x] 5.6 Validate JWT через ReactiveJwtDecoder
  - [x] 5.7 Извлечь consumer_id из `azp` claim (fallback: `clientId`)
  - [x] 5.8 Сохранить consumer_id в exchange.attributes["gateway.consumerId"]

- [x] Task 6: Consumer Whitelist Check (AC: #4, #5)
  - [x] 6.1 В JwtAuthenticationFilter проверить allowedConsumers из route
  - [x] 6.2 Если allowedConsumers != null && consumerId not in list — return 403
  - [x] 6.3 RFC 7807 error response для 403

- [x] Task 7: JWKS Decoder Configuration (AC: #6)
  - [x] 7.1 Создать `KeycloakJwtConfig.kt` с ReactiveJwtDecoder bean
  - [x] 7.2 NimbusReactiveJwtDecoder с jwkSetUri
  - [x] 7.3 Multi-issuer validator (localhost/host.docker.internal/keycloak)
  - [x] 7.4 @ConditionalOnProperty для feature flag

- [x] Task 8: Update DynamicRouteLocator
  - [x] 8.1 Добавить authRequired и allowedConsumers в exchange.attributes
  - [x] 8.2 Аналогично rateLimitId — загрузка из dbRoute

- [x] Task 9: RFC 7807 Error Handler
  - [x] 9.1 Создать вспомогательный метод для 401/403 responses
  - [x] 9.2 Content-Type: application/problem+json
  - [x] 9.3 Включить correlationId из exchange.attributes

- [x] Task 10: Unit Tests
  - [x] 10.1 JwtAuthenticationFilterTest — все сценарии AC1-AC7
  - [x] 10.2 Тест с public route (authRequired=false)
  - [x] 10.3 Тест с protected route и valid JWT
  - [x] 10.4 Тест с protected route и без токена (401)
  - [x] 10.5 Тест с consumer whitelist (403)
  - [x] 10.6 Тест с invalid/expired JWT (401)

- [x] Task 11: Integration Tests
  - [x] 11.1 Существующие интеграционные тесты проходят с новыми колонками
  - [x] 11.2 259 тестов gateway-core pass, 798 тестов gateway-admin pass

- [x] Task 12: Manual Verification
  - [x] 12.1 Smoke test с feature flag OFF — маршрутизация работает без JWT ✅
  - [x] 12.2 Smoke test с feature flag ON — protected route требует JWT ✅
  - [x] 12.3 Проверить consumer whitelist — 403 для company-a не в whitelist ✅
  - [x] 12.4 Проверить 401/403 RFC 7807 формат ✅

## API Dependencies Checklist

**Backend API — Route entity changes:**

| Field | Type | Default | DB Column |
|-------|------|---------|-----------|
| `authRequired` | Boolean | true | `auth_required` |
| `allowedConsumers` | List<String>? | null | `allowed_consumers` |

**Keycloak endpoints (внешние):**

| Endpoint | Purpose | Статус |
|----------|---------|--------|
| `{KEYCLOAK_URL}/realms/api-gateway/protocol/openid-connect/certs` | JWKS | ✅ Story 12.1 |

**ВАЖНО:** В этой story НЕ изменяется Route API в gateway-admin. Поля authRequired/allowedConsumers будут добавлены в gateway-admin API в Story 12.7 (Route Authentication Configuration).

## Dev Notes

### Filter Order в Gateway Core

```
CorrelationIdFilter     (HIGHEST_PRECEDENCE)      — генерация X-Correlation-ID
JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 5)  — валидация JWT ← ЭТА STORY
RateLimitFilter         (order = 10)              — rate limiting
MetricsFilter           (order = 100)             — метрики
LoggingFilter           (LOWEST_PRECEDENCE - 1)   — logging
```

### Отличие от Gateway Admin

**Gateway Admin** использует Spring Security OAuth2 Resource Server с ServerHttpSecurity:
```kotlin
http.oauth2ResourceServer { oauth2 ->
    oauth2.jwt { ... }
}
```

**Gateway Core** использует кастомный GlobalFilter, потому что:
1. Spring Cloud Gateway использует свою filter chain (GatewayFilterChain)
2. Нужен доступ к route-specific настройкам (authRequired per route)
3. Routing выполняется ДО Spring Security filter chain

### JWT Consumer Identity Extraction

```kotlin
// Приоритет извлечения consumer_id:
val consumerId = jwt.claims["azp"]?.toString()  // Authorized Party
    ?: jwt.claims["clientId"]?.toString()       // Fallback
    ?: "unknown"
```

**Пример API Consumer JWT (client_credentials):**
```json
{
  "iss": "http://localhost:8180/realms/api-gateway",
  "sub": "service-account-company-a",
  "azp": "company-a",           // ← consumer_id
  "clientId": "company-a",
  "realm_access": {
    "roles": ["api:consumer"]
  }
}
```

### Route Auth Attributes

DynamicRouteLocator устанавливает атрибуты при match:
```kotlin
exchange.attributes["gateway.routeId"] = dbRoute.id          // Уже есть
exchange.attributes["gateway.rateLimit"] = rateLimit         // Уже есть
exchange.attributes["gateway.authRequired"] = dbRoute.authRequired   // НОВОЕ
exchange.attributes["gateway.allowedConsumers"] = dbRoute.allowedConsumers  // НОВОЕ
```

### RFC 7807 Error Response Format

```json
{
  "type": "https://api.gateway/errors/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Missing or invalid JWT token",
  "instance": "/api/orders/123",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Database Migration (V11)

```sql
-- V11__add_route_auth_fields.sql
-- Добавляем поля для аутентификации маршрутов

ALTER TABLE routes
ADD COLUMN auth_required BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE routes
ADD COLUMN allowed_consumers TEXT[] DEFAULT NULL;

COMMENT ON COLUMN routes.auth_required IS 'Требуется ли JWT аутентификация для маршрута';
COMMENT ON COLUMN routes.allowed_consumers IS 'Whitelist consumer IDs (NULL = все разрешены)';

CREATE INDEX idx_routes_auth_required ON routes(auth_required);
```

### Feature Flag Configuration

```yaml
# application.yml (gateway-core)
keycloak:
  enabled: ${KEYCLOAK_ENABLED:false}  # По умолчанию ВЫКЛЮЧЕН
  url: ${KEYCLOAK_URL:http://localhost:8180}
  realm: api-gateway
```

### JwtAuthenticationFilter Implementation

```kotlin
@Component
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class JwtAuthenticationFilter(
    private val jwtDecoder: ReactiveJwtDecoder
) : GlobalFilter, Ordered {

    companion object {
        const val CONSUMER_ID_ATTRIBUTE = "gateway.consumerId"
        const val AUTH_REQUIRED_ATTRIBUTE = "gateway.authRequired"
        const val ALLOWED_CONSUMERS_ATTRIBUTE = "gateway.allowedConsumers"
    }

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 5

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val authRequired = exchange.getAttribute<Boolean>(AUTH_REQUIRED_ATTRIBUTE) ?: true
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

        // Public route без токена — пропускаем
        if (!authRequired && authHeader == null) {
            exchange.attributes[CONSUMER_ID_ATTRIBUTE] = "anonymous"
            return chain.filter(exchange)
        }

        // Protected route без токена — 401
        if (authRequired && authHeader == null) {
            return unauthorized(exchange, "Missing Authorization header")
        }

        // Валидация JWT
        val token = authHeader?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrBlank()) {
            return unauthorized(exchange, "Invalid Authorization header format")
        }

        return jwtDecoder.decode(token)
            .flatMap { jwt ->
                val consumerId = extractConsumerId(jwt)
                val allowedConsumers = exchange.getAttribute<List<String>>(ALLOWED_CONSUMERS_ATTRIBUTE)

                // Проверка whitelist
                if (allowedConsumers != null && consumerId !in allowedConsumers) {
                    return@flatMap forbidden(exchange, "Consumer not allowed for this route")
                }

                exchange.attributes[CONSUMER_ID_ATTRIBUTE] = consumerId
                chain.filter(exchange)
            }
            .onErrorResume { e ->
                unauthorized(exchange, "Invalid JWT: ${e.message}")
            }
    }

    private fun extractConsumerId(jwt: Jwt): String {
        return jwt.claims["azp"]?.toString()
            ?: jwt.claims["clientId"]?.toString()
            ?: "unknown"
    }
}
```

### Previous Story Intelligence

Из Story 12.3:
- KeycloakProperties уже реализован в gateway-admin — можно скопировать
- Multi-issuer validator для Docker/localhost — обязателен
- KeycloakGrantedAuthoritiesConverter не нужен для gateway-core (нет ролей admin-ui:*)
- RFC 7807 error response pattern уже есть в RateLimitFilter

Из Story 12.1:
- JWKS endpoint: `http://localhost:8180/realms/api-gateway/protocol/openid-connect/certs`
- Тестовые consumers: company-a, company-b, company-c
- JWT подписан RS256 (asymmetric)

### Critical Constraints

1. **Feature flag OFF по умолчанию** — gateway работает без JWT валидации
2. **authRequired=true по умолчанию** — новые маршруты защищены
3. **НЕ модифицировать Route API** — это Story 12.7
4. **RFC 7807 для всех ошибок** — consistency
5. **JWKS caching обязателен** — Keycloak может быть недоступен
6. **consumer_id в exchange.attributes** — Story 12.5 использует его

### File Structure

```
backend/gateway-core/src/main/kotlin/com/company/gateway/core/
├── config/
│   └── KeycloakJwtConfig.kt        # NEW: JWT decoder bean
├── filter/
│   └── JwtAuthenticationFilter.kt  # NEW: JWT validation filter
├── properties/
│   └── KeycloakProperties.kt       # NEW: Keycloak config
└── route/
    └── DynamicRouteLocator.kt      # MODIFY: add auth attributes

backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/
└── Route.kt                        # MODIFY: add authRequired, allowedConsumers

backend/gateway-admin/src/main/resources/db/migration/
└── V11__add_route_auth_fields.sql  # NEW: database migration
```

### Testing Strategy

1. **Unit Tests:**
   - JwtAuthenticationFilter — все комбинации AC
   - Mock ReactiveJwtDecoder для isolated testing

2. **Integration Tests:**
   - MockWebServer для JWKS endpoint
   - Full filter chain с test route

3. **Manual Testing:**
   - Feature flag OFF → маршрутизация работает
   - Feature flag ON → protected routes требуют JWT
   - Consumer whitelist → 403 для неавторизованных

### Environment Variables

```bash
# docker-compose.yml / .env
KEYCLOAK_ENABLED=false              # Feature flag — ВЫКЛЮЧЕН по умолчанию
KEYCLOAK_URL=http://keycloak:8080   # Для Docker internal network
```

### Project Structure Notes

- Все новые файлы в существующих директориях
- KeycloakProperties копируется из gateway-admin (DRY нарушение принято — два сервиса)
- Route.kt в gateway-common — общий для gateway-core и gateway-admin

### References

- [Source: architecture.md#Gateway Core Filter Chain (Phase 2)]
- [Source: architecture.md#JwtAuthenticationFilter]
- [Source: architecture.md#Route Authentication Configuration]
- [Source: epics.md#Story 12.4]
- [Source: 12-3-gateway-admin-keycloak-jwt-validation.md] — аналогичная реализация для gateway-admin
- [Spring Cloud Gateway GlobalFilter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/global-filters.html)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — реализация прошла без проблем.

### Completion Notes List

1. **JWT Authentication Filter** — создан `JwtAuthenticationFilter.kt` как GlobalFilter с order HIGHEST_PRECEDENCE + 5. Валидация JWT через ReactiveJwtDecoder, извлечение consumer_id из azp/clientId claims.

2. **Consumer Whitelist** — реализована проверка allowed_consumers per route. Если consumer не в whitelist — 403 Forbidden с RFC 7807.

3. **RFC 7807 Error Responses** — все ошибки (401, 403) возвращаются в формате Problem Details с correlationId, instance, type, title, detail.

4. **Feature Flag** — `keycloak.enabled=false` по умолчанию для обратной совместимости. При включении gateway-core начинает валидировать JWT токены.

5. **Multi-Issuer Support** — поддержка трёх issuer URL для работы в Docker и локально (localhost:8180, host.docker.internal:8180, keycloak:8080).

6. **Database Migration V10** — добавлены колонки `auth_required` (BOOLEAN DEFAULT true) и `allowed_consumers` (TEXT[] DEFAULT NULL).

7. **Unit Tests** — 21 тест в JwtAuthenticationFilterTest покрывают все AC1-AC7 (включая edge cases).

8. **Manual Verification** — все smoke tests пройдены:
   - Feature flag OFF: маршрутизация работает без JWT
   - Feature flag ON: protected route требует JWT
   - Consumer whitelist: 403 для неавторизованных consumers
   - Public routes (auth_required=false): работают без токена

### Change Log

- 2026-02-24: Code Review — добавлено 4 теста (AC6, AC3+, AC3++, whitespace token), исправлен RFC 7807 content-type в RateLimitFilter
- 2026-02-24: Story 12.4 implemented — JWT Authentication Filter для Gateway Core

### File List

**New Files:**
- `backend/gateway-admin/src/main/resources/db/migration/V10__add_route_auth_fields.sql`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/properties/KeycloakProperties.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/config/KeycloakJwtConfig.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/JwtAuthenticationFilter.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/JwtAuthenticationFilterTest.kt`
- `backend/gateway-core/src/test/resources/db/migration/V10__add_route_auth_fields.sql`

**Modified Files:**
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt` — добавлены authRequired, allowedConsumers
- `backend/gateway-core/build.gradle.kts` — добавлена зависимость oauth2-resource-server
- `backend/gateway-core/src/main/resources/application.yml` — добавлена keycloak.* конфигурация
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/GatewayApplication.kt` — EnableConfigurationProperties
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt` — установка auth атрибутов
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt` — [Code Review] исправлен content-type на APPLICATION_PROBLEM_JSON для RFC 7807 консистентности
- `docker-compose.override.yml` — добавлены KEYCLOAK_* env vars для gateway-core (не в git)
