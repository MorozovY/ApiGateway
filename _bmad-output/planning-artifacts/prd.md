---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
inputDocuments:
  - 'product-brief-ApiGateway-2026-02-10.md'
  - 'brainstorming-session-2026-02-10.md'
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 1
  projectDocs: 0
workflowType: 'prd'
classification:
  projectType: api_backend
  domain: general
  complexity: medium
  projectContext: greenfield
---

# Product Requirements Document - ApiGateway

**Author:** Yury
**Date:** 2026-02-10

## Executive Summary

**ApiGateway** — централизованное решение для управления API-маршрутизацией 100+ микросервисов.

**Проблема:** Команды тратят часы на ручную настройку маршрутов, рискуют ошибками, зависят друг от друга.

**Решение:** Self-service платформа с workflow согласования, где Backend создаёт маршруты, Security контролирует публикацию, DevOps фокусируется на инфраструктуре.

**Целевые пользователи:** DevOps, Backend-разработчики, Security-специалисты.

**Ключевая ценность:** Время настройки маршрута < 5 минут (vs часы), 90% операций без участия DevOps.

**Технологический стек:** Kotlin + Spring Cloud Gateway, PostgreSQL, Redis, React, Prometheus + Grafana.

## Success Criteria

### User Success

| Персона | Метрика | Целевое значение | "Aha!" момент |
|---------|---------|------------------|---------------|
| **DevOps** | Время настройки маршрута | < 5 минут (vs часы ранее) | "Наконец-то всё в одном месте!" |
| **DevOps** | Ошибки конфигурации | Снижение на 80% | Единый источник правды |
| **Backend** | Self-service операции | 90% без участия DevOps | "Я сам могу настроить за 2 минуты!" |
| **Backend** | Время от запроса до деплоя | < 1 дня (vs неделя) | Автономность команд |
| **Security** | Время на аудит интеграций | < 30 минут (vs дни) | "Вижу все интеграции и кто что менял!" |
| **Security** | Полнота видимости | 100% интеграций | Единая картина безопасности |

### Business Success

| Цель | Метрика | Target |
|------|---------|--------|
| Adoption | % сервисов через gateway | 100% |
| Efficiency | Экономия времени команд | Измеримо через time-to-deploy |
| Reliability | Инциденты из-за misconfig | -80% |
| Visibility | Картина нагрузки | Real-time dashboard |

### Technical Success

| Критерий | Метрика | Target |
|----------|---------|--------|
| Функциональность | CRUD операции | 100% работают |
| Routing | Корректная маршрутизация | 100% success rate |
| Rate Limiting | Применение лимитов | 100% маршрутов |
| Мониторинг | Метрики в Grafana | Real-time данные |

### Measurable Outcomes

**Go/No-Go Decision для MVP:**
MVP успешен если ≥5 сервисов работают через gateway без критических инцидентов в течение 1 недели.

## Product Scope

### MVP Strategy

**Approach:** Problem-solving MVP — минимальный набор функций для решения ключевой проблемы (централизованное управление маршрутами с self-service и контролем).

**Core Value:** Команды самостоятельно управляют маршрутами, Security контролирует публикацию, DevOps освобождён от рутины.

### MVP - Minimum Viable Product (Phase 1)

| Компонент | Функционал |
|-----------|------------|
| Gateway Core | Динамический routing (REST, path-based) |
| Конфигурация | PostgreSQL + R2DBC, hot-reload |
| Rate Limiting | Redis, per-route лимиты |
| Мониторинг | Prometheus + Grafana (RPS, latency, errors) |
| Admin UI | CRUD маршрутов, React SPA |
| Workflow | Draft → Approval → Published |
| Roles | Developer, Security, Admin |
| Auth (MVP) | Базовая аутентификация или API keys |
| Audit | Базовый аудит-лог изменений |

### Growth Features (Post-MVP / Phase 2)

- Keycloak интеграция (JWT validation, SSO)
- Per-endpoint/per-client аналитика
- Dashboard метрик в Admin UI
- Header-based routing
- Feature flags для маршрутов
- Уведомления (email/Slack)

