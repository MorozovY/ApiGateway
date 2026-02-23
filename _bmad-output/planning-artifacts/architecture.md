---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
status: 'complete'
completedAt: '2026-02-11'
inputDocuments:
  - 'prd.md'
  - 'product-brief-ApiGateway-2026-02-10.md'
  - 'brainstorming-session-2026-02-10.md'
workflowType: 'architecture'
project_name: 'ApiGateway'
user_name: 'Yury'
date: '2026-02-11'
revisions:
  - date: '2026-02-23'
    author: 'Yury'
    description: 'Phase 2: Keycloak Integration & Multi-tenant Metrics architecture'
    sections_added:
      - 'Keycloak Integration'
      - 'Gateway Core Filter Chain (Phase 2)'
      - 'Route Authentication Configuration'
      - 'Multi-tenant Metrics'
      - 'Per-consumer Rate Limiting'
      - 'Consumer Management'
      - 'Admin UI Keycloak Integration'
      - 'Gateway Admin Keycloak Integration'
      - 'Grafana Dashboard Updates'
      - 'Phase 2 Implementation Sequence'
      - 'Phase 2 Architecture Validation'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

31 FR в 7 категориях:
- **Route Management (FR1-6):** CRUD маршрутов, фильтрация, клонирование
- **Approval Workflow (FR7-12):** Draft → Pending → Published/Rejected state machine
- **Rate Limiting (FR13-16):** Политики лимитов, назначение на маршруты
- **Monitoring & Metrics (FR17-20):** Real-time метрики, Prometheus export
- **Audit & Compliance (FR21-24):** Аудит-лог, история изменений, фильтрация
- **User & Access Management (FR25-27):** Аутентификация, RBAC (Developer, Security, Admin)
- **Gateway Runtime (FR28-31):** Маршрутизация, error handling, hot-reload, logging

**Non-Functional Requirements:**

| Категория | Ключевые требования |
|-----------|---------------------|
| **Performance** | P50 < 50ms, P95 < 200ms, P99 < 500ms (Gateway latency) |
| **Reliability** | 99.9% uptime, graceful degradation, zero-downtime deploys |
| **Scalability** | 100 RPS baseline, 1000 RPS headroom, horizontal scaling |
| **Security** | RBAC, audit trail, TLS 1.2+, secrets management |
| **Observability** | Prometheus metrics, structured logs, health checks |

**Scale & Complexity:**

- Primary domain: **API Backend**
- Complexity level: **Medium**
- Project context: **Greenfield**
- Estimated architectural components: 5-7 (Gateway, Admin API, Admin UI, DB, Cache, Monitoring)

### Technical Constraints & Dependencies

| Constraint | Source | Impact |
|------------|--------|--------|
| Spring Cloud Gateway | PRD/Brainstorming | Определяет reactive stack (WebFlux, Netty) |
| PostgreSQL + R2DBC | PRD | Reactive database access |
| Redis | PRD | Rate limiting, config caching |
| React SPA | PRD | Separate frontend build |
| Prometheus + Grafana | PRD | Metrics format, dashboards |

### Cross-Cutting Concerns Identified

| Concern | Affected Components | Approach |
|---------|---------------------|----------|
| **Authentication** | Admin API, Admin UI | JWT/API keys (MVP), Keycloak (Phase 2) |
| **Authorization** | All admin operations | RBAC with 3 roles |
| **Audit Logging** | Route/RateLimit changes | Event-based audit trail |
| **Error Handling** | Gateway, Admin API | Structured errors, correlation IDs |
| **Configuration** | Gateway runtime | Hot-reload from DB, caching |
| **Metrics** | All components | Micrometer → Prometheus |

## Starter Template Evaluation

### Primary Technology Domain

**Multi-component API Backend** с отдельным Admin UI:
- Gateway Service (Kotlin/Spring Cloud Gateway)
- Admin API (часть Gateway или отдельный модуль)
- Admin UI (React SPA)

### Project Structure: Monorepo

```
api-gateway/
├── backend/                    # Gradle multi-module
│   ├── gateway-core/          # Spring Cloud Gateway
│   ├── gateway-admin/         # Admin API
│   └── gateway-common/        # Shared entities, utils
├── frontend/                   # React SPA (Vite)
│   └── admin-ui/
├── docker/                     # Docker Compose configs
├── docs/                       # Documentation
└── build.gradle.kts           # Root build file
```

### Selected Starters

**Backend: Spring Initializr (Spring Boot 3.4.x)**

```bash
curl https://start.spring.io/starter.zip \
  -d type=gradle-project-kotlin \
  -d language=kotlin \
  -d bootVersion=3.4.2 \
  -d baseDir=backend \
  -d groupId=com.company \
  -d artifactId=api-gateway \
  -d name=api-gateway \
  -d packageName=com.company.gateway \
  -d dependencies=cloud-gateway,webflux,data-r2dbc,postgresql,data-redis-reactive,actuator,prometheus \
  -o backend.zip
```

**Frontend: Vite + React + TypeScript**

```bash
npm create vite@latest frontend/admin-ui -- --template react-ts
```

### Architectural Decisions Provided by Starters

**Backend (Spring Cloud Gateway):**

| Решение | Значение |
|---------|----------|
| **Runtime** | Netty (non-blocking) |
| **Reactive Stack** | Project Reactor (Mono/Flux) |
| **Database Access** | R2DBC (reactive) |
| **Configuration** | application.yml + profiles |
| **Build** | Gradle Kotlin DSL |
| **Testing** | JUnit 5 + WebTestClient |

**Frontend (Vite + React):**

| Решение | Значение |
|---------|----------|
| **Build Tool** | Vite (esbuild + Rollup) |
| **Language** | TypeScript (strict) |
| **Dev Server** | HMR enabled |
| **Production** | Optimized bundle |

### Additional Dependencies

**Backend:**
- `spring-boot-starter-security` — authentication/authorization
- `kotlinx-coroutines-reactor` — Kotlin coroutines support
- `flyway-core` — database migrations
- `testcontainers` — integration testing

**Frontend:**
- `react-router-dom` — routing
- `@tanstack/react-query` — data fetching & caching
- `axios` — HTTP client
- `antd` — UI component library
- `react-hook-form` + `zod` — form handling & validation

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Database: PostgreSQL + R2DBC
- Cache: Redis (primary) + Caffeine (local fallback)
- Authentication: JWT self-issued (MVP)
- API Format: REST + OpenAPI

**Important Decisions (Shape Architecture):**
- Migrations: Flyway
- Error Format: RFC 7807 Problem Details
- Frontend State: React Query + Context
- UI Library: Ant Design

**Deferred Decisions (Post-MVP / Phase 2):**
- Keycloak integration
- Kubernetes deployment
- Circuit breaker (Resilience4j)

### Data Architecture

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **Database** | PostgreSQL 16 + R2DBC | Reactive access, production-ready |
| **Migrations** | Flyway | Spring Boot стандарт, version control |
| **Primary Cache** | Redis | Distributed, shared state |
| **Local Cache** | Caffeine | Fast fallback при недоступности Redis |
| **Cache Strategy** | Write-through + event invalidation | Consistency + performance |

### Authentication & Security

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **MVP Auth** | JWT self-issued | Stateless, простота, готовность к Keycloak |
| **Token Storage** | HTTP-only cookies | XSS protection |
| **Session Type** | Stateless | Horizontal scaling без session affinity |
| **Authorization** | RBAC (Developer, Security, Admin) | Простая модель для 3 ролей |
| **Password Hashing** | BCrypt | Spring Security default |

### API & Communication Patterns

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **API Style** | REST | Простота, широкая поддержка |
| **Documentation** | OpenAPI 3.0 (springdoc-openapi) | Auto-generated, interactive |
| **Error Format** | RFC 7807 Problem Details | Стандарт, structured errors |
| **Correlation** | X-Correlation-ID header | Traceability across logs |
| **Versioning** | URL path (/api/v1/) | Explicit, simple |

### Frontend Architecture

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **Build Tool** | Vite | Fast HMR, modern bundling |
| **State Management** | React Query + Context | Server state + minimal client state |
| **UI Library** | Ant Design | Ready-made admin components |
| **Forms** | React Hook Form + Zod | Performance + type-safe validation |
| **Routing** | React Router v6 | Standard, nested routes |
| **HTTP Client** | Axios | Interceptors, error handling |

### Infrastructure & Deployment

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **Local Dev** | Docker Compose | Full stack locally |
| **Container Registry** | Docker Hub | Standard, accessible |
| **Logging Format** | JSON structured | Loki/ELK ready |
| **Metrics Format** | Prometheus | Micrometer export |
| **Health Checks** | Spring Actuator | /health, /ready endpoints |

