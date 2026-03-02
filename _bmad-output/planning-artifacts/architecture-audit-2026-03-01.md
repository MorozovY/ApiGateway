# Архитектурный Аудит ApiGateway

**Дата:** 2026-03-01
**Автор:** Winston (Architect Agent)
**Версия:** 1.0

---

## Executive Summary

| Область | Оценка | Статус |
|---------|--------|--------|
| **Backend** | 8.5/10 | Отлично |
| **Frontend** | 7.5/10 | Хорошо |
| **Infrastructure** | 7.5/10 | Хорошо |
| **Security** | 7/10 | Требует внимания |
| **Observability** | 6/10 | Требует внимания |

**Общая зрелость:** Production-ready с техническим долгом

**Результат:** Создан Epic 14 (Technical Debt & Reliability) с 5 stories, ~23 SP

---

## 1. Backend Architecture

### 1.1 Технологический стек

| Компонент | Версия | Статус |
|-----------|--------|--------|
| Spring Boot | 3.4.2 | Актуальная |
| Spring Cloud Gateway | 2024.0.0 | Актуальная |
| Kotlin | 1.9.22 | Актуальная |
| Java | 21 | LTS |
| PostgreSQL (R2DBC) | 1.0.4 | Актуальная |
| Redis | Reactive | Актуальная |
| Flyway | Core | Актуальная |

### 1.2 Модульная структура

```
backend/
├── gateway-common/     # Shared entities, DTOs, constants
├── gateway-admin/      # Management API (port 8081)
│   ├── controller/     # 8 REST controllers
│   ├── service/        # 14 business services
│   ├── repository/     # R2DBC repositories
│   └── security/       # JWT + Keycloak
└── gateway-core/       # Request Gateway (port 8080)
    ├── filter/         # 6 global filters
    ├── route/          # Dynamic routing
    ├── cache/          # Redis + Caffeine
    └── ratelimit/      # Token bucket
```

### 1.3 Сильные стороны

| Аспект | Оценка | Детали |
|--------|--------|--------|
| Reactive Architecture | 5/5 | Полный WebFlux, нет `.block()` |
| Error Handling | 4/5 | RFC 7807, Correlation ID |
| Security | 4/5 | Keycloak + Legacy JWT, RBAC |
| Caching | 4/5 | Redis + Caffeine fallback |
| Database | 4/5 | R2DBC, Flyway, 13 миграций |
| API Design | 4/5 | REST, OpenAPI, пагинация |

### 1.4 Критические проблемы

#### P0: Синхронный DB call в reactive chain
**Файл:** `gateway-core/route/DynamicRouteLocator.kt:88`
```kotlin
// ПРОБЛЕМА
rateLimit = cacheManager.loadRateLimitSync(rateLimitId)

// РЕШЕНИЕ
cacheManager.loadRateLimitAsync(rateLimitId)
    .flatMap { rateLimit -> ... }
```
**Импакт:** Блокировка Netty event loop при cache miss

#### P0: Flyway out-of-order enabled
**Файл:** `application.yml`
```yaml
flyway:
  out-of-order: true  # ОПАСНО — должно быть false
```
**Импакт:** Миграции могут выполняться в неправильном порядке

### 1.5 Средние проблемы

| # | Проблема | Файл | Приоритет |
|---|----------|------|-----------|
| 1 | Test coverage ~56% | `*/test/kotlin/` | Medium |
| 2 | Нет cache warming | `RouteCacheManager` | Medium |
| 3 | Migration numbering V3, V3_1 | `db/migration/` | Low |
| 4 | X-Forwarded-For без validation | `RateLimitFilter` | Medium |

---

## 2. Frontend Architecture

### 2.1 Технологический стек

| Компонент | Версия | Статус |
|-----------|--------|--------|
| React | 18.2.0 | Актуальная |
| TypeScript | 5.3.3 | Strict mode |
| Vite | 5.1.4 | Актуальная |
| React Router | 6.22.1 | v6 |
| React Query | 5.24.1 | TanStack |
| Ant Design | 5.15.0 | Актуальная |

### 2.2 Структура

```
frontend/admin-ui/src/
├── features/           # Feature-based модули
│   ├── auth/          # Keycloak OIDC
│   ├── routes/        # Route management
│   ├── approval/      # Approval workflow
│   ├── audit/         # Audit logs
│   ├── rate-limits/   # Rate limiting
│   ├── metrics/       # Monitoring
│   ├── consumers/     # JWT consumers
│   └── users/         # User management
├── shared/            # Reusable components
├── layouts/           # MainLayout, AuthLayout
└── test/              # Test utilities
```

