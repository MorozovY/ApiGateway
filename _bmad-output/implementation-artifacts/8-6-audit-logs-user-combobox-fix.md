# Story 8.6: Исправить комбобокс пользователей в Audit Logs

Status: done

## Story

As a **Security Specialist**,
I want the user filter dropdown in Audit Logs to show all users,
so that I can filter audit events by user.

## Acceptance Criteria

**AC1 — Dropdown пользователей заполняется всеми системными пользователями:**

**Given** пользователь с ролью security или admin находится на `/audit`
**When** страница загружается
**Then** dropdown "Пользователь" содержит список всех системных пользователей

**AC2 — Список пользователей отсортирован по алфавиту:**

**Given** пользователь открывает dropdown пользователей
**When** dropdown раскрывается
**Then** пользователи отображаются в алфавитном порядке по username

**AC3 — Фильтрация работает корректно:**

**Given** пользователь выбирает конкретного пользователя из dropdown
**When** фильтр применяется
**Then** таблица аудит-логов показывает только события этого пользователя

## Проблема

**Текущее состояние:**

Dropdown пользователей в AuditFilterBar вызывает API `/api/v1/users` через `fetchUsers()`:

```tsx
// AuditFilterBar.tsx:56-60
const { data: usersData } = useQuery({
  queryKey: ['users-for-filter'],
  queryFn: () => fetchUsers({ offset: 0, limit: 1000 }),
  staleTime: 5 * 60 * 1000,
})
```

**Проблема доступа:**

API `/api/v1/users` защищён аннотацией `@RequireRole(Role.ADMIN)`:

```kotlin
// UserController.kt:33-35
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(Role.ADMIN)
class UserController
```

Страница `/audit` доступна для ролей **security** и **admin** (проверка в AuditPage.tsx:53), но Security role НЕ МОЖЕТ получить список пользователей из `/api/v1/users` — получает HTTP 403.

**Результат:** Для Security роли dropdown пользователей пустой.

## Решение

Создать **минимальный endpoint** для получения списка пользователей (только id и username), доступный для ролей SECURITY и ADMIN:

```
GET /api/v1/users/options → UserOptionsResponse
```

**Почему новый endpoint, а не изменение существующего:**

1. **Минимальные привилегии** — Security не должен видеть email, role, isActive и другую информацию пользователей
2. **Разделение ответственности** — `/api/v1/users` для администрирования, `/api/v1/users/options` для фильтров
3. **Оптимизация** — минимальный payload (только id + username), без пагинации

## Tasks / Subtasks

- [x] Task 1: Backend — добавить endpoint `/api/v1/users/options` (AC1, AC2)
  - [x] Subtask 1.1: Создать DTO `UserOption` (id, username) и `UserOptionsResponse` (items)
  - [x] Subtask 1.2: Добавить метод `findAllActiveOrderByUsername()` в UserRepository
  - [x] Subtask 1.3: Добавить метод `getAllOptions()` в UserService
  - [x] Subtask 1.4: Добавить endpoint в UserController с `@RequireRole(Role.SECURITY)`
  - [x] Subtask 1.5: Добавить сортировку по username (ORDER BY username ASC)

- [x] Task 2: Frontend — обновить AuditFilterBar для использования нового API (AC1, AC3)
  - [x] Subtask 2.1: Добавить функцию `fetchUserOptions()` в usersApi.ts
  - [x] Subtask 2.2: Добавить тип `UserOption` и `UserOptionsResponse` в user.types.ts
  - [x] Subtask 2.3: Обновить useQuery в AuditFilterBar для использования нового endpoint
  - [x] Subtask 2.4: Типы автоматически экспортируются через `export * from './types/user.types'`

- [x] Task 3: Тесты
  - [x] Subtask 3.1: Backend — 6 integration тестов для `/api/v1/users/options` (Story8_6_GetUserOptions)
  - [x] Subtask 3.2: Frontend — обновить мок в AuditFilterBar.test.tsx
  - [x] Subtask 3.3: Все тесты проходят ✅

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/users/options` | GET | — | ❌ Требуется создать |
| `/api/v1/audit` | GET | `userId`, `action`, `entityType`, `dateFrom`, `dateTo` | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Endpoint `/api/v1/audit` существует и работает
- [x] Фильтрация по userId в audit работает
- [ ] Endpoint `/api/v1/users/options` → **ТРЕБУЕТСЯ СОЗДАТЬ**

## Dev Notes

### Backend Implementation

**Новые файлы:**

1. **DTO (gateway-admin/dto/UserOptionDto.kt):**
```kotlin
package com.company.gateway.admin.dto

import java.util.UUID

/**
 * Минимальные данные пользователя для dropdowns и фильтров.
 * Не содержит чувствительную информацию (email, role, isActive).
 */
data class UserOption(
    val id: UUID,
    val username: String
)

/**
 * Ответ со списком пользователей для фильтров.
 */
data class UserOptionsResponse(
    val items: List<UserOption>
)
```

2. **Repository method (UserRepository.kt):**
```kotlin
// Добавить в UserRepository интерфейс
@Query("SELECT id, username FROM users WHERE is_active = true ORDER BY username ASC")
fun findAllActiveOptions(): Flux<UserOption>
```

3. **Service method (UserService.kt):**
```kotlin
/**
 * Получение списка всех активных пользователей для dropdowns.
 * Возвращает только id и username, отсортированные по алфавиту.
 */
