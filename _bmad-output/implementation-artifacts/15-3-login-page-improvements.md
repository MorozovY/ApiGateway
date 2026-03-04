# Story 15.3: Улучшения страницы входа

Status: done

## Story

As a **Security Engineer**,
I want demo accounts to have secure generated passwords and logical ordering,
So that the login page looks professional and follows security best practices.

## Корень проблемы

**Story 9.5** добавила демо-credentials на страницу входа с простыми паролями.
Текущее состояние содержит несколько проблем:

| Компонент | Текущее значение | Проблема |
|-----------|-----------------|----------|
| DemoCredentials.tsx | `developer → security → admin` | ❌ Порядок не соответствует иерархии |
| DemoCredentials.tsx | `developer123, security123, admin123` | ❌ Простые пароли, не security best practice |
| realm-export.json | `dev123` для developer | ❌ **Несоответствие UI и Keycloak!** |
| UserService.kt | `developer123` для developer | ✅ Соответствует UI |

**Критическая проблема:** Пароль developer в Keycloak (`dev123`) не совпадает с отображаемым на UI (`developer123`).

## Acceptance Criteria

### AC1: Порядок логинов соответствует иерархии ролей
**Given** страница входа с демо-аккаунтами
**When** пользователь просматривает список логинов
**Then** порядок отображения: admin → security → developer
**And** порядок соответствует иерархии ролей (от высшей к низшей)

### AC2: Пароли содержат минимум 12 символов
**Given** демо-аккаунты
**When** проверяются пароли
**Then** каждый аккаунт имеет уникальный сгенерированный пароль
**And** пароли содержат минимум 12 символов

### AC3: Пароли содержат буквы, цифры и спецсимволы
**Given** демо-аккаунты
**When** анализируется состав пароля
**Then** пароли содержат буквы верхнего и нижнего регистра
**And** пароли содержат цифры
**And** пароли содержат спецсимволы

### AC4: Seed script и Keycloak соответствуют UI
**Given** seed script для сброса данных и realm-export.json
**When** скрипт выполняется / realm импортируется
**Then** пароли демо-аккаунтов соответствуют отображаемым на UI

### AC5: Документация актуальна
**Given** документация (CLAUDE.md, README)
**When** проверяется актуальность
**Then** новые пароли задокументированы в соответствующих файлах

## Предлагаемые пароли

Пароли должны быть:
- Уникальными для каждой роли
- Минимум 12 символов
- Содержать буквы, цифры и спецсимволы
- Легко читаемыми и копируемыми

**Рекомендуемый формат:** `{Role}Pass!2026#` или подобный

| Role | Username | Предлагаемый пароль |
|------|----------|---------------------|
| Admin | admin | `Admin@Pass!2026` |
| Security | security | `Secure#Pass2026` |
| Developer | developer | `Dev!Pass#2026x` |

## Tasks / Subtasks

