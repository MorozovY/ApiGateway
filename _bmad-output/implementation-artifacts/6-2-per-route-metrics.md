# Story 6.2: Per-Route Metrics

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **DevOps Engineer**,
I want metrics broken down by route,
so that I can identify which specific routes have issues (FR18).

## Acceptance Criteria

**AC1 — Метрики содержат route-specific labels:**

**Given** запросы проходят через gateway
**When** метрики записываются
**Then** каждая метрика включает labels:
- `route_id` — UUID маршрута
- `route_path` — path pattern (e.g., `/api/orders`)
- `upstream_host` — hostname upstream-сервиса
- `method` — HTTP метод (GET, POST, etc.)
- `status` — категория статус-кода (2xx, 4xx, 5xx)

**AC2 — Prometheus queries по route_path работают:**

**Given** Prometheus scraping метрики
**When** выполняется query по конкретному маршруту
**Then** `gateway_request_duration_seconds{route_path="/api/orders"}` возвращает метрики только для этого маршрута
**And** фильтрация по любому label работает корректно

**AC3 — Path normalization для контроля cardinality:**

**Given** маршрут `/api/orders/{id}` получает запросы
**When** запрос на `/api/orders/123`, `/api/orders/456`
**Then** `route_path` label нормализуется к паттерну `/api/orders/{id}`
**And** cardinality остаётся контролируемой (один путь = один label)

**AC4 — Агрегация по upstream_host:**

**Given** множество маршрутов к разным upstream-сервисам
**When** выполняется query `sum(rate(gateway_requests_total[5m])) by (upstream_host)`
**Then** результат показывает распределение трафика по upstream-сервисам
**And** hostname извлекается корректно из полного URL

**AC5 — Unknown route fallback:**

**Given** запрос не соответствует ни одному маршруту
**When** метрики записываются
**Then** `route_id="unknown"` и `route_path="unknown"`
**And** метрика всё равно записывается для visibility

## Tasks / Subtasks

- [x] Task 1: Обновить MetricsFilter для добавления labels (AC1, AC5)
  - [x] Извлечь route_id из exchange.getAttribute(GATEWAY_ROUTE_ATTR)
  - [x] Извлечь route_path из Route.predicate или exchange.request.path
  - [x] Извлечь upstream_host из Route.uri
  - [x] Добавить fallback "unknown" для route без match
  - [x] Добавить labels во все метрики: counter, histogram

- [x] Task 2: Реализовать path normalization (AC3)
  - [x] Создать `PathNormalizer.kt` utility class
  - [x] Заменить path parameters на placeholders: `/api/orders/123` → `/api/orders/{id}`
  - [x] Поддержать UUID формат: `/users/abc-123-def` → `/users/{uuid}`
  - [x] Поддержать числовые ID: `/items/42` → `/items/{id}`
  - [x] Добавить configuration для custom patterns (опционально — не реализовано, базовые patterns достаточны)

- [x] Task 3: Извлечение upstream_host (AC4)
  - [x] Создать helper `extractUpstreamHost(uri: URI): String`
  - [x] Извлечь host:port из upstream URL
  - [x] Обработать edge cases: null URI, malformed URL

- [x] Task 4: Unit тесты для path normalization
  - [x] Тест: `/api/orders/123` → `/api/orders/{id}`
  - [x] Тест: `/users/abc-def-123` → `/users/{uuid}`
  - [x] Тест: `/static/file.txt` → `/static/file.txt` (без изменений)
  - [x] Тест: `/api/v1/items/42/details` → `/api/v1/items/{id}/details`

- [x] Task 5: Integration тесты для route metrics
  - [x] Тест: метрики содержат правильные labels
  - [x] Тест: query по route_path возвращает filtered результат
  - [x] Тест: unknown route записывает метрику с fallback labels

## Dev Notes

### Связь с Story 6.1

Story 6.1 создала базовый MetricsFilter. Story 6.2 расширяет его:
- **Добавляем labels** к существующим метрикам
- **Path normalization** — новая функциональность
- **upstream_host extraction** — новая функциональность

### Текущий MetricsFilter (из Story 6.1)

