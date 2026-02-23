---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
status: complete
completedAt: '2026-02-11'
inputDocuments:
  - 'prd.md'
  - 'architecture.md'
  - 'ux-design-specification.md'
revisions:
  - date: '2026-02-23'
    author: 'Yury'
    description: 'Phase 2: Epic 12 — Keycloak Integration & Multi-tenant Metrics'
    stories_added: 10
    frs_covered: 'FR32-FR59'
---

# ApiGateway - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for ApiGateway, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

**Route Management:**
- FR1: Developer может создать новый маршрут с указанием path, upstream URL и HTTP методов
- FR2: Developer может редактировать существующий маршрут
- FR3: Developer может удалить маршрут в статусе draft
- FR4: Developer может просматривать список всех маршрутов с фильтрацией и поиском
- FR5: Developer может просматривать детали конкретного маршрута
- FR6: Developer может клонировать существующий маршрут

**Approval Workflow:**
- FR7: Developer может отправить маршрут на согласование Security
- FR8: Security может просматривать список маршрутов на согласовании
- FR9: Security может одобрить маршрут для публикации
- FR10: Security может отклонить маршрут с указанием причины
- FR11: Developer может видеть статус своего маршрута (draft/pending/published/rejected)
- FR12: System автоматически публикует маршрут после одобрения Security

**Rate Limiting:**
- FR13: Admin может создать политику rate limiting с настройкой лимитов
- FR14: Admin может редактировать существующую политику rate limiting
- FR15: Developer может назначить политику rate limiting на маршрут
- FR16: System применяет rate limiting к запросам через Gateway

**Monitoring & Metrics:**
- FR17: DevOps может просматривать real-time метрики Gateway (RPS, latency, errors)
- FR18: DevOps может просматривать метрики по конкретному маршруту
- FR19: DevOps может экспортировать метрики в Prometheus формате
- FR20: DevOps может проверить health status Gateway

**Audit & Compliance:**
- FR21: Security может просматривать аудит-лог всех изменений маршрутов
- FR22: Security может фильтровать аудит-лог по пользователю, действию, дате
- FR23: Security может просматривать историю изменений конкретного маршрута
- FR24: Security может фильтровать маршруты по upstream для аудита интеграций

**User & Access Management:**
- FR25: User может аутентифицироваться в системе
- FR26: Admin может назначать роли пользователям (Developer, Security, Admin)
- FR27: System ограничивает действия пользователя согласно его роли

**Gateway Runtime:**
- FR28: System маршрутизирует входящие запросы на соответствующий upstream
- FR29: System возвращает корректные коды ошибок при недоступности upstream
- FR30: System применяет изменения конфигурации без перезапуска (hot-reload)
- FR31: System логирует все запросы через Gateway

### NonFunctional Requirements

**Performance:**
- NFR1: Gateway Latency — P50 < 50ms, P95 < 200ms, P99 < 500ms (без учёта upstream)
- NFR2: Admin API Response — < 500ms для всех CRUD операций
- NFR3: Configuration Reload — < 5 секунд для применения изменений маршрутов
- NFR4: Metrics Update — Real-time, задержка < 10 секунд

**Reliability:**
- NFR5: Uptime — 99.9% (~8.7 часов downtime/год)
- NFR6: Data Durability — 99.99% для конфигурации маршрутов
- NFR7: Graceful Degradation — при недоступности Redis fallback на in-memory
- NFR8: Zero-Downtime Deploys — Rolling updates без прерывания трафика

**Scalability:**
- NFR9: Throughput — 100 RPS baseline, с запасом до 1000 RPS (10x)
- NFR10: Concurrent Connections — 1000+ одновременных подключений к Gateway
- NFR11: Routes — 500+ активных маршрутов
- NFR12: Horizontal Scaling — несколько инстансов Gateway

**Security:**
- NFR13: Authentication — все запросы к Admin API аутентифицированы
- NFR14: Authorization — RBAC: Developer, Security, Admin
- NFR15: Audit Trail — все изменения логируются с user_id и timestamp
- NFR16: Data in Transit — HTTPS/TLS 1.2+ для всех соединений
- NFR17: Secrets Management — credentials не хранятся в plaintext

**Observability:**
- NFR18: Metrics — Prometheus-compatible endpoint
- NFR19: Logging — Structured JSON logs, correlation IDs
- NFR20: Health Checks — Liveness и Readiness endpoints
- NFR21: Alerting — интеграция с Grafana alerting

### Additional Requirements

**Из Architecture:**
- **Starter Template**: Spring Initializr (Spring Boot 3.4.x) + Vite React TypeScript — использовать для инициализации проекта
- **Monorepo структура**: backend/ (gateway-core, gateway-admin, gateway-common) + frontend/ (admin-ui)
- **Database**: PostgreSQL 16 + R2DBC (reactive), Flyway для migrations
- **Cache**: Redis (primary) + Caffeine (local fallback), write-through + event invalidation
- **Authentication MVP**: JWT self-issued, HTTP-only cookies, stateless
- **API Format**: REST + OpenAPI 3.0 (springdoc-openapi)
- **Error Format**: RFC 7807 Problem Details
- **Frontend State**: React Query + Context, Ant Design UI library
- **Infrastructure**: Docker Compose для local dev, Prometheus metrics, JSON structured logs

**Из UX Design:**
- **Platform**: Desktop-first Web SPA (min 1280px, optimal 1920px)
- **Input**: Mouse + Keyboard shortcuts для power users (⌘+N новый, ⌘+Enter сабмит)
- **Layout**: Ant Design Pro — collapsible sidebar, breadcrumbs, header actions
- **Tables**: ProTable с фильтрами, сортировкой, inline actions
- **Status Badges**: Draft (серый), Pending (жёлтый), Published (зелёный), Rejected (красный)
- **Notifications**: Toast notifications для feedback после actions
- **Role-Based Dashboard**: персонализированный home screen для каждой роли
- **Inline Actions**: approve/reject без перехода на детальную страницу
- **Error Prevention**: валидация в реальном времени, защита от ошибок до сабмита

### FR Coverage Map

| FR | Epic | Описание |
|----|------|----------|
| FR1 | Epic 3 | Developer может создать новый маршрут |
| FR2 | Epic 3 | Developer может редактировать маршрут |
| FR3 | Epic 3 | Developer может удалить маршрут в статусе draft |
| FR4 | Epic 3 | Developer может просматривать список маршрутов |
| FR5 | Epic 3 | Developer может просматривать детали маршрута |
| FR6 | Epic 3 | Developer может клонировать маршрут |
| FR7 | Epic 4 | Developer может отправить маршрут на согласование |
| FR8 | Epic 4 | Security может просматривать pending маршруты |
| FR9 | Epic 4 | Security может одобрить маршрут |
| FR10 | Epic 4 | Security может отклонить маршрут |
| FR11 | Epic 4 | Developer может видеть статус маршрута |
| FR12 | Epic 4 | System автоматически публикует после одобрения |
| FR13 | Epic 5 | Admin может создать политику rate limiting |
| FR14 | Epic 5 | Admin может редактировать политику rate limiting |
| FR15 | Epic 5 | Developer может назначить политику на маршрут |
| FR16 | Epic 5 | System применяет rate limiting к запросам |
| FR17 | Epic 6 | DevOps может просматривать real-time метрики |
| FR18 | Epic 6 | DevOps может просматривать метрики по маршруту |
| FR19 | Epic 6 | DevOps может экспортировать метрики в Prometheus |
| FR20 | Epic 6 | DevOps может проверить health status |
| FR21 | Epic 7 | Security может просматривать аудит-лог |
| FR22 | Epic 7 | Security может фильтровать аудит-лог |
| FR23 | Epic 7 | Security может просматривать историю маршрута |
| FR24 | Epic 7 | Security может фильтровать по upstream |
| FR25 | Epic 2 | User может аутентифицироваться |
| FR26 | Epic 2 | Admin может назначать роли |
| FR27 | Epic 2 | System ограничивает действия по роли |
| FR28 | Epic 1 | System маршрутизирует запросы на upstream |
| FR29 | Epic 1 | System возвращает коды ошибок при недоступности |
| FR30 | Epic 1 | System применяет изменения без перезапуска |
| FR31 | Epic 1 | System логирует все запросы |
| **Phase 2 — Keycloak & Multi-tenant** | | |
| FR32 | Epic 12 | Admin UI аутентификация через Keycloak |
| FR33 | Epic 12 | Gateway Admin валидирует JWT от Keycloak |
| FR34 | Epic 12 | System извлекает роли из JWT claims |
| FR35 | Epic 12 | API consumers аутентификация через Client Credentials |
| FR36 | Epic 12 | Gateway Core валидирует JWT через JWKS |
| FR37 | Epic 12 | Developer может настроить маршрут как public |
| FR38 | Epic 12 | Developer может настроить маршрут как protected |
| FR39 | Epic 12 | Developer может ограничить маршрут для consumers |
| FR40 | Epic 12 | System возвращает 401 для protected routes без JWT |
| FR41 | Epic 12 | System возвращает 403 если consumer не в whitelist |
| FR42 | Epic 12 | System извлекает consumer_id из JWT azp |
| FR43 | Epic 12 | System принимает X-Consumer-ID header как fallback |
| FR44 | Epic 12 | System использует "anonymous" для неидентифицированных |
| FR45 | Epic 12 | System добавляет consumer_id в Reactor Context |
| FR46 | Epic 12 | System добавляет consumer_id label к метрикам |
| FR47 | Epic 12 | DevOps может фильтровать метрики по consumer_id |
| FR48 | Epic 12 | DevOps может сравнивать метрики consumers |
| FR49 | Epic 12 | Admin UI отображает breakdown по consumers |
| FR50 | Epic 12 | Admin может создать per-consumer rate limit |
| FR51 | Epic 12 | System применяет per-consumer rate limits |
| FR52 | Epic 12 | System возвращает 429 с указанием типа лимита |
| FR53 | Epic 12 | Per-consumer limits имеют приоритет |
| FR54 | Epic 12 | Admin может просматривать список consumers |
| FR55 | Epic 12 | Admin может создать нового consumer |
| FR56 | Epic 12 | Admin может деактивировать consumer |
| FR57 | Epic 12 | Admin может ротировать secret consumer |
| FR58 | Epic 12 | System синхронизирует с Keycloak Admin API |
| FR59 | Epic 12 | Admin может просматривать метрики consumer |

## Epic List

### Epic 1: Project Foundation & Gateway Core
Gateway принимает и маршрутизирует HTTP-запросы к upstream-сервисам. Базовая инфраструктура работает: monorepo structure, Docker Compose, database migrations, health checks.

**FRs covered:** FR28, FR29, FR30, FR31
**NFRs addressed:** NFR1, NFR5, NFR7, NFR8, NFR18, NFR19, NFR20
**Additional:** Starter template, monorepo setup, PostgreSQL + Redis, Flyway migrations

---

### Epic 2: User Authentication & Access Control
Пользователи могут войти в Admin UI и видят интерфейс согласно своей роли (Developer/Security/Admin). JWT-based authentication, role-based access control.

**FRs covered:** FR25, FR26, FR27
**NFRs addressed:** NFR13, NFR14, NFR16, NFR17
**Additional:** JWT self-issued, HTTP-only cookies, RBAC middleware

---

### Epic 3: Route Management (Self-Service)
Developer может создавать, редактировать, удалять и просматривать маршруты. Мария за 2 минуты настраивает новый endpoint без участия DevOps.

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6
**NFRs addressed:** NFR2, NFR6, NFR11
**Additional:** ProTable с фильтрами, inline validation, клонирование маршрутов

---

### Epic 4: Approval Workflow
Security контролирует публикацию маршрутов (approve/reject). Developer видит статус своих маршрутов. Автоматическая публикация после одобрения. State machine: Draft → Pending → Published/Rejected.

**FRs covered:** FR7, FR8, FR9, FR10, FR11, FR12
**NFRs addressed:** NFR3, NFR15
**Additional:** Inline approve/reject actions, status badges, toast notifications

---

### Epic 5: Rate Limiting
Admin создаёт политики лимитов, Developer назначает их на маршруты. Gateway защищает upstream от перегрузки. Redis-based token bucket algorithm.

**FRs covered:** FR13, FR14, FR15, FR16
**NFRs addressed:** NFR7, NFR9, NFR10
**Additional:** Graceful degradation при недоступности Redis, per-route limits

---

### Epic 6: Monitoring & Observability
DevOps видит real-time метрики (RPS, latency, errors), может экспортировать в Prometheus, проверять health. Алексей за 5 минут находит проблему при инциденте.

