# Story 14.5: Distributed Tracing Integration

Status: review

## Story

As a **Platform Engineer**,
I want distributed tracing integrated into the API Gateway,
So that I can trace requests across gateway-core, gateway-admin, and downstream services.

As a **Developer**,
I want to see trace IDs in logs and have access to Jaeger UI,
So that I can debug latency issues and understand request flow.

## Acceptance Criteria

### AC1: OpenTelemetry Integration
**Given** Spring Boot applications (gateway-core, gateway-admin)
**When** requests are processed
**Then** traces are automatically created via Micrometer Tracing
**And** traces include span for each filter in gateway-core
**And** traces propagate to downstream services via W3C Trace Context headers

### AC2: Jaeger Backend Configuration
**Given** centralized infrastructure in infra project
**When** Jaeger service is deployed
**Then** Jaeger all-in-one container runs with OTLP support enabled
**And** Jaeger UI is accessible at `http://localhost:16686`
**And** OTLP collector endpoint is available at port 4318 (HTTP)

### AC3: Trace Propagation Headers
**Given** request arrives at gateway-core
**When** request is forwarded to upstream service
**Then** W3C Trace Context headers are propagated:
  - `traceparent` — trace ID and span ID
  - `tracestate` — vendor-specific state
**And** `X-Correlation-ID` continues to be set (backward compatibility)

### AC4: Custom Span Attributes
**Given** request is processed by gateway-core
**When** routing decision is made
**Then** custom span attributes are added:
  - `gateway.route.id` — matched route ID
  - `gateway.route.path` — route path pattern
  - `gateway.consumer.id` — consumer ID (if authenticated)
  - `gateway.ratelimit.decision` — allowed/denied
**And** these attributes are searchable in Jaeger

### AC5: Trace-Log Correlation
**Given** structured logging is configured
**When** log entries are written
**Then** traceId and spanId are included in JSON logs
**And** logs can be correlated with traces in Jaeger
**And** MDC context is properly bridged to reactive context

### AC6: Sampling Configuration
**Given** tracing is enabled in production
**When** high traffic occurs
**Then** sampling is configurable (default 10% in prod, 100% in dev)
**And** sampling rate is tunable via environment variable

### AC7: Jaeger Grafana Integration
**Given** traces are collected in Jaeger
**When** developer views Grafana
**Then** Jaeger data source is configured
**And** trace links are available from SLO dashboard (drill-down)

### AC8: Documentation
**Given** tracing is implemented
**When** developer needs to use tracing
**Then** documentation exists in `docs/tracing.md`:
  - How to view traces in Jaeger UI
  - How to add custom spans in code
  - How to correlate logs with traces
  - Sampling configuration

## Tasks / Subtasks

- [x] Task 1: Add OpenTelemetry Dependencies (AC: 1)
  - [x] 1.1 Add `micrometer-tracing-bridge-otel` to gateway-core/build.gradle.kts
  - [x] 1.2 Add `micrometer-tracing-bridge-otel` to gateway-admin/build.gradle.kts
  - [x] 1.3 Add `opentelemetry-exporter-otlp` to both modules
  - [x] 1.4 Verify dependency versions via Spring Boot BOM (не указывать версии явно)
- [x] Task 2: Configure Tracing Properties (AC: 1, 3, 6)
  - [x] 2.1 Add OTLP endpoint configuration to gateway-core/application.yml
  - [x] 2.2 Add OTLP endpoint configuration to gateway-admin/application.yml
  - [x] 2.3 Configure W3C Trace Context propagation
  - [x] 2.4 Configure sampling probability (profile-based: 1.0 dev, 0.1 prod)
- [x] Task 3: Deploy Jaeger in Infra Project (AC: 2)
  - [x] 3.1 Create `G:\Projects\infra\infra\infra.jaeger.yml` service definition
  - [x] 3.2 Create `tracing-net` network: `docker network create tracing-net`
  - [x] 3.3 Configure local port 16686 for Jaeger UI
  - [x] 3.4 Update `G:\Projects\infra\scripts\start.ps1` with jaeger service
  - [x] 3.5 Create data directory `E:\docker-data\jaeger`
- [x] Task 4: Connect ApiGateway to tracing-net (AC: 1)
  - [x] 4.1 Add `tracing-net: external: true` to docker-compose.yml networks
  - [x] 4.2 Add `tracing-net` to gateway-core service networks
  - [x] 4.3 Add `tracing-net` to gateway-admin service networks
