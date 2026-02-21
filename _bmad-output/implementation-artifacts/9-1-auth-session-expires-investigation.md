# Story 9.1: Auth Session Expires Investigation

Status: review

## Story

As a **User**,
I want my session to remain active while I'm using the application,
so that I don't get unexpectedly logged out.

## Bug Report

- **Severity:** HIGH
- **Observed:** Under admin role, application randomly requires re-login
- **Reproduction:** Нестабильный — происходит в произвольные моменты, при перезагрузке страницы
- **Affected:** Все роли (admin, developer, security)

## Root Cause Analysis

### Проблема 1: Нет восстановления сессии при перезагрузке

**Текущее состояние:**
- JWT токен хранится в HTTP-only cookie (правильно)
- При перезагрузке страницы `AuthContext` инициализируется с `user: null`
- **Нет endpoint `/api/v1/auth/me`** для получения текущего пользователя по cookie
- Пользователь видит login форму, хотя cookie ещё валидна

**Файлы:**
- `frontend/admin-ui/src/features/auth/context/AuthContext.tsx:25` — `useState<User | null>(null)`
- `backend/gateway-admin/src/main/kotlin/.../controller/AuthController.kt` — только login/logout endpoints

### Проблема 2: Нет обработки 401 в axios interceptor

**Текущее состояние:**
- При 401 ответе axios просто возвращает ошибку
- **Нет автоматического logout** при истёкшем токене
- Пользователь может оказаться в "зависшем" состоянии

**Файл:**
- `frontend/admin-ui/src/shared/utils/axios.ts:24-28` — обработка 401 без logout

### Проблема 3: Нет refresh token механизма

**Текущее состояние:**
- JWT expiration = 24 часа (86400000 ms)
- После истечения токена требуется полный re-login
- **Нет refresh token** для продления сессии

**Файл:**
- `backend/gateway-admin/src/main/kotlin/.../security/JwtService.kt:25` — `expiration: Long`

## Acceptance Criteria

**AC1 — Сессия восстанавливается при перезагрузке:**

**Given** пользователь залогинен и JWT cookie валидна
**When** пользователь перезагружает страницу
**Then** сессия автоматически восстанавливается
**And** пользователь видит dashboard (не login форму)

**AC2 — Автоматический logout при истёкшем токене:**

**Given** JWT токен истёк
**When** пользователь выполняет любое действие (API запрос)
**Then** пользователь автоматически перенаправляется на /login
**And** показывается сообщение "Сессия истекла, войдите снова"

**AC3 — Loading state при проверке сессии:**

**Given** приложение загружается
**When** проверяется статус аутентификации
**Then** показывается loading indicator
**And** контент не мигает (нет flash of login form)

**AC4 — Graceful handling при недоступности backend:**

**Given** backend недоступен при загрузке
**When** проверка сессии не удаётся
**Then** пользователь перенаправляется на /login
**And** показывается сообщение об ошибке сети

## Tasks / Subtasks

- [x] Task 1: Backend — добавить endpoint `/api/v1/auth/me` (AC1)
  - [x] Subtask 1.1: Добавить `@GetMapping("/me")` в AuthController
  - [x] Subtask 1.2: Возвращать LoginResponse (userId, username, role) из текущего токена
  - [x] Subtask 1.3: Возвращать 401 если токен невалиден или отсутствует
  - [x] Subtask 1.4: Добавить integration тест для `/api/v1/auth/me`

- [x] Task 2: Frontend — добавить проверку сессии при инициализации (AC1, AC3)
  - [x] Subtask 2.1: Создать `checkSession()` функцию в authApi.ts
  - [x] Subtask 2.2: Добавить `useEffect` в AuthProvider для проверки сессии при mount
  - [x] Subtask 2.3: Добавить `isInitializing` state для loading indicator
  - [x] Subtask 2.4: Показывать loading spinner пока проверяется сессия

