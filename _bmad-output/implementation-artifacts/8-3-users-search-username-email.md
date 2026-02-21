# Story 8.3: Поиск пользователей по username и email

Status: done

## Story

As an **Admin**,
I want a single search field on Users page that filters by username and email,
so that I can quickly find users.

## Acceptance Criteria

**AC1 — Поле поиска отображается:**

**Given** admin переходит на `/users`
**When** страница загружается
**Then** поле поиска (Input.Search) отображается над таблицей

**AC2 — Поиск по username и email работает:**

**Given** admin вводит "john" в поле поиска
**When** input debounced (300ms)
**Then** таблица показывает пользователей где username ИЛИ email содержит "john"
**And** поиск case-insensitive

**AC3 — Очистка поиска:**

**Given** admin очищает поле поиска
**When** поле пустое
**Then** все пользователи отображаются (без фильтрации)

**AC4 — Backend API поддерживает search:**

**Given** GET `/api/v1/users?search=john`
**When** API обрабатывает запрос
**Then** возвращаются пользователи где username ИЛИ email содержит "john" (ILIKE)

## Tasks / Subtasks

- [x] Task 1: Backend — добавить параметр search в UserController (AC4)
  - [x] Subtask 1.1: Добавить query param `search` в `listUsers()`
  - [x] Subtask 1.2: Обновить UserService.findAll() для фильтрации
  - [x] Subtask 1.3: Добавить query в UserRepository для поиска по username/email

- [x] Task 2: Frontend — добавить поле поиска на UsersPage (AC1, AC2, AC3)
  - [x] Subtask 2.1: Добавить `search` параметр в UserListParams типы
  - [x] Subtask 2.2: Обновить usersApi.fetchUsers() для передачи search
  - [x] Subtask 2.3: Добавить Input.Search в UsersPage.tsx над таблицей
  - [x] Subtask 2.4: Добавить debounced состояние поиска
  - [x] Subtask 2.5: Передавать search в UsersTable для использования в useUsers

- [x] Task 3: Тесты
  - [x] Subtask 3.1: Unit тест UserController с search параметром
  - [x] Subtask 3.2: Unit тест UsersPage — поиск работает
  - [x] Subtask 3.3: Запустить все тесты

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/users` | GET | `offset`, `limit`, `search` | ✅ Все параметры реализованы |

**Проверки после разработки:**

- [x] `search` параметр существует в backend → **Добавлено**
- [x] Query фильтрует по username OR email (ILIKE) → **Добавлено**
- [x] Response format не меняется → ✅ UserListResponse остаётся тем же
- [x] Role-based access — только ADMIN → ✅ Уже настроено

## Dev Notes

### Backend изменения

**UserController.kt** — добавить search параметр:
```kotlin
@GetMapping
fun listUsers(
    @RequestParam(defaultValue = "0") offset: Int,
    @RequestParam(defaultValue = "20") limit: Int,
    @RequestParam(required = false) search: String?  // ← добавить
): Mono<UserListResponse> {
    return userService.findAll(offset, limit, search)
}
```

**UserService.kt** — обновить findAll():
```kotlin
fun findAll(offset: Int, limit: Int, search: String?): Mono<UserListResponse> {
    // Если search задан — использовать поиск по username/email
    // Иначе — использовать обычную пагинацию
}
```

**UserRepository.kt** — добавить query для поиска:
```kotlin
@Query("""
    SELECT * FROM users
    WHERE (username ILIKE :searchPattern OR email ILIKE :searchPattern)
    ORDER BY created_at ASC
    LIMIT :limit OFFSET :offset
""")
fun searchUsers(searchPattern: String, limit: Int, offset: Int): Flux<User>

@Query("""
    SELECT COUNT(*) FROM users
    WHERE (username ILIKE :searchPattern OR email ILIKE :searchPattern)
""")
fun countBySearch(searchPattern: String): Mono<Long>
```

**Формат searchPattern:** `%${search}%` — конструируется в сервисе.

### Frontend изменения

**user.types.ts:**
```typescript
export interface UserListParams {
  offset?: number
  limit?: number
  search?: string  // ← добавить
}
```

**usersApi.ts:**
```typescript
export async function fetchUsers(params: UserListParams = {}): Promise<UserListResponse> {
  const { offset = 0, limit = 20, search } = params
  const response = await axios.get<UserListResponse>('/api/v1/users', {
    params: { offset, limit, search: search || undefined },  // ← не отправлять пустую строку
  })
  return response.data
}
```

**UsersPage.tsx — добавить поиск:**
```tsx
import { Input } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { useState, useDeferredValue } from 'react'

