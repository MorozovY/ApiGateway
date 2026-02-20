# Story 6.4: Prometheus & Grafana Setup

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **DevOps Engineer**,
I want pre-configured Prometheus and Grafana,
so that I have production-ready monitoring out of the box (NFR21).

## Acceptance Criteria

**AC1 — Docker Compose profile для мониторинга:**

**Given** Docker Compose environment
**When** `docker-compose --profile monitoring up -d`
**Then** следующие сервисы запускаются:
- Prometheus на порту 9090
- Grafana на порту 3001 (3000 занят frontend dev server)
**And** Prometheus сконфигурирован для scrape метрик gateway

**AC2 — Prometheus конфигурация:**

**Given** файл `docker/prometheus/prometheus.yml` существует
**When** Prometheus стартует
**Then** scrape config содержит target gateway-core на `/actuator/prometheus`
**And** scrape interval = 15 секунд
**And** job name = "gateway"

**AC3 — Grafana datasource:**

**Given** Grafana стартует
**When** доступ к http://localhost:3001
**Then** Prometheus datasource предустановлен и подключён
**And** default credentials: admin/admin (с prompt для смены)

**AC4 — Grafana dashboard:**

**Given** файл `docker/grafana/dashboards/gateway-dashboard.json` существует
**When** Grafana стартует
**Then** dashboard "API Gateway" auto-provisioned
**And** dashboard содержит панели:
- Requests per second (graph)
- Latency percentiles P50/P95/P99 (graph)
- Error rate (graph)
- Top routes by traffic (table)
- Active connections (gauge)
- Status code distribution (pie chart)
- Errors by type (graph)

**AC5 — Grafana alerting (опционально):**

**Given** Grafana alerting сконфигурирован
**When** настроены alert rules
**Then** provisioned alert rules:
- High error rate: > 5% errors за 5 минут
- High latency: P95 > 500ms за 5 минут
- Gateway down: нет метрик за 1 минуту

## Tasks / Subtasks

- [x] Task 1: Настроить Docker Compose profile "monitoring" (AC1)
  - [x] Добавить Prometheus service в docker-compose.yml с profile "monitoring"
  - [x] Добавить Grafana service в docker-compose.yml с profile "monitoring"
  - [x] Настроить volumes для persistence конфигов
  - [x] Настроить networks для связи с gateway-core

- [x] Task 2: Создать Prometheus конфигурацию (AC2)
  - [x] Создать директорию `docker/prometheus/`
  - [x] Создать `docker/prometheus/prometheus.yml` с scrape config
  - [x] Настроить job "gateway" с target host.docker.internal:8080/actuator/prometheus
  - [x] Настроить scrape_interval: 15s

- [x] Task 3: Настроить Grafana provisioning (AC3)
  - [x] Создать директорию `docker/grafana/provisioning/`
  - [x] Создать datasource provisioning: `datasources/prometheus.yml`
  - [x] Создать dashboard provisioning: `dashboards/dashboard.yml`
  - [x] Настроить GF_SECURITY_ADMIN_PASSWORD через environment

- [x] Task 4: Создать Grafana dashboard (AC4)
  - [x] Создать `docker/grafana/dashboards/gateway-dashboard.json`
  - [x] Панель: Requests per second (PromQL: rate(gateway_requests_total[5m]))
  - [x] Панель: Latency percentiles (histogram_quantile)
  - [x] Панель: Error rate (rate(gateway_errors_total[5m]) / rate(gateway_requests_total[5m]))
  - [x] Панель: Top routes table (topk by route_path)
  - [x] Панель: Active connections gauge
  - [x] Панель: Status code distribution pie chart

- [x] Task 5: Настроить Grafana alerts (AC5, optional)
  - [x] Создать alert rules provisioning файл
  - [x] Alert: High error rate > 5%
  - [x] Alert: High latency P95 > 500ms
  - [x] Alert: Gateway down (no metrics)

