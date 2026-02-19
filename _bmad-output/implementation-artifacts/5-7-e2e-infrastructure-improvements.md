# Story 5.7: E2E Infrastructure Improvements

Status: ready-for-dev

## Story

As a **QA Engineer**,
I want a clean and reliable E2E test environment,
so that tests run consistently without interference from stale data.

## Problem Statement

Текущие проблемы E2E тестирования:
1. **Замусоривание БД** — каждый прогон оставляет тестовые данные (политики, маршруты), что приводит к конфликтам и flaky тестам
2. **Тесты путаются в данных** — при большом количестве записей тесты не могут найти свои данные среди чужих
3. **Отсутствие документации команд** — разработчик/агент тратит время на поиск правильных команд для запуска/рестарта сервисов

## Acceptance Criteria

**AC1 — Очистка тестовой среды:**

**Given** E2E тесты готовы к запуску
**When** запускается `npx playwright test`
**Then** global-setup.ts очищает тестовые данные перед прогоном
**And** только системные данные (admin user) сохраняются

**AC2 — Фильтрация в тестах:**

**Given** Тест создал данные с префиксом `e2e-{timestamp}`
**When** тест ищет свои данные в UI таблицах
**Then** тест использует фильтры/поиск для изоляции своих данных
**And** тест не путается с данными других тестов

**AC3 — Документация команд:**

**Given** Разработчик или AI агент работает над проектом
**When** нужно запустить/перезапустить сервисы
**Then** команды доступны в CLAUDE.md секции "Development Commands"
**And** не требуется искать команды каждый раз

## Tasks / Subtasks

- [ ] Task 1: Database cleanup в global-setup.ts (AC1)
  - [ ] Создать SQL скрипт очистки тестовых данных
  - [ ] Добавить cleanup в `e2e/global-setup.ts` перед созданием тестовых пользователей
  - [ ] Сохранить системные данные (admin, базовые роли)
  - [ ] Протестировать что cleanup работает

- [ ] Task 2: Фильтрация в E2E тестах (AC2)
  - [ ] Добавить helper функцию для фильтрации таблиц по имени
  - [ ] Обновить существующие тесты использовать фильтры перед поиском
  - [ ] Убедиться что все тесты используют `e2e-{TIMESTAMP}` префикс

- [ ] Task 3: Документация команд в CLAUDE.md (AC3)
  - [ ] Добавить секцию "Development Commands" в CLAUDE.md
  - [ ] Документировать: запуск Docker, backend, frontend
  - [ ] Документировать: запуск E2E тестов
  - [ ] Документировать: очистка/сброс среды

## Dev Notes

### AC1: Database Cleanup Strategy

**Таблицы для очистки (в правильном порядке из-за FK):**
```sql
-- Очистка в порядке зависимостей
DELETE FROM audit_logs WHERE created_by IN (SELECT id FROM users WHERE username LIKE 'test-%');
DELETE FROM route_versions WHERE route_id IN (SELECT id FROM routes WHERE path LIKE '/e2e-%');
DELETE FROM routes WHERE path LIKE '/e2e-%';
DELETE FROM rate_limit_policies WHERE name LIKE 'e2e-%';
-- НЕ удаляем: users (тестовые пользователи создаются в global-setup)
```

**Интеграция в global-setup.ts:**
```typescript
// e2e/global-setup.ts
import { Pool } from 'pg'

async function cleanupTestData() {
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/gateway'
  })

  try {
    await pool.query(`DELETE FROM routes WHERE path LIKE '/e2e-%'`)
    await pool.query(`DELETE FROM rate_limit_policies WHERE name LIKE 'e2e-%'`)
    console.log('✓ Test data cleaned up')
  } finally {
    await pool.end()
  }
}

export default async function globalSetup() {
  await cleanupTestData()  // Сначала очистка
  await createTestUsers()   // Потом создание пользователей
}
```

### AC2: Filter Helper Pattern

**Helper для фильтрации таблиц:**
```typescript
// e2e/helpers/table.ts
export async function filterTableByName(page: Page, searchText: string) {
  const searchInput = page.locator('input[placeholder*="Search"], input[placeholder*="Поиск"]')
  if (await searchInput.isVisible()) {
    await searchInput.fill(searchText)
    await page.waitForTimeout(500) // debounce
  }
}

// Использование в тесте
await filterTableByName(page, `e2e-policy-${TIMESTAMP}`)
await expect(page.locator(`tr:has-text("e2e-policy-${TIMESTAMP}")`)).toBeVisible()
```

**Проверить наличие фильтров в UI:**
- `/rate-limits` — есть ли поиск?
- `/routes` — есть ли фильтр по path?
- `/approvals` — есть ли фильтр?

### AC3: CLAUDE.md Commands Section

**Предлагаемая структура:**
```markdown
## Development Commands

### Запуск инфраструктуры
\`\`\`bash
# Docker (PostgreSQL, Redis)
docker-compose up -d

# Проверка статуса
docker-compose ps
\`\`\`

### Запуск backend
\`\`\`bash
# Gateway Admin (port 8081)
./gradlew :gateway-admin:bootRun

# Gateway Core (port 8080)
./gradlew :gateway-core:bootRun
\`\`\`

### Запуск frontend
\`\`\`bash
cd frontend/admin-ui
npm run dev  # port 3000
\`\`\`

### E2E тесты
\`\`\`bash
cd frontend/admin-ui
npx playwright test                    # все тесты
npx playwright test e2e/epic-5.spec.ts # конкретный файл
npx playwright test --ui               # UI режим
\`\`\`

### Полный рестарт
\`\`\`bash
# Остановить всё
docker-compose down
pkill -f "bootRun" || true

# Запустить заново
docker-compose up -d
./gradlew :gateway-admin:bootRun &
./gradlew :gateway-core:bootRun &
cd frontend/admin-ui && npm run dev
\`\`\`
```

## References

- [Source: frontend/admin-ui/e2e/global-setup.ts] — текущий global setup
- [Source: frontend/admin-ui/playwright.config.ts] — конфигурация Playwright
- [Source: CLAUDE.md] — правила проекта