### Decision Impact Analysis

**Implementation Sequence:**
1. Project scaffolding (Spring Initializr + Vite)
2. Database schema + Flyway migrations
3. Core entities (Route, RateLimit, User)
4. JWT authentication
5. Admin API endpoints
6. Gateway routing logic
7. Admin UI

**Cross-Component Dependencies:**
- JWT: Backend issues → Frontend stores in cookies → Backend validates
- Cache: Redis shared between Gateway instances, Caffeine per-instance
- Config: DB → Redis cache → Caffeine → Gateway runtime

## Implementation Patterns & Consistency Rules

### Naming Patterns

| Область | Конвенция | Примеры |
|---------|-----------|---------|
| **Database tables** | snake_case (plural) | `routes`, `rate_limits`, `audit_logs` |
| **Database columns** | snake_case | `user_id`, `created_at`, `upstream_url` |
| **API JSON fields** | camelCase | `{ "routeId": 1, "upstreamUrl": "..." }` |
| **Kotlin variables** | camelCase | `val routeId`, `fun findByPath()` |
| **Kotlin classes** | PascalCase | `RouteService`, `RateLimitRepository` |
| **React components** | PascalCase | `RouteList.tsx`, `RouteForm.tsx` |
| **React hooks** | camelCase + use | `useRoutes()`, `useAuth()` |
| **CSS classes** | kebab-case | `.route-card`, `.form-error` |

### Structure Patterns

**Backend (Kotlin/Spring):**
```
backend/
├── src/main/kotlin/com/company/gateway/
│   ├── config/           # Spring configuration
│   ├── controller/       # REST controllers
│   ├── service/          # Business logic
│   ├── repository/       # Data access
│   ├── model/            # Domain entities
│   ├── dto/              # Request/Response DTOs
│   ├── security/         # Auth, JWT, filters
│   └── exception/        # Custom exceptions
├── src/main/resources/
│   ├── db/migration/     # Flyway migrations
│   └── application.yml
└── src/test/kotlin/      # Tests mirror main structure
```

**Frontend (React):**
```
frontend/admin-ui/src/
├── features/
│   ├── routes/           # Route management feature
│   │   ├── components/   # RouteList, RouteForm, RouteCard
│   │   ├── hooks/        # useRoutes, useRouteForm
│   │   ├── api/          # routesApi.ts
│   │   └── types/        # Route types
│   ├── rate-limits/
│   ├── audit/
│   └── auth/
├── shared/
│   ├── components/       # Button, Modal, Table
│   ├── hooks/            # useApi, useAuth
│   └── utils/            # formatDate, validation
├── layouts/              # MainLayout, AuthLayout
└── App.tsx
```

### Format Patterns

**API Response - Single Item:**
```json
{
  "id": "uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "status": "published",
  "createdAt": "2026-02-11T10:30:00Z"
}
```

**API Response - List:**
```json
{
  "items": [...],
  "total": 42,
  "offset": 0,
  "limit": 20
}
```

**API Error (RFC 7807):**
```json
{
  "type": "https://api.gateway/errors/validation",
  "title": "Validation Error",
  "status": 400,
  "detail": "Path must start with /",
  "instance": "/api/v1/routes",
  "correlationId": "abc-123"
}
```

### Communication Patterns

**Audit Events:**
```kotlin
// Event naming: entity.action (lowercase)
"route.created", "route.updated", "route.published"

// Event payload structure
data class AuditEvent(
    val entityType: String,
    val entityId: String,
    val action: String,
    val userId: String,
    val timestamp: Instant,
    val changes: Map<String, Any?>
)
```

**Frontend State (React Query keys):**
```typescript
// Query keys: [entity, ...params]
["routes"]
["routes", routeId]
["routes", { status: "draft" }]
["rateLimits"]
```

### Process Patterns

**Error Handling:**

| Тип ошибки | UI Pattern |
|------------|------------|
| Form validation | Inline под полем |
| API error (4xx) | Inline + подсветка поля |
| Global error (5xx, network) | Toast notification |
| Auth error (401) | Redirect to login |

**Loading States:**

| Контекст | UI Pattern |
|----------|------------|
| Списки/таблицы | Skeleton loader |
| Form submit | Button spinner + disabled |
| Page navigation | Top progress bar |
| Initial load | Full-page skeleton |

### Enforcement Guidelines

**All AI Agents MUST:**
- Использовать snake_case для DB columns, camelCase для JSON полей
- Следовать структуре features/ для React компонентов
- Возвращать RFC 7807 для всех ошибок
- Включать correlationId во все логи и ответы
- Использовать Flyway для всех изменений схемы

**Anti-Patterns (избегать):**
- ❌ snake_case в JSON: `{ "user_id": 1 }` (JSON должен быть camelCase)
- ❌ camelCase в DB columns: `userId` (PostgreSQL требует кавычки для camelCase)
- ❌ Смешение стилей в одном слое
- ❌ Тесты вне `src/test/`
- ❌ Компоненты вне `features/` или `shared/`

## Project Structure & Boundaries

### Complete Project Directory Structure

```
api-gateway/
├── README.md
├── docker-compose.yml
├── docker-compose.dev.yml
├── .gitignore
├── .env.example
│
├── backend/
│   ├── build.gradle.kts                    # Root Gradle build
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   │
│   ├── gateway-common/                     # Shared code
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/company/gateway/common/
│   │       ├── model/                      # Domain entities
│   │       │   ├── Route.kt
│   │       │   ├── RateLimit.kt
│   │       │   ├── User.kt
│   │       │   └── AuditLog.kt
│   │       ├── dto/                        # Shared DTOs
│   │       │   ├── RouteDto.kt
│   │       │   ├── RateLimitDto.kt
│   │       │   └── PagedResponse.kt
│   │       ├── exception/                  # Custom exceptions
│   │       │   ├── ApiException.kt
│   │       │   └── ErrorResponse.kt
│   │       └── util/                       # Utilities
│   │           ├── CorrelationId.kt
│   │           └── JsonUtils.kt
│   │
│   ├── gateway-admin/                      # Admin API
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/
│   │       │   ├── kotlin/com/company/gateway/admin/
│   │       │   │   ├── AdminApplication.kt
│   │       │   │   ├── config/
│   │       │   │   │   ├── SecurityConfig.kt
│   │       │   │   │   ├── R2dbcConfig.kt
│   │       │   │   │   ├── RedisConfig.kt
│   │       │   │   │   └── OpenApiConfig.kt
│   │       │   │   ├── controller/
│   │       │   │   │   ├── RouteController.kt
│   │       │   │   │   ├── RateLimitController.kt
│   │       │   │   │   ├── AuditController.kt
│   │       │   │   │   ├── AuthController.kt
│   │       │   │   │   └── MetricsController.kt
│   │       │   │   ├── service/
│   │       │   │   │   ├── RouteService.kt
│   │       │   │   │   ├── RateLimitService.kt
│   │       │   │   │   ├── AuditService.kt
│   │       │   │   │   ├── AuthService.kt
│   │       │   │   │   └── ApprovalService.kt
│   │       │   │   ├── repository/
│   │       │   │   │   ├── RouteRepository.kt
│   │       │   │   │   ├── RateLimitRepository.kt
│   │       │   │   │   ├── UserRepository.kt
│   │       │   │   │   └── AuditLogRepository.kt
│   │       │   │   ├── security/
│   │       │   │   │   ├── JwtTokenProvider.kt
│   │       │   │   │   ├── JwtAuthenticationFilter.kt
│   │       │   │   │   └── RoleBasedAccessControl.kt
│   │       │   │   └── exception/
│   │       │   │       └── GlobalExceptionHandler.kt
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       ├── application-dev.yml
│   │       │       ├── application-prod.yml
│   │       │       └── db/migration/
│   │       │           ├── V1__create_users.sql
│   │       │           ├── V2__create_routes.sql
│   │       │           ├── V3__create_rate_limits.sql
│   │       │           └── V4__create_audit_logs.sql
│   │       └── test/kotlin/com/company/gateway/admin/
│   │           ├── controller/
│   │           ├── service/
│   │           └── integration/
│   │
│   └── gateway-core/                       # Gateway Runtime
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── kotlin/com/company/gateway/core/
│           │   │   ├── GatewayApplication.kt
│           │   │   ├── config/
│           │   │   │   ├── GatewayConfig.kt
│           │   │   │   ├── RouteLocatorConfig.kt
│           │   │   │   └── CacheConfig.kt
│           │   │   ├── filter/
│           │   │   │   ├── RateLimitFilter.kt
│           │   │   │   ├── CorrelationIdFilter.kt
│           │   │   │   └── LoggingFilter.kt
│           │   │   ├── route/
│           │   │   │   ├── DynamicRouteLocator.kt
│           │   │   │   └── RouteRefreshService.kt
│           │   │   └── cache/
│           │   │       ├── RouteCacheManager.kt
│           │   │       └── CaffeineFallback.kt
│           │   └── resources/
│           │       ├── application.yml
│           │       └── application-dev.yml
│           └── test/kotlin/com/company/gateway/core/
│
├── frontend/
│   └── admin-ui/
│       ├── package.json
│       ├── vite.config.ts
│       ├── tsconfig.json
│       ├── index.html
│       ├── .env.example
│       └── src/
│           ├── main.tsx
│           ├── App.tsx
│           ├── vite-env.d.ts
│           ├── features/
│           │   ├── auth/
│           │   │   ├── components/
│           │   │   │   ├── LoginForm.tsx
│           │   │   │   └── ProtectedRoute.tsx
│           │   │   ├── hooks/useAuth.ts
│           │   │   ├── api/authApi.ts
│           │   │   └── types/auth.types.ts
│           │   ├── routes/
│           │   │   ├── components/
│           │   │   │   ├── RouteList.tsx
│           │   │   │   ├── RouteForm.tsx
│           │   │   │   ├── RouteCard.tsx
│           │   │   │   └── RouteStatusBadge.tsx
│           │   │   ├── hooks/
│           │   │   │   ├── useRoutes.ts
│           │   │   │   └── useRouteForm.ts
│           │   │   ├── api/routesApi.ts
│           │   │   └── types/route.types.ts
│           │   ├── rate-limits/
│           │   │   ├── components/
│           │   │   │   ├── RateLimitList.tsx
│           │   │   │   └── RateLimitForm.tsx
│           │   │   ├── hooks/useRateLimits.ts
│           │   │   ├── api/rateLimitsApi.ts
│           │   │   └── types/rateLimit.types.ts
│           │   ├── audit/
│           │   │   ├── components/
│           │   │   │   ├── AuditLogList.tsx
│           │   │   │   └── AuditLogFilters.tsx
│           │   │   ├── hooks/useAuditLogs.ts
│           │   │   └── api/auditApi.ts
│           │   └── approval/
│           │       ├── components/
│           │       │   ├── PendingApprovalsList.tsx
│           │       │   └── ApprovalActions.tsx
│           │       └── hooks/useApprovals.ts
│           ├── shared/
│           │   ├── components/
│           │   │   ├── PageHeader.tsx
│           │   │   ├── DataTable.tsx
│           │   │   ├── ConfirmModal.tsx
│           │   │   └── ErrorBoundary.tsx
│           │   ├── hooks/
│           │   │   ├── useApi.ts
│           │   │   └── useNotification.ts
│           │   └── utils/
│           │       ├── axios.ts
│           │       ├── formatDate.ts
│           │       └── validation.ts
│           ├── layouts/
│           │   ├── MainLayout.tsx
│           │   ├── AuthLayout.tsx
│           │   └── Sidebar.tsx
│           └── styles/global.css
│
└── docker/
    ├── Dockerfile.gateway-admin
    ├── Dockerfile.gateway-core
    ├── Dockerfile.admin-ui
    └── nginx/nginx.conf
```