- [x] Task 6: Тестирование и документация
  - [x] Протестировать `docker-compose --profile monitoring up -d`
  - [x] Проверить Prometheus targets в UI (http://localhost:9090/targets)
  - [x] Проверить Grafana dashboard отображается
  - [x] Добавить документацию в CLAUDE.md или README

## Dev Notes

### Архитектурный контекст

Story 6.4 завершает infrastructure часть Epic 6 (Monitoring & Observability):
- **Story 6.1** (done) — базовые метрики в gateway-core (Micrometer)
- **Story 6.2** (done) — per-route labels (route_id, route_path, upstream_host, method, status)
- **Story 6.3** (done) — REST API для метрик в gateway-admin
- **Story 6.4** (current) — Prometheus + Grafana infrastructure
- **Story 6.5** (next) — Admin UI dashboard с виджетами метрик

### Существующие метрики (из Stories 6.1, 6.2)

**gateway-core экспортирует на `/actuator/prometheus`:**

```
# Счётчик запросов
gateway_requests_total{route_id="...", route_path="/api/orders", upstream_host="order-service:8080", method="GET", status="2xx"} 1500

# Histogram latency
gateway_request_duration_seconds_bucket{route_id="...", route_path="/api/orders", method="GET", le="0.05"} 1200
gateway_request_duration_seconds_bucket{route_id="...", route_path="/api/orders", method="GET", le="0.1"} 1450
gateway_request_duration_seconds_sum{...} 45.5
gateway_request_duration_seconds_count{...} 1500

# Счётчик ошибок
gateway_errors_total{route_id="...", route_path="/api/orders", error_type="upstream_error"} 5

# Gauge активных соединений
gateway_active_connections 42
```

**Histogram buckets (из Story 6.1):**
```kotlin
val HISTOGRAM_BUCKETS_SECONDS = doubleArrayOf(
    0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0
)
```

**Percentiles (из Story 6.3):**
```kotlin
// MetricsConfig.kt добавляет publishPercentiles
.publishPercentiles(0.5, 0.95, 0.99)
```

### Docker Compose структура

**Текущий docker-compose.yml:**
```yaml
services:
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    ...

  redis:
    image: redis:7
    ports:
      - "6379:6379"
    ...
```

**Добавить monitoring profile:**
```yaml
services:
  # ... existing services ...

  prometheus:
    image: prom/prometheus:v2.51.0
    profiles: ["monitoring"]
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
    extra_hosts:
      - "host.docker.internal:host-gateway"
    networks:
      - gateway-network

  grafana:
    image: grafana/grafana:10.4.0
    profiles: ["monitoring"]
    ports:
      - "3001:3000"  # 3000 занят frontend
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./docker/grafana/dashboards:/var/lib/grafana/dashboards:ro
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus
    networks:
      - gateway-network

volumes:
  prometheus_data:
  grafana_data:

networks:
  gateway-network:
    driver: bridge
```

### Prometheus конфигурация

**docker/prometheus/prometheus.yml:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'gateway'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:8080']  # gateway-core
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        replacement: 'gateway-core'
```

**Важно:** `host.docker.internal` позволяет контейнеру обращаться к host machine где запущен gateway-core (через `./gradlew bootRun`).

### Grafana provisioning

**docker/grafana/provisioning/datasources/prometheus.yml:**
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

**docker/grafana/provisioning/dashboards/dashboard.yml:**
```yaml
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    editable: true
    options:
      path: /var/lib/grafana/dashboards
```

### Grafana Dashboard JSON

**Ключевые панели (PromQL queries):**

**1. Requests per Second:**
```promql
sum(rate(gateway_requests_total[5m]))
```

**2. Latency Percentiles:**
```promql
# P50
histogram_quantile(0.50, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))

# P95
histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))

# P99
histogram_quantile(0.99, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))
```

**3. Error Rate:**
```promql
sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m])) * 100
```

**4. Top Routes by Traffic:**
```promql
topk(10, sum(rate(gateway_requests_total[5m])) by (route_path))
```

**5. Active Connections:**
```promql
gateway_active_connections
```

**6. Status Code Distribution:**
```promql
sum(rate(gateway_requests_total[5m])) by (status)
```

### Grafana Alerts

**Alert Rules (provisioning):**

```yaml
# docker/grafana/provisioning/alerting/alerts.yml
apiVersion: 1

groups:
  - name: gateway-alerts
    folder: Gateway
    interval: 1m
    rules:
      - uid: high-error-rate
        title: High Error Rate
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300  # 5 minutes
              to: 0
            datasourceUid: prometheus
            model:
              expr: sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m])) * 100
              instant: true
        for: 5m
        annotations:
          summary: "Error rate is {{ $value | printf \"%.2f\" }}%"
        labels:
          severity: warning

      - uid: high-latency
        title: High Latency P95
        condition: C
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le)) * 1000
              instant: true
        for: 5m
        annotations:
          summary: "P95 latency is {{ $value | printf \"%.0f\" }}ms"
        labels:
          severity: warning

      - uid: gateway-down
        title: Gateway Down
        condition: C
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: absent(gateway_requests_total)
              instant: true
        for: 1m
        annotations:
          summary: "No gateway metrics received"
        labels:
          severity: critical
```

### Docker Networking

**Проблема:** Prometheus в Docker не может напрямую обратиться к localhost:8080 (gateway-core на host machine).

**Решение 1 (рекомендуемое для dev):**
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```
Тогда target: `host.docker.internal:8080`

**Решение 2 (для production):**
Gateway-core тоже в Docker → использовать service name:
```yaml
targets: ['gateway-core:8080']
```

### File Structure

```
docker/
├── prometheus/
│   └── prometheus.yml
└── grafana/
    ├── provisioning/
    │   ├── datasources/
    │   │   └── prometheus.yml
    │   ├── dashboards/
    │   │   └── dashboard.yml
    │   └── alerting/
    │       └── alerts.yml
    └── dashboards/
        └── gateway-dashboard.json
```

### Команды для тестирования

