# Story 14.3: Custom Metrics & SLI/SLO Definition

Status: review

## Story

As a **Platform Engineer**,
I want custom domain metrics and defined SLI/SLO targets,
So that I can measure reliability quantitatively and set meaningful alerts.

As a **Developer**,
I want visibility into route changes, approvals, and rate limit events,
So that I can understand system behavior and troubleshoot issues faster.

## Acceptance Criteria

### AC1: Route Management Metrics
**Given** routes are created, updated, or deleted
**When** these operations occur in gateway-admin
**Then** counter metrics are incremented:
  - `gateway_route_operations_total{operation="create|update|delete", status="success|failure"}`
**And** current route count is tracked:
  - `gateway_routes_active_total{status="published|pending|draft|rejected"}`

### AC2: Approval Workflow Metrics
**Given** approval workflow actions occur
**When** route is submitted, approved, or rejected
**Then** counter metrics are incremented:
  - `gateway_approval_actions_total{action="submit|approve|reject", role="developer|security"}`
**And** approval latency is tracked:
  - `gateway_approval_duration_seconds` (histogram: time from PENDING to PUBLISHED/REJECTED)

### AC3: Rate Limit Metrics
**Given** rate limiting is applied to requests
**When** rate limit check occurs in gateway-core
**Then** metrics are recorded:
  - `gateway_ratelimit_decisions_total{decision="allowed|denied", limit_type="route|consumer"}`
  - `gateway_ratelimit_remaining_tokens{route_id, consumer_id}` (gauge, sampled)
**And** cache performance is tracked:
  - `gateway_ratelimit_cache_hits_total{cache="redis|caffeine"}`
  - `gateway_ratelimit_cache_misses_total`

### AC4: Cache Performance Metrics
**Given** route cache operations occur
**When** cache hit/miss happens in RouteCacheManager
**Then** metrics are recorded:
  - `gateway_cache_operations_total{cache="route|ratelimit", result="hit|miss|refresh"}`
  - `gateway_cache_size{cache="route|ratelimit"}` (gauge)
  - `gateway_cache_refresh_duration_seconds` (histogram)

### AC5: SLI Definitions Documented
**Given** metrics are available
**When** SRE reviews reliability
**Then** SLI definitions exist in documentation:
  - **Availability SLI**: `(total_requests - 5xx_errors) / total_requests`
  - **Latency SLI**: `requests with latency < 200ms / total_requests` (P95 target)
  - **Error SLI**: `(total_requests - all_errors) / total_requests`
**And** PromQL queries for each SLI are documented

### AC6: SLO Targets Defined
**Given** SLIs are defined
**When** SLO targets are set
**Then** the following targets are documented:
  - **Availability**: 99.9% (43.8 min downtime/month)
  - **Latency P95**: < 200ms
  - **Latency P99**: < 500ms
  - **Error Rate**: < 1%
**And** error budget calculations are documented

### AC7: Grafana SLO Dashboard
**Given** SLI/SLO targets are defined
**When** dashboard is viewed
**Then** SLO dashboard panels exist:
  - SLO compliance percentage (30-day rolling)
  - Error budget remaining (%)
  - Burn rate (1h, 6h, 24h windows)
  - SLI trend graphs
**And** dashboard is provisioned in infra project

### AC8: Burn Rate Alerts
**Given** SLO targets are defined
**When** error budget burns faster than expected
**Then** alerts fire:
  - `SLOBurnRateHigh` — 14.4x burn rate for 1h (2% budget in 1h)
  - `SLOBurnRateCritical` — 6x burn rate for 6h (10% budget in 6h)
**And** alerts are documented in Prometheus alert rules

## Tasks / Subtasks

- [x] Task 1: Add Route Management Metrics (AC: 1)
  - [x] 1.1 Add `gateway_route_operations_total` counter in RouteService
  - [x] 1.2 Add `gateway_routes_active_total` gauge updated on route changes
  - [x] 1.3 Add tests for route metrics
- [x] Task 2: Add Approval Workflow Metrics (AC: 2)
  - [x] 2.1 Add `gateway_approval_actions_total` counter in ApprovalService
  - [x] 2.2 Add `gateway_approval_duration_seconds` histogram
  - [x] 2.3 Track pending timestamp for duration calculation
  - [x] 2.4 Add tests for approval metrics
- [x] Task 3: Add Rate Limit Metrics (AC: 3)
  - [x] 3.1 Add `gateway_ratelimit_decisions_total` counter in RateLimitFilter
  - [x] 3.2 Add `gateway_ratelimit_cache_hits_total` counter in RateLimitService
  - [x] 3.3 Add sampled remaining tokens gauge (every Nth request)
  - [x] 3.4 Add tests for rate limit metrics
