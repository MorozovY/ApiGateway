# Story 1.2: Database Setup & Initial Migrations

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want PostgreSQL database configured with R2DBC and Flyway migrations,
So that I can persist route configurations reliably.

## Acceptance Criteria

1. **AC1:** При старте gateway-admin приложения Flyway выполняет миграцию V1__create_routes.sql
2. **AC2:** Таблица `routes` создаётся с колонками:
   - `id` (UUID, primary key)
   - `path` (VARCHAR(255), not null, unique)
   - `upstreamUrl` (VARCHAR(512), not null)
   - `methods` (VARCHAR array)
   - `status` (VARCHAR(20): draft/pending/published/rejected)
   - `createdBy` (UUID, nullable)
   - `createdAt` (TIMESTAMP with timezone)
   - `updatedAt` (TIMESTAMP with timezone)
3. **AC3:** R2DBC connection pool настроен (max 10 connections)
4. **AC4:** При успешном подключении к БД приложение логирует "Database connection established"
5. **AC5:** Docker Compose корректно инициализирует PostgreSQL и приложение может к нему подключиться
6. **AC6:** Flyway migration baseline правильно версионирован (V1__)

## Tasks / Subtasks

- [x] **Task 1: Flyway Configuration** (AC: #1, #6)
  - [x] Subtask 1.1: Добавить Flyway dependencies в gateway-admin/build.gradle.kts (flyway-core, flyway-database-postgresql)
  - [x] Subtask 1.2: Настроить Flyway в application.yml (spring.flyway.*)
  - [x] Subtask 1.3: Создать директорию src/main/resources/db/migration/

- [x] **Task 2: R2DBC Configuration** (AC: #3, #4)
  - [x] Subtask 2.1: Настроить R2DBC connection pool в application.yml
  - [x] Subtask 2.2: Создать R2dbcConfig.kt с настройками пула (maxSize=10)
  - [x] Subtask 2.3: Добавить логирование подключения к БД

- [x] **Task 3: Routes Migration** (AC: #2, #5)
  - [x] Subtask 3.1: Создать V1__create_routes.sql с полной схемой таблицы
  - [x] Subtask 3.2: Добавить индексы для path и status
  - [x] Subtask 3.3: Создать Route entity в gateway-common

- [x] **Task 4: Verification** (AC: #1-#6)
  - [x] Subtask 4.1: Запустить docker-compose up -d (PostgreSQL)
  - [x] Subtask 4.2: Запустить gateway-admin и проверить выполнение миграций
  - [x] Subtask 4.3: Проверить создание таблицы routes в PostgreSQL
  - [x] Subtask 4.4: Проверить логи подключения к БД

## Dev Notes

### Previous Story Intelligence (Story 1.1)

**Выполненная работа:**
- Создан Gradle multi-module проект (gateway-common, gateway-admin, gateway-core)
- Настроены все Spring Boot dependencies включая R2DBC, Flyway
- Docker Compose с PostgreSQL 16 и Redis 7 готов
- Spring Security базово настроен в SecurityConfig.kt

**Уроки из Code Review:**
- HIGH-2: Версии dependencies должны быть в gradle.properties - уже исправлено
- Тесты должны быть в src/test/kotlin/ - структура создана

**Созданные файлы, релевантные для этой истории:**
- `backend/gateway-admin/build.gradle.kts` - dependencies уже включают R2DBC и Flyway
- `backend/gateway-admin/src/main/resources/application.yml` - нужно добавить R2DBC и Flyway config
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/SecurityConfig.kt` - готов
- `docker-compose.yml` - PostgreSQL 16 настроен на порту 5432

**Конфигурация PostgreSQL из docker-compose.yml:**
```yaml
postgres:
  image: postgres:16
  ports:
    - "5432:5432"
  environment:
    POSTGRES_DB: gateway
    POSTGRES_USER: gateway
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-gateway}
  volumes:
    - postgres_data:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U gateway"]
```

### Architecture Compliance

**MANDATORY: Database Naming Conventions (из architecture.md)**

| Layer | Convention | Examples |
|-------|------------|----------|
| **Tables** | snake_case (plural) | `routes`, `rate_limits`, `audit_logs` |
| **Columns** | snake_case | `user_id`, `created_at`, `upstream_url` |

> **NOTE:** PostgreSQL преобразует все идентификаторы в lowercase без кавычек. snake_case — стандарт для PostgreSQL и Spring Data R2DBC.

**R2DBC vs JDBC:**
- Проект использует REACTIVE stack (Spring WebFlux)
- Использовать ТОЛЬКО R2DBC, НЕ JDBC
- Connection pool: r2dbc-pool (io.r2dbc:r2dbc-pool)

**Flyway + R2DBC особенность:**
- Flyway использует JDBC для миграций (это нормально)
- R2DBC используется для runtime queries
- Нужны ОБЕ зависимости: postgresql JDBC driver (для Flyway) + r2dbc-postgresql

### Technical Requirements

**Dependencies (уже в build.gradle.kts, проверить):**
```kotlin
// R2DBC
implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
implementation("io.r2dbc:r2dbc-postgresql")
implementation("io.r2dbc:r2dbc-pool")

// Flyway (uses JDBC internally)
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
runtimeOnly("org.postgresql:postgresql") // JDBC driver for Flyway
```

**application.yml структура:**
```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:gateway}
    username: ${POSTGRES_USER:gateway}
    password: ${POSTGRES_PASSWORD:gateway}
    pool:
      initial-size: 2
      max-size: 10
      max-idle-time: 30m

  flyway:
    enabled: true
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:gateway}
    user: ${POSTGRES_USER:gateway}
    password: ${POSTGRES_PASSWORD:gateway}
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### File Structure Requirements

**Миграции располагаются в:**
```
backend/gateway-admin/src/main/resources/db/migration/
├── V1__create_routes.sql
```

**Entity располагается в gateway-common:**
```
backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/
├── Route.kt
```

**Config располагается в gateway-admin:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/
├── R2dbcConfig.kt
├── SecurityConfig.kt (уже есть)
```

### V1__create_routes.sql Specification

```sql
-- V1__create_routes.sql
-- ApiGateway Routes Table

CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path VARCHAR(255) NOT NULL UNIQUE,
    upstream_url VARCHAR(512) NOT NULL,
    methods VARCHAR(20)[] DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'pending', 'published', 'rejected')),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for filtering by status
