---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: ['brainstorming-session-2026-02-10.md']
date: 2026-02-10
author: Yury
---

# Product Brief: ApiGateway

## Executive Summary

**ApiGateway** — это собственное решение для централизованного управления API, разработанное для упрощения интеграций между 100+ микросервисами. Продукт решает критические проблемы: высокую когнитивную нагрузку на команды, ошибки при ручном управлении конфигурациями, и зависимость от дорогих или негибких enterprise-решений.

Построенный на open-source стеке (Kotlin, Spring Cloud Gateway, PostgreSQL, Redis), ApiGateway обеспечивает полный контроль над инфраструктурой при нулевых лицензионных затратах.

---

## Core Vision

### Problem Statement

Команды разработки, DevOps и Security тратят значительные усилия на управление интеграциями между 100+ микросервисами. Отсутствие единой точки контроля приводит к:
- Необходимости помнить конфигурации множества сервисов
- Человеческим ошибкам при ручных изменениях
- Дублированию логики авторизации и rate limiting
- Разрозненному мониторингу без единой картины

### Problem Impact

- **DevOps**: Увеличенное время на деплой и troubleshooting
- **Backend-разработчики**: Повторяющийся boilerplate код в каждом сервисе
- **Security**: Сложность аудита и контроля доступа

### Why Existing Solutions Fall Short

| Решение | Причина отказа |
|---------|----------------|
| Kong, AWS API Gateway | Высокая стоимость enterprise лицензий |
| Managed решения | Vendor lock-in, недостаточный контроль |
| Nginx | Ограниченный функционал, сложная конфигурация |

### Proposed Solution

Собственный API Gateway на базе Kotlin + Spring Cloud Gateway с:
- **Динамическим routing** из PostgreSQL
- **Централизованным rate limiting** через Redis
- **Единой аутентификацией** через Keycloak
- **Self-service Admin UI** для управления маршрутами
- **Полным observability** (Prometheus + Grafana + Loki)

### Key Differentiators

1. **Self-service управление** — команды сами управляют своими маршрутами
2. **Полный контроль** — свой код, своя инфраструктура, zero vendor lock-in
3. **Прозрачность** — open-source стек, полная видимость
4. **Cost-effective** — нулевые лицензионные затраты
5. **Простота** — снижение когнитивной нагрузки через единый интерфейс

---

## Target Users

### Primary Users

#### DevOps Engineer — "Алексей"

**Контекст:** Отвечает за инфраструктуру и деплой 100+ микросервисов. Работает с множеством конфигураций ежедневно.

**Текущая боль:**
- Разные настройки в разных сервисах — нет единообразия
- Постоянный поиск информации о конфигурациях
- Высокий риск ошибок при ручных изменениях
- Зависимость от знаний "в голове"

**Ценность от ApiGateway:**
- Унификация всех настроек в одном месте
- Единый источник правды для всех интеграций
- Снижение когнитивной нагрузки и риска ошибок

**"Aha!" момент:** "Наконец-то всё в одном месте, не надо лезть в каждый сервис!"

---

#### Backend Developer — "Мария"

**Контекст:** Разрабатывает микросервисы в одной из команд. Часто нуждается в настройке интеграций.

**Текущая боль:**
- Разные реализации настроек в разных командах
- Постоянные консультации с DevOps для настройки сервисов
- Зависимость от других для простых изменений

**Ценность от ApiGateway:**
- Self-service настройка своих сервисов
- Автономность — не нужно ждать DevOps
- Передача настроек на уровень Security без посредников

**"Aha!" момент:** "Я сам могу настроить маршрут за 2 минуты!"

---

### Secondary Users

#### Security Specialist — "Дмитрий"

**Контекст:** Отвечает за безопасность интеграций, согласование доступов, аудит.

**Текущая боль:**
- Нет полной картины по всем интеграциям
- Ручное согласование через переписку/тикеты
- Сложно отследить изменения

**Ценность от ApiGateway:**
- Полная видимость всех интеграций в одном UI
- Самостоятельное управление политиками доступа
- Аудит-лог всех изменений

**"Aha!" момент:** "Вижу все интеграции и кто что менял!"

---

### User Journey

| Этап | DevOps/Backend | Security |
|------|----------------|----------|
| **Discovery** | Узнают от коллег или на внутреннем демо | Приглашаются после внедрения |
| **Onboarding** | Логин через Keycloak, изучение UI | Обзор dashboard и политик |
| **Core Usage** | CRUD маршрутов, мониторинг | Ревью настроек, аудит |
| **Success** | Первый self-service деплой | Полная картина интеграций |
| **Long-term** | Ежедневный инструмент | Периодический контроль |

---

## Success Metrics

### User Success Metrics