**FRs covered:** FR17, FR18, FR19, FR20
**NFRs addressed:** NFR4, NFR18, NFR20, NFR21
**Additional:** Micrometer → Prometheus, Grafana dashboards, correlation IDs

---

### Epic 7: Audit & Compliance
Security может просматривать полный аудит-лог, фильтровать по пользователю/дате/действию, видеть историю изменений маршрутов. Дмитрий за 30 минут готовит отчёт по интеграциям.

**FRs covered:** FR21, FR22, FR23, FR24
**NFRs addressed:** NFR15, NFR19
**Additional:** Event-based audit trail, upstream filtering, change history

---

## Epic 1: Project Foundation & Gateway Core

Gateway принимает и маршрутизирует HTTP-запросы к upstream-сервисам. Базовая инфраструктура работает: monorepo structure, Docker Compose, database migrations, health checks.

### Story 1.1: Project Scaffolding & Monorepo Setup

As a **Developer**,
I want a properly structured monorepo with Gradle multi-module backend and Vite React frontend,
So that I have a solid foundation for implementing all gateway features.

**Acceptance Criteria:**

**Given** a new project directory
**When** the scaffolding is complete
**Then** the following structure exists:
- `backend/` with Gradle multi-module (gateway-core, gateway-admin, gateway-common)
- `frontend/admin-ui/` with Vite + React + TypeScript
- Root `docker-compose.yml` for local development
- `.gitignore`, `README.md`, `.env.example` files
**And** `./gradlew build` compiles without errors
**And** `npm install && npm run dev` starts frontend dev server
**And** all modules follow naming conventions from Architecture doc

---

### Story 1.2: Database Setup & Initial Migrations

As a **Developer**,
I want PostgreSQL database configured with R2DBC and Flyway migrations,
So that I can persist route configurations reliably.

**Acceptance Criteria:**

**Given** Docker Compose is running with PostgreSQL
**When** the gateway-admin application starts
**Then** Flyway executes migration V1__create_routes.sql
**And** the `routes` table is created with columns:
- `id` (UUID, primary key)
- `path` (VARCHAR, not null)
- `upstreamUrl` (VARCHAR, not null)
- `methods` (VARCHAR array)
- `status` (VARCHAR: draft/pending/published/rejected)
- `createdBy` (VARCHAR)
- `createdAt` (TIMESTAMP)
- `updatedAt` (TIMESTAMP)
**And** R2DBC connection pool is configured (max 10 connections)
**And** application logs successful database connection on startup

---

### Story 1.3: Basic Gateway Routing

As a **DevOps Engineer**,
I want the gateway to route incoming requests to upstream services based on database configuration,
So that all API traffic flows through the centralized gateway (FR28).

**Acceptance Criteria:**

**Given** a route exists in database: `path=/api/orders, upstreamUrl=http://order-service:8080, status=published`
**When** a request is made to `GET /api/orders/123`
**Then** the gateway proxies the request to `http://order-service:8080/api/orders/123`
**And** the response from upstream is returned to the client
**And** original headers are preserved (except hop-by-hop headers)

**Given** a route with `status=draft` exists
**When** a request matches that route path
**Then** the gateway returns 404 Not Found (draft routes are not active)

**Given** no matching route exists
**When** a request is made to an unknown path
**Then** the gateway returns 404 with RFC 7807 error format

---

### Story 1.4: Error Handling for Upstream Failures

As a **DevOps Engineer**,
I want proper error responses when upstream services are unavailable,
So that clients receive meaningful error information (FR29).

**Acceptance Criteria:**

**Given** a published route exists
**When** the upstream service is unreachable (connection refused)
**Then** the gateway returns HTTP 502 Bad Gateway
**And** the response body follows RFC 7807 format:
```json
{
  "type": "https://api.gateway/errors/upstream-unavailable",
  "title": "Bad Gateway",
  "status": 502,
  "detail": "Upstream service is unavailable",
  "correlationId": "abc-123"
}
```

**Given** a published route exists
**When** the upstream service times out (>30 seconds)
**Then** the gateway returns HTTP 504 Gateway Timeout

**Given** a published route exists
**When** the upstream returns 5xx error
**Then** the gateway passes through the upstream error response unchanged

---

### Story 1.5: Configuration Hot-Reload

As a **DevOps Engineer**,
I want route configuration changes to apply without restarting the gateway,
So that I can update routing in production safely (FR30).

**Acceptance Criteria:**

**Given** gateway-core is running with cached routes
**When** a route is updated in the database
**And** a cache invalidation event is published to Redis
**Then** gateway-core refreshes its route cache within 5 seconds
**And** subsequent requests use the updated route configuration
**And** no requests are dropped during the refresh

**Given** Redis is unavailable
**When** a cache invalidation is needed
**Then** gateway-core uses Caffeine local cache with TTL fallback (60 seconds)
**And** a warning is logged about Redis unavailability

---

### Story 1.6: Request Logging & Correlation IDs

As a **DevOps Engineer**,
I want all gateway requests logged with correlation IDs,
So that I can trace requests across services (FR31).

**Acceptance Criteria:**

**Given** an incoming request without X-Correlation-ID header
**When** the request passes through the gateway
**Then** a new UUID correlation ID is generated
**And** the correlation ID is added to the request headers sent to upstream
**And** the correlation ID is included in the response headers
**And** the request is logged in JSON format with fields:
- `timestamp`, `correlationId`, `method`, `path`, `status`, `duration`, `upstreamUrl`

**Given** an incoming request with X-Correlation-ID header
**When** the request passes through the gateway
**Then** the existing correlation ID is preserved and propagated

---

### Story 1.7: Health Checks & Docker Compose

As a **DevOps Engineer**,
I want health check endpoints and a complete local development environment,
So that I can verify system health and develop locally (NFR20).

**Acceptance Criteria:**

**Given** gateway-core and gateway-admin are running
**When** a request is made to `/actuator/health`
**Then** the response returns HTTP 200 with status "UP"
**And** includes health of: database, redis, diskSpace

**Given** gateway-core is starting but database is not ready
**When** a request is made to `/actuator/health/readiness`
**Then** the response returns HTTP 503 with status "DOWN"

**Given** Docker Compose file exists
**When** `docker-compose up -d` is executed
**Then** the following services start:
- PostgreSQL 16 on port 5432
- Redis 7 on port 6379
**And** services have health checks configured
**And** gateway applications can connect to both services

---

## Epic 2: User Authentication & Access Control

Пользователи могут войти в Admin UI и видят интерфейс согласно своей роли (Developer/Security/Admin). JWT-based authentication, role-based access control.

### Story 2.1: User Entity & Database Schema

As a **Developer**,
I want a users table with role support,
So that I can store user credentials and permissions.

**Acceptance Criteria:**

**Given** gateway-admin application starts
**When** Flyway runs migrations
**Then** migration V2__create_users.sql creates `users` table with columns:
- `id` (UUID, primary key)
- `username` (VARCHAR, unique, not null)
- `email` (VARCHAR, unique, not null)
- `passwordHash` (VARCHAR, not null)
- `role` (VARCHAR: developer/security/admin)
- `isActive` (BOOLEAN, default true)
- `createdAt` (TIMESTAMP)
- `updatedAt` (TIMESTAMP)
**And** a seed admin user is created:
- username: `admin`, role: `admin`, password: from environment variable

---

### Story 2.2: JWT Authentication Service

As a **User**,
I want to login with username and password,
So that I can access the Admin UI securely (FR25).

**Acceptance Criteria:**

**Given** a user exists with username "maria" and valid password
**When** POST `/api/v1/auth/login` with body `{"username": "maria", "password": "correct"}`
**Then** response returns HTTP 200
**And** JWT token is set in HTTP-only cookie named `auth_token`
**And** cookie has attributes: HttpOnly, Secure (in prod), SameSite=Strict, Path=/
**And** JWT payload contains: `sub` (user_id), `username`, `role`, `exp` (24h)
**And** response body contains: `{"userId": "...", "username": "maria", "role": "developer"}`

**Given** invalid credentials
**When** POST `/api/v1/auth/login` with wrong password
**Then** response returns HTTP 401 Unauthorized
**And** response follows RFC 7807 format with detail "Invalid credentials"
**And** no cookie is set

**Given** a logged-in user
**When** POST `/api/v1/auth/logout`
**Then** auth_token cookie is cleared (MaxAge=0)
**And** response returns HTTP 200

---

### Story 2.3: Authentication Middleware

As a **Developer**,
I want all Admin API endpoints protected by JWT authentication,
So that only authenticated users can access them (NFR13).

**Acceptance Criteria:**

**Given** a request to protected endpoint without auth_token cookie
**When** the request is processed
**Then** response returns HTTP 401 Unauthorized
**And** response body follows RFC 7807 format

**Given** a request with valid JWT in auth_token cookie
**When** the request is processed
**Then** the request proceeds to the controller
**And** user info is available in SecurityContext

**Given** a request with expired JWT
**When** the request is processed
**Then** response returns HTTP 401 with detail "Token expired"

**Given** a request with tampered/invalid JWT
**When** the request is processed
**Then** response returns HTTP 401 with detail "Invalid token"

**Given** endpoints `/api/v1/auth/login` and `/actuator/health`
**When** accessed without authentication
**Then** requests are allowed (public endpoints)

---

### Story 2.4: Role-Based Access Control

As a **System**,
I want to restrict actions based on user role,
So that users can only perform authorized operations (FR27).

**Acceptance Criteria:**

**Given** a user with role "developer"
**When** accessing an endpoint annotated with `@RequireRole(ADMIN)`
**Then** response returns HTTP 403 Forbidden
**And** response body contains detail "Insufficient permissions"

**Given** a user with role "admin"
**When** accessing an endpoint annotated with `@RequireRole(ADMIN)`
**Then** request proceeds successfully

**Given** role hierarchy: Admin > Security > Developer
**When** endpoint requires "security" role
**Then** users with "admin" or "security" roles can access
**And** users with "developer" role receive 403

**Given** the following role-to-permission mapping:
| Role | Permissions |
|------|-------------|
| developer | routes:create, routes:read, routes:update (own), routes:delete (own draft) |
| security | developer + routes:approve, routes:reject, audit:read |
| admin | security + users:manage, ratelimits:manage |
**When** permissions are checked
**Then** the system enforces the mapping correctly

---

### Story 2.5: Admin UI Login Page

As a **User**,
I want a login page in the Admin UI,
So that I can authenticate and access the dashboard.

**Acceptance Criteria:**

**Given** the Admin UI is loaded at `/login`
**When** the page renders
**Then** a login form is displayed with:
- Username input field
- Password input field
- "Login" button
- Error message area (hidden by default)
**And** the form follows Ant Design styling

**Given** valid credentials entered
**When** the login button is clicked
**Then** a loading spinner appears on the button
**And** on success, user is redirected to `/dashboard`
**And** AuthContext stores user info (userId, username, role)

**Given** invalid credentials entered
**When** the login button is clicked
**Then** error message "Invalid username or password" is displayed
**And** form fields are not cleared
**And** password field is focused

**Given** user is not authenticated
**When** navigating to any protected route (e.g., `/routes`)
**Then** user is redirected to `/login`
**And** after successful login, user is redirected to originally requested route

---

### Story 2.6: User Management for Admin

As an **Admin**,
I want to manage users and their roles,
So that I can control who has access to the system (FR26).

**Acceptance Criteria:**

**Given** an authenticated admin user
**When** GET `/api/v1/users`
**Then** response returns paginated list of all users
**And** each user includes: id, username, email, role, isActive, createdAt
**And** passwordHash is never included in response

**Given** an authenticated admin user
**When** POST `/api/v1/users` with valid user data
**Then** new user is created with hashed password (BCrypt)
**And** response returns HTTP 201 with created user (without passwordHash)

**Given** an authenticated admin user
**When** PUT `/api/v1/users/{id}` with role change
**Then** user role is updated
**And** audit log entry is created

**Given** an authenticated admin user
**When** accessing Users page in Admin UI
**Then** a table displays all users with columns: Username, Email, Role, Status, Actions
**And** "Add User" button opens a modal form
**And** each row has Edit and Deactivate actions

**Given** a non-admin user (developer or security)
**When** attempting to access `/api/v1/users` endpoints
**Then** response returns HTTP 403 Forbidden

---

## Epic 3: Route Management (Self-Service)

Developer может создавать, редактировать, удалять и просматривать маршруты. Мария за 2 минуты настраивает новый endpoint без участия DevOps.

### Story 3.1: Route CRUD API

