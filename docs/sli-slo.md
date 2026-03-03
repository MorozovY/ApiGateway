# SLI/SLO Definitions — API Gateway

## Overview

Этот документ определяет Service Level Indicators (SLI) и Service Level Objectives (SLO)
для API Gateway. SLI — это метрики, по которым измеряется надёжность сервиса.
SLO — это целевые значения этих метрик.

**Story:** 14.3 — Custom Metrics & SLI/SLO Definition

---

## SLI Definitions

### 1. Availability SLI

**Определение:** Процент успешных запросов (не возвращающих 5xx ошибки).

**Формула:**
```
Availability = (total_requests - 5xx_errors) / total_requests
```

**PromQL Query:**
```promql
# Availability за последние 5 минут (rolling)
sum(rate(gateway_requests_total{status!~"5.."}[5m]))
/
sum(rate(gateway_requests_total[5m]))
```

**PromQL Query (30-day rolling для SLO compliance):**
```promql
# 30-day rolling availability
sum(increase(gateway_requests_total{status!~"5.."}[30d]))
/
sum(increase(gateway_requests_total[30d]))
```

**Измерение:**
- Источник: `gateway_requests_total` метрика из MetricsFilter
- Агрегация: по всем маршрутам и consumers
- Исключения: Health checks, внутренние endpoints

---

### 2. Latency SLI (P95)

**Определение:** Процент запросов с latency менее 200ms (P95 target).

**Формула:**
```
Latency_SLI = requests_below_threshold / total_requests
```

**PromQL Query:**
```promql
# Процент запросов быстрее 200ms
sum(rate(gateway_request_duration_seconds_bucket{le="0.2"}[5m]))
/
sum(rate(gateway_request_duration_seconds_count[5m]))
```

**PromQL Query (P95 latency value):**
```promql
# P95 latency в секундах
histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))
```

**Измерение:**
- Источник: `gateway_request_duration_seconds` histogram из MetricsFilter
- Buckets: автоматически настроены Micrometer
- Включает: время обработки gateway + upstream latency

---

### 3. Latency SLI (P99)

**Определение:** Процент запросов с latency менее 500ms (P99 target).

**PromQL Query:**
```promql
# Процент запросов быстрее 500ms
sum(rate(gateway_request_duration_seconds_bucket{le="0.5"}[5m]))
/
sum(rate(gateway_request_duration_seconds_count[5m]))
```

**PromQL Query (P99 latency value):**
```promql
# P99 latency в секундах
histogram_quantile(0.99, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))
```

---

### 4. Error Rate SLI

**Определение:** Процент запросов без ошибок (только 2xx и 3xx).

**Формула:**
```
Error_Rate_SLI = (total_requests - 4xx - 5xx) / total_requests
```

**PromQL Query:**
```promql
# Процент успешных запросов (2xx + 3xx)
sum(rate(gateway_requests_total{status=~"2..|3.."}[5m]))
/
sum(rate(gateway_requests_total[5m]))
```

**Примечание:** Error Rate SLI отличается от Availability тем, что учитывает и 4xx ошибки.
Для большинства случаев Availability SLI предпочтительнее.

---

## SLO Targets

| SLI | Target | Error Budget (30d) | Description |
|-----|--------|-------------------|-------------|
| **Availability** | 99.9% | 43.2 min | Максимальный downtime в месяц |
| **Latency P95** | < 200ms | 0.1% slow | 99.9% запросов быстрее 200ms |
| **Latency P99** | < 500ms | 1% slow | 99% запросов быстрее 500ms |
| **Error Rate** | < 1% | 1% | Максимум 1% ошибок |

---

## Error Budget Calculations

### Monthly Error Budget

```
Monthly Minutes = 30 days × 24 hours × 60 minutes = 43,200 minutes
```

**Availability 99.9%:**
```
Error Budget = 43,200 × (1 - 0.999) = 43.2 minutes downtime allowed
```

**Availability 99.5% (для reference):**
```
Error Budget = 43,200 × (1 - 0.995) = 216 minutes downtime allowed
```

### Error Budget Consumption Formula

```promql
# Error budget consumed (%)
(1 - (
  sum(increase(gateway_requests_total{status!~"5.."}[30d]))
  /
  sum(increase(gateway_requests_total[30d]))
)) / 0.001 * 100
```

**Explanation:**
- `0.001` = 100% - 99.9% (SLO threshold)
- Результат: процент error budget, который уже использован

---

## Burn Rate Alerting

Burn rate — это скорость, с которой расходуется error budget.
При burn rate = 1x весь error budget расходуется за 30 дней.

### Burn Rate Thresholds

| Alert Level | Burn Rate | Window | Budget Consumed | Action |
|-------------|-----------|--------|-----------------|--------|
| **Critical** | 14.4x | 1h | 2% за 1h | Page on-call |
| **High** | 6x | 6h | 10% за 6h | Alert team |
| **Warning** | 3x | 1d | 10% за 1d | Notify |

### Burn Rate Formula

```promql
# Текущий burn rate (1h window)
(
  sum(rate(gateway_requests_total{status=~"5.."}[1h]))
  /
  sum(rate(gateway_requests_total[1h]))
) / 0.001
```

**Explanation:**
- `0.001` = target error rate (100% - 99.9%)
- Результат: множитель (1x = нормальный, 14.4x = критический)

---

## Prometheus Alert Rules

Alert rules хранятся в infra проекте: `config/prometheus/alerts/slo-burn-rate.yml`

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

      - alert: SLOBurnRateHigh
        expr: |
          (
            sum(rate(gateway_requests_total{status=~"5.."}[6h]))
            /
            sum(rate(gateway_requests_total[6h]))
          ) > (6 * 0.001)
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "SLO burn rate высокий (6x за 6h)"
          description: "Error budget расходуется быстрее нормы. Текущий burn rate: {{ $value }}"
```

---

## Grafana Dashboard

Dashboard "API Gateway SLO" автоматически provisioned в infra проекте.

### Dashboard Panels

1. **SLO Compliance (Stat)**
   - Query: 30-day rolling availability
   - Thresholds: green >99.9%, yellow >99%, red <99%

2. **Error Budget Remaining (Gauge)**
   - Query: (1 - error_budget_consumed) * 100
   - Thresholds: green >50%, yellow >20%, red <20%

3. **Burn Rate (Time Series)**
   - Queries: 1h, 6h, 24h burn rates
   - Threshold lines: 14.4x (critical), 6x (high), 3x (warning)

4. **SLI Trends (Time Series)**
   - Availability over time
   - P95/P99 latency over time
   - Error rate over time

---

## Measurement Methodology

### Data Sources

| Metric | Source | Labels |
|--------|--------|--------|
| `gateway_requests_total` | MetricsFilter | route_id, status, method, consumer_id |
| `gateway_request_duration_seconds` | MetricsFilter | route_id, status, method |
| `gateway_errors_total` | MetricsFilter | route_id, error_type |

### Aggregation

- **SLI calculations:** агрегируем по всем маршрутам
- **Per-route SLI:** доступен с фильтром `route_id`
- **Scrape interval:** 15 секунд (настроено в Prometheus)

### Exclusions

Из SLI расчётов исключаются:
- Health check endpoints (`/actuator/health`)
- Metrics endpoints (`/actuator/prometheus`)
- Внутренние administrative endpoints

---

## References

- [Google SRE Book: Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
- [Micrometer Documentation](https://micrometer.io/docs)
- Story 14.3: Custom Metrics & SLI/SLO Definition
