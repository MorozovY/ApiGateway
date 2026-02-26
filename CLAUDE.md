# Правила проекта ApiGateway

## Язык документации и комментариев

### Обязательные правила

1. **Комментарии в коде** — только на русском языке
2. **Названия тестов** — только на русском языке (например: `'генерирует UUID когда header отсутствует'`)
3. **Документация** — на русском языке
4. **Commit messages** — на английском языке (стандарт git)
5. **Идентификаторы кода** (переменные, функции, классы) — на английском языке (стандарт программирования)

### Примеры

**Правильно:**
```kotlin
// Генерируем correlation ID если он отсутствует в запросе
val correlationId = exchange.request.headers
    .getFirst(CORRELATION_ID_HEADER)
    ?: UUID.randomUUID().toString()
```

**Неправильно:**
```kotlin
// Generate correlation ID if missing from request
val correlationId = exchange.request.headers
    .getFirst(CORRELATION_ID_HEADER)
    ?: UUID.randomUUID().toString()
```

**Тесты — правильно:**
```kotlin
@Test
fun `генерирует UUID когда X-Correlation-ID header отсутствует`() {
    // ...
}

@Test
fun `сохраняет существующий correlation ID`() {
    // ...
}
```

**Тесты — неправильно:**
```kotlin
@Test
fun `generates UUID when X-Correlation-ID header missing`() {
    // ...
}
```

---

## Reactive Patterns (Spring WebFlux)

### Запрещено

1. **`@PostConstruct`** — использовать `@EventListener(ApplicationReadyEvent::class)` для инициализации
2. **Блокирующие вызовы** (`.block()`, `Thread.sleep()`) — использовать reactive chains
3. **`ThreadLocal`** без context propagation — использовать Reactor Context + MDC bridging
4. **`synchronized` блоки** — использовать `AtomicReference` для thread-safe state

### Обязательно

1. **RFC 7807** для всех error responses
2. **Correlation ID** во всех логах и error responses
3. **snake_case** для колонок PostgreSQL
4. **Testcontainers** для integration tests

---

## Code Review Checklist

Перед отправкой на review убедиться:

- [ ] Комментарии на русском языке
- [ ] Названия тестов на русском языке
- [ ] Нет `@PostConstruct` в reactive контексте
- [ ] Нет блокирующих вызовов
- [ ] Все error responses в RFC 7807 формате
- [ ] Тесты покрывают все AC
- [ ] Нет placeholder тестов

---

## Git-процесс

### Коммит

Просьба "закоммить" означает:
1. `git add` нужных файлов
2. `git commit` с message на английском языке
3. `git push` в GitHub

Оба шага (локальный коммит и push) выполняются всегда вместе, если явно не указано иное.

---

## Конвенции именования

| Область | Конвенция | Пример |
|---------|-----------|--------|
| Классы Kotlin | PascalCase | `CorrelationIdFilter` |
| Функции Kotlin | camelCase | `generateCorrelationId()` |
| Константы | SCREAMING_SNAKE_CASE | `CORRELATION_ID_HEADER` |
| Колонки PostgreSQL | snake_case | `created_at`, `upstream_url` |
| Таблицы PostgreSQL | snake_case (plural) | `routes`, `users` |
| HTTP Headers | X-Header-Name | `X-Correlation-ID` |

---

## Development Commands

### Запуск всего стека (рекомендуемый способ)

```bash
# Первый запуск: собрать образы и запустить всё
docker-compose up -d --build

# Последующие запуски
docker-compose up -d

# С мониторингом (Prometheus + Grafana)
docker-compose --profile monitoring up -d

# Проверка статуса
docker-compose ps

# Логи конкретного сервиса
docker-compose logs -f gateway-admin
docker-compose logs -f gateway-core
docker-compose logs -f admin-ui
```

**Сервисы:**
- **gateway-admin**: http://localhost:8081 (API + Swagger UI: /swagger-ui.html)
- **gateway-core**: http://localhost:8080 (Gateway)
- **admin-ui**: http://localhost:3000 (Frontend)
- **Prometheus**: http://localhost:9090 (с --profile monitoring)
- **Grafana**: http://localhost:3001 (с --profile monitoring, login: admin/admin)

