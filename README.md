# API Gateway

Self-service API Gateway с административной панелью управления маршрутами.

## Требования

- **Java 21+** (JDK)
- **Node.js 18+** (для frontend)
- **Docker & Docker Compose** (для PostgreSQL и Redis)

## Быстрый старт

### 1. Запуск инфраструктуры

```bash
docker-compose up -d
```

Это запустит:
- PostgreSQL 16 на порту 5432
- Redis 7 на порту 6379

### 2. Backend

```bash
cd backend

# Сборка всех модулей
./gradlew build

# Запуск Admin API (порт 8081)
./gradlew :gateway-admin:bootRun

# Запуск Gateway Core (порт 8080)
./gradlew :gateway-core:bootRun
```

### 3. Frontend

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
├── docker-compose.yml         # Development infrastructure
├── docker-compose.dev.yml     # Development overrides
├── .env.example               # Environment variables template
└── README.md
```

## Порты сервисов

| Сервис | Порт | Описание |
|--------|------|----------|
| gateway-core | 8080 | Gateway runtime (request routing) |
| gateway-admin | 8081 | Admin API |
| admin-ui | 3000 | Frontend dev server |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache |

## Конфигурация

Скопируйте `.env.example` в `.env` и настройте переменные окружения:

```bash
cp .env.example .env
```

## API документация

После запуска gateway-admin:
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/api-docs

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
