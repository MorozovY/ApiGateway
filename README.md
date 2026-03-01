# API Gateway

Self-service API Gateway с административной панелью управления маршрутами.

## Git Repositories

Проект использует два remote:

| Remote | URL | Назначение |
|--------|-----|------------|
| `gitlab` | http://localhost:8929/root/api-gateway.git | Primary (CI/CD) |
| `origin` | https://github.com/MorozovY/ApiGateway.git | Mirror (public) |

```bash
# Clone (GitHub)
git clone https://github.com/MorozovY/ApiGateway.git

# Добавить локальный GitLab (после установки)
git remote add gitlab http://localhost:8929/root/api-gateway.git
```

Для настройки локального GitLab см. [docker/gitlab/README.md](docker/gitlab/README.md).

## Требования

- **Java 21+** (JDK)
- **Node.js 18+** (для frontend)
- **Docker & Docker Compose** (для PostgreSQL и Redis)

## Быстрый старт

### 0. Централизованная инфраструктура (Story 13.9)

ApiGateway использует централизованную инфраструктуру из infra проекта:
- **PostgreSQL** — запущен в infra проекте, доступен через `postgres-net`
- **Keycloak** — запущен в infra проекте, доступен через Docker networks

Перед запуском ApiGateway убедитесь что infra stack запущен.

### 1. Создание Docker networks

```bash
# Создать сети (если не существуют)
docker network create traefik-net 2>/dev/null || true
docker network create postgres-net 2>/dev/null || true
```

### 2. Запуск локальной инфраструктуры

```bash
docker-compose up -d
```

Это запустит:
- Redis 7 на порту 6379

### 3. Backend

```bash
cd backend

# Сборка всех модулей
./gradlew build

# Запуск Admin API (порт 8081)
./gradlew :gateway-admin:bootRun

# Запуск Gateway Core (порт 8080)
./gradlew :gateway-core:bootRun
```

### 4. Frontend

```bash
cd frontend/admin-ui

# Установка зависимостей
npm install

# Запуск dev server (порт 3000)
npm run dev
```

## Структура проекта

```
api-gateway/
├── backend/                    # Gradle multi-module
│   ├── gateway-core/          # Spring Cloud Gateway runtime
│   ├── gateway-admin/         # Admin API
│   └── gateway-common/        # Shared entities, utils
├── frontend/                   # React SPA
│   └── admin-ui/
├── docker/                     # Docker configuration files
│   ├── keycloak/              # Keycloak realm configuration
│   │   └── realm-export.json  # Realm auto-import file
│   ├── postgres/              # PostgreSQL init scripts
│   │   └── init-keycloak-db.sql
│   ├── prometheus/            # Prometheus configuration
│   └── grafana/               # Grafana dashboards & provisioning
├── docker-compose.yml         # Development infrastructure
├── docker-compose.dev.yml     # Development overrides
├── .env.example               # Environment variables template
└── README.md
```

## Порты сервисов

| Сервис | Порт | Описание |
|--------|------|----------|
| gateway-core | 8080 | Gateway runtime (request routing) |
| gateway-admin | 8082 | Admin API (8081 занят Nexus) |
| admin-ui | 3000 | Frontend dev server |
| Redis | 6379 | Cache |

**Централизованная инфраструктура (infra проект):**
| Сервис | Доступ | Описание |
|--------|--------|----------|
| PostgreSQL | postgres:5432 (через postgres-net) | Database |
| Keycloak | keycloak:8080 (через Docker networks) | Identity Provider (SSO) |

## Reverse Proxy (Traefik)

Проект использует централизованный Traefik для внешнего доступа (Story 13.8).

**Внешние URL** (Backend = внутренний порт контейнера):
| URL | Backend (container port) |
|-----|--------------------------|
| https://gateway.ymorozov.ru/ | admin-ui:3000 |
| https://gateway.ymorozov.ru/api/v1/* | gateway-admin:8081 |
| https://gateway.ymorozov.ru/api/* | gateway-core:8080 |
| https://gateway.ymorozov.ru/swagger-ui.html | gateway-admin:8081 |

> **Примечание:** Host-mapped порты отличаются (gateway-admin: 8082 на host, 8081 внутри контейнера).
> Keycloak routing (`/keycloak/*`) не включён — Keycloak доступен напрямую на порту 8180.

**Требования:**
- Traefik должен быть запущен в infra проекте
- Сеть `traefik-net` должна быть создана

## Конфигурация

Скопируйте `.env.example` в `.env` и настройте переменные окружения:

```bash
cp .env.example .env
```

## API документация

После запуска gateway-admin:
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/api-docs

## Keycloak (Identity Provider)

При первом запуске Keycloak автоматически импортирует realm `api-gateway` с преднастроенными клиентами и пользователями.

### Доступ к Admin Console

- URL: http://localhost:8180
- Login: `admin` / `admin`

### Тестовые пользователи

| Пользователь | Пароль | Роль |
|--------------|--------|------|
| admin@example.com | admin123 | admin-ui:admin |
| dev@example.com | dev123 | admin-ui:developer |
| security@example.com | security123 | admin-ui:security |

### API Consumer (для тестирования)

Client Credentials flow:
- Client ID: `company-a`
- Client Secret: `company-a-secret-change-in-production`

> ⚠️ **ВАЖНО**: Все credentials выше предназначены только для локальной разработки.
> В production необходимо сгенерировать новые секреты и пароли!

Получение токена:
```bash
curl -X POST "http://localhost:8180/realms/api-gateway/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=company-a" \
  -d "client_secret=company-a-secret-change-in-production"
```

## Технологический стек

### Backend
- Kotlin
- Spring Boot 3.4.x
- Spring Cloud Gateway
- R2DBC (reactive database)
- PostgreSQL 16
- Redis 7

### Frontend
- React 18+
- TypeScript (strict)
- Vite
- Ant Design
- React Query
- React Router v6

## CI/CD Pipeline

Проект использует GitLab CI для автоматизации build и test процессов.

### Pipeline Stages

**Build:**
- Backend: Gradle build (JDK 21)
- Frontend: npm ci + build (Node 20)

**Test:**
- Backend: Gradle tests с GitLab Services (PostgreSQL + Redis)
- Frontend: Vitest unit tests с coverage
- E2E: Playwright tests (закомментировано, требует запущенного стека)

**Sync:**
- Ручная синхронизация master ветки в GitHub mirror

### Локальный GitLab

Для настройки локального GitLab и CI/CD pipeline см. [docker/gitlab/README.md](docker/gitlab/README.md).

Pipeline автоматически запускается при каждом push в GitLab remote. Test results и coverage reports доступны в GitLab UI.
