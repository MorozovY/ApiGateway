# Distributed Tracing — Руководство

> Story 14.5: Distributed Tracing Integration

## Обзор

API Gateway использует **OpenTelemetry** для distributed tracing. Traces экспортируются в **Jaeger** и доступны через веб-интерфейс.

### Компоненты

| Компонент | Назначение |
|-----------|------------|
| Micrometer Tracing | Abstraction layer для tracing |
| OpenTelemetry | Стандарт для traces и spans |
| OTLP Exporter | Протокол экспорта traces |
| Jaeger | Бэкенд для хранения и визуализации |

## Jaeger UI

**URL:** http://localhost:16686

### Поиск traces

1. Откройте Jaeger UI
2. Выберите **Service**: `gateway-core` или `gateway-admin`
3. Укажите **Operation** (опционально): `GET`, `POST`, etc.
4. Установите **Lookback** период
5. Нажмите **Find Traces**

### Фильтрация по tags

Доступные tags для поиска:

```
gateway.route.id=<route-id>
gateway.consumer.id=<consumer-id>
gateway.ratelimit.decision=allowed|denied
gateway.route.path=/api/users/**
```

Пример поиска denied requests:
```
gateway.ratelimit.decision=denied
```

## Корреляция с логами

### Structured JSON logs

В production логи содержат `traceId` и `spanId`:

```json
{
  "timestamp": "2026-03-03T12:34:56.789Z",
  "level": "INFO",
  "traceId": "abc123def456789...",
  "spanId": "xyz789...",
  "correlationId": "uuid-...",
  "message": "Request forwarded to upstream"
}
```

### Поиск логов по trace

1. Найдите trace в Jaeger
2. Скопируйте `traceId` из заголовка trace
3. Ищите в логах: `grep "abc123def456789" logs.json`

## Добавление custom spans в коде

### Kotlin пример

```kotlin
import io.micrometer.tracing.Tracer

@Component
class MyService(private val tracer: Tracer) {

    fun processData(data: String): Mono<Result> {
        // Создаём child span
        val span = tracer.nextSpan().name("process-data").start()

        return try {
            span.tag("data.size", data.length.toString())
            span.tag("data.type", "json")

            doActualProcessing(data)
                .doOnSuccess { span.tag("result.status", "success") }
                .doOnError { span.tag("result.status", "error") }
        } finally {
            span.end()
        }
    }
}
```

### Reactive chains

Для reactive chains используйте `tap()` оператор:

```kotlin
fun processRequest(): Mono<Response> {
    return service.getData()
        .tap { signal ->
            val span = tracer.currentSpan()
            if (signal.isOnNext) {
                span?.tag("data.received", "true")
            }
        }
        .map { transform(it) }
}
```

## Sampling Configuration

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TRACING_SAMPLING_PROBABILITY` | 1.0 | Процент traces для записи (0.0-1.0) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://jaeger:4318/v1/traces | OTLP endpoint |

### Profile-based sampling

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
```

**Рекомендации:**
- **Development:** 1.0 (100%) — все traces
- **Staging:** 0.5 (50%)
- **Production:** 0.1 (10%) — для high-traffic

## W3C Trace Context

### Propagation headers

Traces автоматически propagate через HTTP headers:

```
traceparent: 00-<trace-id>-<span-id>-01
tracestate: <vendor-specific-state>
```

### X-Correlation-ID

Для backward compatibility `X-Correlation-ID` продолжает устанавливаться:

```
X-Correlation-ID: uuid-v4-here
```

## Grafana Integration

### Data Source

Jaeger datasource автоматически provisioned в Grafana:
- **Name:** Jaeger
- **UID:** jaeger

### Trace links из dashboard

Для добавления trace links в существующие panels:

1. Откройте panel → Edit
2. Перейдите на вкладку **Data links**
3. Добавьте link:
   - **Title:** View trace
   - **URL:** `http://localhost:16686/trace/${__value.raw}`

### Exemplars (optional)

Для корреляции metrics → traces настройте exemplars в Prometheus scrape config.

## Troubleshooting

### Traces не появляются в Jaeger

1. Проверьте connectivity:
   ```bash
   docker exec gateway-core-dev curl -s http://jaeger:4318/v1/traces
   ```

2. Проверьте sampling probability:
   ```bash
   docker exec gateway-core-dev env | grep TRACING
   ```

3. Проверьте логи gateway:
   ```bash
   docker logs gateway-core-dev 2>&1 | grep -i "trace\|otel"
   ```

### MDC не содержит traceId

Убедитесь что `context-propagation` dependency добавлена:

```kotlin
implementation("io.micrometer:context-propagation")
```

И `Hooks.enableAutomaticContextPropagation()` вызван в `MdcContextConfig.kt`.

### Jaeger UI недоступен

1. Проверьте что сервис запущен:
   ```bash
   docker ps | grep jaeger
   ```

2. Проверьте healthcheck:
   ```bash
   docker inspect jaeger --format='{{.State.Health.Status}}'
   ```

## См. также

- [Spring Boot Tracing Documentation](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