As a **Developer**,
I want to create, update, and delete routes via API,
So that I can manage API routing configurations (FR1, FR2, FR3).

**Acceptance Criteria:**

**Given** an authenticated developer
**When** POST `/api/v1/routes` with valid data:
```json
{
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "description": "Order service endpoints"
}
```
**Then** response returns HTTP 201 Created
**And** route is created with `status: draft` and `createdBy: current_user_id`
**And** response includes generated `id` and `createdAt`

**Given** a route in draft status owned by current user
**When** PUT `/api/v1/routes/{id}` with updated data
**Then** route is updated
**And** `updatedAt` is set to current timestamp
**And** response returns HTTP 200 with updated route

**Given** a route in `published` or `pending` status
**When** PUT `/api/v1/routes/{id}` is attempted
**Then** response returns HTTP 409 Conflict
**And** detail: "Cannot edit route in current status"

**Given** a route in draft status owned by current user
**When** DELETE `/api/v1/routes/{id}`
**Then** route is deleted
**And** response returns HTTP 204 No Content

**Given** a route not in draft status
**When** DELETE `/api/v1/routes/{id}` is attempted
**Then** response returns HTTP 409 Conflict
**And** detail: "Only draft routes can be deleted"

**Given** invalid route data (path not starting with /, invalid URL)
**When** POST or PUT is attempted
**Then** response returns HTTP 400 Bad Request
**And** validation errors are listed in RFC 7807 format

---

### Story 3.2: Route List API with Filtering & Search

As a **Developer**,
I want to list and search routes with filters,
So that I can find specific routes quickly (FR4).

**Acceptance Criteria:**

**Given** an authenticated user
**When** GET `/api/v1/routes`
**Then** response returns paginated list:
```json
{
  "items": [...],
  "total": 156,
  "offset": 0,
  "limit": 20
}
```
**And** default sort is by `createdAt` descending

**Given** query parameter `status=draft`
**When** GET `/api/v1/routes?status=draft`
**Then** only routes with draft status are returned

**Given** query parameter `createdBy=me`
**When** GET `/api/v1/routes?createdBy=me`
**Then** only routes created by current user are returned

**Given** query parameter `search=order`
**When** GET `/api/v1/routes?search=order`
**Then** routes with "order" in path or description are returned (case-insensitive)

**Given** query parameters `offset=20&limit=10`
**When** GET `/api/v1/routes?offset=20&limit=10`
**Then** routes 21-30 are returned
**And** `total` reflects the full count matching filters

**Given** multiple filters combined
**When** GET `/api/v1/routes?status=published&search=api`
**Then** filters are applied with AND logic

---

### Story 3.3: Route Details & Clone API

As a **Developer**,
I want to view route details and clone existing routes,
So that I can reuse configurations efficiently (FR5, FR6).

**Acceptance Criteria:**

**Given** a route exists with id `abc-123`
**When** GET `/api/v1/routes/abc-123`
**Then** response returns HTTP 200 with full route details:
- id, path, upstreamUrl, methods, description
- status, createdBy, createdAt, updatedAt
- rateLimitId (if assigned)
- Full creator user info (username, not just id)

**Given** a route does not exist
**When** GET `/api/v1/routes/nonexistent`
**Then** response returns HTTP 404 Not Found

**Given** an existing route with id `abc-123`
**When** POST `/api/v1/routes/abc-123/clone`
**Then** a new route is created with:
- Same path (with "-copy" suffix if conflict)
- Same upstreamUrl, methods, description
- Status: draft
- CreatedBy: current user
**And** response returns HTTP 201 with the cloned route

**Given** a route with path `/api/orders` exists
**When** cloning and `/api/orders-copy` also exists
**Then** cloned route gets path `/api/orders-copy-2`

---

### Story 3.4: Routes List UI

As a **Developer**,
I want a routes list page with filtering and search,
So that I can efficiently manage all routes.

**Acceptance Criteria:**

**Given** user navigates to `/routes`
**When** the page loads
**Then** a ProTable displays routes with columns:
- Path (clickable, links to details)
- Upstream URL
- Methods (tags: GET, POST, etc.)
- Status (badge: Draft gray, Pending yellow, Published green, Rejected red)
- Author (username)
- Created (relative time, e.g., "2 hours ago")
- Actions (Edit, Delete for drafts; View for others)

**Given** the routes list is displayed
**When** user types in search box
**Then** list filters in real-time (debounced 300ms)
**And** search term is highlighted in results

**Given** the routes list is displayed
**When** user selects status filter dropdown
**Then** list updates to show only matching routes
**And** active filter is shown as a chip that can be removed

**Given** user clicks "New Route" button
**When** the action is triggered
**Then** user is navigated to route creation form
**And** keyboard shortcut ⌘+N also triggers this action

**Given** pagination controls
**When** user changes page or page size
**Then** list updates without full page reload
**And** URL query params are updated for bookmarking

---

### Story 3.5: Route Create/Edit Form UI

As a **Developer**,
I want an intuitive form to create and edit routes,
So that I can configure routes quickly with validation feedback.

**Acceptance Criteria:**

**Given** user is on route create page
**When** the form renders
**Then** the following fields are displayed:
- Path (required, text input with "/" prefix shown)
- Upstream URL (required, text input with URL validation)
- Methods (required, multi-select: GET, POST, PUT, DELETE, PATCH)
- Description (optional, textarea)
**And** "Save as Draft" button is primary action
**And** "Cancel" returns to routes list

**Given** user types in Path field
**When** path already exists in database
**Then** inline error shows "Path already exists"
**And** validation is debounced (500ms)

**Given** user types invalid upstream URL
**When** URL format is incorrect
**Then** inline error shows "Invalid URL format"

**Given** all required fields are valid
**When** user clicks "Save as Draft"
**Then** button shows loading spinner
**And** on success, toast notification "Route created"
**And** user is redirected to route details page

**Given** user is editing an existing draft route
**When** form loads
**Then** all fields are pre-populated with current values
**And** page title shows "Edit Route"

**Given** user presses ⌘+Enter (or Ctrl+Enter)
**When** form is valid
**Then** form is submitted (keyboard shortcut for save)

---

### Story 3.6: Route Details View & Clone UI

As a **Developer**,
I want to view route details and clone routes from UI,
So that I can inspect configurations and reuse them.

**Acceptance Criteria:**

**Given** user navigates to `/routes/{id}`
**When** the page loads
**Then** route details are displayed in a card layout:
- Header: Path as title, Status badge
- Sections: Configuration (upstream, methods), Metadata (author, dates)
- Actions: Edit (if draft), Clone, Back to list

**Given** route is in draft status and user is owner
**When** user clicks "Edit"
**Then** user is navigated to edit form

**Given** route is not in draft status
**When** page loads
**Then** "Edit" button is not displayed

**Given** user clicks "Clone" button
**When** action completes
**Then** toast notification "Route cloned successfully"
**And** user is navigated to the new cloned route's edit page

**Given** route has a rate limit policy assigned
**When** details page loads
**Then** rate limit information is displayed (name, limits)

**Given** route does not exist
**When** user navigates to `/routes/nonexistent`
**Then** 404 page is displayed with "Route not found"
**And** link to return to routes list

---

## Epic 4: Approval Workflow

Security контролирует публикацию маршрутов (approve/reject). Developer видит статус своих маршрутов. Автоматическая публикация после одобрения. State machine: Draft → Pending → Published/Rejected.

### Story 4.1: Submit for Approval API

As a **Developer**,
I want to submit my draft route for security approval,
So that it can be reviewed and published (FR7).

**Acceptance Criteria:**

**Given** a route in `draft` status owned by current user
**When** POST `/api/v1/routes/{id}/submit`
**Then** route status changes to `pending`
**And** `submittedAt` timestamp is recorded
**And** response returns HTTP 200 with updated route
**And** audit log entry is created: "route.submitted"

**Given** a route not in `draft` status
**When** POST `/api/v1/routes/{id}/submit`
**Then** response returns HTTP 409 Conflict
**And** detail: "Only draft routes can be submitted for approval"

**Given** a route owned by different user
**When** POST `/api/v1/routes/{id}/submit`
**Then** response returns HTTP 403 Forbidden
**And** detail: "You can only submit your own routes"

**Given** a draft route with validation errors (e.g., invalid upstream URL)
**When** POST `/api/v1/routes/{id}/submit`
**Then** response returns HTTP 400 Bad Request
**And** validation errors are listed

---

### Story 4.2: Approval & Rejection API

As a **Security Specialist**,
I want to approve or reject pending routes,
So that I control what gets published to production (FR9, FR10, FR12).

**Acceptance Criteria:**

**Given** an authenticated user with security or admin role
**And** a route in `pending` status
**When** POST `/api/v1/routes/{id}/approve`
**Then** route status changes to `published`
**And** `approvedBy` is set to current user id
**And** `approvedAt` timestamp is recorded
**And** cache invalidation event is published to Redis
**And** response returns HTTP 200 with updated route
**And** audit log entry: "route.approved"

**Given** a route is approved
**When** gateway-core receives cache invalidation
**Then** route becomes active within 5 seconds (FR30, NFR3)
**And** requests to the route path are proxied to upstream

**Given** an authenticated user with security or admin role
**And** a route in `pending` status
**When** POST `/api/v1/routes/{id}/reject` with body:
```json
{
  "reason": "Upstream URL points to internal service not allowed for external access"
}
```
**Then** route status changes to `rejected`
**And** `rejectionReason` is stored
**And** `rejectedBy` and `rejectedAt` are recorded
**And** response returns HTTP 200 with updated route
**And** audit log entry: "route.rejected"

**Given** rejection without reason
**When** POST `/api/v1/routes/{id}/reject` with empty reason
**Then** response returns HTTP 400 Bad Request
**And** detail: "Rejection reason is required"

**Given** a user with developer role (not security/admin)
**When** attempting to approve or reject
**Then** response returns HTTP 403 Forbidden

**Given** a route not in `pending` status
**When** approve or reject is attempted
**Then** response returns HTTP 409 Conflict
**And** detail: "Only pending routes can be approved/rejected"

---

### Story 4.3: Pending Approvals List API

As a **Security Specialist**,
I want to see all routes waiting for my approval,
So that I can process them efficiently (FR8).

**Acceptance Criteria:**

**Given** an authenticated user with security or admin role
**When** GET `/api/v1/routes/pending`
**Then** response returns list of all routes with `status: pending`
**And** sorted by `submittedAt` ascending (oldest first)
**And** each route includes: id, path, upstreamUrl, methods, submittedAt, createdBy (with username)

**Given** query parameter `sort=submittedAt:desc`
**When** GET `/api/v1/routes/pending?sort=submittedAt:desc`
**Then** routes are sorted by newest first

**Given** no pending routes exist
**When** GET `/api/v1/routes/pending`
**Then** response returns empty list with `total: 0`

**Given** an authenticated user with developer role
**When** GET `/api/v1/routes/pending`
**Then** response returns HTTP 403 Forbidden

---

### Story 4.4: Route Status Tracking

As a **Developer**,
I want to see the full status history of my routes,
So that I understand the approval process and any rejection reasons (FR11).

**Acceptance Criteria:**

**Given** routes table schema
**When** migration V3__add_approval_fields.sql runs
**Then** the following columns are added to `routes`:
- `submittedAt` (TIMESTAMP, nullable)
- `approvedBy` (UUID, nullable, FK to users)
- `approvedAt` (TIMESTAMP, nullable)
- `rejectedBy` (UUID, nullable, FK to users)
- `rejectedAt` (TIMESTAMP, nullable)
- `rejectionReason` (TEXT, nullable)

**Given** a route was rejected
**When** GET `/api/v1/routes/{id}`
**Then** response includes `rejectionReason`, `rejectedBy` (username), `rejectedAt`

**Given** a route was approved
**When** GET `/api/v1/routes/{id}`
**Then** response includes `approvedBy` (username), `approvedAt`

**Given** a rejected route
**When** developer edits and resubmits
**Then** route status changes back to `pending`
**And** previous rejection info is cleared
**And** audit log tracks the resubmission

**Given** routes list API
**When** fetching routes with `?createdBy=me`
**Then** developer sees their routes with current status
**And** status badge shows: Draft, Pending, Published, or Rejected

---

### Story 4.5: Submit for Approval UI

As a **Developer**,
I want to submit my draft routes for approval from the UI,
So that I can initiate the review process easily.

**Acceptance Criteria:**

**Given** user is viewing a draft route they own
**When** the page loads
**Then** "Submit for Approval" button is displayed prominently

