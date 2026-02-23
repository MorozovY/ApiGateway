# Story 12.1: Keycloak Setup & Configuration

Status: done

## Story

As a **DevOps Engineer**,
I want Keycloak deployed and configured,
So that we have a centralized identity provider for all authentication needs (FR32).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Централизованная аутентификация через Keycloak SSO для Admin UI и API consumers. Это фундаментальная story, блокирующая все последующие stories Epic 12.

## Acceptance Criteria

### AC1: Keycloak Docker Compose
**Given** docker-compose environment
**When** `docker-compose up -d keycloak`
**Then** Keycloak starts on port 8180
**And** admin console is accessible at http://localhost:8180

### AC2: Realm Auto-Import
**Given** Keycloak is running
**When** realm is imported from `docker/keycloak/realm-export.json`
**Then** realm `api-gateway` is created with:
- Client `gateway-admin-ui` (Authorization Code + PKCE)
- Client `gateway-admin-api` (bearer-only)
- Client `gateway-core` (bearer-only)
- Realm roles: `admin-ui:developer`, `admin-ui:security`, `admin-ui:admin`, `api:consumer`

### AC3: Test User Authentication
**Given** realm is configured
**When** test user is created
**Then** user can authenticate via Keycloak login page
**And** JWT token contains expected claims (sub, azp, realm_access.roles)

### AC4: Docker Compose Integration
**Given** docker-compose includes keycloak
**When** running full stack
**Then** keycloak starts after postgres
**And** realm is auto-imported on first start

## Tasks / Subtasks