- [x] Task 1: Обновить порядок и пароли в DemoCredentials.tsx (AC: #1, #2, #3)
  - [x] 1.1 Изменить порядок в `DEMO_CREDENTIALS`: admin → security → developer
  - [x] 1.2 Обновить пароли на сгенерированные сложные
  - [x] 1.3 Обновить тесты DemoCredentials.test.tsx

- [x] Task 2: Синхронизировать Keycloak realm-export.json (AC: #4)
  - [x] 2.1 Обновить пароли для admin, security, developer
  - [x] 2.2 Убедиться что пароли соответствуют UI

- [x] Task 3: Синхронизировать UserService.kt (AC: #4)
  - [x] 3.1 Обновить пароли в demoUsers map
  - [x] 3.2 Убедиться что порядок не влияет на функциональность

- [x] Task 4: Опционально — обновить документацию (AC: #5)
  - [x] 4.1 Проверить CLAUDE.md на упоминания демо-паролей
  - [x] 4.2 Обновить README.md — таблица тестовых пользователей обновлена

## Dev Notes

### Компоненты для изменения

| Файл | Изменение |
|------|-----------|
| `frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx` | Порядок + пароли |
| `frontend/admin-ui/src/features/auth/components/DemoCredentials.test.tsx` | Обновить ожидаемые значения |
| `docker/keycloak/realm-export.json` | Пароли users |
| `backend/gateway-admin/src/main/kotlin/.../service/UserService.kt` | demoUsers map |

### Важно: Keycloak vs Local DB

При `keycloak.enabled=true` (production) используется Keycloak для аутентификации.
При `keycloak.enabled=false` (legacy) используется локальная БД.

Оба источника должны иметь согласованные пароли для корректной работы кнопки "Сбросить пароли".

### Текущий код DemoCredentials.tsx

```tsx
const DEMO_CREDENTIALS = [
  { username: 'developer', password: 'developer123', role: 'Developer', ... },
  { username: 'security', password: 'security123', role: 'Security', ... },
  { username: 'admin', password: 'admin123', role: 'Admin', ... },
]
```

### Текущий код UserService.kt

```kotlin
val demoUsers = mapOf(
    "developer" to DemoUser("developer123", Role.DEVELOPER, ...),
    "security" to DemoUser("security123", Role.SECURITY, ...),
    "admin" to DemoUser("admin123", Role.ADMIN, ...)
)
```

### Текущий Keycloak realm-export.json

```json
{ "username": "admin", "credentials": [{ "value": "admin123" }] }
{ "username": "developer", "credentials": [{ "value": "dev123" }] }  // ⚠️ НЕ СОВПАДАЕТ!
{ "username": "security", "credentials": [{ "value": "security123" }] }
```

### Testing Standards

- Unit тесты на DemoCredentials компонент
- Проверка порядка отображения
- Проверка паролей
- E2E тест на логин с новыми credentials опционален (существующие тесты покрывают)

### References

- [Source: frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx] — UI компонент
- [Source: docker/keycloak/realm-export.json:238-290] — Keycloak users
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/UserService.kt:456-470] — resetDemoPasswords
- [Source: Story 9.5] — оригинальная реализация демо-credentials

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/auth/reset-demo-passwords` | POST | - | ✅ Существует |
| `/api/v1/auth/login` | POST | username, password | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Все необходимые endpoints существуют в backend
- [x] API reset-demo-passwords обновит пароли автоматически
- [x] Login будет работать с новыми паролями после сброса

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — имплементация прошла без ошибок.

### Completion Notes List

1. **Task 1** (DemoCredentials.tsx): Изменён порядок credentials на admin → security → developer, обновлены пароли на Admin@Pass!2026, Secure#Pass2026, Dev!Pass#2026x. Unit-тесты обновлены — все 12 тестов прошли.

2. **Task 2** (realm-export.json): Обновлены пароли для всех трёх демо-пользователей в Keycloak конфиге. Исправлена критическая проблема — пароль developer теперь соответствует UI (был dev123, стал Dev!Pass#2026x).

3. **Task 3** (UserService.kt): Обновлен demoUsers map с новыми паролями. Обновлён комментарий KDoc. Backend integration тесты обновлены и прошли (все 32 теста AuthControllerIntegrationTest).

4. **Task 4** (Документация): README.md — обновлена таблица тестовых пользователей. CLAUDE.md не содержит демо-паролей — изменения не требуются.

### File List

**Modified:**
- `frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx`
- `frontend/admin-ui/src/features/auth/components/DemoCredentials.test.tsx`
- `docker/keycloak/realm-export.json`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/UserService.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuthControllerIntegrationTest.kt`
- `README.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

| Дата | Изменение |
|------|-----------|
| 2026-03-04 | Story 15.3 реализована: порядок демо-аккаунтов admin→security→developer, новые пароли с 12+ символов и спецсимволами, синхронизированы UI/Keycloak/Backend |
| 2026-03-04 | Code Review: исправлен порядок пользователей в README.md (admin→security→developer). LOW issues noted: тест порядка можно улучшить, email developer различается в Keycloak/UserService |
