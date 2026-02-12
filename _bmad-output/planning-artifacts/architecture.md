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
User → admin-ui → gateway-admin → PostgreSQL
                                → Redis (cache invalidation)
                                        ↓
External Request → gateway-core → Redis (rate limit) → Upstream Service
                              → Caffeine (route config)
```

### Requirements to Structure Mapping

| FR Category | Backend Location | Frontend Location |
|-------------|------------------|-------------------|
| **Route Management (FR1-6)** | `gateway-admin/controller/RouteController.kt` | `features/routes/` |
| **Approval Workflow (FR7-12)** | `gateway-admin/service/ApprovalService.kt` | `features/approval/` |
| **Rate Limiting (FR13-16)** | `gateway-admin/` + `gateway-core/filter/` | `features/rate-limits/` |
| **Monitoring (FR17-20)** | `gateway-admin/controller/MetricsController.kt` | Grafana (external) |
| **Audit (FR21-24)** | `gateway-admin/controller/AuditController.kt` | `features/audit/` |
| **Auth (FR25-27)** | `gateway-admin/security/` | `features/auth/` |
| **Gateway Runtime (FR28-31)** | `gateway-core/` | — |

### Integration Points

**Internal:**
- gateway-admin ↔ gateway-core: Redis pub/sub для cache invalidation
- admin-ui → gateway-admin: REST API через Axios

**External:**
- Prometheus: scrape `/actuator/prometheus`
- Grafana: dashboards via Prometheus datasource
- Upstream services: dynamic routing через gateway-core

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