- [x] Task 5: Add Custom Span Attributes (AC: 4)
  - [x] 5.1 Create TracingAttributesFilter in gateway-core
  - [x] 5.2 Add route.id and route.path attributes after routing
  - [x] 5.3 Add consumer.id from JWT claims (exchange attribute)
  - [x] 5.4 Add ratelimit.decision from RateLimitFilter
  - [x] 5.5 Add unit tests for TracingAttributesFilter
- [x] Task 6: Configure Trace-Log Correlation (AC: 5)
  - [x] 6.1 Add traceId/spanId to EXISTING logback-spring.xml в gateway-core
  - [x] 6.2 Create logback-spring.xml для gateway-admin (сейчас отсутствует!)
  - [x] 6.3 Verify MDC contains traceId/spanId (context propagation уже работает)
  - [x] 6.4 Test log correlation with trace lookup
- [x] Task 7: Configure Grafana Jaeger Integration (AC: 7)
  - [x] 7.1 Create `G:\Projects\infra\config\grafana\provisioning\datasources\jaeger.yml`
  - [x] 7.2 Add trace links to SLO dashboard panels (exemplars or data links)
- [x] Task 8: Create Tracing Documentation (AC: 8)
  - [x] 8.1 Create docs/tracing.md with usage guide
  - [x] 8.2 Document Jaeger UI navigation
  - [x] 8.3 Document custom span creation patterns
  - [x] 8.4 Document sampling configuration

## Dev Notes

### КРИТИЧЕСКИ ВАЖНО: Существующий код

**НЕ СОЗДАВАТЬ дублирующий код! Следующее УЖЕ РЕАЛИЗОВАНО:**

| Компонент | Файл | Статус |
|-----------|------|--------|
| Context Propagation | `gateway-core/config/MdcContextConfig.kt` | ✅ УЖЕ ЕСТЬ `Hooks.enableAutomaticContextPropagation()` |
| MDC Registration | `gateway-core/config/MdcContextConfig.kt` | ✅ УЖЕ ЕСТЬ `ContextRegistry.registerThreadLocalAccessor()` |
| Correlation ID | `gateway-core/filter/CorrelationIdFilter.kt` | ✅ УЖЕ propagates X-Correlation-ID |
| Structured Logging | `gateway-core/logback-spring.xml` | ✅ УЖЕ JSON с correlationId (нужно ДОБАВИТЬ traceId) |
| context-propagation | `gateway-core/build.gradle.kts` | ✅ УЖЕ есть зависимость |

**❌ НЕ ДЕЛАТЬ:**
- НЕ создавать TracingConfig.kt — context propagation уже в MdcContextConfig.kt
- НЕ создавать logback-spring.xml с нуля — РАСШИРИТЬ существующий
- НЕ добавлять `io.micrometer:context-propagation` — уже есть в build.gradle.kts
- НЕ дублировать Hooks.enableAutomaticContextPropagation() — уже вызывается

### Зависимости (build.gradle.kts)

**Добавить в gateway-core И gateway-admin:**

```kotlin
dependencies {
    // Micrometer Tracing с OpenTelemetry bridge
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    // OTLP exporter для отправки в Jaeger
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // context-propagation УЖЕ ЕСТЬ в gateway-core, добавить в gateway-admin
}
```

**Версии управляются Spring Boot BOM** — не указываем явно.

### Конфигурация application.yml

**Добавить в gateway-core/application.yml и gateway-admin/application.yml:**

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
    propagation:
      type: w3c
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://jaeger:4318/v1/traces}
```

### Jaeger Service Definition

**Создать файл: `G:\Projects\infra\infra\infra.jaeger.yml`**

```yaml
services:
  jaeger:
    image: jaegertracing/all-in-one:latest
    container_name: jaeger
    restart: unless-stopped
    environment:
      - COLLECTOR_OTLP_ENABLED=true
      - SPAN_STORAGE_TYPE=badger
      - BADGER_EPHEMERAL=false
      - BADGER_DIRECTORY_VALUE=/badger/data
      - BADGER_DIRECTORY_KEY=/badger/key
    volumes:
      - /e/docker-data/jaeger:/badger
    ports:
      - "16686:16686"   # UI — локальный доступ
    expose:
      - "4317"          # OTLP gRPC (внутренний)
      - "4318"          # OTLP HTTP (внутренний)
    networks:
      - tracing-net
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:14269/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

networks:
  tracing-net:
    external: true