### Architectural Boundaries

**Service Boundaries:**

| Service | Responsibility | Port | Dependencies |
|---------|---------------|------|--------------|
| **gateway-core** | Request routing, rate limiting | 8080 | PostgreSQL (read), Redis |
| **gateway-admin** | Admin API, CRUD operations | 8081 | PostgreSQL (read/write), Redis |
| **admin-ui** | User interface | 3000 | gateway-admin API |

**Data Flow:**
```
# Admin Flow (через Nginx)
User → Nginx → admin-ui (/) → gateway-admin (/api/v1/) → PostgreSQL
                                                       → Redis (cache invalidation)
                                                               ↓
# Gateway Flow (через Nginx)
External Request → Nginx → gateway-core (/api/) → Redis (rate limit) → Upstream Service
                                                → Caffeine (route config)
```

**Production Data Flow (с Nginx):**
```
Internet → DNS (gateway.ymorozov.ru) → Nginx:80/443
                                           │
           ┌───────────────────────────────┼───────────────────────────────┐
           │                               │                               │
           ▼                               ▼                               ▼
    admin-ui:3000               gateway-admin:8081              gateway-core:8080
    (React SPA)                  (Admin API)                    (Gateway Runtime)
           │                               │                               │
           └───────────────────────────────┴───────────────────────────────┘
                                           │
                                    ┌──────┴──────┐
                                    ▼             ▼
                               PostgreSQL      Redis
                                 :5432         :6379
```

### Requirements to Structure Mapping

| FR Category | Backend Location | Frontend Location | Documentation |
|-------------|------------------|-------------------|---------------|
| **Route Management (FR1-6)** | `gateway-admin/controller/RouteController.kt` | `features/routes/` | — |
| **Approval Workflow (FR7-12)** | `gateway-admin/service/ApprovalService.kt` | `features/approval/` | — |
| **Rate Limiting (FR13-16)** | `gateway-admin/` + `gateway-core/filter/` | `features/rate-limits/` | [docs/rate-limiting.md](../../docs/rate-limiting.md) |
| **Monitoring (FR17-20)** | `gateway-admin/controller/MetricsController.kt` | Grafana (external) | — |
| **Reactive Patterns** | All backend services | — | [docs/webflux-patterns.md](../../docs/webflux-patterns.md) |
| **Audit (FR21-24)** | `gateway-admin/controller/AuditController.kt` | `features/audit/` | — |
| **Auth (FR25-27)** | `gateway-admin/security/` | `features/auth/` | — |
| **Gateway Runtime (FR28-31)** | `gateway-core/` | — | — |
| **Cache Sync** | `gateway-admin/publisher/` + `gateway-core/route/` | — | [docs/cache-sync.md](../../docs/cache-sync.md) |

### Integration Points

**Internal:**
- gateway-admin ↔ gateway-core: Redis pub/sub для cache invalidation (см. [docs/cache-sync.md](../../docs/cache-sync.md))
- admin-ui → gateway-admin: REST API через Axios

**External:**
- Prometheus: scrape `/actuator/prometheus`
- Grafana: dashboards via Prometheus datasource
- Upstream services: dynamic routing через gateway-core

### Gateway Filter Ordering

Фильтры gateway-core выполняются в определённом порядке. Order values управляют последовательностью:

| Filter | Order | Purpose |
|--------|-------|---------|
| `CorrelationIdFilter` | `HIGHEST_PRECEDENCE` | Генерация/проброс X-Correlation-ID |
| `MetricsFilter` | `HIGHEST_PRECEDENCE + 10` | Сбор метрик (время, статус) |
| `RateLimitFilter` | `HIGHEST_PRECEDENCE + 100` | Проверка rate limit |
| `LoggingFilter` | `HIGHEST_PRECEDENCE + 200` | Логирование запросов |
| `RewritePathGatewayFilterFactory` | `10001` | Переписывание path |

**Важно:**
- `MetricsFilter` должен быть раньше других чтобы измерять полное время запроса
- `CorrelationIdFilter` должен быть первым для трассировки всех последующих логов

### Gateway Metrics Collection

**Что создаёт метрики:**
- Только запросы к **published routes** (статус = published)
- Запросы проходящие через `MetricsFilter` в gateway-core

**Что НЕ создаёт метрики:**
- `/actuator/*` endpoints — не проходят через MetricsFilter
- Запросы к gateway-admin API
- Health checks

**Метрики собираемые MetricsFilter:**

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `gateway_request_total` | Counter | route_id, route_path, method, status | Общее кол-во запросов |
| `gateway_request_duration_seconds` | Timer | route_id, route_path, method | Latency (p50, p95, p99) |
| `gateway_request_errors_total` | Counter | route_id, route_path, error_type | Ошибки |

**Prometheus Query Examples:**
```promql
# RPS по маршруту
rate(gateway_request_total{route_path="/api/orders"}[5m])

# P95 latency
histogram_quantile(0.95, rate(gateway_request_duration_seconds_bucket[5m]))

# Error rate
rate(gateway_request_errors_total[5m]) / rate(gateway_request_total[5m])
```

### Redis Pub/Sub Channels

| Channel | Publisher | Subscriber | Purpose |
|---------|-----------|------------|---------|
| `rateLimit:cache:invalidate` | gateway-admin | gateway-core | Инвалидация кэша rate limit при изменении политик |
| `route:cache:invalidate` | gateway-admin | gateway-core | Инвалидация кэша маршрутов при CRUD операциях |