### Vision (Future / Phase 3)

- Миграция на Kubernetes
- Resilience4j (circuit breaker, bulkhead)
- WebSocket/gRPC поддержка
- Developer Portal (self-service onboarding)
- Динамический service discovery

### Risk Mitigation Strategy

| Риск | Тип | Митигация |
|------|-----|-----------|
| Redis SPOF | Technical | Graceful degradation → in-memory fallback |
| Keycloak отложен | Technical | Базовая auth для MVP, Keycloak в Phase 2 |
| Миграция 100+ сервисов | Market | Пилот с 5 сервисами, постепенное подключение |
| Ресурсы/команда | Resource | Минимальный MVP, возможность сокращения scope |

## User Journeys

### Journey 1a: Алексей (DevOps) — Подключение нового сервиса

**Персона:** Алексей, DevOps Engineer. Отвечает за инфраструктуру 100+ микросервисов.

**Opening Scene:** Команда backend просит подключить новый `order-service` к Gateway. Раньше Алексей часами копался в конфигах разных сервисов, искал примеры, рисковал ошибиться при ручных изменениях.

**Rising Action:** Алексей открывает Admin UI и видит — команда уже сама создала маршрут в статусе draft. Все настройки на месте: path, upstream, rate limits.

**Climax:** "Мне больше не нужно делать это самому — команда уже всё настроила!"

**Resolution:** Алексей фокусируется на инфраструктуре Gateway и мониторинге, а не на ручной рутине по каждому сервису. Команды автономны.

### Journey 1b: Алексей (DevOps) — Срочный инцидент

**Opening Scene:** 3 ночи, сработал алерт — один из сервисов перегружает систему. Раньше Алексей искал бы причину в логах каждого сервиса, не понимая общей картины.

**Rising Action:** Открывает Grafana dashboard → сразу видит какой маршрут и клиент создают аномальную нагрузку. Переходит в Admin UI → снижает rate limit для проблемного маршрута.

**Climax:** "Вижу всю картину в одном месте и решил за 5 минут без перезапуска сервисов!"

**Resolution:** Инцидент локализован, сервисы стабильны. Утром команда разбирается с root cause, имея полные данные из Gateway.

### Journey 2: Мария (Backend Developer) — Создание маршрута

**Персона:** Мария, Backend Developer. Разрабатывает микросервисы, часто нуждается в настройке интеграций.

**Opening Scene:** Мария разработала новый endpoint в `payment-service`. Раньше — писала тикет DevOps, ждала неделю, объясняла требования через переписку, зависела от других.

**Rising Action:** Логинится в Admin UI через Keycloak. Создаёт маршрут: указывает path, upstream URL, rate limits. Сохраняет как draft и отправляет на согласование Security.

**Climax:** "Я сама настроила за 2 минуты, не дёргая никого!"

**Resolution:** Маршрут на согласовании у Security. Мария видит статус в UI, получает уведомление после публикации. Время от запроса до деплоя — часы вместо недели.

### Journey 3a: Дмитрий (Security) — Согласование маршрута

**Персона:** Дмитрий, Security Specialist. Отвечает за безопасность интеграций и аудит.

**Opening Scene:** Дмитрий получает уведомление о новом маршруте на согласование. Раньше — искал информацию в переписке, согласовывал "вслепую", не видел полной картины.

**Rising Action:** Открывает Admin UI → видит список маршрутов на согласование. Проверяет настройки: path, upstream, rate limits, автор, дата создания. Всё прозрачно.

**Climax:** "Вижу все интеграции и кто что менял!"

**Resolution:** Одобряет маршрут → он автоматически публикуется. Изменение записано в аудит-лог с полной историей.

### Journey 3b: Дмитрий (Security) — Аудит по запросу

**Opening Scene:** Запрос от руководства: "Какие сервисы имеют доступ к `user-data-service`?" Раньше — дни на сбор информации из разных источников.

