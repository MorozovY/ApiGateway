# Story 10.6: Swagger Links on Login Page

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want quick access to API documentation from the login page,
so that I can explore the API before logging in.

## Feature Context

**Source:** Epic 9 Retrospective (2026-02-22) — FR-03 feedback from Yury (Project Lead)

**Business Value:** Разработчики и интеграторы должны иметь возможность изучить API документацию до логина. Это особенно важно для новых пользователей, которые хотят понять возможности API Gateway перед началом работы.

**Текущее состояние:**
- Swagger UI уже доступен по `/swagger-ui.html` (или `/api/v1/swagger-ui.html` через Nginx)
- Login page содержит форму входа и секцию Demo Credentials
- Swagger доступен без аутентификации (permitAll в SecurityConfig)

**Что нужно добавить:** Секция со ссылками на API документацию под формой входа.

## Acceptance Criteria

### AC1: Swagger links displayed on login page
**Given** user is on `/login` page
**When** page loads
**Then** links to Swagger UI are displayed:
- Gateway Admin API: `/swagger-ui.html`
- Label clearly identifies this as API Documentation

### AC2: Links open in new tab
**Given** user clicks Swagger link
**When** link is activated
**Then** Swagger UI opens in new tab (`target="_blank"`)
**And** link has `rel="noopener noreferrer"` for security

### AC3: Links positioned appropriately
**Given** login page displays all elements
**When** page renders
**Then** Swagger links appear AFTER Demo Credentials section
**And** section has clear visual separation (divider)

## Analysis Summary

### Swagger UI Endpoint: ✅ ALREADY EXISTS

Из `backend/gateway-admin/src/main/resources/application.yml:60-61`:
```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

**SecurityConfig.kt** разрешает доступ без аутентификации:
```kotlin
.pathMatchers("/swagger-ui/**").permitAll()
.pathMatchers("/swagger-ui.html").permitAll()
```

### Frontend Changes Required

**Создать новый компонент ApiDocsLinks.tsx:**

```typescript
// Ссылки на API документацию для страницы логина (Story 10.6)
import { Typography, Divider, Space } from 'antd'
import { FileTextOutlined } from '@ant-design/icons'

const { Text, Link } = Typography

/**
 * Ссылки на API документацию (Swagger UI) на странице логина.
 *
 * AC1: Отображает ссылку на Swagger UI для Gateway Admin API.
 * AC2: Ссылки открываются в новой вкладке.
 * AC3: Визуально отделены от Demo Credentials.
 */