**Формат сообщений:**
```json
{
  "type": "invalidate",
  "entityId": "uuid",
  "timestamp": "2026-02-21T10:30:00Z"
}
```

**Подписка в gateway-core:**
```kotlin
// RouteRefreshService.kt
redisTemplate.listenTo(ChannelTopic("route:cache:invalidate"))
    .doOnNext { message ->
        routeCacheManager.evictByRouteId(message.entityId)
    }
    .subscribe()
```

## Production Deployment

### External Access

**Домен:** `gateway.ymorozov.ru`

**Архитектура:**
- Nginx служит reverse proxy с маршрутизацией запросов
- Admin UI и Admin API доступны через root path (`/`)
- Gateway Core API доступен через префикс `/api/`
- Admin API v1 доступен через `/api/v1/`

**URL маршрутизация:**

| Path | Backend Service | Описание |
|------|-----------------|----------|
| `/` | admin-ui:3000 | React SPA (Admin UI) |
| `/api/v1/*` | gateway-admin:8081 | Admin API (CRUD, аутентификация) |
| `/api/*` | gateway-core:8080 | Gateway Core (публичные маршруты) |

### Nginx Reverse Proxy

Nginx выполняет роль reverse proxy перед backend сервисами, обеспечивая:
- Единую точку входа для всех запросов
- Маршрутизацию по path prefix
- Проброс заголовков (X-Real-IP, X-Forwarded-For, X-Forwarded-Proto)
- WebSocket upgrade для hot-reload в development

**Конфигурация upstream:**

```nginx
# docker/nginx/nginx.conf

upstream admin_ui {
    server admin-ui:3000;
}

upstream gateway_admin {
    server gateway-admin:8081;
}

upstream gateway_core {
    server gateway-core:8080;
}
```

**Конфигурация server block:**

```nginx
server {
    listen 80;
    server_name gateway.ymorozov.ru localhost 127.0.0.1 192.168.0.168;

    client_max_body_size 10M;

    # Logging
    access_log /var/log/nginx/access.log;
    error_log /var/log/nginx/error.log;

    # Admin API v1 (более специфичный путь — обрабатывается первым)
    location /api/v1/ {
        proxy_pass http://gateway_admin/api/v1/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Gateway Core API
    location /api/ {
        proxy_pass http://gateway_core/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Admin UI (React SPA)
    location / {
        proxy_pass http://admin_ui;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_connect_timeout 10s;
        proxy_send_timeout 10s;
        proxy_read_timeout 30s;
    }

    # Health check
    location /nginx-health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
```

**Проброс заголовков:**

| Заголовок | Значение | Назначение |
|-----------|----------|------------|
| `Host` | `$host` | Оригинальный Host для backend |
| `X-Real-IP` | `$remote_addr` | IP клиента |
| `X-Forwarded-For` | `$proxy_add_x_forwarded_for` | Цепочка proxy |
| `X-Forwarded-Proto` | `$scheme` | Оригинальный протокол (http/https) |

### SSL/TLS Configuration

**Текущее состояние:**
- TLS termination происходит на внешнем уровне (VPN tunnel или reverse proxy провайдера)
- Nginx в Docker слушает только HTTP (порт 80)
- Внутренний трафик между контейнерами идёт по HTTP

**Production setup (при необходимости):**

```nginx
server {
    listen 443 ssl http2;
    server_name gateway.ymorozov.ru;

    # SSL/TLS сертификаты (Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/gateway.ymorozov.ru/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gateway.ymorozov.ru/privkey.pem;

    # TLS настройки
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;

    # HSTS (опционально)
    add_header Strict-Transport-Security "max-age=63072000" always;

    # ... location блоки как выше ...
}

# HTTP → HTTPS redirect
server {
    listen 80;
    server_name gateway.ymorozov.ru;
    return 301 https://$server_name$request_uri;
}
```

**Сертификаты:**
- Provider: Let's Encrypt (certbot)
- Auto-renewal: через cron или systemd timer
- TLS Version: 1.2+ (TLS 1.3 рекомендуется)
- Termination: на уровне Nginx

### Deployment Topology

```
                    ┌─────────────────────────────────────┐
                    │            Internet                  │
                    └─────────────────────────────────────┘
                                     │
                                     ▼
                    ┌─────────────────────────────────────┐
                    │   gateway.ymorozov.ru (DNS)         │
                    └─────────────────────────────────────┘
                                     │
                                     ▼
                    ┌─────────────────────────────────────┐
                    │   Nginx (Reverse Proxy)             │
                    │   Port: 80 (443 с TLS)              │
                    │   - URL routing                     │
                    │   - Header forwarding               │
                    └─────────────────────────────────────┘
                      │              │              │
        ┌─────────────┘              │              └─────────────┐
        ▼                            ▼                            ▼
┌──────────────────┐    ┌──────────────────┐        ┌──────────────────┐
│   admin-ui       │    │  gateway-admin   │        │   gateway-core   │
│   :3000          │    │  :8081           │        │   :8080          │
│   React SPA      │    │  Admin API +     │        │   Gateway        │
│   (Vite)         │    │  Authentication  │        │   Runtime        │
└──────────────────┘    └──────────────────┘        └──────────────────┘
                                │                            │
                                └─────────────┬──────────────┘
                                              ▼
                                ┌──────────────────────────────┐
                                │         PostgreSQL           │
                                │         :5432                │
                                └──────────────────────────────┘
                                              │
                                ┌──────────────────────────────┐
                                │           Redis              │
                                │           :6379              │
                                │   (Cache + Pub/Sub)          │
                                └──────────────────────────────┘
```

**Docker Compose Production Stack:**

| Service | Container | Port (internal) | Port (external) |
|---------|-----------|-----------------|-----------------|
| nginx | nginx | 80 | 80, 443 |
| admin-ui | admin-ui | 3000 | — |
| gateway-admin | gateway-admin | 8081 | — |
| gateway-core | gateway-core | 8080 | — |
| postgres | postgres | 5432 | 5432 (dev only) |
| redis | redis | 6379 | 6379 (dev only) |
| prometheus | prometheus | 9090 | 9090 (profile: monitoring) |
| grafana | grafana | 3000 | 3001 (profile: monitoring) |

**Примечание:** В production внешние порты PostgreSQL и Redis закрыты — доступ только внутри Docker network. Prometheus и Grafana запускаются с `--profile monitoring`.

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
- Spring Boot 3.4 + Spring Cloud Gateway — совместимы
- Kotlin + R2DBC — полная поддержка
- Redis + Caffeine caching — работают вместе (multilevel cache)
- Vite + React + Ant Design — совместимы
- JWT + Spring Security — стандартная интеграция

**Pattern Consistency:**
- snake_case для DB (tables и columns), camelCase для API JSON и Kotlin code
- PascalCase для React компонентов и Kotlin классов
- RFC 7807 для всех API ошибок
- Структура features/ для frontend модулей

**Structure Alignment:**
- Monorepo поддерживает все компоненты
- Gradle multi-module для backend
- Чёткие границы между gateway-core и gateway-admin

### Requirements Coverage ✅

**Functional Requirements Coverage:**

| FR Category | Components | Status |
|-------------|------------|--------|
| Route Management (FR1-6) | RouteController, RouteService, features/routes | ✅ |
| Approval Workflow (FR7-12) | ApprovalService, features/approval | ✅ |
| Rate Limiting (FR13-16) | RateLimitFilter, Redis, features/rate-limits | ✅ |
| Monitoring (FR17-20) | Actuator, MetricsController, Prometheus | ✅ |
| Audit (FR21-24) | AuditService, AuditLogRepository, features/audit | ✅ |
| Auth (FR25-27) | JwtTokenProvider, RBAC, features/auth | ✅ |
| Gateway Runtime (FR28-31) | DynamicRouteLocator, filters, cache | ✅ |

**Non-Functional Requirements Coverage:**

| NFR | Architectural Support | Status |
|-----|----------------------|--------|
| Performance (P95 < 200ms) | Netty + R2DBC + Caffeine | ✅ |
| Reliability (99.9%) | Graceful degradation, health checks | ✅ |
| Scalability (1000 RPS) | Horizontal scaling, stateless | ✅ |
| Security | JWT, RBAC, TLS, audit | ✅ |
| Observability | Prometheus, JSON logs, correlation IDs | ✅ |

### Implementation Readiness ✅

**Decision Completeness:**
- Все критические решения задокументированы
- Версии технологий указаны
- Rationale для каждого решения

**Structure Completeness:**
- Полное дерево проекта с файлами
- Границы сервисов определены
- Integration points специфицированы