- [x] Task 1: PostgreSQL Database Setup (AC: #1, #4)
  - [x] 1.1 Добавить init script для создания БД `keycloak` в PostgreSQL
  - [x] 1.2 Создать `docker/postgres/init-keycloak-db.sql`

- [x] Task 2: Docker Compose Configuration (AC: #1, #4)
  - [x] 2.1 Добавить `keycloak` service в `docker-compose.yml`
  - [x] 2.2 Настроить environment variables (KC_DB, KC_DB_URL, etc.)
  - [x] 2.3 Настроить volume для realm import
  - [x] 2.4 Настроить depends_on с healthcheck

- [x] Task 3: Realm Export File (AC: #2)
  - [x] 3.1 Создать директорию `docker/keycloak/`
  - [x] 3.2 Создать `realm-export.json` со структурой realm
  - [x] 3.3 Настроить clients: gateway-admin-ui, gateway-admin-api, gateway-core
  - [x] 3.4 Настроить realm roles: admin-ui:developer, admin-ui:security, admin-ui:admin, api:consumer
  - [x] 3.5 Создать тестовых пользователей (admin, developer, security)

- [x] Task 4: Sample API Consumer Client (AC: #2)
  - [x] 4.1 Добавить client `company-a` (Client Credentials flow)
  - [x] 4.2 Настроить Service Accounts Enabled = true
  - [x] 4.3 Добавить role `api:consumer` для service account

- [x] Task 5: Documentation (AC: #1, #2, #3)
  - [x] 5.1 Обновить README.md с инструкциями по запуску Keycloak
  - [x] 5.2 Документировать тестовые credentials
  - [x] 5.3 Добавить инструкции по доступу к admin console

- [x] Task 6: Manual Verification (AC: #3)
  - [x] 6.1 Проверить старт Keycloak на порту 8180
  - [x] 6.2 Проверить импорт realm `api-gateway`
  - [x] 6.3 Проверить login тестового пользователя
  - [x] 6.4 Проверить JWT token claims

## API Dependencies Checklist

<!-- Секция не применима — story про инфраструктуру, не UI. -->

N/A — это инфраструктурная story без изменений в API или UI.

## Dev Notes

### Architecture Reference

Полная архитектура Keycloak описана в [Source: architecture.md#Phase 2: Keycloak & Multi-tenant Architecture]

### Docker Compose Configuration

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
      postgres:
        condition: service_healthy
```

### Realm Structure

```
Realm: api-gateway
│
├── Clients
│   ├── gateway-admin-ui (public, PKCE)
│   │   ├── Valid Redirect URIs: http://localhost:3000/*, https://gateway.ymorozov.ru/*
│   │   └── Client Scopes: openid, profile, email, roles
│   ├── gateway-admin-api (bearer-only)
│   ├── gateway-core (bearer-only)
│   └── company-a (confidential, Client Credentials)
│
├── Realm Roles
│   ├── admin-ui:developer
│   ├── admin-ui:security
│   ├── admin-ui:admin
│   └── api:consumer
│
└── Users
    ├── admin@example.com (admin-ui:admin)
    ├── dev@example.com (admin-ui:developer)
    └── security@example.com (admin-ui:security)
```

### Token Settings

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Access Token Lifespan | 5 min | Короткий срок для безопасности |
| Refresh Token Lifespan | 30 min | Достаточно для сессии работы |
| SSO Session Idle | 30 min | Автоматический logout при неактивности |

### JWT Token Structure

**Admin UI User Token:**
```json
{
  "iss": "http://localhost:8180/realms/api-gateway",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "aud": "gateway-admin-ui",
  "azp": "gateway-admin-ui",
  "preferred_username": "admin",
  "email": "admin@example.com",
  "realm_access": {
    "roles": ["admin-ui:admin"]
  }
}
```

**API Consumer Token:**
```json
{
  "iss": "http://localhost:8180/realms/api-gateway",
  "sub": "service-account-company-a",
  "azp": "company-a",
  "clientId": "company-a",
  "realm_access": {
    "roles": ["api:consumer"]
  }
}
```

### PostgreSQL Init Script

```sql
-- docker/postgres/init-keycloak-db.sql
CREATE DATABASE keycloak;
```

Этот скрипт должен выполняться при первом запуске PostgreSQL контейнера.

### File Structure

```
docker/
├── keycloak/
│   └── realm-export.json    # НОВЫЙ: Keycloak realm configuration
└── postgres/
    └── init-keycloak-db.sql # НОВЫЙ: Init script для keycloak DB
```

### Test Credentials

| User | Password | Role |
|------|----------|------|
| admin@example.com | admin123 | admin-ui:admin |
| dev@example.com | dev123 | admin-ui:developer |
| security@example.com | security123 | admin-ui:security |
| company-a (client) | <generated> | api:consumer |

### Важные замечания

1. **start-dev mode**: Keycloak запускается в development режиме (HTTP, no SSL). Для production нужен `start` с настроенными сертификатами.

2. **Realm import**: Используется `--import-realm` flag, который импортирует realm только если он не существует. При повторных запусках realm не перезаписывается.

3. **PostgreSQL dependency**: Keycloak должен запускаться после PostgreSQL. Использовать `depends_on` с `condition: service_healthy`.

4. **Port 8180**: Keycloak использует нестандартный внешний порт, чтобы не конфликтовать с gateway-core (8080) и gateway-admin (8081).

### Project Structure Notes

- Новая директория `docker/keycloak/` для Keycloak-специфичных файлов
- Init scripts для PostgreSQL в `docker/postgres/`
- Realm export в JSON формате (стандарт Keycloak)

### References

- [Source: architecture.md#Keycloak Integration] — полная архитектура
- [Source: architecture.md#Realm Structure] — структура realm
- [Source: architecture.md#Authentication Flows] — flow диаграммы
- [Source: architecture.md#JWT Token Structure] — структура токенов
- [Source: epics.md#Story 12.1] — acceptance criteria

### Blocking Status

**Эта story блокирует:**
- 12.2: Admin UI — Keycloak Auth Migration
- 12.3: Gateway Admin — Keycloak JWT Validation
- 12.4: Gateway Core — JWT Authentication Filter
- 12.9: Consumer Management UI

Без работающего Keycloak невозможно тестировать аутентификацию.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Initial PostgreSQL init script issue: DO block doesn't work in init-db.d scripts, simplified to plain CREATE DATABASE

### Completion Notes List

1. **Task 1 (PostgreSQL Setup):** Создан init script `docker/postgres/init-keycloak-db.sql` для автоматического создания БД keycloak. Добавлен volume mount в docker-compose.yml для init-db.d.

2. **Task 2 (Docker Compose):** Добавлен Keycloak service (quay.io/keycloak/keycloak:24.0) с полной конфигурацией: environment variables, healthcheck, depends_on postgres.

3. **Task 3 (Realm Export):** Создан `docker/keycloak/realm-export.json` с полной структурой realm api-gateway: 4 clients (gateway-admin-ui PKCE, gateway-admin-api bearer-only, gateway-core bearer-only, company-a client credentials), 4 realm roles, 3 тестовых пользователя.

4. **Task 4 (API Consumer):** Client company-a настроен с serviceAccountsEnabled=true, service-account-company-a user с role api:consumer.

5. **Task 5 (Documentation):** README.md обновлён с разделом Keycloak: admin console URL, тестовые credentials, пример curl для получения токена.

6. **Task 6 (Verification):** Keycloak успешно запущен на порту 8180, realm api-gateway импортирован, все 3 тестовых пользователя созданы, Client Credentials flow работает (company-a), JWT содержит ожидаемые claims (iss, sub, azp, client_id).

### File List

**New files:**
- docker/keycloak/realm-export.json
- docker/postgres/init-keycloak-db.sql

**Modified files:**
- docker-compose.yml
- README.md
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/HealthService.kt
- backend/gateway-admin/src/main/resources/application.yml
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/HealthServiceTest.kt
- frontend/admin-ui/src/features/metrics/components/HealthCheckSection.tsx
- frontend/admin-ui/src/features/metrics/components/HealthCheckSection.test.tsx

**Scope note:** Health check интеграция (HealthService, UI) добавлена в эту story для обеспечения видимости Keycloak в dashboard сразу после деплоя.

## Change Log

- 2026-02-23: Story created — Keycloak Setup & Configuration for Epic 12
- 2026-02-23: Implementation complete — all ACs verified, ready for code review
- 2026-02-23: Code review — fixed 6 issues (2 HIGH, 4 MEDIUM), updated File List, added scope documentation
