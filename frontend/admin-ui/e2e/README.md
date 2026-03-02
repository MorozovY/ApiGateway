# E2E Tests — Admin UI

End-to-end тесты для API Gateway Admin UI.

## Принципы

1. **CI-first** — тесты разработаны для CI с первого дня
2. **Изоляция** — никаких реальных зависимостей (Keycloak, PostgreSQL)
3. **Простота** — нет условной логики `if (isCI)`
4. **Детерминизм** — одинаковый результат локально и в CI

## Структура

```
e2e/
├── fixtures/
│   ├── auth.fixture.ts     # Mock авторизация (admin токен)
│   └── api.fixture.ts      # Playwright route handlers для API
├── tests/
│   ├── 01-login.spec.ts        # P0: Login flow
│   ├── 02-dashboard.spec.ts    # P0: Dashboard
│   ├── 03-routes-list.spec.ts  # P0: Список маршрутов
│   ├── 04-routes-create.spec.ts# P0: Создание маршрута
│   ├── 05-routes-edit.spec.ts  # P1: Редактирование
│   ├── 06-routes-details.spec.ts # P1: Детали маршрута
│   ├── 07-approvals.spec.ts    # P1: Согласование
│   ├── 08-users.spec.ts        # P2: Пользователи
│   ├── 09-rate-limits.spec.ts  # P2: Rate limits
│   └── 10-audit.spec.ts        # P2: Audit logs
└── README.md
```

## Запуск

### Все тесты

```bash
cd frontend/admin-ui
npx playwright test
```

### Конкретный файл

```bash
npx playwright test e2e/tests/01-login.spec.ts
```

### С UI

```bash
npx playwright test --ui
```

### С браузером (headed)

```bash
npx playwright test --headed
```

## Fixtures

### auth.fixture.ts

Mock авторизация для тестов. Устанавливает токен admin в sessionStorage.

```typescript
import { setupMockAuth } from '../fixtures/auth.fixture'

test.beforeEach(async ({ page }) => {
  await setupMockAuth(page)
})
```

### api.fixture.ts

Playwright route handlers для мока backend API. Перехватывает все запросы к `/api/*`.

```typescript
import { setupMockApi } from '../fixtures/api.fixture'

test.beforeEach(async ({ page }) => {
  await setupMockApi(page)
})
```

Mock данные включают:
- `mockRoutes` — 4 маршрута разных статусов
- `mockUsers` — 3 пользователя (admin, developer, security)
- `mockRateLimits` — 3 политики rate limit
- `mockAuditLogs` — 3 записи аудита

## CI Integration

Тесты запускаются в GitLab CI с помощью job:

```yaml
e2e-tests:
  image: mcr.microsoft.com/playwright:v1.52.0
  script:
    - cd frontend/admin-ui
    - npm ci
    - npx playwright test --reporter=line
```

## Отладка

При падении теста Playwright сохраняет:
- Screenshot: `test-results/*/test-failed-*.png`
- Trace: `test-results/*/trace.zip`
- Error context: `test-results/*/error-context.md`

Посмотреть trace:

```bash
npx playwright show-trace test-results/.../trace.zip
```