**Pattern Completeness:**
- Naming conventions для всех слоёв
- API response formats с примерами
- Error handling patterns
- Loading state patterns

### Gap Analysis

**Critical Gaps:** None

**Important Gaps (Post-MVP):**
- CI/CD pipeline (GitHub Actions) — добавить при первом deploy
- Kubernetes manifests — Phase 3

**Nice-to-Have:**
- Grafana dashboard templates
- E2E tests (Playwright)
- API client SDK generation

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context analyzed
- [x] Scale and complexity assessed
- [x] Technical constraints identified
- [x] Cross-cutting concerns mapped

**✅ Architectural Decisions**
- [x] Critical decisions documented with versions
- [x] Technology stack fully specified
- [x] Integration patterns defined
- [x] Performance considerations addressed

**✅ Implementation Patterns**
- [x] Naming conventions established
- [x] Structure patterns defined
- [x] Communication patterns specified
- [x] Process patterns documented

**✅ Project Structure**
- [x] Complete directory structure defined
- [x] Component boundaries established
- [x] Integration points mapped
- [x] Requirements to structure mapping complete

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** HIGH

**Key Strengths:**
- Proven technology stack (Spring Cloud Gateway)
- Clear separation of concerns (3 services)
- Comprehensive patterns prevent AI agent conflicts
- All 31 FRs mapped to specific components

**Areas for Future Enhancement:**
- Add CI/CD pipeline when ready for deployment
- Kubernetes migration in Phase 3
- Consider GraphQL for complex queries (Phase 2+)

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries
- Refer to this document for all architectural questions

**First Implementation Priority:**
```bash
# 1. Initialize backend
curl https://start.spring.io/starter.zip ... -o backend.zip

# 2. Initialize frontend
npm create vite@latest frontend/admin-ui -- --template react-ts

# 3. Setup Docker Compose
docker-compose up -d postgres redis
```

---

## Phase 2: Keycloak & Multi-tenant Architecture

_Добавлено: 2026-02-23. Расширение архитектуры для Epic 12: Keycloak Integration & Multi-tenant Metrics._

### Keycloak Integration

#### Keycloak Deployment

**Docker Compose дополнение:**

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: postgres
    ports:
      - "8180:8080"
    volumes:
      - ./docker/keycloak/realm-export.json:/opt/keycloak/data/import/realm.json
    depends_on:
      - postgres
```

**Порты:**
- Keycloak: `8180` (внешний) → `8080` (внутренний)
- Отдельная БД `keycloak` в том же PostgreSQL инстансе

#### Realm Structure

```
Realm: api-gateway
│
├── Realm Settings
│   ├── Token Lifespan: Access Token = 5 min, Refresh = 30 min
│   ├── SSO Session Idle: 30 min
│   └── Login Theme: keycloak (default)
│
├── Clients
│   │
│   ├── gateway-admin-ui
│   │   ├── Client Protocol: openid-connect
│   │   ├── Access Type: public
│   │   ├── Standard Flow: enabled (Authorization Code + PKCE)
│   │   ├── Direct Access Grants: disabled
│   │   ├── Valid Redirect URIs:
│   │   │   - http://localhost:3000/*
│   │   │   - https://gateway.ymorozov.ru/*
│   │   ├── Web Origins: + (same as redirect URIs)
│   │   └── Client Scopes: openid, profile, email, roles
│   │
│   ├── gateway-admin-api
│   │   ├── Client Protocol: openid-connect
│   │   ├── Access Type: bearer-only
│   │   └── Purpose: Валидация JWT от Admin UI
│   │
│   ├── gateway-core
│   │   ├── Client Protocol: openid-connect
│   │   ├── Access Type: bearer-only
│   │   └── Purpose: Валидация JWT от API consumers
│   │
│   └── API Consumers (Client Credentials)
│       ├── company-a
│       │   ├── Access Type: confidential
│       │   ├── Service Accounts Enabled: true
│       │   ├── Standard Flow: disabled
│       │   ├── Direct Access Grants: disabled
│       │   └── Client Authenticator: Client Id and Secret
│       ├── company-b
│       └── company-c
│
├── Realm Roles
│   ├── admin-ui:developer   → Создание/редактирование маршрутов
│   ├── admin-ui:security    → Approval workflow
│   ├── admin-ui:admin       → Управление пользователями
│   └── api:consumer         → Доступ к API через gateway
│
├── Client Scopes
│   └── api-access
│       └── Mappers:
│           └── client_id → добавляет client_id в JWT claims
│
└── Users (Admin Portal)
    ├── yury@company.com
    │   └── Realm Roles: admin-ui:admin
    ├── dev@company.com
    │   └── Realm Roles: admin-ui:developer
    └── security@company.com
        └── Realm Roles: admin-ui:security
```

#### Authentication Flows

**Admin UI — Authorization Code + PKCE:**

```
┌──────────┐      ┌──────────────┐      ┌──────────────┐
│  Browser │      │   Admin UI   │      │   Keycloak   │
└────┬─────┘      └──────┬───────┘      └──────┬───────┘
     │                   │                     │
     │ 1. /login         │                     │
     │──────────────────▶│                     │
     │                   │                     │
     │                   │ 2. Redirect to      │
     │                   │    /auth?client_id= │
     │                   │    &redirect_uri=   │
     │                   │    &code_challenge= │
     │◀──────────────────│                     │
     │                   │                     │
     │ 3. Login form     │                     │
     │──────────────────────────────────────────▶
     │                   │                     │
     │ 4. Authorization  │                     │
     │    code           │                     │
     │◀──────────────────────────────────────────
     │                   │                     │
     │ 5. code + verifier│                     │
     │──────────────────▶│                     │
     │                   │ 6. POST /token      │
     │                   │──────────────────────▶
     │                   │                     │
     │                   │ 7. access_token +   │
     │                   │    refresh_token    │
     │                   │◀──────────────────────
     │                   │                     │
     │ 8. Set cookie     │                     │
     │◀──────────────────│                     │
```

**API Consumer — Client Credentials:**

```
┌──────────────┐                    ┌──────────────┐
│   Consumer   │                    │   Keycloak   │
│  (Company A) │                    │              │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │ 1. POST /token                    │
       │    grant_type=client_credentials  │
       │    client_id=company-a            │
       │    client_secret=xxx              │
       │──────────────────────────────────▶│
       │                                   │
       │ 2. { access_token: "eyJ...",      │
       │      expires_in: 300 }            │
       │◀──────────────────────────────────│
       │                                   │

┌──────────────┐                    ┌──────────────┐
│   Consumer   │                    │ Gateway Core │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │ 3. GET /api/orders                │
       │    Authorization: Bearer eyJ...   │
       │──────────────────────────────────▶│
       │                                   │
       │        (JWT validated via JWKS)   │
       │                                   │
       │ 4. Response                       │
       │◀──────────────────────────────────│
```

#### JWT Token Structure

**Admin UI User Token:**

```json
{
  "iss": "https://keycloak.gateway.ymorozov.ru/realms/api-gateway",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "aud": "gateway-admin-ui",
  "exp": 1708700000,
  "iat": 1708699700,
  "azp": "gateway-admin-ui",
  "preferred_username": "yury",
  "email": "yury@company.com",
  "realm_access": {
    "roles": ["admin-ui:admin"]
  }
}
```

**API Consumer Token:**

```json
{
  "iss": "https://keycloak.gateway.ymorozov.ru/realms/api-gateway",
  "sub": "service-account-company-a",
  "aud": "gateway-core",
  "exp": 1708700000,
  "iat": 1708699700,
  "azp": "company-a",
  "clientId": "company-a",
  "realm_access": {
    "roles": ["api:consumer"]
  }
}
```

**Ключевые claims для извлечения:**

| Claim | Использование | Consumer Identity |
|-------|---------------|-------------------|
| `azp` | Authorized Party — client_id | ✅ Primary source |
| `clientId` | Явный client ID | Fallback |
| `sub` | Subject (user/service account UUID) | Audit logging |
| `realm_access.roles` | Роли для authorization | Access control |

#### JWKS Caching Strategy

**Spring Security OAuth2 Resource Server:**

```kotlin
// SecurityConfig.kt
@Bean
fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
    return http
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwkSetUri("${keycloakUrl}/realms/api-gateway/protocol/openid-connect/certs")
            }
        }
        .build()
}
```

**Caching Configuration:**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| JWKS Cache TTL | 5 minutes | Баланс между безопасностью и производительностью |
| JWKS Refresh on Unknown Key | Enabled | Автоматический refresh при rotation |
| Connection Timeout | 5 seconds | Fail fast при недоступности Keycloak |
| Read Timeout | 5 seconds | Предотвращение зависания |

**Graceful Degradation:**
- При недоступности Keycloak JWKS endpoint — использовать cached keys
- Log warning если cache старше 10 минут
- При полном отказе — reject все JWT (security over availability)

### Gateway Core Filter Chain (Phase 2)

#### Updated Filter Order

```
Request
   │
   ▼