**Given** user clicks "Submit for Approval"
**When** the action is triggered
**Then** confirmation modal appears:
- Title: "Submit for Approval"
- Message: "This route will be sent to Security for review. You won't be able to edit it until approved or rejected."
- Buttons: "Cancel", "Submit"

**Given** user confirms submission
**When** API call succeeds
**Then** modal closes
**And** toast notification: "Route submitted for approval"
**And** page refreshes to show updated status (Pending badge)
**And** "Submit for Approval" button is replaced with status indicator

**Given** user views their route in `pending` status
**When** page loads
**Then** message displays: "Awaiting Security approval"
**And** no edit actions are available

**Given** user views their route in `rejected` status
**When** page loads
**Then** rejection reason is displayed prominently
**And** "Edit & Resubmit" button is available

---

### Story 4.6: Pending Approvals UI with Inline Actions

As a **Security Specialist**,
I want to review and approve/reject routes efficiently,
So that I can process multiple requests quickly.

**Acceptance Criteria:**

**Given** user with security/admin role navigates to `/approvals`
**When** the page loads
**Then** a table displays pending routes with columns:
- Path
- Upstream URL
- Methods
- Submitted By (username)
- Submitted At (relative time)
- Actions (Approve, Reject buttons)
**And** badge in sidebar shows count of pending approvals

**Given** user clicks "Approve" button on a route row
**When** action is triggered
**Then** route is approved immediately (no confirmation for approve)
**And** row is removed from table with fade animation
**And** toast notification: "Route approved and published"
**And** pending count in sidebar decreases

**Given** user clicks "Reject" button on a route row
**When** action is triggered
**Then** modal appears with:
- Route path displayed
- Textarea for rejection reason (required)
- "Cancel" and "Reject" buttons

**Given** user enters rejection reason and confirms
**When** "Reject" button is clicked
**Then** route is rejected
**And** row is removed from table
**And** toast notification: "Route rejected"

**Given** empty rejection reason
**When** user clicks "Reject" in modal
**Then** validation error: "Please provide a reason for rejection"
**And** modal stays open

**Given** user clicks on route path in the table
**When** action is triggered
**Then** route details slide-over panel opens
**And** full route configuration is visible for review
**And** Approve/Reject buttons are available in panel

**Given** keyboard navigation
**When** user presses 'A' with row focused
**Then** route is approved (keyboard shortcut)
**When** user presses 'R' with row focused
**Then** rejection modal opens

---

## Epic 5: Rate Limiting

Admin создаёт политики лимитов, Developer назначает их на маршруты. Gateway защищает upstream от перегрузки. Redis-based token bucket algorithm.

### Story 5.1: Rate Limit Policy Entity & CRUD API

As an **Admin**,
I want to create and manage rate limiting policies,
So that I can define reusable rate limit configurations (FR13, FR14).

**Acceptance Criteria:**

**Given** gateway-admin application starts
**When** Flyway runs migrations
**Then** migration V4__create_rate_limits.sql creates `rate_limits` table:
- `id` (UUID, primary key)
- `name` (VARCHAR, unique, not null)
- `description` (TEXT, nullable)
- `requestsPerSecond` (INTEGER, not null)
- `burstSize` (INTEGER, not null)
- `createdBy` (UUID, FK to users)
- `createdAt` (TIMESTAMP)
- `updatedAt` (TIMESTAMP)

**Given** an authenticated admin user
**When** POST `/api/v1/rate-limits` with:
```json
{
  "name": "standard",
  "description": "Standard rate limit for most services",
  "requestsPerSecond": 100,
  "burstSize": 150
}
```
**Then** policy is created
**And** response returns HTTP 201 with created policy

**Given** an authenticated admin user
**When** PUT `/api/v1/rate-limits/{id}` with updated values
**Then** policy is updated
**And** cache invalidation is triggered for routes using this policy

**Given** an authenticated admin user
**When** DELETE `/api/v1/rate-limits/{id}`
**And** policy is not used by any routes
**Then** policy is deleted
**And** response returns HTTP 204

**Given** a policy used by existing routes
**When** DELETE is attempted
**Then** response returns HTTP 409 Conflict
**And** detail: "Policy is in use by N routes"

**Given** a non-admin user
**When** attempting CRUD operations on rate-limits
**Then** response returns HTTP 403 Forbidden

**Given** an authenticated user
**When** GET `/api/v1/rate-limits`
**Then** response returns list of all policies
**And** includes `usageCount` (number of routes using each policy)

---

### Story 5.2: Assign Rate Limit to Route API

As a **Developer**,
I want to assign a rate limit policy to my route,
So that my service is protected from excessive traffic (FR15).

**Acceptance Criteria:**

**Given** migration V5__add_rate_limit_to_routes.sql
**When** executed
**Then** column `rateLimitId` (UUID, nullable, FK to rate_limits) is added to `routes` table

**Given** an authenticated developer with a draft route
**When** PUT `/api/v1/routes/{id}` with:
```json
{
  "rateLimitId": "policy-uuid-here"
}
```
**Then** route is updated with the rate limit policy
**And** response includes full rate limit details

**Given** a non-existent rateLimitId
**When** PUT `/api/v1/routes/{id}` is attempted
**Then** response returns HTTP 400 Bad Request
**And** detail: "Rate limit policy not found"

**Given** a published route with rate limit assigned
**When** the policy is updated (e.g., limits increased)
**Then** gateway-core receives cache invalidation
**And** new limits are applied within 5 seconds

**Given** a route with rate limit assigned
**When** GET `/api/v1/routes/{id}`
**Then** response includes full `rateLimit` object:
```json
{
  "rateLimit": {
    "id": "...",
    "name": "standard",
    "requestsPerSecond": 100,
    "burstSize": 150
  }
}
```

**Given** removing rate limit from route
**When** PUT `/api/v1/routes/{id}` with `"rateLimitId": null`
**Then** rate limit is removed from route
**And** route will have no rate limiting applied

---

### Story 5.3: Rate Limiting Filter Implementation

As a **Gateway System**,
I want to enforce rate limits on incoming requests,
So that upstream services are protected from overload (FR16).

**Acceptance Criteria:**

**Given** a published route with rate limit: 10 req/s, burst 15
**When** client sends 10 requests within 1 second
**Then** all 10 requests are proxied to upstream
**And** Redis counter is incremented for each request

**Given** a published route with rate limit: 10 req/s, burst 15
**When** client sends 20 requests within 1 second
**Then** first 15 requests succeed (burst allowance)
**And** remaining 5 requests receive HTTP 429 Too Many Requests
**And** response includes headers:
- `X-RateLimit-Limit: 10`
- `X-RateLimit-Remaining: 0`
- `X-RateLimit-Reset: <unix-timestamp>`
- `Retry-After: <seconds>`

**Given** rate limit with token bucket algorithm
**When** tokens replenish over time
**Then** replenishment rate equals `requestsPerSecond`
**And** maximum tokens equals `burstSize`

**Given** Redis is unavailable
**When** rate limit check is attempted
**Then** request is allowed (graceful degradation - NFR7)
**And** warning is logged: "Rate limiting disabled: Redis unavailable"
**And** Caffeine local cache is used as fallback with conservative limits

**Given** a published route without rate limit assigned
**When** requests are made
**Then** no rate limiting is applied
**And** requests pass through without rate limit headers

**Given** multiple gateway instances
**When** rate limiting the same route
**Then** limits are shared via Redis (distributed rate limiting)
**And** total rate across all instances respects the policy

---

### Story 5.4: Rate Limit Policies Management UI

As an **Admin**,
I want to manage rate limit policies through the UI,
So that I can configure protection levels easily.

**Acceptance Criteria:**

**Given** admin user navigates to `/rate-limits`
**When** the page loads
**Then** a table displays all policies with columns:
- Name
- Description
- Requests/sec
- Burst Size
- Used By (count of routes)
- Actions (Edit, Delete)

**Given** admin clicks "New Policy" button
**When** modal opens
**Then** form displays fields:
- Name (required, unique)
- Description (optional)
- Requests per Second (required, number, min 1)
- Burst Size (required, number, >= requests/sec)
**And** validation prevents burst < requests/sec

**Given** admin fills valid policy data and submits
**When** creation succeeds
**Then** modal closes
**And** toast: "Policy created"
**And** table refreshes with new policy

**Given** admin clicks "Edit" on a policy
**When** modal opens
**Then** form is pre-populated with current values
**And** "Save" updates the policy

**Given** admin clicks "Delete" on a policy with 0 usage
**When** confirmation is accepted
**Then** policy is deleted
**And** toast: "Policy deleted"

**Given** admin clicks "Delete" on a policy in use
**When** action is attempted
**Then** error toast: "Cannot delete: policy is used by N routes"
**And** policy is not deleted

**Given** admin clicks on "Used By" count
**When** count > 0
**Then** modal or panel shows list of routes using this policy

---

### Story 5.5: Assign Rate Limit to Route UI

As a **Developer**,
I want to select a rate limit policy when creating/editing routes,
So that I can protect my service endpoints.

**Acceptance Criteria:**

**Given** user is on route create/edit form
**When** form renders
**Then** "Rate Limit Policy" dropdown field is displayed
**And** options include: "None" + all available policies
**And** each option shows: name (requests/sec)

**Given** user selects a rate limit policy
**When** route is saved
**Then** route is associated with selected policy

**Given** user selects "None"
**When** route is saved
**Then** route has no rate limit applied

**Given** user is viewing route details
**When** route has rate limit assigned
**Then** rate limit section displays:
- Policy name
- Requests per second
- Burst size
**And** styled as info card

**Given** user is viewing route details
**When** route has no rate limit
**Then** message displays: "No rate limiting configured"
**And** suggestion: "Consider adding rate limiting for production routes"

**Given** routes list table
**When** displayed
**Then** optional column "Rate Limit" shows policy name or "-"
**And** column is hideable via column settings

---

### Story 5.6: E2E Playwright Happy Path Tests

As a **QA Engineer**,
I want E2E tests covering the Rate Limiting happy path,
So that critical user flows are verified in a real browser environment.

**Acceptance Criteria:**

**Given** Playwright test suite is configured
**When** E2E tests for Epic 5 are executed
**Then** the following scenarios pass:

**Scenario 1 — Admin создаёт политику rate limit:**
- Admin логинится
- Переходит на /rate-limits
- Нажимает "New Policy"
- Заполняет форму (name, requestsPerSecond, burstSize)
- Сохраняет → видит политику в таблице

**Scenario 2 — Developer назначает политику на маршрут:**
- Developer логинится
- Создаёт новый маршрут
- Выбирает Rate Limit Policy в dropdown
- Сохраняет маршрут
- Переходит на детали маршрута → видит rate limit info

**Scenario 3 — Rate limiting применяется в Gateway:**
- Published маршрут с rate limit существует
- Отправляются запросы через Gateway
- При превышении лимита возвращается HTTP 429
- Заголовки X-RateLimit-* присутствуют

**Scenario 4 — Admin редактирует/удаляет политику:**
- Admin редактирует существующую политику
- Изменения сохраняются
- Admin пытается удалить используемую политику → ошибка
- Admin удаляет неиспользуемую политику → успех

---

## Epic 6: Monitoring & Observability

DevOps видит real-time метрики (RPS, latency, errors), может экспортировать в Prometheus, проверять health. Алексей за 5 минут находит проблему при инциденте.

### Story 6.1: Metrics Collection with Micrometer

As a **DevOps Engineer**,
I want gateway metrics collected and exposed in Prometheus format,
So that I can monitor system health in real-time (FR17, FR19).

**Acceptance Criteria:**

**Given** gateway-core is running
**When** requests pass through the gateway
**Then** the following metrics are collected via Micrometer:
- `gateway_requests_total` (counter) — total requests
- `gateway_request_duration_seconds` (histogram) — request latency
- `gateway_errors_total` (counter) — error count by type
- `gateway_active_connections` (gauge) — current connections

**Given** gateway-core is running
**When** GET `/actuator/prometheus`
**Then** response returns metrics in Prometheus text format
**And** response content-type is `text/plain; version=0.0.4`

**Given** metrics are being collected
**When** inspecting `gateway_request_duration_seconds`
**Then** histogram buckets are configured: 0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0 seconds
**And** quantiles P50, P95, P99 can be calculated

**Given** an error occurs (upstream failure, rate limit exceeded)
**When** error is recorded
**Then** `gateway_errors_total` is incremented
**And** label `error_type` distinguishes: upstream_error, rate_limited, not_found, internal_error

**Given** Spring Boot Actuator is configured
**When** application starts
**Then** `/actuator/prometheus` endpoint is enabled
**And** endpoint is accessible without authentication (for Prometheus scraping)
**And** other actuator endpoints require authentication

