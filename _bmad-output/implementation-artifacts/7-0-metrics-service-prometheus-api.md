# Story 7.0: MetricsService — Prometheus HTTP API Integration

Status: in-progress

## Story

As a **DevOps Engineer**,
I want the Admin UI metrics to display real gateway-core data via Prometheus,
so that I see actual traffic metrics instead of empty values.

## Problem Statement

**Текущая проблема:**
- MetricsService в gateway-admin читает метрики из **локального MeterRegistry**
- Метрики собираются в **gateway-core** (MetricsFilter)
- Два сервиса — две отдельные registry
- **Результат:** Admin UI показывает пустые метрики, хотя в Grafana данные есть

**Root cause (из Epic 6 Retro):**
Story 6.3 создала MetricsService с упрощением для MVP — чтение из локального MeterRegistry. Это архитектурно неверно для multi-service setup.

## Acceptance Criteria

**AC1 — Summary метрики из Prometheus:**

**Given** Prometheus доступен и scraping gateway-core
**When** GET `/api/v1/metrics/summary?period=5m`
**Then** MetricsService query Prometheus HTTP API
**And** возвращает реальные time-series метрики за указанный период
**And** RPS вычисляется через `rate(gateway_requests_total[5m])`
**And** Latency percentiles через `histogram_quantile()`

**AC2 — Top Routes из Prometheus:**

**Given** Prometheus содержит per-route метрики
**When** GET `/api/v1/metrics/top-routes?by=requests&limit=10`
**Then** MetricsService query Prometheus с `topk()` или сортировкой
**And** возвращает top маршрутов по реальному трафику
**And** role-based filtering (Story 6.5.1) продолжает работать

**AC3 — Route Metrics из Prometheus:**

**Given** маршрут с route_id существует
**When** GET `/api/v1/metrics/routes/{routeId}?period=5m`
**Then** MetricsService query Prometheus с фильтром по route_id label
**And** возвращает метрики конкретного маршрута

**AC4 — Graceful Degradation:**

**Given** Prometheus недоступен
**When** любой metrics endpoint вызывается
**Then** возвращается HTTP 503 Service Unavailable
**And** RFC 7807 error с detail "Prometheus is unavailable"
**And** retry-after header с рекомендуемым интервалом

**AC5 — API контракт сохранён:**

**Given** существующие DTO (MetricsSummaryDto, TopRouteDto, RouteMetricsDto)
**When** API вызывается
**Then** формат ответа идентичен текущему
**And** существующие тесты проходят (с обновлёнными mocks)

## Tasks / Subtasks

- [x] Task 1: Добавить Prometheus HTTP client (AC1-AC3) ✅
  - [x] Добавить зависимость для HTTP client (WebClient или RestClient)
  - [x] Создать `PrometheusClient.kt` с методами для query API
  - [x] Настроить URL из application.yml (`prometheus.url`)
  - [x] Добавить timeout и retry configuration

- [x] Task 2: Переработать MetricsService.getSummary() (AC1, AC5) ✅
  - [x] Заменить MeterRegistry на PrometheusClient
  - [x] Построить PromQL queries для каждой метрики:
    - `sum(rate(gateway_requests_total[{period}]))` — RPS
    - `sum(increase(gateway_requests_total[{period}]))` — total requests
    - `histogram_quantile(0.5, sum(rate(gateway_request_duration_seconds_bucket[{period}])) by (le))` — P50
    - `histogram_quantile(0.95, ...)` — P95
    - `histogram_quantile(0.99, ...)` — P99
    - `sum(rate(gateway_errors_total[{period}])) / sum(rate(gateway_requests_total[{period}]))` — error rate
  - [x] Парсить Prometheus response (JSON или text format)
  - [x] Маппить на MetricsSummaryDto

- [x] Task 3: Переработать MetricsService.getTopRoutes() (AC2, AC5) ✅
  - [x] PromQL: `topk({limit}, sum(rate(gateway_requests_total[5m])) by (route_id, route_path))`
  - [x] Или для latency: `topk({limit}, histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (route_id, route_path, le)))`
  - [x] Сохранить role-based filtering (ownerId) — фильтровать результат по route.createdBy
  - [x] Маппить на List<TopRouteDto>

- [x] Task 4: Переработать MetricsService.getRouteMetrics() (AC3, AC5) ✅
  - [x] PromQL с фильтром: `{route_id="{routeId}"}`
  - [x] Получить statusBreakdown через отдельные queries по status label
  - [x] Маппить на RouteMetricsDto

