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
  - date: '2026-03-02'
    author: 'Winston (Architect)'
    description: 'Phase 3: Infrastructure Migration & CI/CD (Epic 13 completion)'
    sections_updated:
      - 'Infrastructure & Deployment'
      - 'Production Deployment'
      - 'Project Structure & Boundaries'
    sections_added:
      - 'Centralized Infrastructure'
      - 'GitLab CI/CD Pipeline'
      - 'Traefik Reverse Proxy'
      - 'Vault Secrets Management'
      - 'Phase 3 Architecture Summary'
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
| **Code Splitting** | React.lazy + Suspense | Route-based lazy loading (Story 14.4) |
| **Bundle Analysis** | rollup-plugin-visualizer | `npm run build:analyze` |

#### Code Splitting Strategy (Story 14.4)

**Route-based Lazy Loading:**
- Auth компоненты (LoginPage, CallbackPage) загружаются синхронно — нужны при старте
- Feature pages загружаются через `React.lazy()` при навигации
- Prefetch на hover через `usePrefetch` hook для быстрой навигации

**Vendor Chunks (vite.config.ts):**
| Chunk | Содержимое | Размер (gzip) |
|-------|------------|---------------|
| vendor-react | react, react-dom, react-router-dom | ~53KB |
| vendor-antd | antd, @ant-design/icons | ~359KB |
| vendor-charts | @ant-design/charts | ~1KB |
| vendor-utils | axios, dayjs, zod, react-query | ~27KB |
| vendor-auth | oidc-client-ts, react-oidc-context | <1KB |
| vendor-forms | react-hook-form, @hookform/resolvers | <1KB |
| index (main) | app shell, routing, layouts | ~13KB |
| feature-* | lazy-loaded feature pages | 1-5KB each |

**Feature Chunks:**
| Page | Chunk Size (gzip) |
|------|-------------------|
| DashboardPage | ~0.5KB |
| RoutesPage | ~2.7KB |
| RouteFormPage | ~2.7KB |
| UsersPage | ~2.7KB |
| ConsumersPage | ~4.5KB |
| RateLimitsPage | ~3.6KB |
| AuditPage | ~5.3KB |
| MetricsPage | ~2.9KB |

**Bundle Analysis:**
```bash
npm run build:analyze  # Создаёт dist/stats.html treemap
```

### Infrastructure & Deployment

| Решение | Выбор | Rationale |
|---------|-------|-----------|
| **Local Dev** | Docker Compose + Centralized Infra | Сервисы локально, инфраструктура в infra проекте |
| **Reverse Proxy** | Traefik 3.x | Автоматический routing, Let's Encrypt, Docker labels |
| **Container Registry** | Nexus (local) + Docker Hub | Local для CI, public для mirror |
| **CI/CD** | GitLab CI (local) | Полный контроль, Vault интеграция |
| **Secrets** | HashiCorp Vault | Централизованное управление, AppRole auth |
| **Logging Format** | JSON structured (Logstash) | Loki/ELK ready |
| **Metrics Format** | Prometheus | Micrometer export, централизованный сбор |
| **Health Checks** | Spring Actuator | /health, /ready endpoints |
| **Database** | PostgreSQL (centralized) | Shared instance в infra проекте |
| **Cache** | Redis (centralized) | Shared instance в infra проекте |
| **Auth Provider** | Keycloak (centralized) | SSO, OAuth2/OIDC |

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

### Complete Project Directory Structure (актуально на 2026-03-02)