### 2.3 Сильные стороны

| Аспект | Оценка | Детали |
|--------|--------|--------|
| Stack | 5/5 | Современный, strict TypeScript |
| Structure | 4/5 | Feature-based isolation |
| State | 4/5 | React Query правильно |
| UI | 4/5 | Ant Design + Dark mode |
| Auth | 4/5 | Keycloak, token refresh |
| Testing | 4/5 | 206 test файлов |

### 2.4 Проблемы

| # | Проблема | Приоритет | Решение |
|---|----------|-----------|---------|
| 1 | Нет code splitting | High | `React.lazy()` |
| 2 | sessionStorage для tokens | Medium | httpOnly cookie proxy |
| 3 | Нет optimistic updates | Medium | `setQueryData()` |
| 4 | E2E на localhost only | Low | Параметризировать URL |
| 5 | Bundle size не отслеживается | Low | size-limit в CI |

---

## 3. Infrastructure Architecture

### 3.1 Docker конфигурация

**Файлы:**
- `docker-compose.yml` — networks only (external)
- `docker-compose.override.yml` — dev apps с hot-reload
- `deploy/docker-compose.prod.yml` — production
- `docker/gitlab/docker-compose.yml` — GitLab CI stack

**Dockerfiles:**
- Java 21 + Alpine для backend
- Nginx/Vite для frontend
- Health checks везде

### 3.2 Сети

```
External Networks (infra project):
├── traefik-net      # Reverse proxy
├── postgres-net     # PostgreSQL
├── redis-net        # Redis
└── monitoring-net   # Prometheus/Grafana

Internal:
└── gateway-network  # Local bridge
```

### 3.3 CI/CD Pipeline

**Stages:** build → test → docker → deploy → sync

| Stage | Jobs | Время |
|-------|------|-------|
| build | backend-build, frontend-build | ~7m |
| test | backend-test, frontend-test, SAST | ~12m |
| docker | 3 images | ~2m |
| deploy | dev → test → prod | manual/auto |
| sync | GitHub mirror | manual |

**Total:** ~20-25 минут (hot cache)

### 3.4 Secrets Management

**HashiCorp Vault:**
```
secret/apigateway/
├── database   # POSTGRES_USER, POSTGRES_PASSWORD
├── redis      # REDIS_HOST, REDIS_PORT
└── keycloak   # KEYCLOAK_ADMIN_PASSWORD
```

**AppRole:** `apigateway-ci` (read-only, 1h TTL)

### 3.5 Проблемы Infrastructure

| # | Проблема | Приоритет | Решение |
|---|----------|-----------|---------|
| 1 | SAST allow_failure: true | P0 | Изменить на false |
| 2 | Нет multi-stage Dockerfiles | Medium | Builder + runtime |
| 3 | Hardcoded domain | Low | Параметризировать |
| 4 | Нет distributed tracing | High | Jaeger/Zipkin |
| 5 | Нет custom metrics | High | Micrometer |

---

## 4. Observability

### 4.1 Текущее состояние

**Prometheus:** Centralized в infra проекте
- Scrape interval: 15s
- Targets: gateway-core:8080, gateway-admin:8081

**Grafana:** Centralized в infra проекте
- Dashboard: "API Gateway"
- Data source: prometheus

**Alerts:**
- HighConsumerCardinality (>1000)
- CriticalConsumerCardinality (>5000)
- high-error-rate (>5%)
- high-latency-p95 (>500ms)
- gateway-down (no metrics 1 min)

### 4.2 Проблемы Observability

| # | Проблема | Импакт |
|---|----------|--------|
| 1 | Нет custom metrics | Не видим domain events |
| 2 | Нет SLI/SLO | Нельзя измерить reliability |
| 3 | Нет distributed tracing | Сложно debug latency |
| 4 | Retention 7d | Мало для анализа |
| 5 | Нет HA monitoring | Single point of failure |

---

## 5. Security

### 5.1 Authentication

**Keycloak Mode (Feature Flag):**
- OAuth2 Resource Server
- JWT RS256 (JWKS endpoint)
- MultiIssuerValidator

**Legacy Mode:**
- JWT HMAC-SHA256
- Cookie-based tokens

