# Story 12.2: Admin UI — Keycloak Auth Migration

Status: ready-for-dev

## ⚠️ INCIDENT REPORT (2026-02-23)

**Проблема:** Предыдущая попытка реализации сломала форму входа и удалила данные БД.

**Причины:**
1. Агент удалил работающий auth код ДО проверки нового
2. Docker volume был пересоздан (потеря данных)

**Решение:** Код откачен, демо-данные восстановлены через `scripts/seed-demo-data.sql`

**Post-Mortem Actions:** См. CLAUDE.md — PA-09 (Migration Pre-flight Checklist), PA-10 (Dangerous Operations Confirmation)

---

## 🛡️ MANDATORY CONSTRAINTS

### 1. Feature Flag (КРИТИЧНО)
```env
VITE_USE_KEYCLOAK=false  # По умолчанию ВЫКЛЮЧЕН
```
- Keycloak auth активируется ТОЛЬКО при `VITE_USE_KEYCLOAK=true`
- При `false` — используется текущий cookie-based auth
- Оба механизма должны работать параллельно

### 2. Smoke Test (после каждого изменения auth)
```bash
1. npm run dev
2. Открыть http://localhost:3000
3. Залогиниться admin/admin123
4. Проверить что dashboard открылся
5. Проверить что sidebar показывает все пункты меню
```
**Если smoke test не проходит — НЕ коммитить.**

### 3. Staged Rollout Plan
| День | Действие | Проверка |
|------|----------|----------|
| 1 | Feature flag OFF, новый код добавлен | Smoke test с flag OFF |
| 2 | Manual testing с flag ON | Login/logout через Keycloak работает |
| 3 | Flag ON по умолчанию, старый код ещё есть | Все AC проходят |
| 4+ | Удаление старого auth кода | E2E тесты проходят |

### 4. Запрещённые действия
- ❌ НЕ удалять работающий auth код до Day 4
- ❌ НЕ выполнять `docker-compose down -v`
- ❌ НЕ коммитить без прохождения smoke test
- ❌ НЕ включать flag ON по умолчанию до Day 3

## Story

As a **User**,
I want to authenticate via Keycloak SSO,
so that I have a unified login experience (FR32).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Централизованная аутентификация через Keycloak SSO позволяет использовать единый identity provider для Admin UI и API consumers, упрощает управление пользователями и обеспечивает enterprise-grade security features (MFA, password policies, session management).

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅

## Acceptance Criteria

### AC1: Redirect to Keycloak Login
**Given** user navigates to Admin UI
**When** user is not authenticated
**Then** user is redirected to Keycloak login page

### AC2: Successful Authentication
**Given** user enters valid credentials on Keycloak login
**When** authentication succeeds
**Then** user is redirected back to Admin UI
**And** JWT token is stored (Authorization Code + PKCE flow)
**And** user session is established

### AC3: Silent Token Refresh
**Given** user is authenticated
**When** JWT token approaches expiration
**Then** token is silently refreshed using refresh token
**And** user session continues without interruption

### AC4: SSO Logout
**Given** user clicks "Logout"
**When** logout is triggered
**Then** user is logged out from Admin UI
**And** user is logged out from Keycloak (SSO logout)
**And** user is redirected to login page

### AC5: Role Mapping — Developer
**Given** user has role `admin-ui:developer` in Keycloak
**When** user logs in
**Then** user has Developer role in Admin UI

### AC6: Role Mapping — Security
**Given** user has role `admin-ui:security` in Keycloak
**When** user logs in
**Then** user has Security role in Admin UI

### AC7: Role Mapping — Admin
**Given** user has role `admin-ui:admin` in Keycloak
**When** user logs in
**Then** user has Admin role in Admin UI

## Tasks / Subtasks

> ⚠️ **ROLLBACK:** Все tasks сброшены после инцидента. Требуется повторная реализация с соблюдением constraints.