CREATE INDEX idx_routes_status ON routes (status);

-- Index for path lookups (gateway routing)
CREATE INDEX idx_routes_path ON routes (path);

-- Comment for documentation
COMMENT ON TABLE routes IS 'API Gateway route configurations';
COMMENT ON COLUMN routes.path IS 'URL path pattern for routing (e.g., /api/orders)';
COMMENT ON COLUMN routes.upstream_url IS 'Target upstream service URL';
COMMENT ON COLUMN routes.methods IS 'Allowed HTTP methods (GET, POST, PUT, DELETE, PATCH)';
COMMENT ON COLUMN routes.status IS 'Route lifecycle status: draft -> pending -> published/rejected';
```

> **NOTE:** snake_case для колонок — стандарт PostgreSQL, не требует кавычек в SQL.

### Route.kt Entity Specification

```kotlin
// Route.kt - в gateway-common/model/
package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("routes")
data class Route(
    @Id
    val id: UUID? = null,

    val path: String,

    @Column("upstreamUrl")
    val upstreamUrl: String,

    val methods: List<String> = emptyList(),

    val status: RouteStatus = RouteStatus.DRAFT,

    @Column("createdBy")
    val createdBy: UUID? = null,

    @Column("createdAt")
    val createdAt: Instant? = null,

    @Column("updatedAt")
    val updatedAt: Instant? = null
)