fun getAllOptions(): Mono<UserOptionsResponse> {
    return userRepository.findAllActiveOptions()
        .collectList()
        .map { UserOptionsResponse(it) }
}
```

4. **Controller endpoint (UserController.kt):**
```kotlin
/**
 * Получение списка пользователей для dropdowns и фильтров.
 *
 * GET /api/v1/users/options
 *
 * Доступен для security и admin ролей (для фильтров в audit logs).
 * Возвращает только id и username активных пользователей.
 */
@GetMapping("/options")
@RequireRole(Role.SECURITY) // Security и выше (SECURITY, ADMIN)
fun getUserOptions(): Mono<UserOptionsResponse> {
    return userService.getAllOptions()
}
```

**Важно:** Новый endpoint `@GetMapping("/options")` должен быть ВНУТРИ существующего `UserController`, НО с собственной аннотацией `@RequireRole(Role.SECURITY)` которая переопределяет класс-level `@RequireRole(Role.ADMIN)`.

### Frontend Implementation

1. **API function (usersApi.ts):**
```typescript
export interface UserOption {
  id: string
  username: string
}

export interface UserOptionsResponse {
  items: UserOption[]
}

/**
 * Получение списка пользователей для dropdowns (минимальные данные).
 * Доступен для security и admin ролей.
 *
 * GET /api/v1/users/options
 */
export async function fetchUserOptions(): Promise<UserOptionsResponse> {
  const response = await axios.get<UserOptionsResponse>('/api/v1/users/options')
  return response.data
}
```

2. **AuditFilterBar.tsx — обновить useQuery:**
```tsx
// Было:
import { fetchUsers } from '@features/users/api/usersApi'

const { data: usersData } = useQuery({
  queryKey: ['users-for-filter'],
  queryFn: () => fetchUsers({ offset: 0, limit: 1000 }),
  staleTime: 5 * 60 * 1000,
})

const userOptions = usersData?.items.map((user) => ({
  value: user.id,
  label: user.username,
})) || []

// Стало:
import { fetchUserOptions } from '@features/users/api/usersApi'

const { data: userOptionsData } = useQuery({
  queryKey: ['users-options'],
  queryFn: fetchUserOptions,
  staleTime: 5 * 60 * 1000,
})

const userOptions = userOptionsData?.items.map((user) => ({
  value: user.id,
  label: user.username,
})) || []
```

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| UserOptionDto.kt | `backend/gateway-admin/src/main/kotlin/.../dto/` | Новый файл |
| UserRepository.kt | `backend/gateway-admin/src/main/kotlin/.../repository/` | Добавить метод |
| UserService.kt | `backend/gateway-admin/src/main/kotlin/.../service/` | Добавить метод |
| UserController.kt | `backend/gateway-admin/src/main/kotlin/.../controller/` | Добавить endpoint |
| usersApi.ts | `frontend/admin-ui/src/features/users/api/` | Добавить функцию и типы |
| user.types.ts | `frontend/admin-ui/src/features/users/types/` | Добавить типы |
| AuditFilterBar.tsx | `frontend/admin-ui/src/features/audit/components/` | Обновить useQuery |
| AuditFilterBar.test.tsx | `frontend/admin-ui/src/features/audit/components/` | Обновить мок |
| UserControllerIntegrationTest.kt | `backend/gateway-admin/src/test/.../integration/` | Добавить тесты |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.6]
- [Source: frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx:56-66] — текущий useQuery
- [Source: backend/gateway-admin/src/main/kotlin/.../controller/UserController.kt:33-35] — @RequireRole(ADMIN)
- [Source: _bmad-output/implementation-artifacts/8-5-routes-search-path-upstream.md] — предыдущая story

### Тестовые команды

```bash
# Backend integration тесты
./gradlew :gateway-admin:test --tests "*UserController*"

# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- AuditFilterBar

# Все тесты
./gradlew test
cd frontend/admin-ui && npm run test:run
```

### Связанные stories

- Story 7.5 (Audit Log UI) — создание страницы /audit с фильтрами
- Story 2.6 (User Management for Admin) — API /api/v1/users
- Story 8.5 (Routes Search) — предыдущая story в Epic 8

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- ✅ Backend: Создан новый endpoint `GET /api/v1/users/options` с `@RequireRole(Role.SECURITY)`
- ✅ Backend: DTO `UserOption` и `UserOptionsResponse` в UserOptionDto.kt
- ✅ Backend: Метод `findAllActiveOrderByUsername()` в UserRepository (ORDER BY username ASC)
- ✅ Backend: Метод `getAllOptions()` в UserService
- ✅ Backend: 6 integration тестов в Story8_6_GetUserOptions (admin/security доступ, developer 403, сортировка, только активные)
- ✅ Frontend: Функция `fetchUserOptions()` в usersApi.ts
- ✅ Frontend: Типы `UserOption`, `UserOptionsResponse` в user.types.ts
- ✅ Frontend: AuditFilterBar использует новый endpoint `/api/v1/users/options`
- ✅ Frontend: Обновлён мок в AuditFilterBar.test.tsx
- ✅ Все тесты проходят: Backend (BUILD SUCCESSFUL), Frontend (7 тестов passed)

### File List

**Created:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UserOptionDto.kt`

**Modified:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/UserRepository.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/UserService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/UserController.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/UserControllerIntegrationTest.kt`
- `frontend/admin-ui/src/features/users/api/usersApi.ts`
- `frontend/admin-ui/src/features/users/types/user.types.ts`
- `frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx`
- `frontend/admin-ui/src/features/audit/components/AuditFilterBar.test.tsx`

## Change Log

- 2026-02-21: Story 8.6 implemented — new endpoint `/api/v1/users/options` for user dropdown in audit logs filters
