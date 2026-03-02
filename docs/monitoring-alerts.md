# Мониторинг и Алерты

## Обзор

API Gateway использует централизованную систему мониторинга из infra проекта (Story 13.11):

| Компонент | URL | Назначение |
|-----------|-----|------------|
| Prometheus | https://prometheus.ymorozov.ru | Сбор и хранение метрик |
| Grafana | https://grafana.ymorozov.ru | Визуализация и алерты |

## Dashboard "API Gateway"

Dashboard автоматически provisioned и содержит следующие панели:

### Request Metrics
- **Requests per Second** — RPS по endpoint
- **Error Rate** — процент ошибок (4xx/5xx)
- **Latency P50/P95/P99** — распределение времени ответа

### Consumer Metrics
- **Requests by Consumer** — разбивка по consumer_id
- **Rate Limit Hits** — срабатывания rate limiting
- **Consumer Cardinality** — количество уникальных consumers

### System Metrics
- **JVM Memory Usage** — heap, non-heap, metaspace
- **GC Activity** — частота и длительность garbage collection
- **Thread Pool** — активные и ожидающие потоки

## Alert Rules

### Стандартные алерты

| Алерт | Условие | Severity | Описание |
|-------|---------|----------|----------|
| `high-error-rate` | error_rate > 5% за 5 минут | warning | Высокий процент ошибок |
| `high-latency-p95` | P95 > 500ms за 5 минут | warning | Высокая задержка ответов |
| `gateway-down` | no metrics за 1 минуту | critical | Gateway не отправляет метрики |
| `HighConsumerCardinality` | > 1000 unique consumers | warning | Много уникальных consumer_id |
| `CriticalConsumerCardinality` | > 5000 unique consumers | critical | Критически много consumers |
| `HighMetricsCardinality` | > 100K total series | warning | Высокая кардинальность метрик |

### PromQL для алертов

```promql
# High Error Rate (>5%) — используем стандартные метрики Spring Boot Actuator
rate(http_server_requests_seconds_count{job="gateway-core", status=~"5.."}[5m])
/
rate(http_server_requests_seconds_count{job="gateway-core"}[5m]) > 0.05

# High P95 Latency (>500ms)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="gateway-core"}[5m])) > 0.5

# Gateway Down
up{job="gateway-core"} == 0

# Consumer Cardinality (если используются custom метрики)
count(count by (consumer_id) (gateway_rate_limit_requests_total)) > 1000
```

## Создание алертов через Grafana UI

### Шаг 1: Открыть Alerting

1. Войдите в Grafana: https://grafana.ymorozov.ru (Keycloak SSO)
2. Перейдите в **Alerting** → **Alert rules**
3. Нажмите **+ New alert rule**

### Шаг 2: Настройка условия

1. **Rule name**: `high-error-rate-gateway`
2. **Folder**: `API Gateway`
3. **Group**: `gateway-alerts`

4. **Define query and alert condition**:
   - Data source: `Prometheus`
   - Query A:
     ```promql
     rate(http_server_requests_seconds_count{job="gateway-core", status=~"5.."}[5m])
     /
     rate(http_server_requests_seconds_count{job="gateway-core"}[5m])
     ```
   - Expression: `A > 0.05`

5. **Evaluate every**: `1m`
6. **For**: `5m` (алерт срабатывает после 5 минут превышения)

### Шаг 3: Настройка уведомлений

1. **Add labels**:
   - `severity`: `warning`
   - `service`: `api-gateway`

2. **Add annotations**:
   - `summary`: `High error rate on API Gateway`
   - `description`: `Error rate is {{ $value | printf "%.2f" }}% (threshold: 5%)`

### Шаг 4: Контакт поинты

1. Перейдите в **Alerting** → **Contact points**
2. Создайте contact point (Telegram, Slack, Email и т.д.)
3. В alert rule добавьте **Notification policies** → выберите contact point

## Метрики Gateway

### gateway-core

```
# HTTP метрики (Spring Boot Actuator)
http_server_requests_seconds_count{uri, method, status}
http_server_requests_seconds_sum{uri, method, status}
http_server_requests_seconds_bucket{uri, method, status, le}

# Rate Limiting
gateway_rate_limit_requests_total{route, consumer_id, result}

# Route метрики
gateway_route_requests_total{route_id, upstream_url}
```

### gateway-admin

```
# API метрики
http_server_requests_seconds_count{uri, method, status}

# Route Management
gateway_routes_total{status}
gateway_routes_published_total
```

## Scrape Configuration

Prometheus scrapes метрики каждые 15 секунд:

```yaml
# В централизованном Prometheus (infra проект)
scrape_configs:
  - job_name: 'gateway-core'
    static_configs:
      - targets: ['gateway-core:8080']
    metrics_path: /actuator/prometheus

  - job_name: 'gateway-admin'
    static_configs:
      - targets: ['gateway-admin:8081']
    metrics_path: /actuator/prometheus
```

## Troubleshooting

### Нет метрик в Prometheus

1. Проверьте что gateway services запущены и подключены к `monitoring-net`:
   ```bash
   docker network inspect monitoring-net
   ```

2. Проверьте что `/actuator/prometheus` endpoint доступен:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   curl http://localhost:8082/actuator/prometheus
   ```

3. Проверьте Prometheus targets:
   - Откройте https://prometheus.ymorozov.ru/targets
   - Убедитесь что `gateway-core` и `gateway-admin` в статусе "UP"

### Алерты не срабатывают

1. Проверьте alert rules в Grafana: **Alerting** → **Alert rules**
2. Убедитесь что rule в статусе "Normal" или "Firing" (не "Error")
3. Проверьте notification policies и contact points

## Ссылки

- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Grafana Alerting Documentation](https://grafana.com/docs/grafana/latest/alerting/)
- [PromQL Documentation](https://prometheus.io/docs/prometheus/latest/querying/basics/)