**Hot-reload:**
- Backend: изменения в `backend/*/src` автоматически перезагружают сервис
- Frontend: Vite HMR обновляет браузер при изменениях в `frontend/admin-ui/src`

### Запуск только инфраструктуры

```bash
# Только PostgreSQL и Redis (без приложений)
docker-compose up -d postgres redis

# С Prometheus + Grafana
docker-compose up -d postgres redis && docker-compose --profile monitoring up -d prometheus grafana
```

### Мониторинг (Prometheus + Grafana)

```bash
# Запуск мониторинга
docker-compose --profile monitoring up -d

# UI доступ:
# - Prometheus: http://localhost:9090 (targets: http://localhost:9090/targets)
# - Grafana: http://localhost:3001 (login: admin/admin)

# Остановка мониторинга
docker-compose --profile monitoring down

# Полная очистка с данными
docker-compose --profile monitoring down -v
```

**Примечания:**
- Grafana использует порт 3001 (3000 занят frontend dev server)
- Prometheus scrape interval: 15 секунд
- Dashboard "API Gateway" автоматически provisioned
- Для работы метрик gateway-core должен быть запущен

### Запуск backend (без Docker)

```bash
# Gateway Admin (port 8081)
./gradlew :gateway-admin:bootRun

# Gateway Core (port 8080)
./gradlew :gateway-core:bootRun
```

### Запуск frontend (без Docker)

```bash
cd frontend/admin-ui
npm run dev  # port 3000
```

### E2E тесты

```bash
cd frontend/admin-ui
npx playwright test                    # все тесты
npx playwright test e2e/epic-5.spec.ts # конкретный файл
npx playwright test --ui               # UI режим
npx playwright test --headed           # с браузером
```

### Unit/Integration тесты

```bash
# Backend (Kotlin)
./gradlew test                         # все тесты
./gradlew :gateway-admin:test          # только gateway-admin
./gradlew :gateway-core:test           # только gateway-core

# Frontend (Vitest)
cd frontend/admin-ui
npm run test                           # watch режим
npm run test:run                       # однократный запуск
npm run test:coverage                  # с coverage
```

### Полный рестарт

```bash
# Остановить всё
docker-compose down

# Пересобрать образы и запустить
docker-compose up -d --build
```

### Очистка и сброс

```bash
# Очистка Docker volumes (УДАЛЯЕТ ДАННЫЕ!)
docker-compose down -v

# Очистка build артефактов
./gradlew clean

# Пересборка Docker образов
docker-compose build --no-cache

# Переустановка npm зависимостей
cd frontend/admin-ui
rm -rf node_modules
npm install
```

### Восстановление демо-данных

```bash
# После сброса БД — восстановить демо-маршруты и rate limits
docker exec -i gateway-postgres psql -U gateway -d gateway < scripts/seed-demo-data.sql

# Перезапустить gateway-core для синхронизации routes в Redis
docker restart gateway-core-dev
```

**Скрипт создаёт:**
- 3 rate limit политики (Standard, Premium, Burst)
- 8 маршрутов (3 published, 2 pending, 2 draft, 1 rejected)

### Docker конфигурация

**Файлы:**
- `docker-compose.yml` — инфраструктура (postgres, redis) + monitoring profile
- `docker-compose.override.yml` — dev apps с hot-reload (автоматически применяется)
- `docker-compose.override.yml.example` — шаблон для команды

**Для новых разработчиков:**
```bash
# Копировать шаблон (если override.yml отсутствует)
cp docker-compose.override.yml.example docker-compose.override.yml
```

**Примечание:** `docker-compose.override.yml` в `.gitignore` — каждый разработчик использует свою копию.

---

## Process Agreements (Командные соглашения)

### PA-01: Code Review completeness

**LOW issues исправляем сразу** — code review не approved пока все severity levels не resolved. Не откладываем LOW priority issues "на потом".