**Rising Action:** Открывает Admin UI → фильтрует маршруты по upstream.

**Climax:** Полный список интеграций за минуты с историей изменений.

**Resolution:** Отчёт готов, аудит завершён. Полная видимость 100% интеграций.

### Journey Requirements Summary

| Journey | Выявленные capabilities |
|---------|------------------------|
| Алексей — подключение | Self-service создание маршрутов, статусы draft/published |
| Алексей — инцидент | Real-time dashboard, динамическое изменение rate limits |
| Мария — создание | CRUD маршрутов в UI, workflow согласования, уведомления |
| Дмитрий — согласование | Approval workflow, аудит-лог, role-based access |
| Дмитрий — аудит | Фильтрация/поиск маршрутов, история изменений, отчёты |

## API Backend Specific Requirements

### Project-Type Overview

ApiGateway — это backend-сервис для централизованного управления маршрутизацией API. Предоставляет Admin API для конфигурации маршрутов и Gateway API для проксирования запросов к upstream-сервисам.

### Admin API Endpoints

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/v1/routes` | GET, POST | Список и создание маршрутов |
| `/api/v1/routes/{id}` | GET, PUT, DELETE | CRUD конкретного маршрута |
| `/api/v1/routes/{id}/publish` | POST | Публикация маршрута (после согласования) |
| `/api/v1/rate-limits` | GET, POST | Управление rate limit политиками |
| `/api/v1/rate-limits/{id}` | GET, PUT, DELETE | CRUD конкретной политики |
| `/api/v1/audit` | GET | Аудит-лог изменений |
| `/api/v1/metrics` | GET | Метрики Gateway (RPS, latency, errors) |
| `/api/v1/health` | GET | Health check endpoint |

### Authentication Model

| Аспект | Решение |
|--------|---------|
| **MVP** | Базовая аутентификация или API keys |
| **Phase 2** | Keycloak + JWT validation |
| **Authorization** | Role-based (Admin, Developer, Security) |
| **Gateway requests** | Pass-through (аутентификация на upstream) |

### Data Schemas

| Entity | Ключевые поля |
|--------|---------------|
| **Route** | id, path, upstream_url, method, rate_limit_id, status (draft/published), created_by, created_at |
| **RateLimit** | id, name, requests_per_second, burst_size |
| **AuditLog** | id, entity_type, entity_id, action, user_id, timestamp, changes |

### Rate Limiting

| Аспект | Решение |
|--------|---------|
| **Storage** | Redis |
| **Algorithm** | Token bucket или sliding window |
| **Scope** | Per-route, настраиваемый |
| **Fallback** | Graceful degradation при недоступности Redis |

### API Versioning

| Аспект | Решение |
|--------|---------|
| **Strategy** | URL path versioning (`/api/v1/...`) |
| **Compatibility** | Semver для breaking changes |

### Error Codes

| HTTP Code | Использование |
|-----------|---------------|
| 400 | Невалидные данные запроса |
| 401 | Не аутентифицирован |
| 403 | Нет прав доступа |
| 404 | Ресурс не найден |
| 409 | Конфликт (дубликат маршрута) |
| 429 | Rate limit exceeded |
| 502 | Upstream недоступен |
| 503 | Gateway перегружен |

### Implementation Considerations

- **Reactive stack**: Spring WebFlux + R2DBC для non-blocking I/O
- **Configuration hot-reload**: Изменения маршрутов применяются без рестарта
- **Metrics export**: Micrometer → Prometheus format
- **SDK**: Не планируется (управление через Admin UI и API)

## Functional Requirements

### Route Management

- **FR1:** Developer может создать новый маршрут с указанием path, upstream URL и HTTP методов
- **FR2:** Developer может редактировать существующий маршрут
- **FR3:** Developer может удалить маршрут в статусе draft
- **FR4:** Developer может просматривать список всех маршрутов с фильтрацией и поиском
- **FR5:** Developer может просматривать детали конкретного маршрута
- **FR6:** Developer может клонировать существующий маршрут

### Approval Workflow

- **FR7:** Developer может отправить маршрут на согласование Security
- **FR8:** Security может просматривать список маршрутов на согласовании
- **FR9:** Security может одобрить маршрут для публикации
- **FR10:** Security может отклонить маршрут с указанием причины
- **FR11:** Developer может видеть статус своего маршрута (draft/pending/published/rejected)
- **FR12:** System автоматически публикует маршрут после одобрения Security

### Rate Limiting

- **FR13:** Admin может создать политику rate limiting с настройкой лимитов
- **FR14:** Admin может редактировать существующую политику rate limiting
- **FR15:** Developer может назначить политику rate limiting на маршрут
- **FR16:** System применяет rate limiting к запросам через Gateway

### Monitoring & Metrics

- **FR17:** DevOps может просматривать real-time метрики Gateway (RPS, latency, errors)
- **FR18:** DevOps может просматривать метрики по конкретному маршруту
- **FR19:** DevOps может экспортировать метрики в Prometheus формате
- **FR20:** DevOps может проверить health status Gateway

### Audit & Compliance

- **FR21:** Security может просматривать аудит-лог всех изменений маршрутов
- **FR22:** Security может фильтровать аудит-лог по пользователю, действию, дате
- **FR23:** Security может просматривать историю изменений конкретного маршрута
- **FR24:** Security может фильтровать маршруты по upstream для аудита интеграций

### User & Access Management

- **FR25:** User может аутентифицироваться в системе
- **FR26:** Admin может назначать роли пользователям (Developer, Security, Admin)
- **FR27:** System ограничивает действия пользователя согласно его роли

### Gateway Runtime

- **FR28:** System маршрутизирует входящие запросы на соответствующий upstream
- **FR29:** System возвращает корректные коды ошибок при недоступности upstream
- **FR30:** System применяет изменения конфигурации без перезапуска (hot-reload)
- **FR31:** System логирует все запросы через Gateway

## Non-Functional Requirements

### Performance

| Метрика | Target | Условие |
|---------|--------|---------|
| **Gateway Latency** | P50 < 50ms, P95 < 200ms, P99 < 500ms | Добавленная задержка Gateway (без учёта upstream) |
| **Admin API Response** | < 500ms | Для всех CRUD операций |
| **Configuration Reload** | < 5 секунд | Применение изменений маршрутов |
| **Metrics Update** | Real-time | Задержка < 10 секунд |

### Reliability

| Метрика | Target | Примечание |
|---------|--------|------------|
| **Uptime** | 99.9% | ~8.7 часов downtime/год |
| **Data Durability** | 99.99% | Конфигурация маршрутов |
| **Graceful Degradation** | Обязательно | При недоступности Redis — fallback на in-memory |
| **Zero-Downtime Deploys** | Обязательно | Rolling updates без прерывания трафика |

### Scalability

| Метрика | Target | Примечание |
|---------|--------|------------|
| **Throughput** | 100 RPS (baseline) | С запасом до 1000 RPS (10x) |
| **Concurrent Connections** | 1000+ | Одновременные подключения к Gateway |
| **Routes** | 500+ | Количество активных маршрутов |
| **Horizontal Scaling** | Поддерживается | Несколько инстансов Gateway |

### Security

| Требование | Описание |
|------------|----------|
| **Authentication** | Все запросы к Admin API аутентифицированы |
| **Authorization** | RBAC: Developer, Security, Admin |
| **Audit Trail** | Все изменения логируются с user_id и timestamp |
| **Data in Transit** | HTTPS/TLS 1.2+ для всех соединений |
| **Secrets Management** | Credentials не хранятся в plaintext |

### Observability

| Требование | Описание |
|------------|----------|
| **Metrics** | Prometheus-compatible endpoint |
| **Logging** | Structured JSON logs, correlation IDs |
| **Health Checks** | Liveness и Readiness endpoints |
| **Alerting** | Интеграция с Grafana alerting |

