# Story 13.12: Docker Compose Cleanup & Documentation

Status: done
Story Points: 2

## Story

As a **DevOps Engineer**,
I want Docker Compose files cleaned up and documentation updated after centralized infrastructure migration,
So that the codebase is consistent, maintainable and new developers can easily onboard.

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Infrastructure Migration (Sprint Change Proposal 2026-02-28)

**Business Value:** После миграции на централизованную инфраструктуру (Stories 13.8-13.11) в проекте остались устаревшие файлы и документация, которые могут ввести разработчиков в заблуждение. Очистка и обновление документации обеспечивает:
- Консистентность кодовой базы
- Быстрый onboarding новых разработчиков
- Уменьшение confusion при работе с Docker окружением
- Актуальную документацию для всех сценариев использования

**Dependencies:**
- Story 13.8 (done): Traefik routing — nginx заменён на Traefik
- Story 13.9 (done): PostgreSQL migration — локальный postgres удалён
- Story 13.10 (done): Redis migration — локальный redis удалён
- Story 13.11 (done): Monitoring migration — локальный Prometheus/Grafana удалён

## Acceptance Criteria

### AC1: Obsolete Docker Compose Files Removed
**Given** docker-compose.dev.yml exists with references to local postgres/redis
**When** cleanup is performed
**Then** docker-compose.dev.yml удалён (или обновлён если нужен для специальных случаев)
**And** Все ссылки на локальные postgres/redis сервисы удалены
**And** Никакие рабочие конфигурации не сломаны

### AC2: docker-compose.override.yml.example Updated
**Given** docker-compose.override.yml.example содержит устаревшую конфигурацию
**When** файл обновлён
**Then** Example файл соответствует текущей архитектуре с external networks
**And** Содержит все необходимые Traefik labels (Story 13.8)
**And** Содержит подключение к external networks (postgres-net, redis-net, traefik-net, monitoring-net)
**And** Не содержит depends_on на локальные сервисы
**And** Комментарии описывают централизованную инфраструктуру

### AC3: Obsolete nginx Configuration Cleaned
**Given** docker/nginx/ директория (бэкапы/конфиги от Story 13.8)
**When** cleanup выполнен
**Then** Директория docker/nginx/ удалена (уже пустая — файлы удалены ранее)
**And** docker-compose.override.yml.example не содержит nginx service

### AC4: README/Documentation Updated
**Given** проект README.md и другие docs
**When** документация проверена и обновлена
**Then** README.md содержит актуальные инструкции для локальной разработки
**And** Все упоминания nginx reverse proxy заменены на Traefik
**And** Все упоминания локальных postgres/redis/prometheus/grafana обновлены
**And** Инструкции по запуску соответствуют текущей архитектуре

### AC5: Grafana Alerts via UI Documented (Deferred from 13.11)
**Given** Grafana alerts были file-based в локальном setup
**When** documentation обновлена
**Then** Добавлена документация по созданию Grafana alerts через UI
**Or** Создан скрипт/provisioning для автоматического создания alerts в centralized Grafana
**And** Alert rules documented: high-error-rate (>5%), high-latency-p95 (>500ms), gateway-down (no metrics 1 min)

### AC6: Project Structure Consistent
**Given** все cleanup задачи выполнены
**When** проект проверен
**Then** Нет orphaned файлов, связанных с удалённой инфраструктурой
**And** .gitignore не содержит лишних записей для удалённых компонентов
**And** Структура docker/ директории логична и документирована

## Tasks / Subtasks

- [x] Task 1: Analyze Current State (AC: All)
  - [x] 1.1 Список всех docker-compose файлов и их назначение
  - [x] 1.2 Список всех файлов в docker/ директории
  - [x] 1.3 Идентификация устаревших/orphaned файлов
  - [x] 1.4 Проверка .gitignore на лишние записи