```
api-gateway/
├── README.md
├── CLAUDE.md                              # Rules и conventions
├── docker-compose.yml                     # External networks only
├── docker-compose.override.yml            # Dev services (hot-reload)
├── docker-compose.override.yml.example    # Template для разработчиков
├── .gitignore
├── .env.example
│
├── backend/
│   ├── build.gradle.kts                   # Root Gradle build
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   │
│   ├── gateway-common/                    # Shared code
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/company/gateway/common/
│   │       ├── model/                     # Domain entities
│   │       ├── dto/                       # Shared DTOs
│   │       ├── exception/                 # Custom exceptions
│   │       └── util/                      # Utilities
│   │
│   ├── gateway-admin/                     # Admin API (port 8081)
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/
│   │       │   ├── kotlin/com/company/gateway/admin/
│   │       │   │   ├── AdminApplication.kt
│   │       │   │   ├── client/            # HTTP clients (Prometheus, Keycloak)
│   │       │   │   ├── config/            # Security, OpenAPI, Redis config
│   │       │   │   ├── controller/        # 8 REST controllers
│   │       │   │   ├── dto/               # Request/Response DTOs
│   │       │   │   ├── exception/         # Error handling
│   │       │   │   ├── properties/        # Configuration properties
│   │       │   │   ├── publisher/         # Redis pub/sub publisher
│   │       │   │   ├── repository/        # R2DBC repositories
│   │       │   │   ├── security/          # JWT, Keycloak, RBAC
│   │       │   │   └── service/           # 14 business services
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       ├── application-prod.yml
│   │       │       ├── application-test.yml
│   │       │       └── db/migration/      # 14 Flyway migrations (V1-V13)
│   │       └── test/kotlin/               # 54 tests
│   │
│   └── gateway-core/                      # Gateway Runtime (port 8080)
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── kotlin/com/company/gateway/core/
│           │   │   ├── GatewayApplication.kt
│           │   │   ├── cache/             # Route cache, rate limit cache
│           │   │   ├── config/            # Gateway, Security, Keycloak
│           │   │   ├── controller/        # Health, debug endpoints
│           │   │   ├── exception/         # Error handling
│           │   │   ├── filter/            # 6 global filters
│           │   │   ├── properties/        # Configuration properties
│           │   │   ├── ratelimit/         # Token bucket (Caffeine + Redis)
│           │   │   ├── repository/        # R2DBC repositories
│           │   │   ├── route/             # Dynamic route loading
│           │   │   └── util/              # Utilities
│           │   └── resources/
│           │       ├── application.yml
│           │       └── application-dev.yml
│           └── test/kotlin/               # 25 tests
│
├── frontend/
│   └── admin-ui/
│       ├── package.json
│       ├── vite.config.ts
│       ├── tsconfig.json
│       ├── playwright.config.ts
│       ├── index.html
│       ├── .env
│       ├── e2e/                           # Playwright E2E tests (221 tests)
│       └── src/
│           ├── main.tsx
│           ├── App.tsx
│           ├── features/
│           │   ├── approval/              # Approval workflow
│           │   ├── audit/                 # Audit logs
│           │   ├── auth/                  # OIDC/Keycloak auth
│           │   ├── consumers/             # API consumer management
│           │   ├── dashboard/             # Main dashboard
│           │   ├── metrics/               # Monitoring & analytics
│           │   ├── rate-limits/           # Rate limiting management
│           │   ├── routes/                # Route CRUD
│           │   ├── test/                  # Load generator
│           │   └── users/                 # User management
│           ├── shared/
│           │   ├── components/            # Reusable components
│           │   ├── constants/             # Constants
│           │   ├── hooks/                 # Custom React hooks
│           │   ├── providers/             # Context providers
│           │   └── utils/                 # Utilities
│           ├── layouts/                   # MainLayout, AuthLayout
│           ├── test/                      # Test utilities
│           └── styles/
│
├── docker/
│   ├── Dockerfile.gateway-core            # Production
│   ├── Dockerfile.gateway-core.dev        # Development (hot-reload)
│   ├── Dockerfile.gateway-admin           # Production
│   ├── Dockerfile.gateway-admin.dev       # Development (hot-reload)
│   ├── Dockerfile.admin-ui                # Production (nginx)
│   ├── Dockerfile.admin-ui.dev            # Development (Vite HMR)
│   ├── Dockerfile.admin-ui.ci             # CI build
│   ├── keycloak/
│   │   └── realm-export.json              # Keycloak realm config
│   ├── postgres/
│   │   └── init-keycloak-db.sql           # DB initialization
│   └── gitlab/                            # Local GitLab CI infrastructure
│       ├── docker-compose.yml             # GitLab, Nexus, Runners
│       ├── .gitlab-ci.yml                 # CI/CD pipeline
│       ├── README.md                      # Infrastructure docs
│       ├── setup-nexus.ps1                # Nexus configuration
│       ├── register-runners.sh            # Runner registration
│       ├── vault-secrets.sh               # Vault secrets loader
│       ├── deploy.sh                      # Deployment script
│       └── rollback.sh                    # Rollback script
│
├── deploy/
│   ├── docker-compose.ci-base.yml         # CI/CD deployment
│   └── README.md
│
├── scripts/
│   ├── seed-demo-data.sql                 # Demo routes и rate limits
│   ├── seed-keycloak-consumers.sh         # Keycloak consumer setup
│   └── *.ps1                              # PowerShell utilities
│
├── docs/
│   ├── cache-sync.md                      # Redis pub/sub documentation
│   ├── monitoring-alerts.md               # Alert rules documentation
│   ├── rate-limiting.md                   # Rate limit algorithms
│   ├── webflux-patterns.md                # Reactive patterns guide
│   └── quick-start-guide.md               # Getting started
│
└── _bmad-output/
    ├── planning-artifacts/
    │   ├── architecture.md                # This document
    │   └── *.md                           # Other planning docs
    └── implementation-artifacts/
        ├── sprint-status.yaml             # Current sprint status
        └── *.md                           # Story implementations
```

