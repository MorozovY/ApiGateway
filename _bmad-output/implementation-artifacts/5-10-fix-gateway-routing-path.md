# Story 5.10: Fix Gateway Routing Path Handling

Status: done

## Story

As a **Developer**,
I want Gateway to correctly proxy requests to upstream services preserving the configured path,
so that routes work as expected and E2E tests pass.

## Problem Statement

Текущая проблема: Spring Cloud Gateway заменяет path из upstream URL на request path.

**Пример:**
- Маршрут: `path=/api/orders`, `upstreamUrl=http://orders-service/v1/orders`
- Запрос: `GET /api/orders/123`
- Ожидание: `GET http://orders-service/v1/orders/123`
- Реальность: `GET http://orders-service/api/orders/123` (path из upstream URL потерян!)

**Root Cause:**
Spring Cloud Gateway по умолчанию использует request path при проксировании. Upstream URL используется только для host:port.

**Влияние:**
- E2E тест Story 5.8 AC3 не может пройти — httpbin.org возвращает 404
- Любые маршруты с upstream path (например `/v1/api`) не работают корректно

## Acceptance Criteria

**AC1 — Request path корректно мапится на upstream:**

**Given** Маршрут с `path=/gateway-path` и `upstreamUrl=http://service/upstream-path`
**When** Запрос приходит на `/gateway-path/resource`
**Then** Gateway проксирует на `http://service/upstream-path/resource`

**AC2 — Exact path matching работает:**

**Given** Маршрут с `path=/exact` и `upstreamUrl=http://service/target`
**When** Запрос приходит на `/exact`
**Then** Gateway проксирует на `http://service/target`

**AC3 — E2E тест rate limiting проходит:**

**Given** E2E тест Story 5.8 AC3
**When** Тест создаёт маршрут с `upstreamUrl=http://httpbin.org/anything`
**Then** Gateway проксирует на `http://httpbin.org/anything/...`
**And** httpbin.org возвращает 200
**And** Тест проходит

## Tasks / Subtasks

- [x] Task 1: Добавить RewritePath filter в DynamicRouteLocator (AC1, AC2)
  - [x] Вычислять relative path (request path минус route path)
  - [x] Добавлять relative path к upstream URL path
  - [x] Использовать кастомный GatewayFilter с OrderedGatewayFilter
  - [x] **Ключевое решение**: filter с order=10001 запускается ПОСЛЕ RouteToRequestUrlFilter (order=10000) и модифицирует GATEWAY_REQUEST_URL_ATTR напрямую

- [x] Task 2: Обработать edge cases (AC2)
  - [x] Exact match (request path == route path) → relativePath = ""
  - [x] Trailing slash handling — убираем двойные слэши
  - [x] Empty upstream path → используем relativePath или "/"

- [x] Task 3: Unit тесты для path rewriting
  - [x] Тесты покрыты в DynamicRouteLocatorTest.kt

- [x] Task 4: Запустить E2E тест Story 5.8 AC3 (AC3)
  - [x] Тест "Rate limiting применяется в Gateway" проходит
  - [x] Обновить статус Story 5.8 → done

## Dev Notes

### Текущий код DynamicRouteLocator

```kotlin
Route.async()
    .id(dbRoute.id!!.toString())
    .uri(URI.create(dbRoute.upstreamUrl))  // ← Только host:port используется
    .predicate { exchange -> ... }
    .build()
```

### Решение: Добавить RewritePath filter

```kotlin
Route.async()
    .id(dbRoute.id!!.toString())
    .uri(URI.create(dbRoute.upstreamUrl))
    .predicate { exchange -> ... }
    .filter { exchange, chain ->
        // Вычисляем новый path
        val requestPath = exchange.request.path.value()
        val routePath = dbRoute.path
        val upstreamPath = URI.create(dbRoute.upstreamUrl).path ?: ""

        // relative = requestPath - routePath prefix
        val relativePath = if (requestPath.startsWith(routePath)) {
            requestPath.substring(routePath.length)
        } else {
            requestPath
        }

        // newPath = upstreamPath + relativePath
        val newPath = upstreamPath + relativePath

        // Создаём новый request с изменённым path
        val newRequest = exchange.request.mutate()
            .path(newPath)
            .build()

        chain.filter(exchange.mutate().request(newRequest).build())
    }
    .build()
```

### Альтернатива: Использовать встроенный RewritePath

```kotlin
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory

// В конфигурации
val rewriteFilter = RewritePathGatewayFilterFactory()
    .apply(RewritePathGatewayFilterFactory.Config()
        .setRegexp("${dbRoute.path}(?<segment>.*)")
        .setReplacement("${upstreamPath}\${segment}"))

Route.async()
    ...
    .filter(rewriteFilter)
    .build()
```

### References

- [Source: gateway-core/src/main/kotlin/.../route/DynamicRouteLocator.kt] — текущая логика
- [Spring Cloud Gateway RewritePath](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#the-rewritepath-gatewayfilter-factory)
- [Story 5.8 AC3 blocker] — E2E тест не проходит из-за 404 от upstream

## Git Context

**Паттерн коммита:** `feat: implement Story 5.10 — Fix Gateway Routing Path Handling`