```

**Создать network (одноразово):**
```powershell
docker network create tracing-net
```

**Создать data directory:**
```powershell
mkdir E:\docker-data\jaeger
```

### ApiGateway docker-compose.yml Updates

**Добавить в networks секцию:**
```yaml
networks:
  tracing-net:
    external: true
```

**Добавить tracing-net к gateway services:**
```yaml
gateway-core:
  networks:
    - gateway-network
    - traefik-net
    - postgres-net
    - redis-net
    - monitoring-net
    - tracing-net  # ДОБАВИТЬ

gateway-admin:
  networks:
    - gateway-network
    - traefik-net
    - postgres-net
    - redis-net
    - monitoring-net
    - tracing-net  # ДОБАВИТЬ
```

### TracingAttributesFilter

**Создать: `gateway-core/filter/TracingAttributesFilter.kt`**

```kotlin
package com.company.gateway.core.filter

import io.micrometer.tracing.Tracer
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Добавляет кастомные атрибуты к текущему span для поиска в Jaeger.
 * Выполняется после routing (HIGHEST_PRECEDENCE + 100).
 */
@Component
class TracingAttributesFilter(
    private val tracer: Tracer
) : GlobalFilter, Ordered {

    companion object {
        const val ROUTE_ID_ATTR = "gateway.route.id"
        const val ROUTE_PATH_ATTR = "gateway.route.path"
        const val CONSUMER_ID_ATTR = "gateway.consumer.id"
        const val RATELIMIT_DECISION_ATTR = "gateway.ratelimit.decision"
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 100

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val span = tracer.currentSpan() ?: return chain.filter(exchange)

        // Route info (после RoutePredicateHandlerMapping)
        exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)?.let { route ->
            span.tag(ROUTE_ID_ATTR, route.id)
            route.uri?.path?.let { path -> span.tag(ROUTE_PATH_ATTR, path) }
        }

        // Consumer ID (устанавливается ConsumerIdentityFilter)
        exchange.getAttribute<String>("consumerId")?.let { consumerId ->
            span.tag(CONSUMER_ID_ATTR, consumerId)
        }

        // Rate limit decision (устанавливается RateLimitFilter)
        exchange.getAttribute<String>("rateLimitDecision")?.let { decision ->
            span.tag(RATELIMIT_DECISION_ATTR, decision)
        }

        return chain.filter(exchange)
    }
}
```

### Logback Configuration Updates

**РАСШИРИТЬ существующий `gateway-core/logback-spring.xml`:**

Добавить в `<encoder class="net.logstash.logback.encoder.LogstashEncoder">`:
```xml
<includeMdcKeyName>traceId</includeMdcKeyName>
<includeMdcKeyName>spanId</includeMdcKeyName>
```

Добавить в dev profile pattern:
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] [%X{spanId}] [%X{correlationId}] - %msg%n</pattern>
```

**СОЗДАТЬ `gateway-admin/src/main/resources/logback-spring.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="!dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSXXX</timestampPattern>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>correlationId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
        <logger name="org.springframework" level="WARN" />
    </springProfile>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] [%X{spanId}] - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>
</configuration>
```

**Добавить dependency в gateway-admin/build.gradle.kts:**
```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
```

### Grafana Jaeger Data Source

**Создать: `G:\Projects\infra\config\grafana\provisioning\datasources\jaeger.yml`**

```yaml
apiVersion: 1

datasources:
  - name: Jaeger
    type: jaeger
    uid: jaeger
    access: proxy
    url: http://jaeger:16686
    isDefault: false
    editable: false
```

### Ожидаемый результат

**Jaeger UI (`http://localhost:16686`):**
- Services: gateway-core, gateway-admin
- Operations: GET, POST, filter executions
- Tags: gateway.route.id, gateway.consumer.id, gateway.ratelimit.decision

**Logs (structured JSON):**
```json
{
  "timestamp": "2026-03-03T12:34:56.789Z",
  "level": "INFO",
  "traceId": "abc123def456...",
  "spanId": "789xyz...",
  "correlationId": "uuid-...",
  "message": "Request forwarded to upstream"
}
```

### Architecture Compliance

- **Reactive Patterns:** Использует существующий MdcContextConfig, не блокирует reactive chain
- **RFC 7807:** N/A — no API changes
- **Correlation ID:** Сохраняется для backward compatibility
- **Testing:** Unit tests для TracingAttributesFilter

### Project Structure Notes