- [x] Task 5: Реализовать Graceful Degradation (AC4) ✅
  - [x] Обработка ConnectException, TimeoutException
  - [x] Создать PrometheusUnavailableException
  - [x] Добавить handler в GlobalExceptionHandler → 503 + RFC 7807
  - [x] Добавить retry-after header

- [x] Task 6: Configuration (AC1-AC4) ✅
  - [x] Добавить в application.yml:
    ```yaml
    prometheus:
      url: http://localhost:9090
      timeout: 5s
      retry:
        max-attempts: 3
        delay: 1s
    ```
  - [x] Добавить в docker-compose.yml: environment variable для production URL

- [x] Task 7: Unit тесты PrometheusClient ✅
  - [x] Тест: query возвращает корректные данные
  - [x] Тест: timeout обрабатывается gracefully
  - [x] Тест: retry при transient errors

- [x] Task 8: Unit тесты MetricsService (обновить существующие) ✅
  - [x] Заменить mock MeterRegistry на mock PrometheusClient
  - [x] Тест: getSummary парсит Prometheus response
  - [x] Тест: getTopRoutes с role filtering работает
  - [x] Тест: Prometheus unavailable → 503

- [x] Task 9: Integration тесты ✅
  - [x] Тест с mock PrometheusClient (TestPrometheusConfig)
  - [x] Тест: реальный query → реальный response
  - [x] Тест: graceful degradation при недоступности

- [x] Task 10: E2E тесты (epic-6.spec.ts) — уже покрыты ✅
  - [x] Проверить что UI показывает числовые значения метрик
  - [x] Auto-refresh работает

## Dev Notes

### Архитектурный контекст

**Текущая архитектура (проблемная):**
```
gateway-core (port 8080)
  └── MetricsFilter → MeterRegistry (core)
                          ↓
                    /actuator/prometheus
                          ↓
                      Prometheus ──→ Grafana ✅

gateway-admin (port 8081)
  └── MetricsService → MeterRegistry (admin) ❌ пустой!
                          ↓
                    /api/v1/metrics/*
                          ↓
                      Admin UI ❌ пустые данные
```

**Целевая архитектура (после Story 7.0):**
```
gateway-core (port 8080)
  └── MetricsFilter → MeterRegistry
                          ↓
                    /actuator/prometheus
                          ↓
                      Prometheus
                          ↓
gateway-admin (port 8081)               ↓
  └── MetricsService → PrometheusClient ──┘
                          ↓
                    /api/v1/metrics/*
                          ↓
                      Admin UI ✅ реальные данные
```

### Prometheus HTTP API

**Endpoint:** `POST /api/v1/query` или `GET /api/v1/query?query={promql}`

**Пример запроса:**
```bash
curl 'http://localhost:9090/api/v1/query?query=sum(rate(gateway_requests_total[5m]))'
```

**Пример ответа:**
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {},
        "value": [1708444800, "42.5"]
      }
    ]
  }
}
```

**Range query (для истории):**
```bash
curl 'http://localhost:9090/api/v1/query_range?query=rate(gateway_requests_total[5m])&start=2024-02-20T10:00:00Z&end=2024-02-20T11:00:00Z&step=60s'
```

### PromQL Queries для MetricsSummaryDto

```promql
# Total Requests (за период)
sum(increase(gateway_requests_total[5m]))

# RPS (requests per second)
sum(rate(gateway_requests_total[5m]))

# Average Latency (ms)
avg(rate(gateway_request_duration_seconds_sum[5m]) / rate(gateway_request_duration_seconds_count[5m])) * 1000

# P50 Latency (ms)
histogram_quantile(0.5, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le)) * 1000

# P95 Latency (ms)
histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le)) * 1000

# P99 Latency (ms)
histogram_quantile(0.99, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le)) * 1000

# Error Rate
sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m]))

# Error Count (за период)
sum(increase(gateway_errors_total[5m]))

# Active Routes (из DB, не из Prometheus)
# Оставить как есть — routeRepository.countByStatus(PUBLISHED)
```

### Period Mapping

| API period | PromQL range |
|------------|--------------|
| 5m | [5m] |
| 15m | [15m] |
| 1h | [1h] |
| 6h | [6h] |
| 24h | [24h] |

### PrometheusClient Interface

```kotlin
interface PrometheusClient {
    /**
     * Выполняет instant query к Prometheus.
     *
     * @param query PromQL запрос
     * @return результат в виде списка пар (metric labels, value)
     */
    fun query(query: String): Mono<PrometheusQueryResult>