---

### Story 6.2: Per-Route Metrics

As a **DevOps Engineer**,
I want metrics broken down by route,
So that I can identify which specific routes have issues (FR18).

**Acceptance Criteria:**

**Given** requests are processed through the gateway
**When** metrics are recorded
**Then** each metric includes tags/labels:
- `route_id` — UUID of the route
- `route_path` — path pattern (e.g., `/api/orders`)
- `upstream_host` — upstream service hostname
- `method` — HTTP method (GET, POST, etc.)
- `status` — response status code category (2xx, 4xx, 5xx)

**Given** Prometheus is scraping metrics
**When** querying for specific route performance
**Then** query like `gateway_request_duration_seconds{route_path="/api/orders"}` works
**And** returns metrics only for that route

**Given** high cardinality concern
**When** route_path contains path parameters (e.g., `/api/orders/123`)
**Then** path is normalized to pattern (e.g., `/api/orders/{id}`)
**And** cardinality is controlled

**Given** multiple upstream services
**When** analyzing metrics by upstream
**Then** query `sum(rate(gateway_requests_total[5m])) by (upstream_host)` shows distribution

---

### Story 6.3: Metrics Summary API

As a **DevOps Engineer**,
I want an API endpoint for aggregated metrics,
So that I can display key metrics in the Admin UI (FR17).

**Acceptance Criteria:**

**Given** an authenticated user
**When** GET `/api/v1/metrics/summary`
**Then** response returns aggregated metrics:
```json
{
  "period": "5m",
  "totalRequests": 12500,
  "requestsPerSecond": 41.7,
  "avgLatencyMs": 45,
  "p95LatencyMs": 120,
  "p99LatencyMs": 250,
  "errorRate": 0.02,
  "errorCount": 250,
  "activeRoutes": 45
}
```

**Given** query parameter `period=1h`
**When** GET `/api/v1/metrics/summary?period=1h`
**Then** metrics are aggregated over last hour
**And** valid periods: 5m, 15m, 1h, 6h, 24h

**Given** an authenticated user
**When** GET `/api/v1/metrics/routes/{routeId}`
**Then** response returns metrics for specific route:
```json
{
  "routeId": "...",
  "path": "/api/orders",
  "period": "5m",
  "requestsPerSecond": 5.2,
  "avgLatencyMs": 35,
  "p95LatencyMs": 80,
  "errorRate": 0.01,
  "statusBreakdown": {
    "2xx": 1500,
    "4xx": 10,
    "5xx": 5
  }
}
```

**Given** an authenticated user
**When** GET `/api/v1/metrics/top-routes?by=requests&limit=10`
**Then** response returns top 10 routes by request count
**And** supported sorting: requests, latency, errors

---

### Story 6.4: Prometheus & Grafana Setup

As a **DevOps Engineer**,
I want pre-configured Prometheus and Grafana,
So that I have production-ready monitoring out of the box (NFR21).

**Acceptance Criteria:**

**Given** Docker Compose environment
**When** `docker-compose --profile monitoring up -d`
**Then** the following services start:
- Prometheus on port 9090
- Grafana on port 3000
**And** Prometheus is configured to scrape gateway metrics

**Given** `docker/prometheus/prometheus.yml` exists
**When** Prometheus starts
**Then** scrape config targets gateway-core at `/actuator/prometheus`
**And** scrape interval is 15 seconds

**Given** Grafana starts
**When** accessing http://localhost:3000
**Then** Prometheus datasource is pre-configured
**And** default credentials are admin/admin (prompt to change)

**Given** `docker/grafana/dashboards/gateway-dashboard.json` exists
**When** Grafana starts
**Then** "API Gateway" dashboard is auto-provisioned
**And** dashboard includes panels:
- Requests per second (graph)
- Latency percentiles (graph)
- Error rate (graph)
- Top routes by traffic (table)
- Active connections (gauge)
- Status code distribution (pie chart)

**Given** Grafana alerting
**When** configured
**Then** alert rules are provisioned:
- High error rate: > 5% errors for 5 minutes
- High latency: P95 > 500ms for 5 minutes
- Gateway down: no metrics for 1 minute

---

### Story 6.5: Basic Metrics View in Admin UI

As a **DevOps Engineer**,
I want to see key metrics in the Admin UI dashboard,
So that I have quick visibility without opening Grafana.

**Acceptance Criteria:**

**Given** user with devops or admin role navigates to `/dashboard`
**When** the page loads
**Then** metrics widget displays:
- Current RPS (large number)
- Avg Latency (with trend indicator ↑↓)
- Error Rate (with color coding: green <1%, yellow 1-5%, red >5%)
- Active Routes count

**Given** metrics widget is displayed
**When** data refreshes
**Then** values update every 10 seconds
**And** sparkline charts show last 30 minutes trend

**Given** user clicks on any metric
**When** action is triggered
**Then** user is navigated to detailed metrics page or Grafana link opens

**Given** metrics API is unavailable
**When** widget attempts to load
**Then** widget shows "Metrics unavailable" state
**And** retry button is displayed

**Given** detailed metrics page `/metrics`
**When** user navigates there
**Then** page displays:
- Summary metrics cards at top
- Top routes table with per-route metrics
- Time range selector (5m, 15m, 1h, 6h, 24h)
- "Open in Grafana" button linking to full dashboard

**Given** user is a developer (not devops/admin)
**When** accessing dashboard
**Then** basic metrics widget is visible (read-only)
**And** limited to routes they created

---

### Story 6.6: E2E Playwright Happy Path Tests

As a **QA Engineer**,
I want E2E tests covering the Monitoring & Observability happy path,
So that critical user flows are verified in a real browser environment.

**Acceptance Criteria:**

**Given** Playwright test suite is configured
**When** E2E tests for Epic 6 are executed
**Then** the following scenarios pass:

**Scenario 1 — Prometheus метрики доступны:**
- Gateway-core запущен
- GET /actuator/prometheus возвращает метрики в Prometheus формате
- Метрики включают gateway_requests_total, gateway_request_duration_seconds

**Scenario 2 — Per-route метрики работают:**
- Запрос через published маршрут
- Метрики содержат labels route_path, method, status
- Prometheus query по route_path возвращает данные

**Scenario 3 — Admin видит метрики в UI:**
- Admin/DevOps логинится
- Переходит на /dashboard
- Виджет метрик отображает RPS, Latency, Error Rate
- Данные обновляются автоматически

**Scenario 4 — Grafana dashboard работает:**
- docker-compose --profile monitoring up
- Grafana доступен на port 3000
- Dashboard "API Gateway" отображает графики
- Prometheus datasource подключен

---

## Epic 7: Audit & Compliance

Security может просматривать полный аудит-лог, фильтровать по пользователю/дате/действию, видеть историю изменений маршрутов. Дмитрий за 30 минут готовит отчёт по интеграциям.

### Story 7.1: Audit Log Entity & Event Recording

As a **System**,
I want all changes automatically recorded in an audit log,
So that we have a complete trail of who changed what and when (FR21, NFR15).

**Acceptance Criteria:**

**Given** gateway-admin application starts
**When** Flyway runs migrations
**Then** migration V6__create_audit_logs.sql creates `audit_logs` table:
- `id` (UUID, primary key)
- `entityType` (VARCHAR: route, rate_limit, user)
- `entityId` (UUID)
- `action` (VARCHAR: created, updated, deleted, submitted, approved, rejected, published)
- `userId` (UUID, FK to users)
- `timestamp` (TIMESTAMP with timezone)
- `changes` (JSONB — stores before/after values)
- `ipAddress` (VARCHAR, nullable)
- `correlationId` (VARCHAR)

**Given** a route is created
**When** RouteService.create() completes
**Then** audit log entry is created:
```json
{
  "entityType": "route",
  "entityId": "route-uuid",
  "action": "created",
  "userId": "user-uuid",
  "changes": {
    "after": { "path": "/api/orders", "upstreamUrl": "...", "status": "draft" }
  }
}
```

**Given** a route is updated
**When** RouteService.update() completes
**Then** audit log entry includes before and after:
```json
{
  "action": "updated",
  "changes": {
    "before": { "path": "/api/orders", "upstreamUrl": "http://old:8080" },
    "after": { "path": "/api/orders", "upstreamUrl": "http://new:8080" }
  }
}
```

**Given** the following actions occur
**When** recorded in audit log
**Then** action types are:
| Action | Trigger |
|--------|---------|
| route.created | New route created |
| route.updated | Route fields modified |
| route.deleted | Route deleted |
| route.submitted | Submitted for approval |
| route.approved | Approved by security |
| route.rejected | Rejected by security |
| ratelimit.created | Policy created |
| ratelimit.updated | Policy modified |
| ratelimit.deleted | Policy deleted |
| user.created | User account created |
| user.updated | User role/status changed |

**Given** audit logging is implemented
**When** any audited operation fails
**Then** audit log is still written (with error indicator if applicable)
**And** audit logging never blocks the main operation

---

### Story 7.2: Audit Log API with Filtering

As a **Security Specialist**,
I want to query and filter audit logs,
So that I can investigate changes and generate reports (FR22).

**Acceptance Criteria:**

**Given** an authenticated user with security or admin role
**When** GET `/api/v1/audit`
**Then** response returns paginated audit log entries:
```json
{
  "items": [
    {
      "id": "...",
      "entityType": "route",
      "entityId": "...",
      "action": "approved",
      "user": { "id": "...", "username": "dmitry" },
      "timestamp": "2026-02-11T14:30:00Z",
      "changes": { ... }
    }
  ],
  "total": 1250,
  "offset": 0,
  "limit": 50
}
```
**And** default sort is by timestamp descending (newest first)

**Given** query parameter `userId={uuid}`
**When** GET `/api/v1/audit?userId={uuid}`
**Then** only entries by that user are returned

**Given** query parameter `action=approved`
**When** GET `/api/v1/audit?action=approved`
**Then** only approval entries are returned

**Given** query parameter `entityType=route`
**When** GET `/api/v1/audit?entityType=route`
**Then** only route-related entries are returned

**Given** query parameters `dateFrom` and `dateTo`
**When** GET `/api/v1/audit?dateFrom=2026-02-01&dateTo=2026-02-11`
**Then** only entries within date range are returned
**And** dates are interpreted as start of day (UTC)

**Given** multiple filters combined
**When** GET `/api/v1/audit?entityType=route&action=rejected&userId={uuid}`
**Then** all filters are applied with AND logic

**Given** a user with developer role
**When** attempting to access `/api/v1/audit`
**Then** response returns HTTP 403 Forbidden

---

### Story 7.3: Route Change History API

As a **Security Specialist**,
I want to see the complete history of a specific route,
So that I can understand how it evolved over time (FR23).

**Acceptance Criteria:**

**Given** a route with id `abc-123`
**When** GET `/api/v1/routes/abc-123/history`
**Then** response returns chronological list of all changes:
```json
{
  "routeId": "abc-123",
  "currentPath": "/api/orders",
  "history": [
    {
      "timestamp": "2026-02-11T10:00:00Z",
      "action": "created",
      "user": { "username": "maria" },
      "changes": { "after": { ... } }
    },
    {
      "timestamp": "2026-02-11T10:05:00Z",
      "action": "updated",
      "user": { "username": "maria" },
      "changes": {
        "before": { "upstreamUrl": "http://v1:8080" },
        "after": { "upstreamUrl": "http://v2:8080" }
      }
    },
    {
      "timestamp": "2026-02-11T10:10:00Z",
      "action": "submitted",
      "user": { "username": "maria" }
    },
    {
      "timestamp": "2026-02-11T11:00:00Z",
      "action": "approved",
      "user": { "username": "dmitry" }
    }
  ]
}
```

**Given** route history is requested
**When** changes field exists
**Then** only changed fields are shown (not full entity)
**And** sensitive data is not exposed

**Given** a route does not exist
**When** GET `/api/v1/routes/nonexistent/history`
**Then** response returns HTTP 404

**Given** query parameter `from` and `to`
**When** GET `/api/v1/routes/{id}/history?from=2026-02-01&to=2026-02-10`
**Then** only history within date range is returned

---

### Story 7.4: Routes by Upstream Filter

As a **Security Specialist**,
I want to find all routes pointing to a specific upstream service,
So that I can audit integrations and assess blast radius (FR24).

**Acceptance Criteria:**

**Given** multiple routes exist with different upstreams
**When** GET `/api/v1/routes?upstream=order-service`
**Then** only routes where upstreamUrl contains "order-service" are returned
**And** search is case-insensitive