```bash
# Запуск с monitoring profile
docker-compose --profile monitoring up -d

# Проверка статуса
docker-compose --profile monitoring ps

# Проверка Prometheus targets
# Browser: http://localhost:9090/targets
# gateway target должен быть "UP"

# Проверка Grafana
# Browser: http://localhost:3001
# Login: admin/admin
# Dashboard: API Gateway

# Остановка
docker-compose --profile monitoring down

# Полная очистка с volumes
docker-compose --profile monitoring down -v
```

### Версии образов

| Image | Version | Notes |
|-------|---------|-------|
| prom/prometheus | v2.51.0 | Latest stable (Feb 2024) |
| grafana/grafana | 10.4.0 | Latest stable (Mar 2024) |

Версии выбраны как последние стабильные на момент написания. При необходимости обновить до актуальных.

### Reactive Patterns (из CLAUDE.md)

Не применимо — данная story не содержит Kotlin/reactive кода, только Docker/YAML конфигурации.

### Error Handling

**Prometheus target down:**
- Target отображается как "DOWN" в http://localhost:9090/targets
- Проверить: запущен ли gateway-core? (`./gradlew :gateway-core:bootRun`)
- Проверить: доступен ли endpoint? (`curl http://localhost:8080/actuator/prometheus`)

**Grafana datasource error:**
- Проверить связь Grafana → Prometheus: в UI "Test" datasource
- Проверить network: оба контейнера в одной network

### Dependencies от других stories

- **Story 6.1** (done) — Micrometer metrics в gateway-core
- **Story 6.2** (done) — Per-route labels для детализации
- **Story 6.3** (done) — REST API (независим от Prometheus)

### Обратная совместимость

Эта story добавляет опциональный monitoring profile. Существующий docker-compose workflow без `--profile monitoring` работает как раньше.

### Project Structure Notes

**Новые файлы:**
- `docker/prometheus/prometheus.yml`
- `docker/grafana/provisioning/datasources/prometheus.yml`
- `docker/grafana/provisioning/dashboards/dashboard.yml`
- `docker/grafana/provisioning/alerting/alerts.yml`
- `docker/grafana/dashboards/gateway-dashboard.json`

**Модифицируемые файлы:**
- `docker-compose.yml` — добавить prometheus и grafana services с profile "monitoring"

### Паттерн коммита

```
feat: implement Story 6.4 — Prometheus & Grafana Setup
```

### References

- [Source: planning-artifacts/epics.md#Story-6.4] — Story requirements
- [Source: implementation-artifacts/6-1-metrics-collection-micrometer.md] — Metric names и buckets
- [Source: implementation-artifacts/6-2-per-route-metrics.md] — Per-route labels
- [Source: implementation-artifacts/6-3-metrics-summary-api.md] — MetricsConfig с percentiles
- [Prometheus Configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Grafana Dashboard JSON Model](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/view-dashboard-json-model/)

### Git Context

**Последние коммиты:**
```
806acca feat: implement Story 6.3 — Metrics Summary API
b3157bb fix: code review fixes for Story 6.2 — add integration tests, improve logging
3dbbbd6 feat: implement Story 6.2 — Per-Route Metrics
07a3345 feat: implement Story 6.1 — Metrics Collection with Micrometer
```

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Prometheus targets API: `http://localhost:9090/api/v1/targets` — оба targets "up" (gateway, prometheus)
- Grafana datasources API: `http://localhost:3001/api/datasources` — Prometheus datasource auto-provisioned
- Grafana dashboards API: `http://localhost:3001/api/search?type=dash-db` — dashboard "API Gateway" loaded

### Completion Notes List

1. **Docker Compose profile "monitoring"** — добавлены сервисы Prometheus (v2.51.0) и Grafana (10.4.0) с health checks
2. **Prometheus config** — scrape interval 15s, job "gateway" с target host.docker.internal:8080/actuator/prometheus
3. **Grafana provisioning** — Prometheus datasource и dashboard provisioning настроены
4. **Dashboard "API Gateway"** — 7 панелей: RPS, Latency P50/P95/P99, Error Rate, Top Routes, Active Connections, Status Distribution, Errors by Type
5. **Alert rules** — 3 alerts: High Error Rate (>5%, 5min), High Latency P95 (>500ms, 5min), Gateway Down (1min)
6. **Тестирование** — все контейнеры healthy, targets UP, dashboard auto-loaded
7. **Документация** — добавлена секция "Мониторинг" в CLAUDE.md

### File List

**Новые файлы:**
- docker/prometheus/prometheus.yml
- docker/grafana/provisioning/datasources/prometheus.yml
- docker/grafana/provisioning/dashboards/dashboard.yml
- docker/grafana/provisioning/alerting/alerts.yml
- docker/grafana/dashboards/gateway-dashboard.json

**Изменённые файлы:**
- docker-compose.yml (добавлены services prometheus и grafana с profile "monitoring", volumes, healthchecks)
- CLAUDE.md (добавлена секция "Мониторинг")
- _bmad-output/implementation-artifacts/sprint-status.yaml (статус story)

### Change Log

- 2026-02-20: Implemented Story 6.4 — Prometheus & Grafana Setup (AC1-AC5)
- 2026-02-20: Code review fixes — исправлен hard-coded datasource UID в alerts и dashboard