### PA-02: API Dependencies Checklist

При создании **UI story** проверять что backend API поддерживает все Acceptance Criteria:
- Все endpoints существуют
- Query параметры поддерживают все фильтры (включая multi-select)
- Response format содержит все нужные поля
- Role-based access настроен

### PA-03: Hotfix Reproduction Required

**Баг должен быть воспроизведён** перед созданием hotfix story:
- Задокументировать шаги воспроизведения
- Приложить screenshot или error log
- Не создавать story без подтверждённого воспроизведения

### PA-04: Action Items Review

SM проверяет `_bmad-output/implementation-artifacts/retro-actions.yaml` на каждом **Sprint Planning**:
- Выбрать 1-2 item для текущего спринта
- Включить в sprint backlog
- Обновить статусы после завершения

### PA-05: DTO Field Checklist

При добавлении нового поля в DTO:
- Проверить все методы Service, которые создают этот DTO
- Проверить все методы, которые обновляют этот DTO
- Проверить связанные Service классы (например, ApprovalService при изменении RouteResponse)

### PA-06: useEffect Cleanup

Для всех setInterval/setTimeout/subscriptions в React компонентах:
- Обязателен cleanup в return функции useEffect
- Пример:
```tsx
useEffect(() => {
  const interval = setInterval(() => { ... }, 1000)
  return () => clearInterval(interval)  // cleanup обязателен
}, [])
```

### PA-07: Docker Data Protection

**ЗАПРЕЩЕНО без явного указания пользователя:**

1. `docker-compose down -v` — удаляет все volumes (ПОТЕРЯ ДАННЫХ!)
2. `docker volume rm` — удаление отдельных volumes
3. `docker system prune` — очистка системы
4. Пересоздание контейнеров PostgreSQL/Redis с удалением данных

**Безопасные команды:**
```bash
docker-compose down      # OK — останавливает контейнеры, сохраняет данные
docker-compose restart   # OK — перезапуск без потери данных
docker-compose up -d     # OK — запуск/пересоздание контейнеров с сохранением volumes
```

**При необходимости сброса данных:**
- ВСЕГДА спрашивать пользователя: "Это удалит все данные в БД. Продолжить? (yes/no)"
- Дождаться явного подтверждения "yes"

### PA-08: Non-Breaking Changes (рекомендация)

**При модификации существующего функционала — предложить подход и дождаться подтверждения:**

1. Проверить что работает ДО изменений
2. Инкрементальные изменения — не удалять работающий код пока замена не протестирована
3. Сохранять fallback при миграции auth/API
4. Тестировать после каждого шага

**Формат предложения:**
```
📋 Рекомендую использовать non-breaking подход:
1. Сначала добавить новый функционал
2. Протестировать что работает
3. Затем удалить старый код

Использовать этот подход? (yes/no)
```

**Дождаться подтверждения пользователя перед началом работы.**

### PA-09: Migration Pre-flight Checklist

**Перед миграцией auth/API/DB выполнить:**

- [ ] Текущий функционал работает (проверено вручную)
- [ ] Feature flag добавлен и **выключен по умолчанию**
- [ ] Rollback plan задокументирован в story
- [ ] Данные забэкаплены или есть seed script
- [ ] Smoke test определён (минимальная проверка после изменений)

**Не начинать миграцию пока все пункты не выполнены.**

### PA-10: Dangerous Operations Confirmation

**Спрашивать явное подтверждение пользователя ПЕРЕД:**

1. Удалением файлов с production кодом (не тестов, не конфигов)
2. Изменением auth/security логики
3. Docker volume операциями (см. PA-07)
4. Database migrations с удалением данных
5. Удалением или заменой работающего API endpoint

**Формат запроса:**
```
⚠️ Планирую [описание действия].
Это может [описание риска].
Продолжить? (yes/no)
```

**Не выполнять без явного "yes" от пользователя.**

---

*Последнее обновление: 2026-02-23 (Story 12.2 Incident Post-Mortem — PA-09, PA-10)*