**Given** query parameter `upstreamExact=http://order-service:8080`
**When** GET `/api/v1/routes?upstreamExact=http://order-service:8080`
**Then** only routes with exact upstream URL match are returned

**Given** an authenticated user
**When** GET `/api/v1/routes/upstreams`
**Then** response returns list of unique upstream hosts:
```json
{
  "upstreams": [
    { "host": "order-service:8080", "routeCount": 5 },
    { "host": "user-service:8080", "routeCount": 12 },
    { "host": "payment-service:8080", "routeCount": 3 }
  ]
}
```
**And** sorted by routeCount descending

**Given** security specialist needs integration report
**When** GET `/api/v1/routes?upstream=user-data-service`
**Then** response includes all routes (any status) accessing that service
**And** response includes creator info for each route

---

### Story 7.5: Audit Log UI

As a **Security Specialist**,
I want an audit log page in the Admin UI,
So that I can review and investigate changes visually.

**Acceptance Criteria:**

**Given** user with security/admin role navigates to `/audit`
**When** the page loads
**Then** a table displays audit log entries with columns:
- Timestamp (formatted: "Feb 11, 2026, 14:30")
- Action (badge with color coding)
- Entity Type
- Entity (link to entity if exists)
- User (username)
- Details (expandable)

**Given** filter panel is displayed
**When** user interacts with filters
**Then** available filters include:
- Date range picker (from/to)
- User dropdown (searchable)
- Entity type dropdown (route, rate_limit, user)
- Action dropdown (multi-select)
**And** filters are applied immediately
**And** active filters shown as removable chips

**Given** user clicks on a row
**When** row expands
**Then** full change details are shown:
- Before/After comparison (if applicable)
- Correlation ID
- IP Address

**Given** user clicks "Export" button
**When** action is triggered
**Then** CSV file is downloaded with current filtered results
**And** filename includes date range: `audit-log-2026-02-01-to-2026-02-11.csv`

**Given** large audit log (10000+ entries)
**When** page loads
**Then** pagination handles efficiently
**And** virtual scrolling for smooth performance

**Given** action badges
**When** displayed
**Then** color coding is:
- Created: blue
- Updated: yellow
- Deleted: red
- Approved: green
- Rejected: orange
- Submitted: purple

---

### Story 7.6: Route History & Upstream Report UI

As a **Security Specialist**,
I want to see route history timeline and upstream reports,
So that I can conduct thorough audits.

**Acceptance Criteria:**

**Given** user is viewing route details page
**When** "History" tab is selected
**Then** timeline view displays all changes:
- Vertical timeline with dots for each event
- Each event shows: action, user, timestamp
- Expandable to show change details
- Most recent at top

**Given** timeline entry for "updated" action
**When** expanded
**Then** diff view shows:
- Changed fields highlighted
- Before value (red/strikethrough)
- After value (green)

**Given** user navigates to `/audit/integrations`
**When** the page loads
**Then** upstream services report is displayed:
- Table of unique upstream hosts
- Route count per upstream
- Click to see all routes for that upstream

**Given** user clicks on an upstream host
**When** action is triggered
**Then** filtered routes list is shown
**And** includes routes of all statuses
**And** shows who created each route and when

**Given** security specialist needs to answer "who has access to user-data-service?"
**When** searching for upstream
**Then** results show:
- All routes to that service
- Creators of those routes
- Current status (some may be draft, some published)
- Quick view of route configuration

**Given** export functionality on integrations report
**When** "Export Report" is clicked
**Then** generates report with:
- Upstream service
- All routes accessing it
- Route owners
- Current status
- Last modified date

---

### Story 7.7: E2E Playwright Happy Path Tests

As a **QA Engineer**,
I want E2E tests covering the Audit & Compliance happy path,
So that critical user flows are verified in a real browser environment.

**Acceptance Criteria:**

**Given** Playwright test suite is configured
**When** E2E tests for Epic 7 are executed
**Then** the following scenarios pass:

**Scenario 1 — Audit log записывает события:**
- Developer создаёт маршрут → audit log содержит route.created
- Developer редактирует маршрут → audit log содержит route.updated с before/after
- Security approve/reject → audit log содержит соответствующее событие

**Scenario 2 — Security просматривает audit log UI:**
- Security логинится
- Переходит на /audit
- Таблица отображает события
- Фильтры по user, action, entityType работают
- Экспорт в CSV скачивает файл

**Scenario 3 — Route History отображается:**
- Security открывает детали маршрута
- Переключается на вкладку "History"
- Timeline показывает все изменения
- Раскрытие события показывает diff

**Scenario 4 — Upstream Report работает:**
- Security переходит на /audit/integrations
- Таблица upstream сервисов отображается
- Клик на upstream показывает все маршруты к нему
- Экспорт отчёта работает

**Scenario 5 — Developer не имеет доступа к audit:**
- Developer логинится
- Попытка доступа к /audit → 403 или редирект

---

## Epic 8: UX Improvements & Testing Tools

Улучшения пользовательского опыта на основе обратной связи и добавление инструментов для тестирования нагрузки.

### Story 8.1: Health Check на странице Metrics

As a **DevOps Engineer**,
I want to see health status of all system services on the Metrics page,
So that I can quickly assess system health in one place.

**Acceptance Criteria:**

**Given** user navigates to `/metrics`
**When** the page loads
**Then** a Health Check section displays status for:
- gateway-core (UP/DOWN)
- gateway-admin (UP/DOWN)
- PostgreSQL (UP/DOWN)
- Redis (UP/DOWN)
**And** each service shows colored indicator (green=UP, red=DOWN)
**And** last check timestamp is displayed

**Given** a service is unavailable
**When** health check runs
**Then** that service shows red DOWN status
**And** error details are shown on hover/expand

---

### Story 8.2: Убрать плашки мониторинга с Dashboard

As a **User**,
I want a cleaner Dashboard without monitoring widgets,
So that the dashboard focuses on role-specific actions.

**Acceptance Criteria:**

**Given** user navigates to `/dashboard`
**When** the page loads
**Then** MetricsWidget is NOT displayed
**And** dashboard shows only role-specific content (quick actions, recent items)

**Given** user wants to see metrics
**When** they need monitoring data
**Then** they navigate to dedicated `/metrics` page via sidebar

---

### Story 8.3: Поиск пользователей по username и email

As an **Admin**,
I want a single search field on Users page that filters by username and email,
So that I can quickly find users.

**Acceptance Criteria:**

**Given** admin navigates to `/users`
**When** the page loads
**Then** a search input field is displayed above the table

**Given** admin types "john" in search field
**When** input is debounced (300ms)
**Then** table shows users where username OR email contains "john"
**And** search is case-insensitive

**Given** admin clears search field
**When** field is empty
**Then** all users are displayed

---

### Story 8.4: Показать Author и Rate Limit число в Routes

As a **User**,
I want to see Author column and Rate Limit details in Routes table,
So that I can see who created routes and their limits at a glance.

**Acceptance Criteria:**

**Given** user navigates to `/routes`
**When** the table loads
**Then** "Author" column displays the username of route creator

**Given** route has Rate Limit assigned
**When** displayed in table
**Then** Rate Limit column shows: "{name} ({requestsPerSecond}/s)"
**Example:** "Standard (100/s)"

**Given** route has no Rate Limit
**When** displayed in table
**Then** Rate Limit column shows "-" or "None"

---

### Story 8.5: Поиск Routes по Path и Upstream URL

As a **User**,
I want search on Routes page to filter by both Path and Upstream URL,
So that I can find routes by any criteria.

**Acceptance Criteria:**

**Given** user is on `/routes` page
**When** user types "order" in search field
**Then** routes are filtered where Path OR Upstream URL contains "order"
**And** search is case-insensitive

**Given** backend API `/api/v1/routes`
**When** `search` parameter is provided
**Then** API filters by path OR upstream_url (ILIKE)

---

### Story 8.6: Исправить комбобокс пользователей в Audit Logs

As a **Security Specialist**,
I want the user filter dropdown in Audit Logs to show all users,
So that I can filter audit events by user.

**Acceptance Criteria:**

**Given** user navigates to `/audit`
**When** the page loads
**Then** user filter dropdown is populated with all system users

**Given** user clicks on user dropdown
**When** dropdown opens
**Then** list shows all users (username)
**And** users are sorted alphabetically

**Given** a user is selected from dropdown
**When** filter is applied
**Then** audit log shows only events by that user

---

### Story 8.7: Расширить поиск Approvals на Upstream URL

As a **Security Specialist**,
I want search on Approvals page to include Upstream URL,
So that I can find pending routes by upstream service.

**Acceptance Criteria:**

**Given** user is on `/approvals` page
**When** user types "payment" in search field
**Then** pending routes are filtered where Path OR Upstream URL contains "payment"

---

### Story 8.8: Унифицированные фильтры для всех таблиц

As a **User**,
I want consistent filter UI across all tables,
So that the application has a unified user experience.

**Acceptance Criteria:**

**Given** any table page (Routes, Users, Rate Limits, Approvals, Audit)
**When** the page loads
**Then** filter UI follows consistent pattern:
- Search input at top-left
- Filter dropdowns in a row
- Active filters shown as removable chips
- Clear all filters button

**Given** Users table as reference
**When** comparing other tables
**Then** visual style and interaction patterns match

---

### Story 8.9: Страница Test с генератором нагрузки

As a **DevOps Engineer**,
I want a Test page with load generator,
So that I can simulate traffic and verify monitoring works.

**Acceptance Criteria:**

**Given** user navigates to `/test`
**When** the page loads
**Then** sidebar shows "Test" menu item (icon: experiment/flask)
**And** page displays load generator controls

**Given** load generator controls are displayed
**When** user sees the form
**Then** controls include:
- Target route selector (dropdown of published routes)
- Requests per second (number input, 1-100)
- Duration (seconds, or "until stopped")
- Start/Stop button

**Given** user clicks "Start"
**When** load generation begins
**Then** requests are sent to selected route through Gateway
**And** progress indicator shows: requests sent, success/error count
**And** Stop button becomes active

**Given** load is running
**When** user navigates to `/metrics`
**Then** metrics show the generated traffic (RPS increase)

**Given** user clicks "Stop"
**When** load generation stops
**Then** summary shows: total requests, duration, success rate

---

### Story 8.10: E2E Playwright Tests для Epic 8

As a **QA Engineer**,
I want E2E tests covering UX improvements,
So that changes are verified in real browser environment.

**Acceptance Criteria:**

**Given** Playwright test suite is configured
**When** E2E tests for Epic 8 are executed
**Then** the following scenarios pass:

**Scenario 1 — Metrics Health Check:**
- User navigates to /metrics
- Health status section is visible
- All services show status indicators

**Scenario 2 — Users search:**
- Admin navigates to /users
- Types username in search
- Table filters correctly

**Scenario 3 — Routes search by Upstream:**
- User searches by upstream URL
- Matching routes are displayed

**Scenario 4 — Load Generator:**
- DevOps navigates to /test
- Starts load generation
- Metrics page shows traffic increase
- Stops load generation

---

## Epic 9: Stabilization & Polish

**Goal:** Fix critical bugs discovered in Epic 8 and add essential UX/security features for production readiness.

**Source:** Epic 8 Retrospective (2026-02-21)

**Stories:** 5

---

### Story 9.1: Auth Session Expires Investigation

As a **User**,
I want my session to remain active while I'm using the application,
So that I don't get unexpectedly logged out.

**Bug Report:**
- **Severity:** HIGH
- **Observed:** Under admin role, application randomly requires re-login
- **Reproduction:** Нестабильный — происходит в произвольные моменты
- **Suspected cause:** JWT expiration или refresh token logic

**Acceptance Criteria:**

**Given** user is logged in
**When** JWT token approaches expiration
**Then** system automatically refreshes the token
**And** user session continues without interruption

**Given** refresh token is valid
**When** access token expires
**Then** new access token is obtained silently
**And** no re-login is required

**Given** refresh token is expired
**When** user performs any action
**Then** user is redirected to login page with message
**And** reason for logout is clearly communicated

---

### Story 9.2: Load Generator Fixes

As a **DevOps Engineer**,
I want the Load Generator to work correctly,
So that I can test routes and see metrics in Grafana.

**Bug Report:**
- **Severity:** HIGH
- **Observed:** Ошибки парсинга ответов, нагрузка не видна в метриках и Grafana
- **Reproduction:** Проверяется на `/test-local` маршруте

**Acceptance Criteria:**

**Given** user runs Load Generator on `/test-local` route
**When** requests are sent
**Then** no parsing errors occur
**And** success/error counts are accurate