- [x] Task 3: Frontend — добавить автоматический logout при 401 (AC2)
  - [x] Subtask 3.1: Создать механизм для вызова logout из axios interceptor
  - [x] Subtask 3.2: Добавить обработку 401 с редиректом на /login
  - [x] Subtask 3.3: Добавить сообщение "Сессия истекла" в login форму
  - [x] Subtask 3.4: Исключить /api/v1/auth/login из 401 обработки (чтобы не зациклить)

- [x] Task 4: Тесты
  - [x] Subtask 4.1: Backend integration тест — `/api/v1/auth/me` возвращает пользователя
  - [x] Subtask 4.2: Backend integration тест — `/api/v1/auth/me` возвращает 401 без cookie
  - [x] Subtask 4.3: Frontend unit тест — AuthProvider вызывает checkSession при mount
  - [x] Subtask 4.4: Frontend unit тест — 401 вызывает logout

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/auth/me` | GET | — (читает из cookie) | ❌ Требуется создать |
| `/api/v1/auth/login` | POST | username, password | ✅ Существует |
| `/api/v1/auth/logout` | POST | — | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Существующая JWT логика работает корректно
- [x] Cookie устанавливается при login (HTTP-only)
- [x] JwtService может извлечь claims из токена
- [ ] Endpoint `/api/v1/auth/me` создан → **Требуется**

## Dev Notes

### Backend Implementation

**AuthController.kt — добавить endpoint:**

```kotlin
/**
 * Получение информации о текущем пользователе.
 *
 * Используется для восстановления сессии при перезагрузке страницы.
 * Читает JWT из cookie и возвращает данные пользователя.
 *
 * @return LoginResponse с userId, username и role
 */
@GetMapping("/me")
fun getCurrentUser(exchange: ServerWebExchange): Mono<ResponseEntity<LoginResponse>> {
    // Извлекаем токен из cookie
    val token = cookieService.extractToken(exchange)
        ?: return Mono.just(ResponseEntity.status(401).build())

    // Валидируем токен
    val claims = jwtService.validateToken(token)
        ?: return Mono.just(ResponseEntity.status(401).build())

    // Возвращаем данные пользователя из claims
    return Mono.just(
        ResponseEntity.ok(
            LoginResponse(
                userId = claims.subject,
                username = claims.get("username", String::class.java),
                role = claims.get("role", String::class.java)
            )
        )
    )
}
```

**Примечание:** Не нужно делать запрос в БД — все данные есть в JWT claims.

### Frontend Implementation

**authApi.ts — добавить checkSession:**

```typescript
/**
 * Проверяет текущую сессию пользователя.
 * Используется при инициализации приложения для восстановления сессии.
 *
 * @returns User если сессия валидна, null если нет
 */
