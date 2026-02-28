# Sprint Change Proposal: Centralized Infrastructure Migration

**Date:** 2026-02-28
**Author:** Yury (via SM Agent)
**Status:** Approved

---

## Executive Summary

Появилась централизованная инфраструктура (infra compose group) с Vault, Prometheus, Grafana, PostgreSQL, Traefik, Redis. ApiGateway и n8n будут использовать эту инфраструктуру. Требуется расширение Epic 13 для миграции.

---

## Trigger

| Aspect | Value |
|--------|-------|
| **Type** | Strategic pivot — централизация инфраструктуры |
| **Source** | Внешнее инфраструктурное решение |
| **Scope** | Epic 13 expansion |

## Infrastructure Changes

| Component | Before | After |
|-----------|--------|-------|
| **PostgreSQL** | Свой в compose | Централизованный (infra) |
| **Redis** | Свой в compose | Централизованный (infra) |
| **Reverse Proxy** | Nginx (свой) | Traefik (infra) |
| **Secrets** | GitLab Variables | Vault (infra) |
| **Prometheus/Grafana** | Свой (profile) | Централизованный (infra) |

---

## Impact Analysis

### Epic Impact

**Epic 13: GitLab CI/CD & Secrets Management** — расширен до **GitLab CI/CD & Infrastructure Migration**

| Story | Change |
|-------|--------|
| 13.4 | RENAMED: GitLab Variables → Vault Integration |
| 13.5-13.6 | ADAPT: Deployment pipelines для infra |
| 13.8-13.12 | NEW: Infrastructure migration stories |

### Artifact Impact

| Artifact | Change Required |
|----------|-----------------|
| Architecture.md | Новая секция "Centralized Infrastructure" |
| docker-compose.yml | Убрать postgres, redis, nginx |
| CLAUDE.md | Обновить Development Commands |
| .gitlab-ci.yml | Vault integration |

---

## Stories Added

| ID | Story | SP | Description |
|----|-------|----|----|
| 13.8 | Traefik Routing Configuration | 3 | nginx → Traefik, настройка routing rules |
| 13.9 | PostgreSQL Migration to Infra | 3 | Миграция БД в централизованный postgres |
| 13.10 | Redis Migration to Infra | 2 | Переключение на централизованный redis |
| 13.11 | Monitoring Migration | 3 | Перенос dashboards в infra Grafana |
| 13.12 | Cleanup & Documentation | 2 | Очистка compose, обновление документации |

**Total Added:** 13 SP

## Stories Modified

| ID | Story | Change |
|----|-------|--------|
| 13.4 | Secrets Management | GitLab Variables → Vault |
| 13.5 | Deployment Pipeline | Адаптировать под infra |
| 13.6 | Production Deployment | Адаптировать под infra |

---

## Recommended Implementation Order

```
13.4 (Vault)
    → 13.8 (Traefik)
    → 13.9 (PostgreSQL)
    → 13.10 (Redis)
    → 13.11 (Monitoring)
    → 13.5 (Deploy Dev)
    → 13.6 (Deploy Prod)
    → 13.7 (SAST)
    → 13.12 (Cleanup)
```

---

## Story Details

### Story 13.4: Vault Integration for Secrets (CHANGED)

**As a** DevOps Engineer,
**I want** secrets stored in HashiCorp Vault and injected into applications,
**So that** credentials are centrally managed and securely accessed.

**Acceptance Criteria:**
- Applications retrieve secrets from Vault (DATABASE_URL, REDIS_URL, JWT_SECRET, KEYCLOAK_CLIENT_SECRET)
- Secrets not visible in environment variables or logs
- CI/CD pipeline authenticates via AppRole
- Graceful failure if Vault unavailable

**SP: 5**

---

### Story 13.8: Traefik Routing Configuration (NEW)

**As a** DevOps Engineer,
**I want** ApiGateway services routed through centralized Traefik,
**So that** I have unified reverse proxy management.

**Acceptance Criteria:**
- Traefik routes: gateway.{domain}/ → admin-ui, /api/v1/* → gateway-admin, /api/* → gateway-core
- TLS termination at Traefik level
- nginx container and config removed

**SP: 3**

---

### Story 13.9: PostgreSQL Migration to Infra (NEW)

**As a** DevOps Engineer,
**I want** ApiGateway using centralized PostgreSQL instance,
**So that** database management is unified.

**Acceptance Criteria:**
- Database "gateway" created in infra postgres
- All tables and data migrated
- Flyway baseline set correctly
- Local postgres service removed

**SP: 3**

---

### Story 13.10: Redis Migration to Infra (NEW)

**As a** DevOps Engineer,
**I want** ApiGateway using centralized Redis instance,
**So that** cache and pub/sub are unified.

**Acceptance Criteria:**
- Rate limiting works correctly
- Route cache pub/sub works
- Key prefix "gateway:*" configured
- Local redis service removed

**SP: 2**

---

### Story 13.11: Monitoring Migration (NEW)

**As a** DevOps Engineer,
**I want** metrics in centralized Prometheus and dashboards in centralized Grafana,
**So that** monitoring is unified.

**Acceptance Criteria:**
- Prometheus scrapes gateway-admin and gateway-core
- Grafana dashboards imported
- Alerting rules configured
- Local monitoring profile removed

**SP: 3**

---

### Story 13.12: Docker Compose Cleanup & Documentation (NEW)

**As a** Developer,
**I want** docker-compose cleaned up and documentation updated,
**So that** setup reflects new architecture.

**Acceptance Criteria:**
- Removed from compose: postgres, redis, nginx, prometheus, grafana
- External network to infra configured
- CLAUDE.md updated
- Architecture.md updated

**SP: 2**

---

## Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Data loss during PostgreSQL migration | Low | pg_dump backup before migration |
| Service downtime | Low | Поэтапная миграция, fallback на локальные сервисы |
| Vault unavailability | Low | Graceful degradation, cached secrets |

---

## Approval

| Role | Name | Date | Decision |
|------|------|------|----------|
| Product Owner | Yury | 2026-02-28 | ✅ Approved |

---

## Next Steps

1. Создать story files для 13.8-13.12
2. Начать с 13.4 (Vault Integration)
3. Обновить Architecture.md после завершения миграции