    /**
     * Выполняет range query к Prometheus.
     *
     * @param query PromQL запрос
     * @param start начало периода
     * @param end конец периода
     * @param step шаг
     * @return результат с временными рядами
     */
    fun queryRange(query: String, start: Instant, end: Instant, step: Duration): Mono<PrometheusRangeResult>
}

data class PrometheusQueryResult(
    val status: String,
    val data: PrometheusData
)

data class PrometheusData(
    val resultType: String, // "vector", "matrix", "scalar"
    val result: List<PrometheusMetric>
)

data class PrometheusMetric(
    val metric: Map<String, String>,
    val value: Pair<Long, String>? = null,  // для instant query
    val values: List<Pair<Long, String>>? = null  // для range query
)
```

### Reactive Patterns (из CLAUDE.md)

- Использовать WebClient для HTTP запросов (reactive)
- Возвращать Mono<T> / Flux<T>
- Не использовать .block()
- Обрабатывать ошибки через onErrorResume / onErrorMap

### Error Handling

```kotlin
// В PrometheusClient
fun query(query: String): Mono<PrometheusQueryResult> {
    return webClient.get()
        .uri { it.path("/api/v1/query").queryParam("query", query).build() }
        .retrieve()
        .bodyToMono(PrometheusQueryResult::class.java)
        .timeout(Duration.ofSeconds(5))
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .filter { it is WebClientRequestException })
        .onErrorMap(TimeoutException::class.java) {
            PrometheusUnavailableException("Prometheus query timeout", it)
        }
        .onErrorMap(ConnectException::class.java) {
            PrometheusUnavailableException("Cannot connect to Prometheus", it)
        }
}

// В GlobalExceptionHandler
@ExceptionHandler(PrometheusUnavailableException::class)
fun handlePrometheusUnavailable(ex: PrometheusUnavailableException): ResponseEntity<ProblemDetail> {
    val problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        ex.message ?: "Prometheus is unavailable"
    )
    problem.setProperty("retryAfter", 30)
    return ResponseEntity.status(503)
        .header("Retry-After", "30")
        .body(problem)
}
```

### Configuration

**application.yml:**
```yaml
prometheus:
  url: ${PROMETHEUS_URL:http://localhost:9090}
  timeout: 5s
  retry:
    max-attempts: 3
    delay: 1s
```

**docker-compose.yml (добавить в gateway-admin service):**
```yaml
gateway-admin:
  environment:
    - PROMETHEUS_URL=http://prometheus:9090
```

### Testing Strategy

**Unit тесты:**
- Mock PrometheusClient для MetricsService тестов
- WireMock для PrometheusClient тестов

**Integration тесты:**
- Testcontainers с Prometheus (сложно — нужен gateway-core для метрик)
- Или WireMock с pre-recorded responses

**E2E тесты:**
- Убедиться что UI показывает ненулевые значения
- AC3 в epic-6.spec.ts уже проверяет это

### Project Structure Notes

**Новые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/client/PrometheusClient.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/client/PrometheusClientImpl.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/client/dto/PrometheusResponse.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/PrometheusUnavailableException.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/client/PrometheusClientTest.kt`

**Модифицируемые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/MetricsService.kt`
- `backend/gateway-admin/src/main/resources/application.yml`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/MetricsServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/MetricsControllerIntegrationTest.kt`
- `docker-compose.yml` (PROMETHEUS_URL для gateway-admin)

### Dependencies

- **Requires:** Prometheus running (docker-compose --profile monitoring)
- **Requires:** gateway-core running and producing metrics
- **Blocks:** Полноценное использование Metrics UI в Admin Panel

### Git Context

**Последние коммиты:**
```
25b7c81 docs: Epic 6 retrospective & retro-actions.yaml tracker
7c430c4 feat: implement Story 6.6 — E2E Playwright Happy Path Tests for Epic 6
fcab38d fix: correct Grafana dashboard UID in metrics config
```

**Паттерн коммита:**
```
feat: implement Story 7.0 — MetricsService Prometheus HTTP API Integration
```

### References

- [Source: implementation-artifacts/epic-6-retro-2026-02-20.md] — Problem identified
- [Source: implementation-artifacts/6-3-metrics-summary-api.md] — Current MetricsService
- [Source: implementation-artifacts/6-5-1-metrics-api-role-filtering.md] — Role-based filtering to preserve
- [Prometheus HTTP API](https://prometheus.io/docs/prometheus/latest/querying/api/)
- [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

## Change Log

| Date | Change |
|------|--------|
| 2026-02-20 | Story created from Epic 6 Retro action item E6-08 (CRITICAL) |
| 2026-02-20 | Implementation completed: Tasks 1-10 done, all unit tests passing |