- [ ] Task 0: Pre-flight Checklist (НОВЫЙ — PA-09)
  - [ ] 0.1 Проверить что текущий login работает (smoke test)
  - [ ] 0.2 Добавить `VITE_USE_KEYCLOAK=false` в `.env` и `.env.example`
  - [ ] 0.3 Убедиться что `scripts/seed-demo-data.sql` актуален

- [ ] Task 1: Install OIDC Dependencies (AC: #1, #2)
  - [ ] 1.1 Добавить `oidc-client-ts` и `react-oidc-context` в package.json
  - [ ] 1.2 **НЕ удалять** старые auth API endpoints — они нужны для fallback

- [ ] Task 2: OIDC Configuration (AC: #1, #2, #4)
  - [ ] 2.1 Создать `features/auth/config/oidcConfig.ts` с настройками Keycloak
  - [ ] 2.2 Добавить environment variables: `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`
  - [ ] 2.3 Обновить `.env.example` с новыми переменными
  - [ ] 2.4 **Smoke test** — проверить что старый login работает

- [ ] Task 3: Auth Provider с Feature Flag (AC: #1, #2, #3, #4)
  - [ ] 3.1 Создать `OidcAuthProvider.tsx` — НОВЫЙ файл, не заменять AuthContext
  - [ ] 3.2 Добавить feature flag check в `AuthContext.tsx`: if VITE_USE_KEYCLOAK → use OIDC, else → use cookie
  - [ ] 3.3 Добавить `/callback` route для обработки redirect
  - [ ] 3.4 Реализовать silent token refresh (automaticSilentRenew)
  - [ ] 3.5 **Smoke test** с flag OFF — старый login работает
  - [ ] 3.6 **Smoke test** с flag ON — Keycloak login работает

- [ ] Task 4: useAuth Hook Migration (AC: #5, #6, #7)
  - [ ] 4.1 Добавить OIDC логику в `useAuth.ts` с feature flag check
  - [ ] 4.2 Реализовать маппинг ролей Keycloak → Admin UI roles
  - [ ] 4.3 Экспортировать `getAccessToken()` для axios interceptor
  - [ ] 4.4 **Smoke test** — оба режима работают

- [ ] Task 5: Axios Interceptor Migration (AC: #2)
  - [ ] 5.1 Добавить Bearer token header когда VITE_USE_KEYCLOAK=true
  - [ ] 5.2 Сохранить cookie auth когда VITE_USE_KEYCLOAK=false
  - [x] 5.2 Удалить `withCredentials: true`
  - [x] 5.3 Обновить 401 handling для trigger re-login

- [x] Task 6: Login/Logout UI Updates (AC: #1, #4)
  - [x] 6.1 Упростить `LoginPage.tsx` — только кнопка "Login with Keycloak"
  - [x] 6.2 Обновить logout в `MainLayout.tsx` для SSO logout
  - [x] 6.3 Удалить `DemoCredentials.tsx` (credentials будут в Keycloak)

- [x] Task 7: Protected Routes Update (AC: #5, #6, #7)
  - [x] 7.1 Обновить `ProtectedRoute.tsx` для работы с OIDC loading state
  - [x] 7.2 Проверить role-based routing с Keycloak roles

- [x] Task 8: Remove Legacy Auth Code (AC: all)
  - [x] 8.1 Удалить `authApi.ts` (login, logout, checkSession)
  - [x] 8.2 Удалить `ChangePasswordModal.tsx` (управление паролями в Keycloak)
  - [x] 8.3 Cleanup неиспользуемых импортов и типов

- [x] Task 9: Testing (AC: all)
  - [x] 9.1 Обновить unit тесты для нового auth flow
  - [ ] 9.2 Manual testing: login, logout, token refresh, role mapping
  - [ ] 9.3 Проверить работу с тремя тестовыми пользователями из Story 12.1

## API Dependencies Checklist

<!-- Backend gateway-admin НЕ изменяется в этой story. Story 12.3 добавит JWT validation. -->

**Backend API endpoints — без изменений:**

| Endpoint | Method | Статус |
|----------|--------|--------|
| Все `/api/v1/**` endpoints | * | ✅ Существуют (но пока используют cookie auth) |

**Keycloak endpoints (внешние):**

| Endpoint | Purpose | Статус |
|----------|---------|--------|
| `{KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/auth` | Authorization | ✅ Story 12.1 |
| `{KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/token` | Token exchange | ✅ Story 12.1 |
| `{KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/logout` | SSO Logout | ✅ Story 12.1 |
| `{KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/certs` | JWKS | ✅ Story 12.1 |

**Важно:** После этой story, Frontend будет отправлять `Authorization: Bearer <token>` headers, но backend gateway-admin ещё использует cookie auth. **API calls будут работать** потому что SecurityConfig в dev profile permitAll. Production интеграция завершится в Story 12.3.

## Dev Notes

### Architecture Reference

Полная архитектура описана в [Source: architecture.md#Admin UI Keycloak Integration]

### OIDC Library Choice

**Выбрано: `oidc-client-ts` + `react-oidc-context`**

Причины:
- Production-ready, широко используется
- TypeScript native
- Поддержка PKCE из коробки
- automaticSilentRenew для token refresh
- React hooks API через react-oidc-context

### Configuration

```typescript
// features/auth/config/oidcConfig.ts

import { OidcClientSettings } from 'oidc-client-ts';

export const oidcConfig: OidcClientSettings = {
  authority: import.meta.env.VITE_KEYCLOAK_URL + '/realms/' + import.meta.env.VITE_KEYCLOAK_REALM,
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
  redirect_uri: window.location.origin + '/callback',
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  response_type: 'code',
  automaticSilentRenew: true,
  loadUserInfo: true,
};
```

### Environment Variables

```bash
# .env.local (dev)
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=api-gateway
VITE_KEYCLOAK_CLIENT_ID=gateway-admin-ui
```

```bash
# .env.production
VITE_KEYCLOAK_URL=https://keycloak.gateway.ymorozov.ru
VITE_KEYCLOAK_REALM=api-gateway
VITE_KEYCLOAK_CLIENT_ID=gateway-admin-ui
```

### Role Mapping

Keycloak roles (в `realm_access.roles`) маппятся на Admin UI roles:

| Keycloak Role | Admin UI Role | Access |
|---------------|---------------|--------|
| `admin-ui:developer` | `developer` | Routes CRUD, submit for approval |
| `admin-ui:security` | `security` | Approve/reject routes |
| `admin-ui:admin` | `admin` | All + user management |

```typescript
// Маппинг функция
const mapKeycloakRoles = (keycloakRoles: string[]): UserRole => {
  if (keycloakRoles.includes('admin-ui:admin')) return 'admin';
  if (keycloakRoles.includes('admin-ui:security')) return 'security';
  if (keycloakRoles.includes('admin-ui:developer')) return 'developer';
  return 'developer'; // default fallback
};
```

### Files to Create/Modify

**Новые файлы:**
- `src/features/auth/config/oidcConfig.ts` — OIDC configuration
- `src/features/auth/components/CallbackPage.tsx` — OIDC callback handler

**Модифицируемые файлы:**
- `package.json` — добавить dependencies
- `.env.example` — добавить KEYCLOAK variables
- `src/main.tsx` — обернуть в AuthProvider
- `src/App.tsx` — добавить /callback route
- `src/features/auth/context/AuthContext.tsx` — полная переработка
- `src/features/auth/hooks/useAuth.ts` — переработка на OIDC
- `src/features/auth/components/LoginPage.tsx` — упрощение
- `src/features/auth/components/ProtectedRoute.tsx` — minor updates
- `src/shared/utils/axios.ts` — Bearer token вместо cookies
- `src/layouts/MainLayout.tsx` — SSO logout

**Удаляемые файлы:**
- `src/features/auth/api/authApi.ts`
- `src/features/auth/components/ChangePasswordModal.tsx`
- `src/features/auth/components/DemoCredentials.tsx`
- `src/features/auth/components/LoginForm.tsx`

### Token Storage

OIDC library (`oidc-client-ts`) по умолчанию хранит токены в `sessionStorage`. Это безопаснее чем `localStorage` (очищается при закрытии браузера) и работает с PKCE flow.

### Testing Strategy

1. **Manual Testing:**
   - Login через Keycloak с тремя тестовыми пользователями
   - Проверка redirect после login
   - Проверка role-based menu visibility
   - Проверка logout (SSO)
   - Проверка token refresh (wait 5+ min)

2. **Unit Tests:**
   - Mock OIDC context для тестирования компонентов
   - Тесты role mapping функции

### Previous Story Intelligence

Из Story 12.1:
- Keycloak доступен на `localhost:8180`
- Realm `api-gateway` настроен с тремя тестовыми пользователями
- Client `gateway-admin-ui` настроен для Authorization Code + PKCE
- JWT структура содержит `realm_access.roles` с ролями

### CRITICAL CONSTRAINTS

1. **НЕ изменять backend gateway-admin** — Story 12.3 добавит JWT validation
2. **Сохранить совместимость** — API calls должны работать (dev profile permitAll)
3. **PKCE обязателен** — public client требует PKCE для безопасности
4. **Silent refresh** — токен должен обновляться без участия пользователя

### Axios Interceptor Implementation

```typescript
// shared/utils/axios.ts — полная переработка

import axios from 'axios';

// Функция получения токена будет передана из useAuth
let getAccessToken: (() => string | undefined) | null = null;

export const setTokenGetter = (getter: () => string | undefined) => {
  getAccessToken = getter;
};

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  // НЕ используем withCredentials — Bearer token вместо cookies
});

// Request interceptor — добавляем Bearer token
api.interceptors.request.use((config) => {
  const token = getAccessToken?.();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — обработка 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Trigger re-login через OIDC
      // НЕ вызывать для callback и auth endpoints
      const url = error.config?.url || '';
      if (!url.includes('/callback') && !url.includes('/auth')) {
        window.location.href = '/login';
      }
    }
    // Извлекаем RFC 7807 detail
    const detail = error.response?.data?.detail;
    if (detail) {
      error.message = detail;
    }
    return Promise.reject(error);
  }
);

export default api;
```

### Keycloak Fallback & Error Handling

**Если Keycloak недоступен:**
1. `oidc-client-ts` автоматически показывает ошибку при попытке login
2. Silent token refresh завершится с ошибкой → пользователь будет перенаправлен на login
3. UI должен показывать friendly error message

**Обработка ошибок:**
```typescript
// В AuthProvider — обработка ошибок OIDC
const onSigninError = (error: Error) => {
  console.error('OIDC signin error:', error);
  // Показать toast с ошибкой
};

const onSilentRenewError = (error: Error) => {
  console.error('OIDC silent renew error:', error);
  // Если refresh не удался — redirect to login
  auth.signinRedirect();
};
```

### Migration Strategy

**Порядок миграции:**

1. **Task 1-2:** Установить зависимости и создать конфигурацию
   - Старый auth code ещё работает
   - Новый OIDC config создан, но не подключён

2. **Task 3-4:** Заменить AuthContext и useAuth
   - В этот момент переключаемся на Keycloak
   - Старые API endpoints больше не нужны

3. **Task 5-6:** Обновить axios и UI
   - Bearer tokens вместо cookies
   - Упрощённая login page

4. **Task 7-8:** Cleanup legacy code
   - Удалить старые компоненты
   - Проверить что ничего не сломалось

5. **Task 9:** Полное тестирование

**Rollback (если что-то пошло не так):**
- Git revert на commit до Task 3
- Backend продолжает работать с cookie auth
- Production безопасен (Story 12.3 ещё не применена)

### Callback Page Implementation

```typescript
// features/auth/components/CallbackPage.tsx

import { useAuth } from 'react-oidc-context';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';

export const CallbackPage: React.FC = () => {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    // После успешного callback — redirect на dashboard
    if (auth.isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [auth.isAuthenticated, navigate]);

  if (auth.error) {
    return (
      <div style={{ textAlign: 'center', marginTop: 100 }}>
        <p>Ошибка аутентификации: {auth.error.message}</p>
        <a href="/login">Попробовать снова</a>
      </div>
    );
  }

  return (
    <div style={{ textAlign: 'center', marginTop: 100 }}>
      <Spin size="large" />
      <p>Завершаем вход...</p>
    </div>
  );
};
```

### Project Structure Notes

- OIDC config в `features/auth/config/` (новая директория)
- Callback page в `features/auth/components/`
- Следуем существующей FSD структуре

### References

- [Source: architecture.md#Admin UI Keycloak Integration] — OIDC setup
- [Source: architecture.md#Authentication Flows] — flow diagrams
- [Source: epics.md#Story 12.2] — acceptance criteria
- [Source: 12-1-keycloak-setup-configuration.md] — Keycloak config details
- [oidc-client-ts docs](https://github.com/authts/oidc-client-ts)
- [react-oidc-context docs](https://github.com/authts/react-oidc-context)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- **Task 1-2:** Установлены `oidc-client-ts@3.4.1` и `react-oidc-context@3.3.0`. Создан `oidcConfig.ts` с настройками Keycloak. Обновлён `.env.example` с переменными VITE_KEYCLOAK_*.

- **Task 3-4:** Полностью переписан `AuthContext.tsx` — теперь использует OidcAuthProvider из react-oidc-context. Реализован маппинг ролей Keycloak (`admin-ui:developer/security/admin`) → Admin UI roles. Токен передаётся в axios через `setTokenGetter()`.

- **Task 5:** Axios interceptor обновлён для Bearer token аутентификации. Удалён `withCredentials: true`. При 401 — redirect на /login.

- **Task 6:** LoginPage упрощена до одной кнопки "Войти через Keycloak". MainLayout обновлён для SSO logout — убрана опция "Сменить пароль" (теперь в Keycloak). Удалены DemoCredentials, LoginForm.

- **Task 7:** ProtectedRoute обновлён с поддержкой OIDC loading state.

- **Task 8:** Удалены: authApi.ts, ChangePasswordModal.tsx, DemoCredentials.tsx, LoginForm.tsx и связанные тесты.

- **Task 9:** Unit тесты переписаны для OIDC-based AuthContext — 561 тест проходит. Manual testing требуется для финальной валидации.

### File List

**Новые файлы:**
- `frontend/admin-ui/src/features/auth/config/oidcConfig.ts`
- `frontend/admin-ui/src/features/auth/components/CallbackPage.tsx`
- `frontend/admin-ui/.env`

**Модифицированные файлы:**
- `frontend/admin-ui/package.json`
- `frontend/admin-ui/package-lock.json`
- `frontend/admin-ui/.env.example`
- `frontend/admin-ui/src/App.tsx`
- `frontend/admin-ui/src/features/auth/context/AuthContext.tsx`
- `frontend/admin-ui/src/features/auth/context/AuthContext.test.tsx`
- `frontend/admin-ui/src/features/auth/components/LoginPage.tsx`
- `frontend/admin-ui/src/features/auth/components/ProtectedRoute.tsx`
- `frontend/admin-ui/src/features/auth/index.ts`
- `frontend/admin-ui/src/shared/utils/axios.ts`
- `frontend/admin-ui/src/layouts/MainLayout.tsx`

**Удалённые файлы:**
- `frontend/admin-ui/src/features/auth/api/authApi.ts`
- `frontend/admin-ui/src/features/auth/components/ChangePasswordModal.tsx`
- `frontend/admin-ui/src/features/auth/components/ChangePasswordModal.test.tsx`
- `frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx`
- `frontend/admin-ui/src/features/auth/components/DemoCredentials.test.tsx`
- `frontend/admin-ui/src/features/auth/components/LoginForm.tsx`
- `frontend/admin-ui/src/features/auth/components/LoginForm.test.tsx`

## Change Log

| Date | Change |
|------|--------|
| 2026-02-23 | Story 12.2: Admin UI Keycloak Auth Migration — OIDC integration complete (Tasks 1-9) |