| Персона | Метрика | Целевое значение |
|---------|---------|------------------|
| **DevOps** | Время настройки маршрута | < 5 минут (vs часы ранее) |
| **DevOps** | Количество ошибок конфигурации | Снижение на 80% |
| **Backend** | Self-service операции без DevOps | 90% операций |
| **Backend** | Время от запроса до деплоя | < 1 дня (vs неделя) |
| **Security** | Время на аудит интеграций | < 30 минут (vs дни) |
| **Security** | Полнота видимости | 100% интеграций видны |

---

### Business Objectives

| Цель | Метрика | Target |
|------|---------|--------|
| **Adoption** | % сервисов через gateway | 100% |
| **Efficiency** | Экономия времени команд | Измеримо через time-to-deploy |
| **Reliability** | Снижение инцидентов из-за misconfig | -80% |
| **Visibility** | Полная картина нагрузки | Real-time dashboard |

---

### Key Performance Indicators (Observability)

**Per Endpoint Metrics:**

| Метрика | Описание | Разрезы |
|---------|----------|---------|
| **Request Count** | Количество запросов | endpoint, client, time period |
| **HTTP Status Codes** | Распределение 2xx/4xx/5xx | endpoint, client |
| **Latency** | Время выполнения (P50, P95, P99) | endpoint, client |
| **Error Rate** | % ошибочных ответов | endpoint, client |

**Per Client Analytics:**

| Метрика | Описание |
|---------|----------|
| **Client → Endpoint mapping** | Кто какие endpoints вызывает |
| **Request volume per client** | Объём трафика от каждого клиента |
| **Response time per client** | Latency в разрезе клиентов |
| **Error rate per client** | Ошибки по клиентам |

**Dashboard Requirements:**
- Real-time обновление
- Drill-down: Service → Endpoint → Client
- Исторические данные для трендов
- Алерты при аномалиях

---

## MVP Scope

### Core Features (MVP)

| Компонент | Функционал | Детали |
|-----------|------------|--------|
| **Gateway Core** | Динамический routing | REST only, path-based predicates |
| **Конфигурация** | Хранение маршрутов | PostgreSQL + R2DBC |
| **Rate Limiting** | Ограничение запросов | Redis, базовые лимиты |
| **Мониторинг** | Базовые метрики | Prometheus + Grafana (RPS, latency, errors) |
| **Admin UI** | Управление маршрутами | CRUD операции, React SPA |

**MVP User Stories:**
- DevOps может создать/изменить/удалить маршрут через UI
- Backend-разработчик видит список всех маршрутов
- Система применяет rate limiting к запросам
- Базовые метрики видны в Grafana

---

### Out of Scope for MVP

| Функционал | Причина отложить | Планируемая фаза |
|------------|------------------|------------------|
| **Аутентификация (Keycloak)** | Упрощает MVP, отдельная интеграция | Phase 2 |
| **Per-client analytics** | Требует доп. инфраструктуры | Phase 2 |
| **Dashboard метрик в Admin UI** | Grafana достаточно для MVP | Phase 2 |
| **Feature flags** | Nice-to-have, не критично | Phase 2 |
| **WebSocket / gRPC** | REST покрывает 100% текущих нужд | Phase 3 |
| **Header-based routing** | Path-based достаточно на старте | Phase 2 |
| **Circuit breaker** | WebClient retries достаточно | Phase 3 |
| **Kubernetes** | Docker Compose для старта | Phase 3 |
| **Developer Portal** | После стабилизации core | Phase 3 |

---

### MVP Success Criteria

| Критерий | Метрика | Target |
|----------|---------|--------|
| **Функциональность** | Все CRUD операции работают | 100% |
| **Routing** | Запросы корректно маршрутизируются | 100% success rate |
| **Rate Limiting** | Лимиты применяются корректно | Работает на 100% маршрутов |
| **Мониторинг** | Метрики видны в Grafana | Real-time данные |
| **Adoption** | Первые сервисы подключены | ≥5 сервисов |
| **User Feedback** | DevOps/Backend могут работать | Положительный feedback |

**Go/No-Go Decision:**
MVP успешен если ≥5 сервисов работают через gateway без критических инцидентов в течение 1 недели.

---

### Future Vision (Post-MVP)

**Phase 2 — Security & Analytics:**
- Keycloak интеграция (JWT validation)
- Per-endpoint/per-client аналитика
- Dashboard метрик в Admin UI
- Header-based routing
- Feature flags для маршрутов

**Phase 3 — Scale & Resilience:**
- Миграция на Kubernetes
- Resilience4j (circuit breaker, bulkhead)
- WebSocket/gRPC поддержка
- Developer Portal (self-service onboarding)
- Динамический service discovery

**Long-term Vision:**
Платформа управления всеми интеграциями компании с полной видимостью, self-service для команд, и enterprise-grade надёжностью.