**gateway-core/src/main/kotlin/com/company/gateway/core/filter/MetricsFilter.kt:**
```kotlin
@Component
class MetricsFilter(
    private val meterRegistry: MeterRegistry
) : GlobalFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val startTime = System.nanoTime()
        return chain.filter(exchange)
            .doFinally { recordMetrics(exchange, startTime) }
    }

    private fun recordMetrics(exchange: ServerWebExchange, startTime: Long) {
        val statusCode = exchange.response.statusCode?.value() ?: 0
        val duration = (System.nanoTime() - startTime) / 1_000_000_000.0

        val routeId = exchange.getAttribute<Route>(GATEWAY_ROUTE_ATTR)?.id ?: "unknown"
        val method = exchange.request.method?.name() ?: "UNKNOWN"

        // Метрики с базовыми labels
        meterRegistry.counter(
            "gateway_requests_total",
            "route_id", routeId,
            "method", method,
            "status", statusCategory(statusCode)
        ).increment()

        // ... остальной код
    }
}
```

### Расширение MetricsFilter (для Story 6.2)

```kotlin
private fun recordMetrics(exchange: ServerWebExchange, startTime: Long) {
    val statusCode = exchange.response.statusCode?.value() ?: 0
    val duration = (System.nanoTime() - startTime) / 1_000_000_000.0

    // Извлечение route info
    val route = exchange.getAttribute<Route>(GATEWAY_ROUTE_ATTR)
    val routeId = route?.id ?: "unknown"
    val routePath = route?.let { extractRoutePath(it, exchange) } ?: "unknown"
    val upstreamHost = route?.uri?.let { extractUpstreamHost(it) } ?: "unknown"
    val method = exchange.request.method?.name() ?: "UNKNOWN"

    // Нормализация пути
    val normalizedPath = PathNormalizer.normalize(routePath)

    // Полный набор labels
    val tags = Tags.of(
        "route_id", routeId,
        "route_path", normalizedPath,
        "upstream_host", upstreamHost,
        "method", method,
        "status", statusCategory(statusCode)
    )

    meterRegistry.counter("gateway_requests_total", tags).increment()

    Timer.builder("gateway_request_duration_seconds")
        .tags(tags)
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(duration, TimeUnit.SECONDS)

    if (statusCode >= 400) {
        meterRegistry.counter(
            "gateway_errors_total",
            tags.and("error_type", classifyError(statusCode))
        ).increment()
    }
}

private fun extractRoutePath(route: Route, exchange: ServerWebExchange): String {
    // Приоритет: route predicate path > request path
    return route.predicate.toString()
        .let { extractPathFromPredicate(it) }
        ?: exchange.request.path.value()
}

private fun extractUpstreamHost(uri: URI): String {
    return try {
        "${uri.host}:${if (uri.port > 0) uri.port else 80}"
    } catch (e: Exception) {
        "unknown"
    }
}
```

### PathNormalizer Implementation

**gateway-core/src/main/kotlin/com/company/gateway/core/util/PathNormalizer.kt:**
```kotlin
object PathNormalizer {
    // UUID pattern: 8-4-4-4-12 hex characters
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    // Numeric ID pattern
    private val NUMERIC_ID_PATTERN = Regex("\\d+")

    /**
     * Нормализует path, заменяя динамические сегменты на placeholders.
     *
     * Примеры:
     * - /api/orders/123 → /api/orders/{id}
     * - /users/abc-123-def-456-789 → /users/{uuid}
     * - /static/image.png → /static/image.png (без изменений)
     */
    fun normalize(path: String): String {
        return path.split("/")
            .joinToString("/") { segment ->
                when {
                    segment.isEmpty() -> segment
                    UUID_PATTERN.matches(segment) -> "{uuid}"
                    NUMERIC_ID_PATTERN.matches(segment) -> "{id}"
                    else -> segment
                }
            }
    }
}
```

### Prometheus Queries Examples

После реализации можно использовать следующие queries:

```promql
# RPS по конкретному маршруту
rate(gateway_requests_total{route_path="/api/orders"}[5m])

# Latency percentiles по маршруту
histogram_quantile(0.95, rate(gateway_request_duration_seconds_bucket{route_path="/api/orders"}[5m]))

# Распределение трафика по upstream
sum(rate(gateway_requests_total[5m])) by (upstream_host)

# Error rate по маршруту
sum(rate(gateway_errors_total{route_path="/api/users"}[5m])) / sum(rate(gateway_requests_total{route_path="/api/users"}[5m]))

# Top 10 маршрутов по RPS
topk(10, sum(rate(gateway_requests_total[5m])) by (route_path))
```

### Cardinality Considerations

**Риски high cardinality:**
- Path parameters без нормализации: `/orders/1`, `/orders/2`, ... → миллионы уникальных labels
- Query parameters в path (не рекомендуется)

**Mitigation:**
- PathNormalizer заменяет динамические сегменты
- Не включать query parameters в labels
- route_id — стабильный UUID, не меняется для route

**Estimated label cardinality:**
- `route_id`: ~500 уникальных значений (по количеству routes)
- `route_path`: ~500 уникальных значений (нормализованные paths)
- `upstream_host`: ~20-50 уникальных значений
- `method`: 5 (GET, POST, PUT, DELETE, PATCH)
- `status`: 4 (2xx, 4xx, 5xx, unknown)

**Total combinations:** ~500 × 50 × 5 × 4 = ~500K потенциальных series
В реальности значительно меньше из-за sparse coverage.

### Project Structure Notes

**Новые файлы:**
- `gateway-core/src/main/kotlin/com/company/gateway/core/util/PathNormalizer.kt`

**Модифицируемые файлы:**
- `gateway-core/src/main/kotlin/com/company/gateway/core/filter/MetricsFilter.kt` — добавление labels

### Testing Commands

```bash
# Unit тесты PathNormalizer
./gradlew :gateway-core:test --tests "*PathNormalizer*"

# Integration тесты метрик
./gradlew :gateway-core:test --tests "*MetricsFilter*"

# Проверка labels в prometheus output
curl http://localhost:8080/actuator/prometheus | grep route_path

# Проверка конкретного маршрута
curl http://localhost:8080/actuator/prometheus | grep 'route_path="/api/orders"'
```

### References

- [Source: planning-artifacts/epics.md#Story-6.2] — Story requirements
- [Source: implementation-artifacts/6-1-metrics-collection-micrometer.md] — Previous story, base MetricsFilter
- [Micrometer Tags Documentation](https://micrometer.io/docs/concepts#_tag_naming)
- [Prometheus Cardinality Best Practices](https://prometheus.io/docs/practices/naming/#labels)

### Git Context

**Последние коммиты:**
```
586ace5 docs: Epic 5 retrospective & cleanup E2E users in global-setup
d6bfb63 feat: implement Story 5.9 — Fix E2E Rate Limits Table Refetch
89f9f72 feat: implement Story 5.8 & 5.10 — E2E Gateway Cache Sync & Routing Path Fix
```

**Паттерн коммита:** `feat: implement Story 6.2 — Per-Route Metrics`

### Dependencies

- **Story 6.1** (Metrics Collection with Micrometer) — **MUST be completed first**
- MetricsFilter и базовые метрики должны существовать

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Integration tests в `GatewayRoutingIntegrationTest` имеют существующие провалы (5/8 failed), не связанные с изменениями Story 6.2

### Completion Notes List

- ✅ Реализован PathNormalizer для нормализации путей с поддержкой UUID и числовых ID
- ✅ MetricsFilter расширен новыми labels: route_path, upstream_host
- ✅ Все метрики (counter, timer, errors) теперь содержат полный набор из 5 labels
- ✅ Fallback "unknown" для запросов без matched route
- ✅ extractUpstreamHost корректно обрабатывает HTTP/HTTPS с портами по умолчанию
- ✅ 25 новых unit тестов для PathNormalizer (все проходят)
- ✅ 15 новых unit тестов для MetricsFilter Story 6.2 labels (все проходят)
- ✅ PrometheusEndpointTest расширен тестами для Story 6.2

### File List

**Новые файлы:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/util/PathNormalizer.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/util/PathNormalizerTest.kt`

**Изменённые файлы:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/MetricsFilter.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/MetricsFilterTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/actuator/PrometheusEndpointTest.kt`

### Change Log

- 2026-02-20: Story 6.2 implementation complete — per-route metrics with path normalization