┌──────────────────────────┐
│   CorrelationIdFilter    │  Order: HIGHEST_PRECEDENCE
│   (существующий)         │  Генерация X-Correlation-ID
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│  JwtAuthenticationFilter │  Order: HIGHEST_PRECEDENCE + 5  ← НОВЫЙ
│  - Проверка authRequired │
│  - Валидация JWT         │
│  - Извлечение claims     │
│  - 401 если invalid/missing на protected route
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ ConsumerIdentityFilter   │  Order: HIGHEST_PRECEDENCE + 8  ← НОВЫЙ
│ - JWT azp → consumer_id  │
│ - X-Consumer-ID fallback │
│ - "anonymous" default    │
│ - Context propagation    │
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│     MetricsFilter        │  Order: HIGHEST_PRECEDENCE + 10
│  + consumer_id label     │  ← РАСШИРИТЬ
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│    RateLimitFilter       │  Order: HIGHEST_PRECEDENCE + 100
│  + per-consumer limits   │  ← РАСШИРИТЬ
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│    LoggingFilter         │  Order: HIGHEST_PRECEDENCE + 200
│  + consumer_id в MDC     │  ← РАСШИРИТЬ
└──────────┬───────────────┘
           ▼
      Upstream Service
```

#### JwtAuthenticationFilter

**Расположение:** `gateway-core/src/main/kotlin/com/company/gateway/core/filter/JwtAuthenticationFilter.kt`

```kotlin
@Component
class JwtAuthenticationFilter(
    private val jwtDecoder: ReactiveJwtDecoder,
    private val routeCacheManager: RouteCacheManager
) : WebFilter, Ordered {

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 5

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val routeId = exchange.getAttribute<String>(ROUTE_ID_ATTR)

        return routeCacheManager.getRoute(routeId)
            .flatMap { route ->
                val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

                when {
                    // Public route без токена — пропускаем
                    !route.authRequired && authHeader == null -> {
                        chain.filter(exchange)
                    }
                    // Protected route без токена — 401
                    route.authRequired && authHeader == null -> {
                        unauthorized(exchange, "Missing Authorization header")
                    }
                    // Есть токен — валидируем
                    authHeader != null -> {
                        validateAndProceed(exchange, chain, authHeader, route)
                    }
                    else -> chain.filter(exchange)
                }
            }
    }

    private fun validateAndProceed(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
        authHeader: String,
        route: Route
    ): Mono<Void> {
        val token = authHeader.removePrefix("Bearer ").trim()

        return jwtDecoder.decode(token)
            .flatMap { jwt ->
                // Проверка allowed_consumers
                val consumerId = jwt.claims["azp"]?.toString() ?: jwt.claims["clientId"]?.toString()

                if (route.allowedConsumers != null && consumerId !in route.allowedConsumers) {
                    return@flatMap forbidden(exchange, "Consumer not allowed for this route")
                }

                // Сохраняем claims в context
                val mutatedExchange = exchange.mutate()
                    .request(exchange.request.mutate()
                        .header("X-Consumer-ID", consumerId ?: "unknown")
                        .header("X-User-Subject", jwt.subject)
                        .build())
                    .build()

                chain.filter(mutatedExchange)
                    .contextWrite { ctx -> ctx.put(JWT_CLAIMS_KEY, jwt.claims) }
            }
            .onErrorResume(JwtException::class.java) { e ->
                unauthorized(exchange, "Invalid JWT: ${e.message}")
            }
    }
}
```

#### ConsumerIdentityFilter

**Расположение:** `gateway-core/src/main/kotlin/com/company/gateway/core/filter/ConsumerIdentityFilter.kt`

```kotlin
@Component
class ConsumerIdentityFilter : WebFilter, Ordered {

    companion object {
        const val CONSUMER_ID_KEY = "consumerId"
        const val ANONYMOUS = "anonymous"
    }

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 8

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return Mono.deferContextual { ctx ->
            // Приоритет 1: JWT claim azp (установлен JwtAuthenticationFilter)
            val jwtConsumerId = exchange.request.headers.getFirst("X-Consumer-ID")
                ?.takeIf { it != "unknown" }

            // Приоритет 2: Header X-Consumer-ID (для public routes)
            val headerConsumerId = exchange.request.headers.getFirst("X-Consumer-ID")
                ?.takeIf { it.isNotBlank() && it != "unknown" }

            // Приоритет 3: anonymous
            val consumerId = jwtConsumerId ?: headerConsumerId ?: ANONYMOUS

            // Сохраняем в exchange attributes для MetricsFilter
            exchange.attributes[CONSUMER_ID_KEY] = consumerId

            // MDC для логирования
            chain.filter(exchange)
                .contextWrite { context ->
                    context.put(CONSUMER_ID_KEY, consumerId)
                }
        }.doOnEach { signal ->
            // MDC bridging для structured logging
            signal.contextView.getOrEmpty<String>(CONSUMER_ID_KEY).ifPresent { consumerId ->
                MDC.put("consumerId", consumerId)
            }
        }.doFinally {
            MDC.remove("consumerId")
        }
    }
}
```

### Route Authentication Configuration

#### Database Schema Changes

**Migration V11__add_route_auth_fields.sql:**

```sql
-- Добавляем поля для аутентификации маршрутов
ALTER TABLE routes
ADD COLUMN auth_required BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE routes
ADD COLUMN allowed_consumers TEXT[] DEFAULT NULL;

-- Комментарии
COMMENT ON COLUMN routes.auth_required IS 'Требуется ли JWT аутентификация для маршрута';
COMMENT ON COLUMN routes.allowed_consumers IS 'Whitelist consumer IDs (NULL = все разрешены)';

-- Индекс для поиска по auth_required
CREATE INDEX idx_routes_auth_required ON routes(auth_required);
```

#### Route Entity Update

```kotlin
@Table("routes")
data class Route(
    // ... существующие поля ...

    // === Authentication settings (Phase 2) ===
    @Column("auth_required")
    val authRequired: Boolean = true,

    @Column("allowed_consumers")
    val allowedConsumers: List<String>? = null
)
```

#### Route API Changes

**RouteRequest DTO:**

```kotlin
data class RouteRequest(
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val rateLimitId: UUID?,
    // Phase 2
    val authRequired: Boolean = true,
    val allowedConsumers: List<String>? = null
)
```

**Validation Rules:**

| Field | Rule |
|-------|------|
| `authRequired` | Boolean, default true |
| `allowedConsumers` | Nullable list, если указан — все элементы должны быть валидными client_id |

### Multi-tenant Metrics

#### MetricsFilter Extension

```kotlin
@Component
class MetricsFilter(
    private val meterRegistry: MeterRegistry
) : WebFilter, Ordered {

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 10

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val startTime = System.nanoTime()

        return chain.filter(exchange)
            .doFinally { signalType ->
                val routeId = exchange.getAttribute<String>(ROUTE_ID_ATTR) ?: "unknown"
                val routePath = exchange.getAttribute<String>(ROUTE_PATH_ATTR) ?: "unknown"
                val consumerId = exchange.getAttribute<String>(CONSUMER_ID_KEY) ?: "anonymous"  // ← НОВЫЙ
                val method = exchange.request.method?.name() ?: "UNKNOWN"
                val status = exchange.response.statusCode?.value()?.toString() ?: "unknown"

                val duration = (System.nanoTime() - startTime) / 1_000_000_000.0

                // Counter с consumer_id
                Counter.builder("gateway_requests_total")
                    .tag("route_id", routeId)
                    .tag("route_path", routePath)
                    .tag("consumer_id", consumerId)  // ← НОВЫЙ label
                    .tag("method", method)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment()

                // Timer с consumer_id
                Timer.builder("gateway_request_duration_seconds")
                    .tag("route_id", routeId)
                    .tag("route_path", routePath)
                    .tag("consumer_id", consumerId)  // ← НОВЫЙ label
                    .tag("method", method)
                    .register(meterRegistry)
                    .record(Duration.ofNanos((duration * 1_000_000_000).toLong()))
            }
    }
}
```

#### Prometheus Metrics Structure

```prometheus
# Запросы по consumers
gateway_requests_total{
  route_id="550e8400-e29b-41d4-a716-446655440000",
  route_path="/api/orders",
  consumer_id="company-a",
  method="GET",
  status="200"
} 15000