function UsersPage() {
  const [searchValue, setSearchValue] = useState('')
  const deferredSearch = useDeferredValue(searchValue)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          Users
        </Title>
        <Space>
          <Input
            placeholder="Поиск по username или email"
            prefix={<SearchOutlined />}
            allowClear
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            style={{ width: 250 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            Add User
          </Button>
        </Space>
      </div>

      <UsersTable onEdit={handleEdit} search={deferredSearch} />
      ...
    </div>
  )
}
```

**UsersTable.tsx — принять search prop:**
```tsx
interface UsersTableProps {
  onEdit: (user: User) => void
  search?: string  // ← добавить
}

function UsersTable({ onEdit, search }: UsersTableProps) {
  // ...
  const { data, isLoading } = useUsers({
    offset,
    limit: pagination.pageSize,
    search,  // ← передавать в hook
  })
  // ...
}
```

### Паттерн debounce

Используем `useDeferredValue` (React 18+) вместо custom debounce hook:
- Проще, встроенный в React
- Автоматически откладывает обновление до idle
- Не блокирует UI при быстром вводе

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| UserController.kt | `backend/gateway-admin/src/main/kotlin/.../controller/` | Добавить `search` param |
| UserService.kt | `backend/gateway-admin/src/main/kotlin/.../service/` | Обновить `findAll()` |
| UserRepository.kt | `backend/gateway-admin/src/main/kotlin/.../repository/` | Добавить search queries |
| user.types.ts | `frontend/admin-ui/src/features/users/types/` | Добавить `search` в params |
| usersApi.ts | `frontend/admin-ui/src/features/users/api/` | Передавать `search` |
| UsersPage.tsx | `frontend/admin-ui/src/features/users/components/` | Добавить Input.Search |
| UsersTable.tsx | `frontend/admin-ui/src/features/users/components/` | Принять `search` prop |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.3]
- [Source: backend/gateway-admin/src/main/kotlin/.../controller/UserController.kt] — текущий контроллер
- [Source: backend/gateway-admin/src/main/kotlin/.../service/UserService.kt] — текущий сервис
- [Source: frontend/admin-ui/src/features/users/components/UsersPage.tsx] — текущая страница
- [Source: frontend/admin-ui/src/features/users/components/UsersTable.tsx] — текущая таблица
- [Source: _bmad-output/planning-artifacts/architecture.md#Naming Patterns] — snake_case для DB, camelCase для JSON

### Тестовые команды

```bash
# Backend unit тесты
./gradlew :gateway-admin:test --tests "*UserController*"
./gradlew :gateway-admin:test --tests "*UserService*"

# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- UsersPage
npm run test:run -- UsersTable

# Все тесты
./gradlew test
cd frontend/admin-ui && npm run test:run
```

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- ✅ **Task 1 (AC4):** Backend API обновлён — добавлен параметр `search` в UserController.listUsers(), UserService.findAll() теперь поддерживает поиск по username/email с ILIKE, UserRepository содержит query searchUsers() и countBySearch().
- ✅ **Task 2 (AC1, AC2, AC3):** Frontend обновлён — UserListParams содержит search, usersApi передаёт search в API, UsersPage.tsx содержит Input.Search с useDeferredValue для debounce, UsersTable принимает search prop и использует его в useUsers hook.
- ✅ **Task 3:** Тесты добавлены — 7 новых integration тестов в UserControllerIntegrationTest для поиска (search по username, email, case-insensitive, пагинация с поиском, пустые результаты, пустой search), 3 новых unit теста в UsersPage.test.tsx (поле поиска рендерится, ввод текста вызывает API с search, очистка сбрасывает search).
- ✅ Все тесты проходят: Backend BUILD SUCCESSFUL, Frontend 365 tests passed.

**Code Review Fixes (2026-02-21):**
- ✅ **MEDIUM-1:** Добавлен useEffect для сброса пагинации при изменении search (UsersTable.tsx)
- ✅ **MEDIUM-2:** Обновлён комментарий про useDeferredValue (UsersPage.tsx)
- ✅ **MEDIUM-3:** Обновлён API Dependencies Checklist в story file
- ✅ **MEDIUM-4:** Исправлен тест очистки поля поиска (UsersPage.test.tsx)
- ✅ **MEDIUM-5:** Убрана неиспользуемая переменная в backend тесте
- ✅ **LOW-1/2:** Обновлены комментарии и JSDoc в UsersTable.tsx
- ✅ **LOW-4:** Обновлён комментарий в usersApi.ts

### File List

- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/UserController.kt` — добавлен search параметр
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/UserService.kt` — обновлён findAll() для поиска
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/UserRepository.kt` — добавлены searchUsers() и countBySearch()
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/UserControllerIntegrationTest.kt` — добавлены тесты для search
- `frontend/admin-ui/src/features/users/types/user.types.ts` — добавлен search в UserListParams
- `frontend/admin-ui/src/features/users/api/usersApi.ts` — передаётся search в API
- `frontend/admin-ui/src/features/users/components/UsersPage.tsx` — добавлено поле поиска с useDeferredValue
- `frontend/admin-ui/src/features/users/components/UsersTable.tsx` — принимает search prop
- `frontend/admin-ui/src/features/users/components/UsersPage.test.tsx` — добавлены тесты для поиска

### Change Log

- 2026-02-21: Story 8.3 implemented — Users search by username/email (backend + frontend)
- 2026-02-21: Code review completed — 5 MEDIUM + 4 LOW issues fixed, status → done