**Given** Load Generator is running
**When** user checks /metrics page
**Then** RPS increase is visible

**Given** Load Generator is running
**When** user checks Grafana dashboard
**Then** traffic is visible in graphs

---

### Story 9.3: Role-Based Sidebar Visibility

As a **User**,
I want to see only menu items I have access to,
So that the interface is not cluttered with unavailable options.

**Acceptance Criteria:**

**Given** user with Developer role is logged in
**When** sidebar is displayed
**Then** only Developer-accessible menu items are shown:
- Dashboard
- Routes
- Metrics
- Test

**Given** user with Security role is logged in
**When** sidebar is displayed
**Then** Security-accessible menu items are shown:
- Dashboard
- Routes
- Approvals
- Audit
- Metrics
- Test

**Given** user with Admin role is logged in
**When** sidebar is displayed
**Then** all menu items are shown

---

### Story 9.4: Self-Service Password Change

As a **User**,
I want to change my own password,
So that I can maintain account security.

**Acceptance Criteria:**

**Given** user is logged in
**When** user navigates to profile/settings
**Then** "Change Password" option is available

**Given** user clicks "Change Password"
**When** form is displayed
**Then** form includes:
- Current password (required)
- New password (required)
- Confirm new password (required)

**Given** user submits valid password change
**When** current password is correct
**And** new passwords match
**And** new password meets requirements
**Then** password is updated
**And** success message is shown

**Given** user enters incorrect current password
**When** form is submitted
**Then** error message indicates current password is wrong
**And** password is not changed

---

### Story 9.5: Architecture Documentation Update

As a **Developer**,
I want architecture documentation to reflect current deployment,
So that onboarding and troubleshooting are easier.

**Acceptance Criteria:**

**Given** architecture.md exists
**When** documentation is updated
**Then** includes:
- Nginx reverse proxy configuration
- External domain: gateway.ymorozov.ru
- SSL/TLS setup (if applicable)
- Deployment topology diagram update

---

## Epic 10: Maintenance & Bug Fixes

**Goal:** Fix critical bugs discovered in production testing and add RBAC/UX improvements based on user feedback.

**Source:** Epic 9 Retrospective (2026-02-22) — feedback from Yury (Project Lead)

**Stories:** 10

---

### Story 10.1: Rate Limit Not Working Investigation

As a **DevOps Engineer**,
I want rate limiting to work correctly,
So that upstream services are protected from overload.

**Bug Report:**
- **Severity:** CRITICAL
- **Observed:** Rate limit set to 5 req/s, but 20 requests/second pass through
- **Reproduction:** `/set-local-rate-limit` + Load Generator

**Acceptance Criteria:**

**Given** a route with rate limit 5 req/s
**When** Load Generator sends 20 req/s
**Then** only ~5 requests succeed per second
**And** remaining requests receive HTTP 429

**Given** rate limiting is configured
**When** requests exceed the limit
**Then** X-RateLimit-* headers are present in response

---

### Story 10.2: Approvals Real-Time Updates

As a **Security Specialist**,
I want the Approvals page to update automatically,
So that I see new pending routes without refreshing.

**Bug Report:**
- **Severity:** MEDIUM
- **Observed:** New pending routes don't appear until page refresh
- **Reproduction:** Create pending route, check Approvals tab

**Acceptance Criteria:**

**Given** user is on /approvals page
**When** a new route is submitted for approval
**Then** the route appears in the table within 5 seconds
**And** no manual page refresh is required

**Given** polling or WebSocket is implemented
**When** connection is active
**Then** pending count in sidebar updates automatically

---

### Story 10.3: Security Role Route Rollback

As a **Security Specialist**,
I want to rollback a published route to draft status,
So that I can unpublish problematic routes.

**Acceptance Criteria:**

**Given** user with Security role
**When** viewing a published route
**Then** "Rollback to Draft" action is available

**Given** Security clicks "Rollback to Draft"
**When** action is confirmed
**Then** route status changes to draft
**And** route is removed from gateway-core
**And** audit log records "route.rolledback"

**Given** user with Developer role
**When** viewing a published route
**Then** "Rollback to Draft" action is NOT available

---

### Story 10.4: Author Can Delete Own Draft Route

As a **Developer**,
I want to delete my own draft routes,
So that I can clean up abandoned configurations.

**Acceptance Criteria:**

**Given** user is the author of a draft route
**When** user clicks "Delete" on the route
**Then** route is deleted
**And** confirmation modal is shown first

**Given** user is NOT the author of a draft route
**When** viewing the route
**Then** "Delete" action is NOT available (unless Admin)

**Given** route is not in draft status
**When** author tries to delete
**Then** action is not available

---

### Story 10.5: Nginx Health Check on Metrics Page

As a **DevOps Engineer**,
I want to see Nginx health status on the Metrics page,
So that I can monitor the reverse proxy.

**Acceptance Criteria:**

**Given** user navigates to /metrics
**When** Health Check section loads
**Then** Nginx status is displayed alongside other services
**And** check verifies nginx is responding

---

### Story 10.6: Swagger Links on Login Page

As a **Developer**,
I want quick access to API documentation from the login page,
So that I can explore the API before logging in.

**Acceptance Criteria:**

**Given** user is on /login page
**When** page loads
**Then** links to Swagger UI are displayed:
- Gateway Admin API: /swagger-ui.html
- Gateway Core (if applicable)

**Given** user clicks Swagger link
**When** link is activated
**Then** Swagger UI opens in new tab

---

### Story 10.7: Quick Start Guide

As a **new user** (Developer, Security, Admin),
I want a Quick Start Guide explaining system capabilities and workflows,
So that I can quickly understand how to use API Gateway without asking colleagues.

**Source:** SM Chat Session (2026-02-22) — обсуждение необходимости пользовательской документации

**Acceptance Criteria:**

**Given** новый пользователь открывает документацию
**When** он читает Quick Start Guide
**Then** он понимает:
- Как создать маршрут
- Как отправить на согласование
- Какие статусы существуют и что они значат
- Кто может approve/reject/rollback

**Given** Quick Start Guide
**When** пользователь смотрит на workflow
**Then** он видит визуальную диаграмму (DRAFT → PENDING → PUBLISHED)

**Given** Quick Start Guide
**When** пользователь ищет информацию о ролях
**Then** он находит таблицу permissions по ролям

**Given** пользователь залогинен в систему
**When** он ищет помощь
**Then** ссылка на Quick Start Guide доступна (footer или Help menu)

---

### Story 10.8: Fix Audit Changes Viewer

As a **Security/Admin user**,
I want to see actual change details when expanding audit log entries,
So that I can understand what happened during approve/reject/submit/rollback actions.

**Bug Report:**
- **Severity:** MEDIUM
- **Observed:** "До изменения" и "После изменения" показывают "null" для approved/rejected/submitted/rolledback actions
- **Root Cause:** Backend сохраняет changes в формате `{"previousStatus": "...", "newStatus": "..."}`, frontend ожидает `{"before": {...}, "after": {...}}`

**Acceptance Criteria:**

**Given** audit log entry с любым action (created, updated, deleted, approved, rejected, submitted, rolledback, published)
**When** пользователь раскрывает запись
**Then** changes отображаются корректно (не "null")

**Given** changes без структуры before/after
**When** ChangesViewer рендерит данные
**Then** показывается formatted JSON с заголовком "Детали изменения"

**Given** action = "updated" с before/after структурой
**When** ChangesViewer рендерит данные
**Then** показывается side-by-side diff как раньше

---

### Story 10.9: Fix Theme Support for Modals and Messages

As a **user**,
I want modals and toast messages to respect the current theme (light/dark),
So that the UI is consistent and comfortable to use in dark mode.

**Bug Report:**
- **Severity:** LOW
- **Observed:** Modal.confirm и message.success отображаются в светлой теме при включённой тёмной теме
- **Root Cause:** Ant Design статические методы создают элементы вне React tree, не получают theme context

**Acceptance Criteria:**

**Given** пользователь в тёмной теме
**When** открывается Modal.confirm (Rollback, Delete, etc.)
**Then** модалка отображается в тёмной теме

**Given** пользователь в тёмной теме
**When** появляется toast message
**Then** сообщение отображается в тёмной теме

**Given** пользователь в тёмной теме
**When** появляется Popconfirm
**Then** popconfirm отображается в тёмной теме

---

### Story 10.10: Fix Top Routes Time Range Filter

As a **Security/Admin user**,
I want "Top Routes by Requests" widget to respect the selected time range,
So that I can analyze traffic patterns for different periods.

**Bug Report:**
- **Severity:** MEDIUM
- **Observed:** Виджет "Top Routes by Requests" показывает те же данные при изменении time range
- **Root Cause:** Time range не передаётся в API запрос или React Query key

**Acceptance Criteria:**

**Given** пользователь на странице Metrics
**When** изменяется time range selector
**Then** виджет "Top Routes by Requests" обновляется с новыми данными

**Given** выбран time range "Last 24 hours"
**When** запрашиваются top routes
**Then** API получает параметры `from` и `to` соответствующие 24 часам

**Given** пользователь меняет time range
**When** данные загружаются
**Then** виджет показывает loading spinner

---

## Epic 11: UX Improvements & Documentation

**Goal:** Improve user experience with UI enhancements and document critical undocumented technologies (Lua, Redis Pub/Sub, WebFlux patterns).

**Source:** Epic 10 Retrospective (2026-02-23) — feedback from Yury (Project Lead)

**Stories:** 6

---

### Story 11.1: Integrations Expandable Routes

As a **Security/Admin user**,
I want to see routes in an expandable row on the Integrations page,
So that I can review routes without navigating away from the page.

**Acceptance Criteria:**

**Given** user is on /audit/integrations page
**When** user clicks on an upstream row
**Then** row expands to show all routes for that upstream
**And** routes are displayed in a nested table

**Given** expandable row is open
**When** user clicks again
**Then** row collapses

**Given** upstream has routes
**When** row is expanded
**Then** routes show: path, status, methods, rate limit (if any)

---

### Story 11.2: System Theme Default

As a **user**,
I want the application to respect my system theme preference on first visit,
So that the UI matches my preferred color scheme without manual configuration.

**Acceptance Criteria:**

**Given** user visits the application for the first time
**When** user's system is set to dark mode
**Then** application displays in dark theme

**Given** user visits the application for the first time
**When** user's system is set to light mode
**Then** application displays in light theme

**Given** user manually changes theme
**When** user returns to the application
**Then** manually selected theme is preserved (overrides system preference)

---

### Story 11.3: Lua + Redis Rate Limiting Documentation

As a **new developer**,
I want documentation explaining the Lua-based rate limiting implementation,
So that I can understand and maintain the token bucket algorithm.

**Acceptance Criteria:**

**Given** developer opens architecture.md or dedicated docs
**When** searching for rate limiting
**Then** documentation explains:
- Why Lua is used (atomic operations in Redis)
- Token bucket algorithm implementation
- Redis data structure (`ratelimit:{routeId}:{clientKey}`)
- How to debug and test Lua scripts

---

### Story 11.4: Redis Pub/Sub Documentation

As a **new developer**,
I want documentation explaining the Redis Pub/Sub mechanism,
So that I can understand how route cache synchronization works.

**Acceptance Criteria:**

**Given** developer opens architecture.md or dedicated docs
**When** searching for cache sync
**Then** documentation explains:
- How gateway-admin publishes route changes
- How gateway-core subscribes and updates cache
- Message format and channel naming
- Troubleshooting cache sync issues

---

### Story 11.5: WebFlux Patterns Documentation

As a **new developer**,
I want documentation explaining Spring WebFlux reactive patterns,
So that I can write correct non-blocking code.

**Acceptance Criteria:**

**Given** developer opens architecture.md or dedicated docs
**When** searching for reactive patterns
**Then** documentation explains:
- When to use Mono vs Flux
- Common operators (flatMap, map, switchIfEmpty, etc.)
- Error handling patterns (onErrorResume, onErrorReturn)
- Testing reactive code
- Common anti-patterns to avoid

---

### Story 11.6: Role Check Refactoring

As a **developer**,
I want centralized role-checking helper functions,
So that role permission checks are consistent and type-safe across the codebase.

**Source:** Epic 10 Retrospective — recurring bug with UPPERCASE vs lowercase role checks

**Acceptance Criteria:**

**Given** developer needs to check user permissions
**When** implementing a new feature
**Then** they use centralized helpers: `canRollback()`, `canDelete()`, `canModify()`, etc.

**Given** TypeScript literal type for roles exists
**When** developer writes incorrect role string (e.g., 'SECURITY' instead of 'security')
**Then** TypeScript compiler shows error