- [x] Task 2: Remove Obsolete Files (AC: #1, #3)
  - [x] 2.1 Удалить docker-compose.dev.yml (устаревший)
  - [x] 2.2 Удалить docker/nginx/ директорию (уже пустая) — УЖЕ НЕ СУЩЕСТВУЕТ
  - [x] 2.3 docker/postgres/init-keycloak-db.sql — ОСТАВЛЕН (используется для Keycloak DB в infra)
  - [x] 2.4 Убедиться что удаление не сломало ничего — docker-compose config работает

- [x] Task 3: Update docker-compose.override.yml.example (AC: #2)
  - [x] 3.1 Синхронизировать с текущим docker-compose.override.yml
  - [x] 3.2 Обновить комментарии про централизованную инфраструктуру
  - [x] 3.3 Убрать nginx service
  - [x] 3.4 Добавить все external networks
  - [x] 3.5 Добавить Traefik labels
  - [x] 3.6 Убрать depends_on на локальные сервисы

- [x] Task 4: Update Documentation (AC: #4)
  - [x] 4.1 Обновить README.md:
    - [x] 4.1.1 Удалить docker/prometheus/ и docker/grafana/ из структуры проекта
    - [x] 4.1.2 Удалить docker-compose.dev.yml из структуры проекта
    - [x] 4.1.3 Обновить секцию "Создание Docker networks" — добавить redis-net, monitoring-net
    - [x] 4.1.4 Обновить секцию "Запуск локальной инфраструктуры" — Redis теперь в infra
    - [x] 4.1.5 Обновить таблицу централизованной инфраструктуры — добавить Redis, Prometheus, Grafana
  - [x] 4.2 Проверить docker/gitlab/README.md — актуален (Story 13.4-13.7)
  - [x] 4.3 CLAUDE.md — уже обновлён в Story 13.11 ✅

- [x] Task 5: Grafana Alerts Documentation (AC: #5)
  - [x] 5.1 Задокументировать процесс создания alerts через Grafana UI
  - [x] 5.2 Или создать provisioning config для centralized Grafana — выбрана документация
  - [x] 5.3 Добавить описание alert rules в документацию — docs/monitoring-alerts.md

- [x] Task 6: Final Verification (AC: #6)
  - [x] 6.1 Проверить что docker-compose up -d работает — docker-compose config --services OK
  - [x] 6.2 Проверить что нет orphaned файлов — все устаревшие файлы удалены
  - [x] 6.3 Проверить структуру docker/ директории — корректная
  - [x] 6.4 Run backend tests — BUILD SUCCESSFUL

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Текущее состояние Docker файлов

**Docker Compose файлы:**
| Файл | Статус | Действие |
|------|--------|----------|
| `docker-compose.yml` | ✅ Актуальный | Только networks (external) |
| `docker-compose.override.yml` | ✅ Актуальный | Dev apps с hot-reload |
| `docker-compose.override.yml.example` | ❌ Устаревший | Обновить |
| `docker-compose.dev.yml` | ❌ Устаревший | Удалить |
| `deploy/docker-compose.prod.yml` | ? | Проверить |
| `deploy/docker-compose.ci-base.yml` | ? | Проверить |
| `docker/gitlab/docker-compose.yml` | ✅ Актуальный | GitLab CI stack |

**Docker директории:**
| Директория | Содержимое | Действие |
|------------|------------|----------|
| `docker/` | Dockerfiles | Оставить |
| `docker/nginx/` | ПУСТАЯ | Удалить директорию |
| `docker/postgres/` | init-keycloak-db.sql | ✅ ОСТАВИТЬ — используется для Keycloak DB |
| `docker/keycloak/` | realm-export.json | Оставить |
| `docker/gitlab/` | GitLab CI stack | Оставить |

### Устаревший docker-compose.dev.yml

**Текущее содержимое:**
```yaml
services:
  postgres:
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: gateway
      POSTGRES_USER: gateway
      POSTGRES_PASSWORD: gateway

  redis:
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
```

**Проблема:** Ссылается на postgres и redis сервисы, которые больше не определены в docker-compose.yml (удалены в Stories 13.9, 13.10).

**Решение:** Удалить файл полностью.

### Устаревший docker-compose.override.yml.example

**Проблемы:**
1. Содержит `depends_on: postgres, redis` — эти сервисы больше не локальные
2. Содержит nginx service — заменён на Traefik (Story 13.8)
3. Не содержит external networks (postgres-net, redis-net, traefik-net, monitoring-net)
4. Не содержит Traefik labels
5. Устаревшие environment variables (NGINX_URL, PROMETHEUS_URL без внешнего доступа)

**Решение:** Синхронизировать с текущим docker-compose.override.yml

### docker/nginx/ директория

**Статус:** ПУСТАЯ (файлы уже удалены в предыдущих stories)

Директория осталась пустой после удаления nginx конфигурации при миграции на Traefik (Story 13.8).

**Решение:** Удалить пустую директорию.

### docker/postgres/init-keycloak-db.sql

**Содержимое:** SQL для инициализации Keycloak database в PostgreSQL.

```sql
-- Создаём базу данных keycloak для Keycloak Identity Provider
CREATE DATABASE keycloak;
```

**Статус:** Используется для создания БД keycloak в централизованном PostgreSQL (infra проект).

**Решение:** ОСТАВИТЬ — файл актуален.

### Grafana Alerts (Deferred from Story 13.11)

**Локальные alerts (были в docker/grafana/provisioning/alerting/alerts.yml):**
```yaml
groups:
  - name: gateway-alerts
    rules:
      - alert: high-error-rate
        expr: rate(gateway_request_errors_total[5m]) / rate(gateway_requests_total[5m]) > 0.05
        for: 5m

      - alert: high-latency-p95
        expr: histogram_quantile(0.95, rate(gateway_request_duration_seconds_bucket[5m])) > 0.5
        for: 5m

      - alert: gateway-down
        expr: up{job="gateway-core"} == 0
        for: 1m
```

**Централизованный Grafana:** Использует UI-based alerting (не file provisioning).

**Варианты:**
1. **Документация:** Добавить инструкции по созданию alerts через Grafana UI
2. **Provisioning:** Создать YAML для provisioning в infra Grafana (если поддерживает)
3. **API:** Использовать Grafana API для создания alerts

### Previous Story Intelligence (13.11)

**Ключевые learnings:**
- External network паттерн работает стабильно
- Документацию обновлять сразу после миграции
- Бэкапы удалять после верификации миграции
- Grafana file-based alerts не мигрированы — требуется отдельное решение

### Project Structure после cleanup

```
docker/
├── Dockerfile.admin-ui
├── Dockerfile.admin-ui.ci
├── Dockerfile.admin-ui.dev
├── Dockerfile.gateway-admin
├── Dockerfile.gateway-admin.dev
├── Dockerfile.gateway-core
├── Dockerfile.gateway-core.dev
├── keycloak/
│   └── realm-export.json
├── postgres/
│   └── init-keycloak-db.sql     # ✅ Используется для Keycloak DB
└── gitlab/
    ├── docker-compose.yml
    ├── README.md
    ├── WINDOWS-SETUP.md
    ├── .env.example
    ├── scripts/
    ├── deploy.sh
    ├── rollback.sh
    ├── smoke-test.sh
    └── ...
```

### README.md Issues to Fix

**Устаревшие упоминания в структуре проекта:**
```
├── docker/                     # Docker configuration files
│   ├── prometheus/            # ❌ УДАЛЁН в Story 13.11
│   └── grafana/               # ❌ УДАЛЁН в Story 13.11
├── docker-compose.dev.yml     # ❌ УДАЛИТЬ в этой story
```

**Устаревшая секция "Создание Docker networks":**
```bash
# Текущее:
docker network create traefik-net 2>/dev/null || true
docker network create postgres-net 2>/dev/null || true

# Нужно добавить:
docker network create redis-net 2>/dev/null || true
docker network create monitoring-net 2>/dev/null || true
```

**Устаревшая секция "Запуск локальной инфраструктуры":**
```
# Текущее:
Это запустит:
- Redis 7 на порту 6379

# Нужно изменить на:
Это запустит gateway services (gateway-admin, gateway-core, admin-ui).
Redis запущен в централизованной инфраструктуре (infra проект).
```

**Устаревшая таблица централизованной инфраструктуры:**
Нужно добавить:
- Redis → через redis-net
- Prometheus → https://prometheus.ymorozov.ru
- Grafana → https://grafana.ymorozov.ru

### Security Considerations

1. **Credentials cleanup:** Убедиться что удаляемые файлы не содержат secrets
2. **Git history:** Устаревшие файлы останутся в git history — это OK
3. **Backup verification:** Перед удалением nginx.conf.bak убедиться что Traefik работает

### Rollback Plan

1. **Если что-то сломалось:**
   ```bash
   git checkout HEAD~1 -- docker-compose.dev.yml docker-compose.override.yml.example
   ```

2. **Восстановление nginx:**
   ```bash
   # nginx полностью заменён на Traefik — rollback требует Story 13.8 revert
   # Это крайне маловероятно после успешных 13.9-13.11
   ```

### Files to Modify/Delete

| Файл | Действие |
|------|----------|
| `docker-compose.dev.yml` | DELETE |
| `docker-compose.override.yml.example` | UPDATE |
| `docker/nginx/` | DELETE (пустая директория) |
| `docker/postgres/init-keycloak-db.sql` | ✅ KEEP — используется |
| `README.md` | UPDATE (если нужно) |

### References

- [Source: sprint-status.yaml#13-12] — Story definition
- [Source: 13-11-monitoring-migration-prometheus-grafana.md] — Previous story context
- [Source: docker-compose.yml] — Current main compose file
- [Source: docker-compose.override.yml] — Current dev override
- [Source: docker-compose.dev.yml] — Obsolete file to delete
- [Source: docker-compose.override.yml.example] — File to update
- [Source: docker/nginx/nginx.conf.bak] — Backup to delete
- [Source: CLAUDE.md] — Development Commands (already updated in 13.11)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Нет issues — все задачи выполнены без ошибок.

### Completion Notes List

1. **Task 1: Analyze Current State** — выполнен полный анализ:
   - Найдено 5 docker-compose файлов: основной, override, example, prod, ci-base
   - docker/nginx/ директория уже не существовала
   - .gitignore корректен — нет лишних записей
   - Идентифицированы устаревшие файлы: docker-compose.dev.yml, docker-compose.override.yml.example

2. **Task 2: Remove Obsolete Files**:
   - Удалён docker-compose.dev.yml (ссылался на несуществующие postgres/redis)
   - docker/nginx/ уже удалена в предыдущих stories
   - docker/postgres/init-keycloak-db.sql сохранён (используется для Keycloak)

3. **Task 3: Update docker-compose.override.yml.example**:
   - Полностью синхронизирован с docker-compose.override.yml
   - Добавлены Traefik labels для всех сервисов
   - Добавлены external networks: traefik-net, postgres-net, redis-net, monitoring-net
   - Удалён nginx service
   - Удалены depends_on на локальные postgres/redis
   - Обновлены комментарии про централизованную инфраструктуру

4. **Task 4: Update Documentation**:
   - README.md: обновлена структура проекта (удалены prometheus/, grafana/, docker-compose.dev.yml)
   - README.md: добавлены redis-net и monitoring-net в создание networks
   - README.md: обновлена секция запуска — теперь запускаются только gateway services
   - README.md: расширена таблица централизованной инфраструктуры (Redis, Prometheus, Grafana)

5. **Task 5: Grafana Alerts Documentation**:
   - Создан новый документ docs/monitoring-alerts.md
   - Документация по созданию alerts через Grafana UI
   - PromQL запросы для всех стандартных алертов
   - Troubleshooting guide

6. **Task 6: Final Verification**:
   - docker-compose config --services работает корректно
   - Нет orphaned файлов
   - Backend tests: BUILD SUCCESSFUL (20 tasks, все тесты passed)

### File List

**Deleted:**
- docker-compose.dev.yml
- deploy/docker-compose.prod.yml — устарел (nginx → Traefik, local → centralized infra)
- deploy/nginx/ — nginx заменён на Traefik
- deploy/grafana/ — мониторинг в centralized infra
- deploy/prometheus/ — мониторинг в centralized infra

**Modified:**
- docker-compose.override.yml.example — обновлён, исправлен hardcoded password
- README.md — обновлена структура проекта и инструкции
- deploy/README.md — добавлен warning об устаревшей документации
- docs/monitoring-alerts.md — исправлены PromQL запросы на корректные метрики
- _bmad-output/implementation-artifacts/sprint-status.yaml — story status

**Created:**
- docs/monitoring-alerts.md — документация по мониторингу и алертам

### Change Log

- 2026-03-02: Story 13.12 — Docker Compose cleanup и документация
  - Удалён устаревший docker-compose.dev.yml
  - Обновлён docker-compose.override.yml.example с Traefik labels и external networks
  - Обновлён README.md с актуальной информацией о централизованной инфраструктуре
  - Создана документация docs/monitoring-alerts.md для Grafana alerts
- 2026-03-02: Code Review — дополнительный cleanup
  - Удалён устаревший deploy/docker-compose.prod.yml (nginx → Traefik)
  - Удалены устаревшие директории: deploy/nginx/, deploy/grafana/, deploy/prometheus/
  - Исправлен hardcoded password в docker-compose.override.yml.example
  - Исправлены некорректные PromQL метрики в docs/monitoring-alerts.md
  - Добавлен warning в deploy/README.md об устаревшей документации