### Architectural Boundaries

**Service Boundaries (актуально на 2026-03-02):**

| Service | Responsibility | Port | Dependencies | Networks |
|---------|---------------|------|--------------|----------|
| **gateway-core** | Request routing, rate limiting, JWT validation | 8080 | PostgreSQL (read), Redis, Keycloak (JWKS) | traefik-net, postgres-net, redis-net, monitoring-net |
| **gateway-admin** | Admin API, CRUD operations, Keycloak Admin | 8081 (8082 external) | PostgreSQL (read/write), Redis, Keycloak, Prometheus | traefik-net, postgres-net, redis-net, monitoring-net |
| **admin-ui** | User interface (React SPA) | 3000 | gateway-admin API, Keycloak (OIDC) | traefik-net |

**Централизованные сервисы (infra project):**

| Service | Responsibility | Port | Consumers |
|---------|---------------|------|-----------|
| **PostgreSQL** | Primary database | 5432 | gateway-core, gateway-admin |
| **Redis** | Cache + Pub/Sub | 6379 | gateway-core, gateway-admin |
| **Keycloak** | OAuth2/OIDC provider | 8080 | admin-ui, gateway-admin, gateway-core |
| **Traefik** | Reverse proxy, TLS | 80, 443 | All external traffic |
| **Prometheus** | Metrics collection | 9090 | gateway-core, gateway-admin |
| **Grafana** | Dashboards | 3000 | Operators |
| **Vault** | Secrets management | 8200 | GitLab CI |

**Data Flow (актуально — с Traefik):**
```
# Admin Flow (через Traefik)
User → Traefik (HTTPS) → admin-ui (/) → gateway-admin (/api/v1/) → PostgreSQL
                                                                 → Redis (cache invalidation)
                                                                         ↓ pub/sub
# Gateway Flow (через Traefik)                                           ↓
External Request → Traefik → gateway-core (/api/) → Redis (rate limit) ← ─┘
                                                  → Caffeine (route config)
                                                  → Upstream Service
# Auth Flow
admin-ui → Keycloak (OIDC) → JWT token
gateway-core → Keycloak (JWKS) → JWT validation
```