**Given** permission logic changes
**When** developer updates the helper function
**Then** all usages across the codebase are automatically updated

---

## Epic 12: Keycloak Integration & Multi-tenant Metrics

**Goal:** Централизованная аутентификация через Keycloak + трекинг трафика по consumers для анализа использования API.

**Source:** Phase 2 PRD (2026-02-23) — FR32-FR59

**Stories:** 10

**Key Capabilities:**
- Keycloak SSO для Admin UI и API consumers
- Consumer Identity в каждом запросе (JWT azp claim)
- Multi-tenant метрики с label `consumer_id`
- Per-consumer rate limits
- Consumer Management через Admin UI

---

### Story 12.1: Keycloak Setup & Configuration

As a **DevOps Engineer**,
I want Keycloak deployed and configured,
So that we have a centralized identity provider for all authentication needs (FR32).

**Acceptance Criteria:**

**Given** docker-compose environment
**When** `docker-compose up -d keycloak`
**Then** Keycloak starts on port 8180
**And** admin console is accessible at http://localhost:8180

**Given** Keycloak is running
**When** realm is imported from `docker/keycloak/realm-export.json`
**Then** realm `api-gateway` is created with:
- Client `gateway-admin-ui` (Authorization Code + PKCE)
- Client `gateway-admin-api` (bearer-only)
- Client `gateway-core` (bearer-only)
- Realm roles: `admin-ui:developer`, `admin-ui:security`, `admin-ui:admin`, `api:consumer`

**Given** realm is configured
**When** test user is created
**Then** user can authenticate via Keycloak login page
**And** JWT token contains expected claims (sub, azp, realm_access.roles)

**Given** docker-compose includes keycloak
**When** running full stack
**Then** keycloak starts after postgres
**And** realm is auto-imported on first start

---

### Story 12.2: Admin UI — Keycloak Auth Migration

As a **User**,
I want to authenticate via Keycloak SSO,
So that I have a unified login experience (FR32).

**Acceptance Criteria:**

**Given** user navigates to Admin UI
**When** user is not authenticated
**Then** user is redirected to Keycloak login page

**Given** user enters valid credentials on Keycloak login
**When** authentication succeeds
**Then** user is redirected back to Admin UI
**And** JWT token is stored (Authorization Code + PKCE flow)
**And** user session is established

**Given** user is authenticated
**When** JWT token approaches expiration
**Then** token is silently refreshed using refresh token
**And** user session continues without interruption

**Given** user clicks "Logout"
**When** logout is triggered
**Then** user is logged out from Admin UI
**And** user is logged out from Keycloak (SSO logout)
**And** user is redirected to login page

**Given** user has role `admin-ui:developer` in Keycloak
**When** user logs in
**Then** user has Developer role in Admin UI

**Given** user has role `admin-ui:security` in Keycloak
**When** user logs in
**Then** user has Security role in Admin UI

**Given** user has role `admin-ui:admin` in Keycloak
**When** user logs in
**Then** user has Admin role in Admin UI

---

### Story 12.3: Gateway Admin — Keycloak JWT Validation

As a **System**,
I want Gateway Admin API to validate JWT tokens from Keycloak,
So that only authenticated users can access the API (FR33, FR34).

**Acceptance Criteria:**

**Given** gateway-admin is configured with Keycloak issuer URI
**When** application starts
**Then** Spring Security OAuth2 Resource Server is configured
**And** JWKS endpoint is cached

**Given** request with valid JWT token
**When** request is made to protected endpoint
**Then** request is authenticated
**And** user principal contains Keycloak claims

**Given** request with invalid JWT token
**When** request is made to protected endpoint
**Then** response returns HTTP 401 Unauthorized
**And** error follows RFC 7807 format

**Given** request with expired JWT token
**When** request is made to protected endpoint
**Then** response returns HTTP 401 Unauthorized

**Given** JWT contains `realm_access.roles: ["admin-ui:security"]`
**When** Spring Security evaluates authorization
**Then** user has `ROLE_SECURITY` authority

**Given** user without `admin-ui:admin` role
**When** accessing `/api/v1/users/**` endpoints
**Then** response returns HTTP 403 Forbidden

---

### Story 12.4: Gateway Core — JWT Authentication Filter

As a **System**,
I want Gateway Core to validate JWT tokens for protected routes,
So that only authenticated consumers can access protected APIs (FR35, FR36, FR40, FR41).

**Acceptance Criteria:**

**Given** route with `auth_required = true`
**When** request without Authorization header
**Then** response returns HTTP 401 Unauthorized
**And** response includes `WWW-Authenticate: Bearer` header

**Given** route with `auth_required = true`
**When** request with valid JWT token
**Then** request is forwarded to upstream
**And** consumer_id is extracted from JWT `azp` claim

**Given** route with `auth_required = false`
**When** request without Authorization header
**Then** request is forwarded to upstream (public route)
**And** consumer_id fallback to header or "anonymous"

**Given** route with `allowed_consumers = ["company-a", "company-b"]`
**When** request from consumer "company-c"
**Then** response returns HTTP 403 Forbidden
**And** detail: "Consumer not allowed for this route"

**Given** route with `allowed_consumers = null` (no restriction)
**When** request from any authenticated consumer
**Then** request is allowed

**Given** Keycloak is temporarily unavailable
**When** JWKS is cached
**Then** JWT validation continues using cached keys
**And** warning is logged about Keycloak unavailability

---

### Story 12.5: Gateway Core — Consumer Identity Filter

As a **System**,
I want to identify the consumer for every request,
So that metrics and rate limits can be applied per-consumer (FR42, FR43, FR44, FR45).

**Acceptance Criteria:**

**Given** request with valid JWT token
**When** ConsumerIdentityFilter processes request
**Then** consumer_id is extracted from JWT `azp` claim
**And** consumer_id is stored in Reactor Context
**And** consumer_id is added to MDC for logging

**Given** request without JWT (public route)
**When** `X-Consumer-ID` header is present
**Then** consumer_id is taken from header value

**Given** request without JWT and without `X-Consumer-ID` header
**When** ConsumerIdentityFilter processes request
**Then** consumer_id is set to "anonymous"

**Given** consumer_id is determined
**When** request continues through filter chain
**Then** MetricsFilter has access to consumer_id
**And** RateLimitFilter has access to consumer_id
**And** LoggingFilter has access to consumer_id (via MDC)

**Given** structured log output
**When** request is logged
**Then** log entry includes `consumer_id` field

---

### Story 12.6: Multi-tenant Metrics

As a **DevOps Engineer**,
I want metrics broken down by consumer,
So that I can analyze usage patterns per company (FR46, FR47, FR48).

**Acceptance Criteria:**

**Given** requests pass through gateway
**When** MetricsFilter records metrics
**Then** `consumer_id` label is added to all metrics:
- `gateway_requests_total{consumer_id="company-a", ...}`
- `gateway_request_duration_seconds{consumer_id="company-a", ...}`
- `gateway_errors_total{consumer_id="company-a", ...}`

**Given** Prometheus is scraping metrics
**When** querying per-consumer data
**Then** query `sum by (consumer_id) (rate(gateway_requests_total[5m]))` returns data
**And** each consumer is listed separately

**Given** PromQL builder in gateway-admin
**When** requesting route metrics
**Then** optional `consumer_id` filter is supported

**Given** Grafana dashboard
**When** viewing gateway metrics
**Then** "Consumer" dropdown filter is available
**And** selecting consumer filters all panels

**Given** high cardinality concern
**When** consumers exceed 1000
**Then** alert is triggered for cardinality review

---

### Story 12.7: Route Authentication Configuration

As a **Developer**,
I want to configure authentication requirements per route,
So that I can have both public and protected endpoints (FR37, FR38, FR39).

**Acceptance Criteria:**

**Given** route create/edit form
**When** form renders
**Then** "Authentication Required" toggle is displayed (default: ON)
**And** "Allowed Consumers" multi-select is displayed (optional)

**Given** migration V11__add_route_auth_fields.sql
**When** executed
**Then** columns are added to routes table:
- `auth_required` (BOOLEAN, NOT NULL, DEFAULT true)
- `allowed_consumers` (TEXT[], nullable)

**Given** route with `auth_required = true`
**When** displayed in routes list
**Then** "Protected" badge is shown

**Given** route with `auth_required = false`
**When** displayed in routes list
**Then** "Public" badge is shown

**Given** route details API
**When** GET `/api/v1/routes/{id}`
**Then** response includes `authRequired` and `allowedConsumers` fields

---

### Story 12.8: Per-consumer Rate Limits

As an **Admin**,
I want to set rate limits per consumer,
So that I can control API usage independently of per-route limits (FR50, FR51, FR52, FR53).

**Acceptance Criteria:**

**Given** migration V12__add_consumer_rate_limits.sql
**When** executed
**Then** `consumer_rate_limits` table is created:
- `id` (UUID, PK)
- `consumer_id` (VARCHAR, UNIQUE)
- `requests_per_second` (INTEGER)
- `burst_size` (INTEGER)
- `created_at`, `updated_at`, `created_by`

**Given** admin user
**When** PUT `/api/v1/consumers/{consumerId}/rate-limit`
**Then** per-consumer rate limit is set
**And** Redis key `rate_limit:consumer:{consumerId}` is used for enforcement

**Given** request from consumer with per-consumer rate limit
**When** rate limit is exceeded
**Then** HTTP 429 is returned
**And** `X-RateLimit-Type: consumer` header indicates which limit was hit

**Given** both per-route and per-consumer limits exist
**When** request is processed
**Then** both limits are checked
**And** stricter limit is enforced (lower of two)

**Given** consumer without specific rate limit
**When** request is processed
**Then** only per-route limit applies (if any)

---

### Story 12.9: Consumer Management UI

As an **Admin**,
I want to manage API consumers through Admin UI,
So that I can onboard new partners and manage access (FR54, FR55, FR56, FR57, FR58, FR59).

**Acceptance Criteria:**

**Given** admin navigates to `/consumers`
**When** page loads
**Then** table displays all Keycloak clients with `serviceAccountsEnabled = true`
**And** columns: Client ID, Status (Active/Disabled), Created, Actions

**Given** admin clicks "Create Consumer"
**When** modal opens
**Then** form includes:
- Client ID (required, pattern: lowercase letters, numbers, hyphens)

**Given** admin submits valid consumer creation
**When** creation succeeds
**Then** client is created in Keycloak via Admin API
**And** client secret is displayed (shown only once)
**And** modal warns: "Save this secret now. It won't be shown again."

**Given** admin clicks "Rotate Secret" on existing consumer
**When** action is confirmed
**Then** new client secret is generated in Keycloak
**And** new secret is displayed
**And** old secret is invalidated

**Given** admin clicks "Disable" on consumer
**When** action is confirmed
**Then** consumer is disabled in Keycloak
**And** consumer can no longer authenticate
**And** status changes to "Disabled" in UI

**Given** consumer row in table
**When** admin clicks on row
**Then** consumer details panel opens
**And** shows: metrics summary, rate limit (if any), created date

**Given** consumer details panel
**When** "View Metrics" is clicked
**Then** user is navigated to metrics page filtered by this consumer

---

### Story 12.10: E2E Playwright Tests for Epic 12

As a **QA Engineer**,
I want E2E tests covering Keycloak integration and multi-tenant features,
So that critical flows are verified in a real browser environment.

**Acceptance Criteria:**

**Given** Playwright test suite is configured
**When** E2E tests for Epic 12 are executed
**Then** the following scenarios pass:

**Scenario 1 — Keycloak SSO Login:**
- User opens Admin UI
- User is redirected to Keycloak login
- User enters credentials
- User is redirected back to Admin UI with session

**Scenario 2 — Role-based access after Keycloak auth:**
- User with developer role logs in
- User sees only developer menu items
- User cannot access /users or /consumers

**Scenario 3 — API Consumer authentication:**
- Consumer obtains token via Client Credentials flow
- Consumer makes request to protected route
- Request succeeds with valid token
- Request fails with invalid/expired token

**Scenario 4 — Public route without auth:**
- Route is configured as auth_required=false
- Request without token succeeds
- consumer_id is "anonymous" or from X-Consumer-ID header

**Scenario 5 — Multi-tenant metrics visible:**
- Multiple consumers make requests
- Metrics page shows breakdown by consumer_id
- Grafana dashboard filters by consumer

**Scenario 6 — Consumer Management:**
- Admin creates new consumer
- Client secret is displayed
- Consumer can authenticate with credentials
- Admin disables consumer
- Consumer authentication fails

---