gateway_requests_total{
  route_id="550e8400-e29b-41d4-a716-446655440000",
  route_path="/api/orders",
  consumer_id="company-b",
  method="GET",
  status="200"
} 32000

gateway_requests_total{
  route_id="550e8400-e29b-41d4-a716-446655440001",
  route_path="/public/health",
  consumer_id="anonymous",
  method="GET",
  status="200"
} 100000

# Latency histogram
gateway_request_duration_seconds_bucket{
  route_id="...",
  consumer_id="company-a",
  method="GET",
  le="0.1"
} 12000

# Errors
gateway_errors_total{
  route_id="...",
  consumer_id="company-a",
  error_type="rate_limited"
} 45
```

#### PromQL Query Examples

```promql
# === Per-consumer queries ===

# RPS по consumer
sum by (consumer_id) (
  rate(gateway_requests_total[5m])
)

# Top 10 consumers по трафику
topk(10,
  sum by (consumer_id) (
    rate(gateway_requests_total[5m])
  )
)

# Error rate по consumer
sum by (consumer_id) (rate(gateway_errors_total[5m]))
/
sum by (consumer_id) (rate(gateway_requests_total[5m]))

# P95 latency по consumer
histogram_quantile(0.95,
  sum by (consumer_id, le) (
    rate(gateway_request_duration_seconds_bucket[5m])
  )
)

# Сравнение двух consumers
sum by (consumer_id) (
  rate(gateway_requests_total{consumer_id=~"company-a|company-b"}[5m])
)

# === Combined queries ===

# Traffic breakdown: route + consumer
sum by (route_path, consumer_id) (
  rate(gateway_requests_total[5m])
)
```

#### Cardinality Considerations

**Ограничения:**

| Dimension | Expected Cardinality | Limit |
|-----------|---------------------|-------|
| `route_id` | ~500 | OK |
| `consumer_id` | ~100-1000 | Monitor |
| `method` | 5-7 | OK |
| `status` | ~10 | OK |

**Total cardinality:** ~500 × 1000 × 7 × 10 = 35M series (worst case)

**Mitigation:**
- Агрегировать старые данные (retention rules в Prometheus)
- Использовать recording rules для частых запросов
- Alert при cardinality > 100K series

### Per-consumer Rate Limiting

#### Rate Limit Strategy

**Two-level rate limiting:**

1. **Per-route limit** — общий лимит на маршрут для всех consumers
2. **Per-consumer limit** — индивидуальный лимит для consumer

**Priority:** Применяется более строгий лимит (minimum of both)

#### Database Schema

**Migration V12__add_consumer_rate_limits.sql:**

```sql
-- Таблица для per-consumer rate limits
CREATE TABLE consumer_rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_id VARCHAR(255) NOT NULL,
    requests_per_second INTEGER NOT NULL,
    burst_size INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES users(id),

    CONSTRAINT uq_consumer_rate_limits_consumer UNIQUE (consumer_id)
);

COMMENT ON TABLE consumer_rate_limits IS 'Per-consumer rate limiting policies';

CREATE INDEX idx_consumer_rate_limits_consumer_id ON consumer_rate_limits(consumer_id);
```

#### Redis Key Structure

```
# Per-route rate limit (существующий)
rate_limit:route:{routeId}:{clientIp} → token bucket state

# Per-consumer rate limit (новый)
rate_limit:consumer:{consumerId} → token bucket state

# Combined key для точного rate limiting
rate_limit:route:{routeId}:consumer:{consumerId} → token bucket state
```

#### RateLimitFilter Extension

```kotlin
@Component
class RateLimitFilter(
    private val rateLimitService: RateLimitService,
    private val consumerRateLimitRepository: ConsumerRateLimitRepository
) : WebFilter, Ordered {

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 100

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val routeId = exchange.getAttribute<String>(ROUTE_ID_ATTR)
        val consumerId = exchange.getAttribute<String>(CONSUMER_ID_KEY) ?: "anonymous"

        return Mono.zip(
            // Проверка per-route limit
            rateLimitService.checkRouteLimit(routeId, consumerId),
            // Проверка per-consumer limit
            checkConsumerLimit(consumerId)
        ).flatMap { (routeAllowed, consumerAllowed) ->
            when {
                !routeAllowed.allowed -> {
                    rateLimitExceeded(exchange, "route", routeAllowed.retryAfter)
                }
                !consumerAllowed.allowed -> {
                    rateLimitExceeded(exchange, "consumer", consumerAllowed.retryAfter)
                }
                else -> chain.filter(exchange)
            }
        }
    }

    private fun rateLimitExceeded(
        exchange: ServerWebExchange,
        limitType: String,
        retryAfter: Long
    ): Mono<Void> {
        exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        exchange.response.headers.add("Retry-After", retryAfter.toString())
        exchange.response.headers.add("X-RateLimit-Type", limitType)

        return exchange.response.writeWith(
            Mono.just(exchange.response.bufferFactory().wrap(
                """{"type":"rate_limit_exceeded","limit_type":"$limitType","retry_after":$retryAfter}""".toByteArray()
            ))
        )
    }
}
```

#### Admin API for Consumer Rate Limits

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/consumers/{consumerId}/rate-limit` | Получить лимит consumer |
| PUT | `/api/v1/consumers/{consumerId}/rate-limit` | Установить лимит consumer |
| DELETE | `/api/v1/consumers/{consumerId}/rate-limit` | Удалить лимит (использовать default) |
| GET | `/api/v1/consumer-rate-limits` | Список всех consumer лимитов |

### Consumer Management

#### Keycloak Admin API Integration

**KeycloakAdminClient:**

```kotlin
@Service
class KeycloakAdminClient(
    private val webClient: WebClient,
    @Value("\${keycloak.admin.url}") private val adminUrl: String,
    @Value("\${keycloak.realm}") private val realm: String
) {

    // Получение service account token для admin operations
    private fun getAdminToken(): Mono<String> {
        return webClient.post()
            .uri("$adminUrl/realms/master/protocol/openid-connect/token")
            .bodyValue(mapOf(
                "grant_type" to "client_credentials",
                "client_id" to "admin-cli",
                "client_secret" to adminSecret
            ))
            .retrieve()
            .bodyToMono<TokenResponse>()
            .map { it.accessToken }
    }

    // Создание нового client (consumer)
    fun createClient(clientId: String): Mono<ClientRepresentation> {
        return getAdminToken().flatMap { token ->
            webClient.post()
                .uri("$adminUrl/admin/realms/$realm/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .bodyValue(ClientRepresentation(
                    clientId = clientId,
                    enabled = true,
                    serviceAccountsEnabled = true,
                    standardFlowEnabled = false,
                    directAccessGrantsEnabled = false,
                    clientAuthenticatorType = "client-secret"
                ))
                .retrieve()
                .bodyToMono<ClientRepresentation>()
        }
    }

    // Получение client secret
    fun getClientSecret(clientId: String): Mono<String> {
        return getAdminToken().flatMap { token ->
            webClient.get()
                .uri("$adminUrl/admin/realms/$realm/clients/$clientId/client-secret")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .bodyToMono<CredentialRepresentation>()
                .map { it.value }
        }
    }

    // Regenerate client secret
    fun regenerateClientSecret(clientId: String): Mono<String> {
        return getAdminToken().flatMap { token ->
            webClient.post()
                .uri("$adminUrl/admin/realms/$realm/clients/$clientId/client-secret")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .bodyToMono<CredentialRepresentation>()
                .map { it.value }
        }
    }

    // Список всех API consumers
    fun listConsumers(): Flux<ClientRepresentation> {
        return getAdminToken().flatMapMany { token ->
            webClient.get()
                .uri("$adminUrl/admin/realms/$realm/clients?clientId=company-*")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .bodyToFlux<ClientRepresentation>()
                .filter { it.serviceAccountsEnabled == true }
        }
    }

    // Деактивация consumer
    fun disableClient(clientId: String): Mono<Void> {
        return getAdminToken().flatMap { token ->
            webClient.put()
                .uri("$adminUrl/admin/realms/$realm/clients/$clientId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .bodyValue(mapOf("enabled" to false))
                .retrieve()
                .bodyToMono<Void>()
        }
    }
}
```

#### Admin UI Consumer Management

**Consumer List Page:**