### 5.2 Authorization

**RBAC:**
- DEVELOPER — создание/управление своими routes
- SECURITY — approval/rejection
- ADMIN — user management

**Implementation:** `@RequireRole` AOP aspect

### 5.3 Security Trade-offs

| # | Issue | Risk | Mitigation |
|---|-------|------|------------|
| 1 | sessionStorage для tokens | XSS | Короткоживущие сессии |
| 2 | Direct Access Grants | Less secure | Внутренняя сеть |
| 3 | Vault Secret ID в GitLab | Compromise риск | Protected variables |
| 4 | Dev CORS без ограничений | Open access | Только dev profile |

---

## 6. Архитектурные решения (Highlights)

### 6.1 Two-Level Rate Limiting
```
Per-Route RL × Per-Consumer RL → применяется строжайший
```
**File:** `RateLimitService.checkBothLimits()`

### 6.2 Graceful Degradation
```
Redis → Caffeine (50%) → Allow All
```
**File:** `RateLimitService.handleRedisError()`

### 6.3 Approval Workflow
```
DRAFT → PENDING → PUBLISHED/REJECTED → (rollback) → DRAFT
```
**File:** `ApprovalService` с audit logging

### 6.4 Dynamic Routing
```
PostgreSQL → Redis Cache (1m TTL) → Pub/Sub invalidation
```
**File:** `DynamicRouteLocator` + `RouteCacheManager`

---

## 7. Рекомендации по приоритету

### P0: Критические (Sprint Next)

| # | Задача | SP | Story |
|---|--------|----|----|
| 1 | Fix sync DB call | 3 | 14.1 |
| 2 | Flyway out-of-order: false | - | 14.1 |
| 3 | SAST blocking mode | 5 | 14.2 |

### P1: Высокий приоритет (Sprint +1)

| # | Задача | SP | Story |
|---|--------|----|----|
| 4 | Custom metrics + SLI/SLO | 5 | 14.3 |
| 5 | Frontend code splitting | 5 | 14.4 |
| 6 | Distributed tracing | 5 | 14.5 |

### P2: Средний приоритет (Backlog)

| # | Задача | SP |
|---|--------|---|
| 7 | Multi-stage Dockerfiles | 3 |
| 8 | Test coverage 70%+ | 5 |
| 9 | Cache warming on startup | 2 |
| 10 | Blue-green deployment | 8 |

### P3: Низкий приоритет (Nice to Have)

| # | Задача | SP |
|---|--------|---|
| 11 | Storybook для UI | 5 |
| 12 | Dynamic Vault secrets | 5 |
| 13 | Bundle size CI check | 2 |
| 14 | Lighthouse CI | 3 |

---

## 8. Epic 14: Technical Debt & Reliability

**Создан на основе этого аудита:**

| Story | Название | SP | Приоритет |
|-------|----------|----|----|
| 14.1 | Fix Reactive Pipeline Blocking Calls | 3 | P0 |
| 14.2 | SAST Blocking Mode & Migration Cleanup | 5 | P0 |
| 14.3 | Custom Metrics & SLI/SLO Definition | 5 | P1 |
| 14.4 | Frontend Code Splitting & Performance | 5 | P1 |
| 14.5 | Distributed Tracing Integration | 5 | P1 |

**Total:** 23 SP

**Файлы:**
- Epic описание: `_bmad-output/planning-artifacts/epics.md` (Epic 14)
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`

---

## Приложения

### A. Статистика кодовой базы

| Область | Файлов | LOC (approx) |
|---------|--------|--------------|
| Backend (main) | 147 | ~15,000 |
| Backend (test) | 82 | ~8,000 |
| Frontend (src) | 173 | ~12,000 |
| Frontend (test) | 206 | ~10,000 |
| Infrastructure | ~50 | ~2,000 |

### B. Test Coverage

| Модуль | Coverage |
|--------|----------|
| gateway-common | ~70% |
| gateway-admin | ~55% |
| gateway-core | ~50% |
| admin-ui | ~60% |

### C. Dependencies с Known Vulnerabilities

*На момент аудита критических CVE не обнаружено.*

---

**Следующие шаги:**
1. Завершить Story 13.12 (Docker Compose Cleanup)
2. Sprint Planning для Epic 14
3. Начать с Stories 14.1-14.2 (P0 fixes)

---

*Документ сгенерирован архитектурным аудитом 2026-03-01*