enum class RouteStatus {
    DRAFT, PENDING, PUBLISHED, REJECTED
}
```

### R2dbcConfig.kt Specification

```kotlin
// R2dbcConfig.kt - в gateway-admin/config/
package com.company.gateway.admin.config

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import java.time.Duration
import jakarta.annotation.PostConstruct

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.company.gateway.admin.repository"])
class R2dbcConfig(
    @Value("\${spring.r2dbc.pool.max-size:10}") private val maxPoolSize: Int
) : AbstractR2dbcConfiguration() {

    private val log = LoggerFactory.getLogger(R2dbcConfig::class.java)

    @PostConstruct
    fun logConnectionEstablished() {
        log.info("Database connection established")
    }

    @Bean
    override fun connectionFactory(): ConnectionFactory {
        // Connection pool is auto-configured by Spring Boot
        // This is for explicit configuration if needed
        return super.connectionFactory()
    }
}
```

### Testing Requirements

**Минимальные тесты:**
1. Integration test: проверить что Flyway выполнил миграции
2. Repository test: проверить CRUD операции с Route entity

**Test dependencies (в build.gradle.kts):**
```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("io.projectreactor:reactor-test")
testImplementation("org.testcontainers:postgresql")
testImplementation("org.testcontainers:r2dbc")
```

### Project Structure Notes

**Alignment:**
- Миграции в `db/migration/` - стандартное расположение Flyway
- Entity в gateway-common для shared access между модулями
- Config в gateway-admin т.к. это там используется

**Potential Conflicts:**
- Spring Boot auto-configuration R2DBC может конфликтовать с manual config
- Рекомендуется использовать application.yml настройки вместо Java config где возможно

### References

- [Source: architecture.md#Implementation Patterns & Consistency Rules] - Naming conventions (camelCase for columns)
- [Source: architecture.md#Core Architectural Decisions] - PostgreSQL 16 + R2DBC, Flyway
- [Source: architecture.md#Project Structure & Boundaries] - File locations
- [Source: epics.md#Story 1.2] - Original AC and requirements
- [Source: prd.md#Data Schemas] - Route entity fields
- [Source: 1-1-project-scaffolding-monorepo-setup.md] - Previous story context

### Anti-Patterns to Avoid

- **DO NOT** use camelCase for DB column names (architecture.md requires snake_case for PostgreSQL)
- **DO NOT** use blocking JDBC in runtime code (only Flyway uses JDBC)
- **DO NOT** create ConnectionFactory manually if Spring Boot auto-config works
- **DO NOT** hardcode database credentials (use environment variables)
- **DO NOT** skip indexes on frequently queried columns (path, status)
- **DO NOT** use VARCHAR without length limit for path/url columns

### Environment Variables

Использовать переменные из .env.example (созданы в Story 1.1):
```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=gateway
POSTGRES_USER=gateway
POSTGRES_PASSWORD=gateway
```

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- R2DBC + PostgreSQL camelCase columns issue: PostgreSQL без кавычек преобразует все идентификаторы в lowercase. Изменено на snake_case для совместимости со стандартным Spring Data R2DBC.
- Testcontainers Windows fix: добавлен testcontainers.properties с NpipeSocketClientProviderStrategy для Docker Desktop.

### Completion Notes List

1. **Task 1 (Flyway Configuration):**
   - Flyway dependencies уже присутствовали в build.gradle.kts из Story 1.1
   - Добавлен `locations: classpath:db/migration` в application.yml
   - Создана директория db/migration/

2. **Task 2 (R2DBC Configuration):**
   - Настроен R2DBC pool: initial-size=2, max-size=10, max-idle-time=30m
   - Добавлен r2dbc-pool dependency
   - Создан R2dbcConfig.kt с custom converters для List<String> и RouteStatus
   - R2dbcConverters.kt содержит StringArray и RouteStatus конвертеры
   - Логирование "Database connection established" через @PostConstruct

3. **Task 3 (Routes Migration):**
   - Создан V1__create_routes.sql с полной схемой (snake_case колонки для PostgreSQL совместимости)
   - Индексы idx_routes_status и idx_routes_path созданы
   - Route.kt и RouteStatus enum созданы в gateway-common
   - RouteRepository.kt создан с базовыми методами findByStatus и findByPath

4. **Task 4 (Verification):**
   - 11 integration тестов написаны и прошли успешно
   - Testcontainers используются для изолированного тестирования с PostgreSQL 16
   - Проверены: создание таблицы, колонки, индексы, constraints, CRUD операции

### Change Log

- 2026-02-12: Story 1.2 implementation completed. All 11 tests passing.
- 2026-02-12: Code Review fixes applied:
  - HIGH-1: R2dbcConfig.kt - replaced @PostConstruct with ApplicationReadyEvent for proper connection verification
  - HIGH-2: Added V2__add_updated_at_trigger.sql for auto-updating updated_at on row modification
  - HIGH-3: Fixed documentation - snake_case is REQUIRED for DB columns per architecture.md
  - MEDIUM-1: gateway-common/build.gradle.kts - versions now from gradle.properties
  - MEDIUM-2: Disabled Redis auto-configuration in tests
  - MEDIUM-3: RouteRepository.kt - fixed Mono import
  - MEDIUM-4: Added tests for created_at/updated_at auto-population

### File List

**Created:**
- `backend/gateway-admin/src/main/resources/db/migration/V1__create_routes.sql`
- `backend/gateway-admin/src/main/resources/db/migration/V2__add_updated_at_trigger.sql` (Code Review fix)
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/R2dbcConfig.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/R2dbcConverters.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/DatabaseIntegrationTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/repository/RouteRepositoryTest.kt`
- `backend/gateway-admin/src/test/resources/testcontainers.properties`
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt`

**Modified:**
- `backend/gateway-admin/src/main/resources/application.yml` (added R2DBC pool config, Flyway locations)
- `backend/gateway-admin/build.gradle.kts` (added r2dbc-pool, testcontainers dependencies)
- `backend/gateway-common/build.gradle.kts` (added spring-data-relational dependencies, versions from gradle.properties)