- [x] Task 4: Add Cache Performance Metrics (AC: 4)
  - [x] 4.1 Add `gateway_cache_operations_total` counter in RouteCacheManager
  - [x] 4.2 Add `gateway_cache_size` gauge
  - [x] 4.3 Add `gateway_cache_refresh_duration_seconds` histogram
  - [x] 4.4 Add tests for cache metrics
- [x] Task 5: Document SLI Definitions (AC: 5)
  - [x] 5.1 Create `docs/sli-slo.md` with SLI definitions
  - [x] 5.2 Add PromQL queries for each SLI
  - [x] 5.3 Document measurement methodology
- [x] Task 6: Define SLO Targets (AC: 6)
  - [x] 6.1 Add SLO targets to documentation
  - [x] 6.2 Document error budget calculations
  - [x] 6.3 Add SLO compliance formulas
- [x] Task 7: Create Grafana SLO Dashboard (AC: 7)
  - [x] 7.1 Create SLO dashboard JSON
  - [x] 7.2 Add SLO compliance panels
  - [x] 7.3 Add error budget panels
  - [x] 7.4 Add burn rate panels
  - [x] 7.5 Add to infra project provisioning (reference in docs/)
- [x] Task 8: Configure Burn Rate Alerts (AC: 8)
  - [x] 8.1 Add `SLOBurnRateHigh` alert rule
  - [x] 8.2 Add `SLOBurnRateCritical` alert rule
  - [x] 8.3 Add to infra prometheus alerts (reference in docs/)
  - [x] 8.4 Document alert runbook

## Dev Notes

### Текущее состояние метрик

**Существующие метрики (MetricsFilter):**
- `gateway_requests_total` — общее количество запросов
- `gateway_request_duration_seconds` — latency histogram
- `gateway_errors_total` — ошибки по типам
- `gateway_active_connections` — активные соединения

**Чего не хватает (выявлено аудитом):**
- Domain-specific метрики (route operations, approvals)
- Rate limit метрики с детализацией
- Cache hit/miss ratio
- SLI/SLO определения

### Реализация кастомных метрик

**Паттерн для Counter:**
```kotlin
@Component
class RouteMetrics(meterRegistry: MeterRegistry) {

    // Counter для route operations
    private val routeOperations = Counter.builder("gateway_route_operations_total")
        .description("Количество операций с маршрутами")
        .tag("operation", "create") // заменяется при использовании
        .tag("status", "success")
        .register(meterRegistry)

    fun recordOperation(operation: String, success: Boolean) {
        Counter.builder("gateway_route_operations_total")
            .tag("operation", operation)
            .tag("status", if (success) "success" else "failure")
            .register(meterRegistry)
            .increment()
    }
}
```

**Паттерн для Gauge:**
```kotlin
@Component
class RouteMetrics(
    meterRegistry: MeterRegistry,
    private val routeRepository: RouteRepository
) {
    init {
        // Gauge обновляется автоматически при каждом scrape
        Gauge.builder("gateway_routes_active_total") {
            routeRepository.countByStatus(RouteStatus.PUBLISHED).block() ?: 0.0
        }
        .tag("status", "published")
        .description("Количество активных маршрутов по статусу")
        .register(meterRegistry)
    }
}
```

**Паттерн для Histogram:**
```kotlin
@Component
class ApprovalMetrics(meterRegistry: MeterRegistry) {

    private val approvalDuration = Timer.builder("gateway_approval_duration_seconds")
        .description("Время от PENDING до PUBLISHED/REJECTED")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    fun recordApprovalDuration(pendingTimestamp: Instant, completedTimestamp: Instant) {
        val duration = Duration.between(pendingTimestamp, completedTimestamp)
        approvalDuration.record(duration)
    }
}
```

### Cardinality Control

**ВАЖНО:** Избегать high cardinality labels!

**Опасные labels (НЕ использовать):**
- `user_id` — unbounded
- `request_id` — unbounded
- `consumer_id` без ограничений — контролируется нормализацией

**Безопасные labels:**
- `operation` — фиксированный набор (create, update, delete)
- `status` — фиксированный набор (success, failure)
- `cache` — фиксированный набор (redis, caffeine)
- `route_id` — ограничено количеством маршрутов (~100)

**Для consumer_id используем sampling:**
```kotlin
// Не записываем каждый запрос, только sampling
if (Random.nextInt(100) == 0) {
    remainingTokensGauge.set(tokensRemaining.toDouble())
}
```

### SLI Definitions