```typescript
// features/consumers/components/ConsumerList.tsx

interface Consumer {
  clientId: string;
  enabled: boolean;
  createdAt: string;
  // Metrics (from Prometheus)
  requestsPerDay?: number;
  errorRate?: number;
}

const ConsumerList: React.FC = () => {
  const { data: consumers, isLoading } = useConsumers();

  return (
    <Table
      dataSource={consumers}
      columns={[
        { title: 'Consumer ID', dataIndex: 'clientId' },
        { title: 'Status', dataIndex: 'enabled', render: (v) => v ? 'Active' : 'Disabled' },
        { title: 'Requests/Day', dataIndex: 'requestsPerDay' },
        { title: 'Error Rate', dataIndex: 'errorRate', render: (v) => `${(v * 100).toFixed(2)}%` },
        { title: 'Actions', render: (_, record) => (
          <>
            <Button onClick={() => regenerateSecret(record.clientId)}>Rotate Secret</Button>
            <Button danger onClick={() => disableConsumer(record.clientId)}>Disable</Button>
          </>
        )}
      ]}
    />
  );
};
```

**Create Consumer Modal:**

```typescript
// features/consumers/components/CreateConsumerModal.tsx

const CreateConsumerModal: React.FC<Props> = ({ visible, onClose }) => {
  const [form] = Form.useForm();
  const createConsumer = useCreateConsumer();

  const onFinish = async (values: { clientId: string }) => {
    const result = await createConsumer.mutateAsync(values);

    // Показываем secret один раз
    Modal.success({
      title: 'Consumer Created',
      content: (
        <div>
          <p>Client ID: <code>{result.clientId}</code></p>
          <p>Client Secret: <code>{result.secret}</code></p>
          <Alert
            type="warning"
            message="Save this secret now. It won't be shown again."
          />
        </div>
      ),
    });

    onClose();
  };

  return (
    <Modal title="Create API Consumer" visible={visible} onCancel={onClose}>
      <Form form={form} onFinish={onFinish}>
        <Form.Item
          name="clientId"
          label="Client ID"
          rules={[
            { required: true },
            { pattern: /^[a-z0-9-]+$/, message: 'Only lowercase letters, numbers, and hyphens' }
          ]}
        >
          <Input placeholder="company-name" />
        </Form.Item>
        <Button type="primary" htmlType="submit">Create</Button>
      </Form>
    </Modal>
  );
};
```

### Admin UI Keycloak Integration

#### OIDC Client Setup

**Dependencies (package.json):**

```json
{
  "dependencies": {
    "oidc-client-ts": "^3.0.1",
    "react-oidc-context": "^3.1.0"
  }
}
```

**Auth Provider:**

```typescript
// features/auth/providers/AuthProvider.tsx

import { AuthProvider as OidcAuthProvider } from 'react-oidc-context';

const oidcConfig: OidcClientSettings = {
  authority: import.meta.env.VITE_KEYCLOAK_URL + '/realms/api-gateway',
  client_id: 'gateway-admin-ui',
  redirect_uri: window.location.origin + '/callback',
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  response_type: 'code',
  automaticSilentRenew: true,
  loadUserInfo: true,
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <OidcAuthProvider {...oidcConfig}>
      {children}
    </OidcAuthProvider>
  );
};
```

**useAuth Hook:**

```typescript
// features/auth/hooks/useAuth.ts

import { useAuth as useOidcAuth } from 'react-oidc-context';

export const useAuth = () => {
  const oidc = useOidcAuth();

  return {
    isAuthenticated: oidc.isAuthenticated,
    isLoading: oidc.isLoading,
    user: oidc.user ? {
      id: oidc.user.profile.sub,
      username: oidc.user.profile.preferred_username,
      email: oidc.user.profile.email,
      roles: (oidc.user.profile.realm_access as any)?.roles || [],
    } : null,
    login: () => oidc.signinRedirect(),
    logout: () => oidc.signoutRedirect(),
    getAccessToken: () => oidc.user?.access_token,
  };
};
```

**Axios Interceptor:**

```typescript
// shared/utils/axios.ts

import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

// Добавляем token к каждому запросу
api.interceptors.request.use((config) => {
  const token = getAccessTokenFromOidc(); // Получаем из oidc context
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Обработка 401 — redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Trigger re-login
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

### Gateway Admin Keycloak Integration

#### Spring Security Configuration

```kotlin
// config/SecurityConfig.kt

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private lateinit var issuerUri: String

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { exchanges ->
                exchanges
                    // Public endpoints
                    .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    // Role-based access
                    .pathMatchers(HttpMethod.POST, "/api/v1/routes/*/submit").hasRole("DEVELOPER")
                    .pathMatchers(HttpMethod.POST, "/api/v1/routes/*/approve").hasRole("SECURITY")
                    .pathMatchers(HttpMethod.POST, "/api/v1/routes/*/reject").hasRole("SECURITY")
                    .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                    .pathMatchers("/api/v1/consumers/**").hasRole("ADMIN")
                    // Default: authenticated
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): ReactiveJwtAuthenticationConverter {
        val converter = ReactiveJwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(KeycloakRoleConverter())
        return converter
    }

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri)
    }
}
```

**Keycloak Role Converter:**

```kotlin
// security/KeycloakRoleConverter.kt

class KeycloakRoleConverter : Converter<Jwt, Flux<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Flux<GrantedAuthority> {
        val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
        val roles = realmAccess?.get("roles") as? List<*> ?: emptyList<String>()

        return Flux.fromIterable(roles)
            .map { role ->
                // Keycloak roles: "admin-ui:developer" → Spring role: "ROLE_DEVELOPER"
                val springRole = role.toString()
                    .removePrefix("admin-ui:")
                    .uppercase()
                SimpleGrantedAuthority("ROLE_$springRole")
            }
    }
}
```

### Grafana Dashboard Updates

#### Consumer Metrics Panel

```json
{
  "title": "Requests by Consumer",
  "type": "timeseries",
  "targets": [
    {
      "expr": "sum by (consumer_id) (rate(gateway_requests_total[5m]))",
      "legendFormat": "{{consumer_id}}"
    }
  ]
}
```

#### Consumer Comparison Table

```json
{
  "title": "Consumer Statistics",
  "type": "table",
  "targets": [
    {
      "expr": "sum by (consumer_id) (increase(gateway_requests_total[24h]))",
      "legendFormat": "Requests/Day"
    },
    {
      "expr": "sum by (consumer_id) (rate(gateway_errors_total[5m])) / sum by (consumer_id) (rate(gateway_requests_total[5m]))",
      "legendFormat": "Error Rate"
    }
  ],
  "transformations": [
    {
      "id": "merge"
    }
  ]
}
```

### Phase 2 Implementation Sequence

**Recommended order:**

1. **Keycloak Setup** (Story 12.1)
   - Docker Compose с Keycloak
   - Realm configuration
   - Initial clients setup

2. **Admin UI Keycloak Auth** (Story 12.2)
   - OIDC client integration
   - Replace custom JWT auth
   - Role mapping

3. **Gateway Admin Keycloak** (Story 12.3)
   - Spring Security OAuth2 Resource Server
   - JWT validation
   - Role-based access

4. **Gateway Core JWT Filter** (Story 12.4)
   - JwtAuthenticationFilter
   - Route auth configuration
   - Database migration

5. **Consumer Identity Filter** (Story 12.5)
   - ConsumerIdentityFilter
   - Context propagation
   - MDC integration

6. **Multi-tenant Metrics** (Story 12.6)
   - MetricsFilter extension
   - PromQL queries
   - Grafana dashboards

7. **Consumer Management UI** (Story 12.7)
   - Consumer CRUD
   - Keycloak Admin API client
   - Secret rotation

8. **Per-consumer Rate Limits** (Story 12.8)
   - Database schema
   - RateLimitFilter extension
   - Admin API endpoints

### Phase 2 Architecture Validation

**Decision Compatibility:**
- Keycloak + Spring Security OAuth2 — стандартная интеграция ✅
- OIDC + React (oidc-client-ts) — production-ready ✅
- JWT + Reactive filters — non-blocking ✅
- Prometheus + consumer_id label — cardinality manageable ✅

**Requirements Coverage (Phase 2):**

| FR | Component | Status |
|----|-----------|--------|
| FR32-36 (Keycloak Auth) | SecurityConfig, JwtAuthFilter | ✅ |
| FR37-41 (Route Auth Config) | Route entity, JwtAuthFilter | ✅ |
| FR42-45 (Consumer Identity) | ConsumerIdentityFilter | ✅ |
| FR46-49 (Multi-tenant Metrics) | MetricsFilter, Grafana | ✅ |
| FR50-53 (Per-consumer Rate Limits) | RateLimitFilter, consumer_rate_limits table | ✅ |
| FR54-59 (Consumer Management) | KeycloakAdminClient, Admin UI | ✅ |

**Phase 2 Readiness:** READY FOR IMPLEMENTATION