export function ApiDocsLinks() {
  return (
    <div style={{ marginTop: 24 }} data-testid="api-docs-links">
      <Divider style={{ margin: '16px 0' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>📚 API документация</Text>
      </Divider>

      <Space direction="vertical" size={4} style={{ width: '100%', textAlign: 'center' }}>
        <Link
          href="/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="swagger-link"
        >
          <FileTextOutlined /> Gateway Admin API (Swagger)
        </Link>
      </Space>
    </div>
  )
}
```

**Обновить LoginForm.tsx:**

```typescript
// Добавить import
import { ApiDocsLinks } from './ApiDocsLinks'

// Добавить после DemoCredentials
<DemoCredentials onSelect={handleDemoSelect} />
<ApiDocsLinks />
```

## Tasks / Subtasks

- [x] Task 1: Create ApiDocsLinks component (AC: #1, #2, #3)
  - [x] 1.1 Create `frontend/admin-ui/src/features/auth/components/ApiDocsLinks.tsx`
  - [x] 1.2 Add Divider with "📚 API документация" header
  - [x] 1.3 Add Swagger link with `target="_blank"` and `rel="noopener noreferrer"`
  - [x] 1.4 Use `FileTextOutlined` icon from Ant Design

- [x] Task 2: Integrate ApiDocsLinks into LoginForm (AC: #3)
  - [x] 2.1 Import ApiDocsLinks in LoginForm.tsx
  - [x] 2.2 Add `<ApiDocsLinks />` after DemoCredentials component

- [x] Task 3: Unit tests for ApiDocsLinks component (AC: #1, #2, #3)
  - [x] 3.1 Test: `отображает ссылку на Swagger UI`
  - [x] 3.2 Test: `ссылка открывается в новой вкладке`
  - [x] 3.3 Test: `ссылка имеет корректный URL /swagger-ui.html`

- [x] Task 4: Integration test — LoginForm renders ApiDocsLinks (AC: #3)
  - [x] 4.1 Add test in LoginForm.test.tsx: `отображает ссылки на API документацию`

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/swagger-ui.html` | GET | - | ✅ Существует (springdoc) |
| `/swagger-ui/**` | GET | - | ✅ Существует (springdoc assets) |

**Проверки перед началом разработки:**

- [x] Swagger UI endpoint существует и работает
- [x] Swagger доступен без аутентификации (permitAll)
- [x] LoginForm уже импортирует DemoCredentials — тот же паттерн

**Если endpoint отсутствует или неполный:**
- N/A — все endpoints существуют

## Dev Notes

### Архитектура решения

**Минимальные изменения — следуем существующему паттерну DemoCredentials:**

| Компонент | Изменение |
|-----------|-----------|
| ApiDocsLinks.tsx | Новый компонент (по образцу DemoCredentials) |
| LoginForm.tsx | +1 import, +1 JSX строка |
| ApiDocsLinks.test.tsx | Новый файл (3 теста) |
| LoginForm.test.tsx | +1 тест для проверки наличия ApiDocsLinks |

### Паттерн из существующего кода

**DemoCredentials.tsx — шаблон для ApiDocsLinks:**

```typescript
// DemoCredentials: divider + content structure
<div style={{ marginTop: 32 }} data-testid="demo-credentials-card">
  <Divider style={{ margin: '16px 0' }}>
    <Text type="secondary" style={{ fontSize: 12 }}>🔐 Демо-доступ</Text>
  </Divider>
  {/* Content */}
</div>
```

ApiDocsLinks использует тот же паттерн: `div` → `Divider` → content.

### Swagger URL в разных окружениях

| Окружение | URL |
|-----------|-----|
| Local dev | `http://localhost:8081/swagger-ui.html` |
| Docker (через Nginx) | `/swagger-ui.html` (Nginx проксирует к gateway-admin) |
| Production | `http://gateway.ymorozov.ru/swagger-ui.html` |

**Важно:** Используем относительный путь `/swagger-ui.html` — Nginx корректно проксирует.

### Project Structure Notes

- Новый файл в существующей директории: `features/auth/components/`
- Следуем naming convention: `ApiDocsLinks.tsx`, `ApiDocsLinks.test.tsx`
- Компонент экспортируется как named export (не default)

### References

- [Source: backend/gateway-admin/src/main/resources/application.yml:60-61] — swagger-ui path config
- [Source: backend/gateway-admin/src/main/kotlin/.../config/SecurityConfig.kt:34-35] — permitAll для swagger
- [Source: frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx] — паттерн компонента
- [Source: frontend/admin-ui/src/features/auth/components/LoginForm.tsx:97] — место интеграции
- [Source: README.md:87] — Swagger UI URL documentation
- [Source: epics.md#Story 10.6] — acceptance criteria

## Previous Story Learnings (10.5)

**Из Story 10.5 (Nginx Health Check):**

1. **Минимальный scope** — не добавлять лишние изменения
2. **Тесты на русском языке** — все названия тестов на русском
3. **517 frontend тестов** проходят — не сломать существующие
4. **Паттерн коммитов:** `feat: Story 10.X — краткое описание + code review fixes`

**Применимо к текущей story:**
- Следовать паттерну DemoCredentials для consistency
- Использовать русские названия тестов
- Минимальный scope — только ссылки на Swagger

## Git Intelligence (последние коммиты)

```
802c17d fix: Health Check layout — all 7 services in one row
715d2f2 docs: Story 10.2 — Manual validation passed, status done
132f8e5 feat: Story 10.5 — Nginx health check on Metrics page + code review fixes
d0fc778 feat: Story 10.4 — Author can delete own draft route + code review fixes
ec2d249 docs: Epic 8 and Epic 9 retrospectives
```

**Паттерн коммитов Epic 10:**
- Prefix: `feat:` для новых features
- Format: `feat: Story 10.X — краткое описание + code review fixes`

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — реализация прошла без проблем

### Completion Notes List

- **2026-02-22:** Реализован компонент ApiDocsLinks.tsx по паттерну DemoCredentials
- Добавлены 6 unit-тестов в ApiDocsLinks.test.tsx (все на русском языке)
- Интегрирован в LoginForm.tsx после DemoCredentials секции
- Добавлен 1 интеграционный тест в LoginForm.test.tsx
- Все 525 frontend тестов проходят (добавлено 7 новых)
- AC1: Ссылка на Swagger UI отображается с иконкой FileTextOutlined
- AC2: Ссылка открывается в новой вкладке (target="_blank", rel="noopener noreferrer")
- AC3: Секция расположена после DemoCredentials с Divider разделителем
- **FIX:** Добавлены location в nginx.conf для проксирования swagger на gateway-admin:
  - `/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs`, `/webjars/`

### File List

- frontend/admin-ui/src/features/auth/components/ApiDocsLinks.tsx (new)
- frontend/admin-ui/src/features/auth/components/ApiDocsLinks.test.tsx (new)
- frontend/admin-ui/src/features/auth/components/LoginForm.tsx (modified)
- frontend/admin-ui/src/features/auth/components/LoginForm.test.tsx (modified)
- docker/nginx/nginx.conf (modified) — добавлены location для swagger-ui проксирования

## Change Log

- **2026-02-22:** Story 10.6 implemented — Swagger links on login page (AC1, AC2, AC3 complete)