**Availability SLI:**
```promql
# Процент успешных запросов (не 5xx)
sum(rate(gateway_requests_total{status!~"5.."}[5m]))
/
sum(rate(gateway_requests_total[5m]))
```

**Latency SLI (P95 < 200ms):**
```promql
# Процент запросов быстрее 200ms
sum(rate(gateway_request_duration_seconds_bucket{le="0.2"}[5m]))
/
sum(rate(gateway_request_duration_seconds_count[5m]))
```

**Error SLI:**
```promql
# Процент запросов без ошибок (не 4xx и не 5xx)
sum(rate(gateway_requests_total{status=~"2..|3.."}[5m]))
/
sum(rate(gateway_requests_total[5m]))
```

### SLO Targets

| SLI | Target | Error Budget (30d) |
|-----|--------|-------------------|
| Availability | 99.9% | 43.2 min |
| Latency P95 | < 200ms | 0.1% slow requests |
| Latency P99 | < 500ms | 1% slow requests |
| Error Rate | < 1% | 1% errors allowed |

**Error Budget Calculation:**
```
Monthly Minutes = 30 * 24 * 60 = 43,200 minutes
Error Budget (99.9%) = 43,200 * 0.001 = 43.2 minutes downtime
```

### Burn Rate Alerts

**Multi-window burn rate alerting (Google SRE book):**

| Alert | Burn Rate | Window | Budget Consumed |
|-------|-----------|--------|-----------------|
| Critical | 14.4x | 1h | 2% |
| High | 6x | 6h | 10% |
| Warning | 3x | 1d | 10% |

**Prometheus Alert Rule:**
```yaml
groups:
  - name: slo-burn-rate
    rules:
      - alert: SLOBurnRateCritical
        expr: |
          (
            sum(rate(gateway_requests_total{status=~"5.."}[1h]))
            /
            sum(rate(gateway_requests_total[1h]))
          ) > (14.4 * 0.001)
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "SLO burn rate критический (14.4x за 1h)"
          description: "Error budget сгорает слишком быстро. Текущий burn rate: {{ $value }}"
```

### Grafana Dashboard Structure

**SLO Dashboard Panels:**

1. **SLO Compliance (Stat)**
   - Current SLO compliance percentage
   - Color: green >99.9%, yellow >99%, red <99%

2. **Error Budget Remaining (Gauge)**
   - Percentage of error budget remaining
   - Color: green >50%, yellow >20%, red <20%

3. **Burn Rate (Time Series)**
   - 1h, 6h, 24h burn rate lines
   - Threshold lines at 14.4x, 6x, 3x

4. **SLI Trends (Time Series)**
   - Availability over time
   - Latency P95/P99 over time
   - Error rate over time

### File List (реализовано)

**Backend gateway-admin:**
- `src/main/kotlin/com/company/gateway/admin/metrics/RouteMetrics.kt` — NEW ✅
- `src/main/kotlin/com/company/gateway/admin/metrics/RouteMetricsUpdater.kt` — NEW ✅
- `src/main/kotlin/com/company/gateway/admin/metrics/ApprovalMetrics.kt` — NEW ✅
- `src/main/kotlin/com/company/gateway/admin/service/RouteService.kt` — MODIFY (add metrics calls) ✅
- `src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt` — MODIFY (add metrics calls) ✅
- `src/main/kotlin/com/company/gateway/admin/AdminApplication.kt` — MODIFY (add @EnableScheduling) ✅
- `src/test/kotlin/com/company/gateway/admin/metrics/RouteMetricsTest.kt` — NEW ✅
- `src/test/kotlin/com/company/gateway/admin/metrics/RouteMetricsUpdaterTest.kt` — NEW ✅
- `src/test/kotlin/com/company/gateway/admin/metrics/ApprovalMetricsTest.kt` — NEW ✅
- `src/test/kotlin/com/company/gateway/admin/service/RouteServiceTest.kt` — MODIFY (add RouteMetrics mock) ✅
- `src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt` — MODIFY (add ApprovalMetrics mock) ✅

**Backend gateway-core:**
- `src/main/kotlin/com/company/gateway/core/metrics/RateLimitMetrics.kt` — NEW ✅
- `src/main/kotlin/com/company/gateway/core/metrics/CacheMetrics.kt` — NEW ✅
- `src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt` — MODIFY (add metrics calls) ✅
- `src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt` — MODIFY (add metrics calls) ✅
- `src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt` — MODIFY (add cache hit metrics) ✅
- `src/test/kotlin/com/company/gateway/core/metrics/RateLimitMetricsTest.kt` — NEW ✅
- `src/test/kotlin/com/company/gateway/core/metrics/CacheMetricsTest.kt` — NEW ✅
- `src/test/kotlin/com/company/gateway/core/filter/RateLimitFilterTest.kt` — MODIFY (add RateLimitMetrics mock) ✅
- `src/test/kotlin/com/company/gateway/core/cache/RouteCacheManagerTest.kt` — MODIFY (add CacheMetrics mock) ✅
- `src/test/kotlin/com/company/gateway/core/ratelimit/RateLimitServiceTest.kt` — MODIFY (add RateLimitMetrics mock) ✅