**Новые файлы:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/TracingAttributesFilter.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/TracingAttributesFilterTest.kt`
- `backend/gateway-admin/src/main/resources/logback-spring.xml`
- `docs/tracing.md`

**Изменяемые файлы:**
- `backend/gateway-core/build.gradle.kts` — добавить tracing dependencies
- `backend/gateway-admin/build.gradle.kts` — добавить tracing + logstash dependencies
- `backend/gateway-core/src/main/resources/application.yml` — OTLP config
- `backend/gateway-admin/src/main/resources/application.yml` — OTLP config
- `backend/gateway-core/src/main/resources/logback-spring.xml` — добавить traceId/spanId
- `docker-compose.yml` — добавить tracing-net network
- `docker-compose.override.yml` — добавить tracing-net к services

**Infra project (G:\Projects\infra):**
- `infra/infra.jaeger.yml` — NEW
- `config/grafana/provisioning/datasources/jaeger.yml` — NEW
- `scripts/start.ps1` — UPDATE (добавить jaeger в $ValidServices и $StartOrder)

### Dependencies

**Новые зависимости gateway-core:**
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`

**Новые зависимости gateway-admin:**
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `io.micrometer:context-propagation`
- `net.logstash.logback:logstash-logback-encoder:8.0`

### References

- [Source: architecture-audit-2026-03-01.md#4 Observability — "Нет distributed tracing"]
- [Source: story-14-3-custom-metrics-sli-slo.md — паттерны Micrometer]
- [Source: MdcContextConfig.kt — существующий context propagation]
- [Spring Boot Tracing Documentation](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
- [Distributed Tracing with OpenTelemetry and Jaeger](https://refactorfirst.com/distributed-tracing-with-opentelemetry-jaeger-in-spring-boot)

### Rollback Plan

**Tracing dependencies:**
- Удалить micrometer-tracing-bridge-otel и opentelemetry-exporter-otlp
- Удалить OTLP конфигурацию из application.yml
- Приложение работает без tracing

**Jaeger:**
- Остановить Jaeger в infra
- Traces перестанут экспортироваться, приложение работает

**TracingAttributesFilter:**
- Удалить фильтр — routing не затрагивается

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- ✅ Добавлены OpenTelemetry dependencies в gateway-core и gateway-admin
- ✅ Сконфигурирован OTLP endpoint и W3C propagation в application.yml
- ✅ Создан infra.jaeger.yml для Jaeger в infra проекте
- ✅ Создана tracing-net сеть и директория E:\docker-data\jaeger
- ✅ Обновлён start.ps1 для включения jaeger в список сервисов
- ✅ ApiGateway подключен к tracing-net (docker-compose.yml/override.yml)
- ✅ Создан TracingAttributesFilter с кастомными span attributes
- ✅ Обновлён RateLimitFilter для записи decision в exchange attributes
- ✅ Добавлены unit тесты для TracingAttributesFilter (9 тестов, все прошли)
- ✅ Обновлён logback-spring.xml в gateway-core с traceId/spanId
- ✅ Создан logback-spring.xml для gateway-admin с traceId/spanId
- ✅ Создан Jaeger datasource для Grafana
- ✅ Создана документация docs/tracing.md
- ⚠️ Task 7.2 (trace links in SLO dashboard) отложен — требует manual update в Grafana UI

### File List

**Новые файлы:**
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/TracingAttributesFilter.kt
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/TracingAttributesFilterTest.kt
- backend/gateway-admin/src/main/resources/logback-spring.xml
- docs/tracing.md
- G:\Projects\infra\infra\infra.jaeger.yml (infra project)
- G:\Projects\infra\config\grafana\provisioning\datasources\jaeger.yml (infra project)

**Изменённые файлы:**
- backend/gateway-core/build.gradle.kts — добавлены tracing dependencies
- backend/gateway-admin/build.gradle.kts — добавлены tracing + logstash dependencies
- backend/gateway-core/src/main/resources/application.yml — OTLP config
- backend/gateway-admin/src/main/resources/application.yml — OTLP config
- backend/gateway-core/src/main/resources/logback-spring.xml — добавлены traceId/spanId
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt — записывает decision в exchange
- docker-compose.yml — добавлена tracing-net network
- docker-compose.override.yml — добавлены tracing-net и OTLP env vars
- G:\Projects\infra\scripts\start.ps1 — добавлен jaeger в ValidServices и StartOrder (infra project)

## Change Log

| Date | Changes |
|------|---------|
| 2026-03-03 | Story implementation: OpenTelemetry tracing with Jaeger backend |