**Production Data Flow (с Traefik и централизованной инфраструктурой):**
```
Internet → DNS (gateway.ymorozov.ru) → Traefik:443 (TLS termination)
                                           │
           ┌───────────────────────────────┼───────────────────────────────┐
           │                               │                               │
           ▼                               ▼                               ▼
    admin-ui:3000               gateway-admin:8081              gateway-core:8080
    (React SPA)                  (Admin API)                    (Gateway Runtime)
           │                               │                               │
           │                               │                               │
           ▼                               └───────────────┬───────────────┘
    Keycloak:8080                                          │
    (OIDC login)                           ┌───────────────┼───────────────┐
                                           ▼               ▼               ▼
                                      PostgreSQL        Redis         Keycloak
                                        :5432           :6379          :8080
                                                          │           (JWKS)
                                                          ▼
                                                    Prometheus:9090
                                                    (scrape metrics)
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

**Архитектура (после Epic 13):**
- Traefik служит reverse proxy с автоматическим routing через Docker labels
- TLS termination на уровне Traefik (Let's Encrypt)
- Admin UI и Admin API доступны через root path (`/`)
- Gateway Core API доступен через префикс `/api/`
- Swagger UI доступен через `/swagger-ui.html`

**URL маршрутизация:**

| Path | Backend Service | Описание |
|------|-----------------|----------|
| `/` | admin-ui:3000 | React SPA (Admin UI) |
| `/api/v1/*` | gateway-admin:8081 | Admin API (CRUD, аутентификация) |
| `/swagger-ui.html` | gateway-admin:8081 | OpenAPI Swagger UI |
| `/api/*` | gateway-core:8080 | Gateway Core (публичные маршруты) |

### Traefik Reverse Proxy (заменил Nginx в Epic 13.8)

Traefik выполняет роль reverse proxy, обеспечивая:
- Автоматический routing через Docker labels (без конфигурационных файлов)
- Let's Encrypt TLS сертификаты (автоматическое обновление)
- Health checks и circuit breaker
- Middleware (rate limiting, headers, compression)
- Dashboard для мониторинга

**Docker labels конфигурация:**

```yaml
# docker-compose.override.yml

services:
  admin-ui:
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.gateway-ui.rule=Host(`gateway.ymorozov.ru`)"
      - "traefik.http.routers.gateway-ui.entrypoints=websecure"
      - "traefik.http.routers.gateway-ui.tls.certresolver=letsencrypt"
      - "traefik.http.services.gateway-ui.loadbalancer.server.port=3000"
      - "traefik.http.routers.gateway-ui.priority=1"

  gateway-admin:
    labels:
      - "traefik.enable=true"
      # Admin API v1
      - "traefik.http.routers.gateway-admin-api.rule=Host(`gateway.ymorozov.ru`) && PathPrefix(`/api/v1`)"
      - "traefik.http.routers.gateway-admin-api.entrypoints=websecure"
      - "traefik.http.routers.gateway-admin-api.tls.certresolver=letsencrypt"
      - "traefik.http.services.gateway-admin-api.loadbalancer.server.port=8081"
      - "traefik.http.routers.gateway-admin-api.priority=10"
      # Swagger UI
      - "traefik.http.routers.gateway-swagger.rule=Host(`gateway.ymorozov.ru`) && PathPrefix(`/swagger-ui.html`)"
      - "traefik.http.routers.gateway-swagger.priority=10"

  gateway-core:
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.gateway-core.rule=Host(`gateway.ymorozov.ru`) && PathPrefix(`/api`)"
      - "traefik.http.routers.gateway-core.entrypoints=websecure"
      - "traefik.http.routers.gateway-core.tls.certresolver=letsencrypt"
      - "traefik.http.services.gateway-core.loadbalancer.server.port=8080"
      - "traefik.http.routers.gateway-core.priority=5"
      # Strip /api prefix
      - "traefik.http.middlewares.strip-api.stripprefix.prefixes=/api"
      - "traefik.http.routers.gateway-core.middlewares=strip-api"
```

**Traefik конфигурация (в infra проекте):**

```yaml
# traefik/traefik.yml
api:
  dashboard: true

entryPoints:
  web:
    address: ":80"
    http:
      redirections:
        entryPoint:
          to: websecure
          scheme: https
  websecure:
    address: ":443"

certificatesResolvers:
  letsencrypt:
    acme:
      email: admin@ymorozov.ru
      storage: /letsencrypt/acme.json
      httpChallenge:
        entryPoint: web

providers:
  docker:
    exposedByDefault: false
    network: traefik-net
```

**Преимущества Traefik над Nginx:**

| Аспект | Nginx | Traefik |
|--------|-------|---------|
| Конфигурация | Статичные файлы | Docker labels (динамические) |
| TLS сертификаты | Ручной certbot | Автоматический Let's Encrypt |
| Service discovery | Manual upstream | Автоматический через Docker |
| Hot reload | nginx -s reload | Автоматический |
| Dashboard | Требует настройки | Встроенный |

### SSL/TLS Configuration

**Текущее состояние (после Epic 13.8):**
- TLS termination на уровне Traefik
- Let's Encrypt сертификаты с автоматическим обновлением
- HTTP → HTTPS redirect автоматический
- Внутренний трафик между контейнерами идёт по HTTP (внутри Docker network)

**TLS настройки:**
- Provider: Let's Encrypt (ACME)
- Challenge: HTTP-01
- Auto-renewal: встроено в Traefik
- TLS Version: 1.2+ (настраивается через Traefik middleware)
- HSTS: через middleware headers

### Deployment Topology (после Epic 13)

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
┌─────────────────────────────────────────────────────────────────────────┐
│                     CENTRALIZED INFRASTRUCTURE (infra project)          │
│  ┌─────────────────────────────────────┐                                │
│  │   Traefik (Reverse Proxy)           │                                │
│  │   Port: 80, 443                     │                                │
│  │   - Auto TLS (Let's Encrypt)        │                                │
│  │   - Docker labels routing           │                                │
│  │   - Load balancing                  │                                │
│  └─────────────────────────────────────┘                                │
│        │                                                                │
│  ┌─────┴─────┬──────────────┬──────────────┬──────────────┐            │
│  │           │              │              │              │            │
│  ▼           ▼              ▼              ▼              ▼            │
│ PostgreSQL  Redis       Keycloak     Prometheus      Grafana          │
│ :5432       :6379       :8080        :9090           :3000             │
│ (postgres-  (redis-     (traefik-    (monitoring-    (Keycloak        │
│  net)        net)        net)         net)            SSO)             │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │ traefik-net          │                      │
              ▼                      ▼                      ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│   admin-ui       │    │  gateway-admin   │    │   gateway-core   │
│   :3000          │    │  :8081           │    │   :8080          │
│   React SPA      │    │  Admin API +     │    │   Gateway        │
│   (Vite)         │    │  Keycloak Auth   │    │   Runtime        │
│                  │    │  Swagger UI      │    │   JWT Validation │
└──────────────────┘    └──────────────────┘    └──────────────────┘
        │                       │                       │
        │  ┌────────────────────┴───────────────────────┤
        │  │                                            │
        │  ▼ postgres-net                               ▼ redis-net
        │  PostgreSQL ◄─────────────────────────────────► Redis
        │  (R2DBC)                                      (Cache + Pub/Sub)
        │                                               │
        │  ▼ monitoring-net                             │
        │  Prometheus ◄─────────────────────────────────┘
        │  (scrape /actuator/prometheus)
        │
        └──► Keycloak (OIDC auth)
```

**Docker Networks (External — из infra проекта):**

| Network | Сервисы | Назначение |
|---------|---------|------------|
| `traefik-net` | Traefik, admin-ui, gateway-admin, gateway-core | Reverse proxy routing |
| `postgres-net` | PostgreSQL, gateway-admin, gateway-core | Database access |
| `redis-net` | Redis, gateway-admin, gateway-core | Cache и Pub/Sub |
| `monitoring-net` | Prometheus, Grafana, gateway-admin, gateway-core | Metrics collection |

**Docker Compose Stack (ApiGateway project):**

| Service | Container | Port (internal) | Port (external) | Network |
|---------|-----------|-----------------|-----------------|---------|
| admin-ui | admin-ui-dev | 3000 | 3000 | traefik-net |
| gateway-admin | gateway-admin-dev | 8081 | 8082 | traefik-net, postgres-net, redis-net, monitoring-net |
| gateway-core | gateway-core-dev | 8080 | 8080 | traefik-net, postgres-net, redis-net, monitoring-net |

**Централизованная инфраструктура (infra project):**

| Service | Port | Назначение |
|---------|------|------------|
| traefik | 80, 443 | Reverse proxy, TLS termination |
| postgres (infra-postgres) | 5432 | Shared PostgreSQL instance |
| redis | 6379 | Shared Redis instance |
| keycloak | 8080 | OAuth2/OIDC provider |
| prometheus | 9090 | Metrics collection |
| grafana | 3000 | Dashboards (Keycloak SSO) |
| vault | 8200 | Secrets management |

**Примечание:** PostgreSQL, Redis, Keycloak, Prometheus, Grafana и Vault запущены в отдельном infra проекте. ApiGateway подключается к ним через external Docker networks.

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

**Phase 2 Readiness:** ✅ IMPLEMENTED (Epic 12 completed)

---

## Phase 3: Infrastructure Migration & CI/CD (Epic 13)

_Добавлено: 2026-03-02. Миграция на централизованную инфраструктуру и GitLab CI/CD._

### Centralized Infrastructure

#### Архитектурное решение

**Проблема:** Каждый проект запускал свои PostgreSQL, Redis, Keycloak — дублирование ресурсов и сложность управления.

**Решение:** Централизованная инфраструктура в отдельном `infra` проекте:

```
infra/
├── docker-compose.yml           # Все shared сервисы
├── traefik/                     # Reverse proxy config
├── prometheus/                  # Monitoring config
├── grafana/                     # Dashboards
├── vault/                       # Secrets management
└── keycloak/                    # OAuth2/OIDC provider
```

**Преимущества:**
- Единый PostgreSQL instance для всех проектов
- Единый Redis для кэширования
- Централизованный Keycloak (SSO)
- Общий Prometheus/Grafana stack
- Vault для secrets management

#### Docker Networks

```yaml
# Создаются в infra проекте, используются как external
networks:
  traefik-net:
    external: true
  postgres-net:
    external: true
  redis-net:
    external: true
  monitoring-net:
    external: true
```

**Инициализация сетей:**

```bash
# Выполнить один раз перед первым запуском
docker network create traefik-net 2>/dev/null || true
docker network create postgres-net 2>/dev/null || true
docker network create redis-net 2>/dev/null || true
docker network create monitoring-net 2>/dev/null || true
```

#### Database Configuration

**PostgreSQL в infra проекте:**

```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    container_name: infra-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}  # Из Vault
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d
    networks:
      - postgres-net
```

**Инициализация баз данных:**

```sql
-- infra/postgres/init/01-create-databases.sql
CREATE DATABASE gateway;
CREATE DATABASE keycloak;
CREATE USER gateway WITH PASSWORD 'gateway_password';
GRANT ALL PRIVILEGES ON DATABASE gateway TO gateway;
```

**Подключение из ApiGateway:**

```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://infra-postgres:5432/gateway
    username: gateway
    password: ${POSTGRES_PASSWORD}
```

### GitLab CI/CD Pipeline

#### Локальная GitLab инфраструктура

**Компоненты (docker/gitlab/):**

| Компонент | Порт | Назначение |
|-----------|------|------------|
| GitLab CE | 8929 | Git repository, CI/CD |
| Nexus Repository | 8081 | Docker registry, Maven/npm proxy |
| GitLab Runners (x4) | — | Docker executor |
| Vault | 8200 | Secrets management |

**Архитектура CI/CD:**

```
┌─────────────────────────────────────────────────────────────────┐
│                        GitLab CE                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    .gitlab-ci.yml                        │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐    │   │
│  │  │  build  │→ │  test   │→ │ docker  │→ │ deploy  │    │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│              ┌───────────────┴───────────────┐                  │
│              ▼                               ▼                  │
│  ┌───────────────────┐           ┌───────────────────┐         │
│  │   Runner Pool     │           │      Vault        │         │
│  │  (Docker executor)│           │  (AppRole auth)   │         │
│  │  - runner-1       │           │                   │         │
│  │  - runner-2       │           │  secret/apigateway│         │
│  │  - runner-3       │           │    /database      │         │
│  │  - runner-4       │           │    /redis         │         │
│  └───────────────────┘           │    /keycloak      │         │
│              │                   └───────────────────┘         │
│              ▼                                                  │
│  ┌───────────────────┐                                         │
│  │      Nexus        │                                         │
│  │  - Docker images  │                                         │
│  │  - npm cache      │                                         │
│  │  - Gradle cache   │                                         │
│  └───────────────────┘                                         │
└─────────────────────────────────────────────────────────────────┘
```

#### Pipeline Stages

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - docker
  - deploy
  - sync

# Build stage (~7 min)
backend-build:
  stage: build
  script:
    - ./gradlew assemble
  artifacts:
    paths:
      - backend/*/build/libs/*.jar

frontend-build:
  stage: build
  script:
    - cd frontend/admin-ui && npm ci && npm run build
  artifacts:
    paths:
      - frontend/admin-ui/dist/

# Test stage (~12 min)
backend-test:
  stage: test
  services:
    - postgres:16-alpine
    - redis:7-alpine
  script:
    - ./gradlew test jacocoTestReport

frontend-test:
  stage: test
  script:
    - cd frontend/admin-ui && npm run test:run

sast:
  stage: test
  script:
    - semgrep --config auto
  allow_failure: true  # TODO: Story 14.2 — сделать blocking

# Docker stage (~2 min)
docker-build:
  stage: docker
  script:
    - docker build -f docker/Dockerfile.gateway-core -t gateway-core .
    - docker build -f docker/Dockerfile.gateway-admin -t gateway-admin .
    - docker build -f docker/Dockerfile.admin-ui -t admin-ui .
    - docker push nexus:8082/gateway-core:$CI_COMMIT_SHA
    - docker push nexus:8082/gateway-admin:$CI_COMMIT_SHA
    - docker push nexus:8082/admin-ui:$CI_COMMIT_SHA

# Deploy stages
deploy-dev:
  stage: deploy
  environment: development
  script:
    - ./deploy/deploy.sh dev $CI_COMMIT_SHA
  when: on_success

deploy-test:
  stage: deploy
  environment: test
  script:
    - ./deploy/deploy.sh test $CI_COMMIT_SHA
  when: manual

deploy-prod:
  stage: deploy
  environment: production
  script:
    - ./deploy/deploy.sh prod $CI_COMMIT_SHA
  when: manual
  rules:
    - if: $CI_COMMIT_BRANCH == "master"

# Sync to GitHub
sync-to-github:
  stage: sync
  script:
    - git push origin master
  when: manual
```

#### Vault Integration

**Secrets paths:**

| Path | Secrets |
|------|---------|
| `secret/apigateway/database` | POSTGRES_USER, POSTGRES_PASSWORD, DATABASE_URL |
| `secret/apigateway/redis` | REDIS_HOST, REDIS_PORT, REDIS_URL |
| `secret/apigateway/keycloak` | KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD |
| `secret/apigateway/jwt` | JWT_SECRET (для legacy auth) |

**AppRole authentication:**

```bash
# CI/CD variables в GitLab
VAULT_ADDR=http://vault:8200
VAULT_ROLE_ID=<role-id>
VAULT_SECRET_ID=<secret-id>  # masked, protected

# В pipeline
vault write auth/approle/login \
  role_id=$VAULT_ROLE_ID \
  secret_id=$VAULT_SECRET_ID

export VAULT_TOKEN=$(vault write -field=token auth/approle/login ...)
vault kv get -format=json secret/apigateway/database | jq -r '.data.data'
```

**Скрипт для локальной разработки:**

```bash
# docker/gitlab/vault-secrets.sh
#!/bin/bash
export $(vault kv get -format=json secret/apigateway/database | \
  jq -r '.data.data | to_entries | .[] | "\(.key)=\(.value)"')
export $(vault kv get -format=json secret/apigateway/redis | \
  jq -r '.data.data | to_entries | .[] | "\(.key)=\(.value)"')
```

### Monitoring Migration (Story 13.11)

#### Централизованный Prometheus

**Scrape конфигурация (в infra проекте):**

```yaml
# infra/prometheus/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'apigateway-core'
    static_configs:
      - targets: ['gateway-core:8080']
    metrics_path: /actuator/prometheus

  - job_name: 'apigateway-admin'
    static_configs:
      - targets: ['gateway-admin:8081']
    metrics_path: /actuator/prometheus
```

**Alert Rules:**

```yaml
# infra/prometheus/rules/apigateway.yml
groups:
  - name: apigateway
    rules:
      - alert: HighConsumerCardinality
        expr: count(count by (consumer_id) (gateway_requests_total)) > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High consumer cardinality (>1000)"

      - alert: CriticalConsumerCardinality
        expr: count(count by (consumer_id) (gateway_requests_total)) > 5000
        for: 5m
        labels:
          severity: critical

      - alert: HighErrorRate
        expr: |
          sum(rate(gateway_errors_total[5m])) /
          sum(rate(gateway_requests_total[5m])) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Error rate > 5%"

      - alert: HighLatencyP95
        expr: |
          histogram_quantile(0.95,
            sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 latency > 500ms"

      - alert: GatewayDown
        expr: up{job=~"apigateway-.*"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Gateway instance down"
```

#### Grafana Dashboard

**Dashboard "API Gateway" (автоматически provisioned):**

```json
{
  "title": "API Gateway",
  "panels": [
    {
      "title": "Requests per Second",
      "type": "timeseries",
      "targets": [
        {
          "expr": "sum(rate(gateway_requests_total[5m]))",
          "legendFormat": "Total RPS"
        }
      ]
    },
    {
      "title": "Latency (P50, P95, P99)",
      "type": "timeseries",
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))",
          "legendFormat": "P50"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))",
          "legendFormat": "P95"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))",
          "legendFormat": "P99"
        }
      ]
    },
    {
      "title": "Error Rate",
      "type": "gauge",
      "targets": [
        {
          "expr": "sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m])) * 100"
        }
      ]
    },
    {
      "title": "Top Routes by Traffic",
      "type": "table",
      "targets": [
        {
          "expr": "topk(10, sum by (route_path) (rate(gateway_requests_total[5m])))"
        }
      ]
    },
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
  ]
}
```

### Database Migrations Summary

**14 Flyway миграций (актуальный список):**

| Migration | Description | Lines |
|-----------|-------------|-------|
| V1 | Create routes table | 27 |
| V2 | Add updated_at trigger | 17 |
| V3 | Create users table | 34 |
| V3_1 | Seed admin user | 12 |
| V4 | Create audit_logs | 29 |
| V5 | Add description to routes | 6 |
| V5_1 | Fix audit_logs FK cascade | 17 |
| V6 | Add approval fields to routes | 23 |
| V7 | Create rate_limits table | 34 |
| V8 | Add rate_limit_id to routes | 8 |
| V9 | Extend audit_logs (changes column) | 12 |
| V10 | Add route auth fields (auth_required, allowed_consumers) | 19 |
| V12 | Create consumer_rate_limits table | 35 |
| V13 | Fix rate_limits FK cascade | 30 |

**Total:** 303 lines SQL, 14 migrations

### Current Technology Versions

| Component | Version | Status |
|-----------|---------|--------|
| **Backend** | | |
| Spring Boot | 3.4.2 | ✅ Current |
| Spring Cloud | 2024.0.0 | ✅ Current |
| Kotlin | 1.9.22 | ✅ Current |
| Java | 21 (LTS) | ✅ Current |
| PostgreSQL (R2DBC) | 1.0.4 | ✅ Current |
| Redis Reactive | Latest | ✅ Current |
| Flyway | Core | ✅ Current |
| **Frontend** | | |
| React | 18.2.0 | ✅ Current |
| TypeScript | 5.3.3 | ✅ Strict mode |
| Vite | 5.1.4 | ✅ Current |
| React Query | 5.24.1 | ✅ TanStack v5 |
| React Router | 6.22.1 | ✅ v6 |
| Ant Design | 5.15.0 | ✅ Current |
| **Infrastructure** | | |
| Docker | 24+ | ✅ Current |
| Traefik | 3.x | ✅ Current |
| PostgreSQL | 16-alpine | ✅ Current |
| Redis | 7-alpine | ✅ Current |
| Keycloak | 24.0 | ✅ Current |
| Prometheus | Latest | ✅ Current |
| Grafana | Latest | ✅ Current |
| HashiCorp Vault | Latest | ✅ Current |

### Phase 3 Implementation Sequence (Completed)

| Story | Description | Status |
|-------|-------------|--------|
| 13.0 | Local GitLab setup | ✅ |
| 13.1 | GitHub mirror configuration | ✅ |
| 13.2 | CI/CD pipeline (build/test) | ✅ |
| 13.3 | Docker image build & registry | ✅ |
| 13.4 | Vault integration (secrets management) | ✅ |
| 13.5 | Deployment pipeline (dev/test) | ✅ |
| 13.6 | Production deployment (approval gates) | ✅ |
| 13.7 | Security scanning (SAST) | ✅ |
| 13.8 | Traefik routing (nginx → Traefik) | ✅ |
| 13.9 | PostgreSQL migration to centralized infra | ✅ |
| 13.10 | Redis migration to centralized infra | ✅ |
| 13.11 | Monitoring migration (Prometheus/Grafana) | ✅ |
| 13.12 | Docker compose cleanup & documentation | ✅ |

### Phase 3 Architecture Validation

**Decision Compatibility:**
- Traefik + Docker labels — zero-config routing ✅
- External networks — clean separation of concerns ✅
- Vault + AppRole — secure CI/CD secrets ✅
- Centralized Prometheus — single source of metrics ✅
- GitLab CI + Nexus — full local control ✅

**Infrastructure Benefits:**

| Aspect | Before (Epic 12) | After (Epic 13) |
|--------|------------------|-----------------|
| Reverse Proxy | Nginx (manual config) | Traefik (auto-routing) |
| TLS Certificates | Manual certbot | Auto Let's Encrypt |
| Database | Per-project PostgreSQL | Shared PostgreSQL |
| Cache | Per-project Redis | Shared Redis |
| Secrets | .env files | HashiCorp Vault |
| CI/CD | GitHub Actions | Local GitLab |
| Container Registry | Docker Hub | Local Nexus |
| Monitoring | Per-project stacks | Centralized Prometheus/Grafana |

**Phase 3 Status:** ✅ COMPLETED (Epic 13 done)

---

## Next Phase: Technical Debt & Reliability (Epic 14)

**Planned stories (from Architecture Audit 2026-03-01):**

| Story | Description | Priority | SP |
|-------|-------------|----------|---|
| 14.1 | Fix reactive pipeline blocking calls | P0 | 3 |
| 14.2 | SAST blocking mode & migration cleanup | P0 | 5 |
| 14.3 | Custom metrics & SLI/SLO definition | P1 | 5 |
| 14.4 | Frontend code splitting & performance | P1 | 5 |
| 14.5 | Distributed tracing (Jaeger/OpenTelemetry) | P1 | 5 |

**Total:** ~23 SP

**Key improvements:**
- Устранить синхронные вызовы в reactive pipeline (P0)
- SAST в blocking режиме (P0)
- Определить SLI/SLO для gateway
- Code splitting для уменьшения bundle size
- Distributed tracing для debug latency issues

---

_Последнее обновление: 2026-03-02 (Phase 3 completion)_