**Documentation:**
- `docs/sli-slo.md` — NEW ✅
- `docs/grafana-slo-dashboard.json` — NEW ✅ (reference for infra)
- `docs/prometheus-slo-alerts.yml` — NEW ✅ (reference for infra)

**Infra project (external — reference files in docs/):**
- `config/grafana/dashboards/gateway-slo-dashboard.json` — reference in docs/
- `config/prometheus/alerts/slo-burn-rate.yml` — reference in docs/

## Change Log

| Date | Change |
|------|--------|
| 2026-03-03 | Story implementation completed: all 8 tasks done, metrics classes created, SLI/SLO documented |

### Architecture Compliance

- **Reactive Patterns:** Metrics записываются синхронно (Micrometer thread-safe), не блокируют reactive chain
- **RFC 7807:** N/A — no API changes
- **Correlation ID:** Metrics не содержат correlation ID (cardinality)
- **Testing:** Unit tests для metrics classes, integration test для prometheus endpoint

### Dependencies

**Уже есть в проекте:**
- `io.micrometer:micrometer-registry-prometheus`
- `org.springframework.boot:spring-boot-starter-actuator`

**Дополнительные не требуются.**

### References

- [Source: architecture-audit-2026-03-01.md#4 Observability]
- [Source: MetricsFilter.kt — текущая реализация]
- [Google SRE Book: Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)

### Rollback Plan

**Метрики:**
- Удалить новые metrics classes
- Revert изменений в RouteService, ApprovalService, etc.
- Метрики не влияют на основной функционал

**Dashboard/Alerts:**
- Удалить JSON из infra provisioning
- Метрики останутся, но не будут visualized

### Story Points Justification

**5 SP обосновано:**
- 4 новых metrics classes
- Изменения в 4+ существующих services
- Документация SLI/SLO
- Grafana dashboard JSON
- Prometheus alert rules
- Unit tests для всех metrics

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- All metrics classes implemented with unit tests
- SLI/SLO documentation complete with PromQL queries
- Grafana dashboard JSON exported for infra provisioning
- Prometheus alert rules defined (burn rate, latency, error budget)
- Code review fixes applied: removed stackTrace anti-pattern, added cache miss metric

### File List

**Backend gateway-admin:**
- `src/main/kotlin/com/company/gateway/admin/metrics/RouteMetrics.kt` — NEW
- `src/main/kotlin/com/company/gateway/admin/metrics/RouteMetricsUpdater.kt` — NEW
- `src/main/kotlin/com/company/gateway/admin/metrics/ApprovalMetrics.kt` — NEW
- `src/main/kotlin/com/company/gateway/admin/service/RouteService.kt` — MODIFY
- `src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt` — MODIFY
- `src/main/kotlin/com/company/gateway/admin/AdminApplication.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/admin/metrics/RouteMetricsTest.kt` — NEW
- `src/test/kotlin/com/company/gateway/admin/metrics/RouteMetricsUpdaterTest.kt` — NEW
- `src/test/kotlin/com/company/gateway/admin/metrics/ApprovalMetricsTest.kt` — NEW
- `src/test/kotlin/com/company/gateway/admin/service/RouteServiceTest.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt` — MODIFY

**Backend gateway-core:**
- `src/main/kotlin/com/company/gateway/core/metrics/RateLimitMetrics.kt` — NEW
- `src/main/kotlin/com/company/gateway/core/metrics/CacheMetrics.kt` — NEW
- `src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt` — MODIFY
- `src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt` — MODIFY
- `src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/core/metrics/RateLimitMetricsTest.kt` — NEW
- `src/test/kotlin/com/company/gateway/core/filter/RateLimitFilterTest.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/core/cache/RouteCacheManagerTest.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/core/ratelimit/RateLimitServiceTest.kt` — MODIFY
- `src/test/kotlin/com/company/gateway/core/metrics/CacheMetricsTest.kt` — NEW

**Documentation:**
- `docs/sli-slo.md` — NEW
- `docs/grafana-slo-dashboard.json` — NEW
- `docs/prometheus-slo-alerts.yml` — NEW