export async function checkSessionApi(): Promise<User | null> {
  try {
    const response = await axios.get<User>('/api/v1/auth/me')
    return response.data
  } catch {
    return null
  }
}
```

**AuthContext.tsx — добавить проверку сессии:**

```typescript
export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isInitializing, setIsInitializing] = useState(true)  // ← новое
  const [error, setError] = useState<string | null>(null)
  // ...

  // Проверка сессии при инициализации
  useEffect(() => {
    const initSession = async () => {
      try {
        const userData = await checkSessionApi()
        if (userData) {
          setUser(userData)
        }
      } catch {
        // Игнорируем ошибки — пользователь просто не залогинен
      } finally {
        setIsInitializing(false)
      }
    }
    initSession()
  }, [])

  // Показываем loading пока проверяем сессию
  if (isInitializing) {
    return <LoadingSpinner />  // Или Spin из antd
  }

  // ...rest of component
}
```

**axios.ts — добавить автоматический logout:**

```typescript
// Создаём event для logout (чтобы не импортировать AuthContext напрямую)
export const authEvents = {
  onUnauthorized: () => {},  // Будет установлен в AuthProvider
}

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Не вызываем logout для login endpoint
      const isLoginRequest = error.config?.url?.includes('/auth/login')
      if (!isLoginRequest) {
        authEvents.onUnauthorized()
      }
      // ...existing error handling
    }
    // ...rest
  }
)
```

### Важные моменты

1. **Не хранить токен в localStorage** — уже правильно используется HTTP-only cookie
2. **Не добавлять refresh token в этой story** — это отдельная задача (можно добавить в backlog)
3. **Показывать loading при инициализации** — избегаем flash of login form

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| AuthController.kt | `backend/gateway-admin/src/main/kotlin/.../controller/` | Добавить `/me` endpoint |
| CookieService.kt | `backend/gateway-admin/src/main/kotlin/.../security/` | Возможно добавить `extractToken()` |
| AuthControllerIntegrationTest.kt | `backend/gateway-admin/src/test/.../integration/` | Добавить тесты для `/me` |
| authApi.ts | `frontend/admin-ui/src/features/auth/api/` | Добавить `checkSessionApi()` |
| AuthContext.tsx | `frontend/admin-ui/src/features/auth/context/` | Добавить инициализацию сессии |
| axios.ts | `frontend/admin-ui/src/shared/utils/` | Добавить logout callback для 401 |

### References

- [Source: backend/gateway-admin/src/main/kotlin/.../security/JwtService.kt] — JWT validation
- [Source: backend/gateway-admin/src/main/kotlin/.../controller/AuthController.kt] — существующие endpoints
- [Source: frontend/admin-ui/src/features/auth/context/AuthContext.tsx] — AuthProvider
- [Source: frontend/admin-ui/src/shared/utils/axios.ts] — axios interceptors
- [Source: _bmad-output/implementation-artifacts/epic-8-retro-2026-02-21.md#BUG-01] — bug report

### Тестовые команды

```bash
# Backend integration тесты
./gradlew :gateway-admin:test --tests "*AuthController*"

# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- AuthContext
npm run test:run -- authApi

# Все тесты
./gradlew test
cd frontend/admin-ui && npm run test:run
```

### Связанные stories

- Story 2.2 — JWT Authentication (базовая реализация)
- Story 2.3 — Login UI (LoginForm)
- Story 9.4 — Self-Service Password Change (будущая)

## Out of Scope

Следующие улучшения НЕ входят в эту story:

1. **Refresh token** — добавление механизма обновления токена (отдельная story)
2. **Remember me** — опция "Запомнить меня" при логине
3. **Session timeout warning** — предупреждение перед истечением сессии
4. **Multi-device logout** — выход со всех устройств

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Backend: все 24 теста AuthController проходят (включая 5 новых для `/me` endpoint)
- Frontend: все 441 тест проходят (включая 5 новых для Story 9.1)

### Completion Notes List

- ✅ Реализован endpoint `GET /api/v1/auth/me` для восстановления сессии
- ✅ Добавлен метод `extractToken()` в CookieService для извлечения JWT из cookie
- ✅ Добавлена функция `checkSessionApi()` в authApi.ts
- ✅ AuthProvider теперь проверяет сессию при инициализации с `useEffect`
- ✅ Добавлен `isInitializing` state для показа loading spinner (предотвращает flash of login form)
- ✅ Реализован механизм `authEvents.onUnauthorized` для автоматического logout при 401
- ✅ Сообщение "Сессия истекла, войдите снова" показывается при истечении токена
- ✅ Исключены `/auth/login` и `/auth/me` из автоматического logout (предотвращает зацикливание)

### File List

**Backend (изменённые):**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuthController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/CookieService.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuthControllerIntegrationTest.kt

**Frontend (изменённые):**
- frontend/admin-ui/src/features/auth/api/authApi.ts
- frontend/admin-ui/src/features/auth/context/AuthContext.tsx
- frontend/admin-ui/src/features/auth/context/AuthContext.test.tsx
- frontend/admin-ui/src/shared/utils/axios.ts

## Change Log

- 2026-02-21: Story 9.1 created from Epic 8 Retrospective BUG-01
- 2026-02-21: Story 9.1 implementation completed — session restore and 401 handling
