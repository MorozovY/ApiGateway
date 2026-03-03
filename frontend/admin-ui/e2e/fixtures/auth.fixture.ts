// E2E Auth Fixture — Mock авторизация для изолированных тестов
// Story 13.15: E2E тесты с чистого листа, CI-first подход
// Story 14.6: Добавлены роли developer и security для RBAC тестов

import { test as base, type Page } from '@playwright/test'

/**
 * Типы ролей в приложении
 */
export type AppRole = 'admin' | 'developer' | 'security'

/**
 * Mock данные пользователя admin
 */
export const mockAdminUser = {
  userId: 'test-admin-id-12345',
  username: 'admin',
  role: 'admin' as AppRole,
}

/**
 * Mock данные пользователя developer (Story 14.6, Task 1.1)
 */
export const mockDeveloperUser = {
  userId: 'test-developer-id-67890',
  username: 'developer',
  role: 'developer' as AppRole,
}

/**
 * Mock данные пользователя security (Story 14.6, Task 1.2)
 */
export const mockSecurityUser = {
  userId: 'test-security-id-11111',
  username: 'security',
  role: 'security' as AppRole,
}

/**
 * Mock токен для Keycloak.
 * Формат: header.payload.signature (base64 encoded)
 * Payload содержит минимальные claims для работы AuthContext.
 */
function createMockJwt(payload: object): string {
  const header = { alg: 'RS256', typ: 'JWT' }
  const headerB64 = Buffer.from(JSON.stringify(header)).toString('base64url')
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString('base64url')
  // Фиктивная подпись (не валидируется в mock mode)
  const signature = 'mock-signature-for-e2e-tests'
  return `${headerB64}.${payloadB64}.${signature}`
}

/**
 * Маппинг ролей приложения в Keycloak роли (Story 14.6, Task 1.4)
 * admin: имеет admin-ui:admin + admin-ui:developer
 * security: имеет только admin-ui:security
 * developer: имеет только admin-ui:developer
 */
const roleToKeycloakRoles: Record<AppRole, string[]> = {
  admin: ['admin-ui:admin', 'admin-ui:developer', 'default-roles-gateway'],
  security: ['admin-ui:security', 'default-roles-gateway'],
  developer: ['admin-ui:developer', 'default-roles-gateway'],
}

/**
 * Генерирует mock JWT token для указанной роли (Story 14.6, Task 1.4)
 */
export function createMockAccessTokenForRole(role: AppRole): string {
  const userMap: Record<AppRole, typeof mockAdminUser> = {
    admin: mockAdminUser,
    developer: mockDeveloperUser,
    security: mockSecurityUser,
  }
  const user = userMap[role]

  return createMockJwt({
    sub: user.userId,
    preferred_username: user.username,
    email: `${user.username}@example.com`,
    realm_access: {
      roles: roleToKeycloakRoles[role],
    },
    resource_access: {
      'gateway-admin': {
        roles: [role],
      },
    },
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  })
}

/**
 * Mock access token с claims для admin пользователя.
 * Использует правильные Keycloak role names: admin-ui:admin, admin-ui:security, admin-ui:developer
 */
export const mockAccessToken = createMockAccessTokenForRole('admin')

/**
 * Создаёт mock Keycloak tokens для указанной роли (Story 14.6)
 * saved_at вычисляется в момент вызова, чтобы токен не был "истёкшим"
 */
function createMockKeycloakTokensForRole(role: AppRole) {
  return {
    access_token: createMockAccessTokenForRole(role),
    refresh_token: `mock-refresh-token-${role}-e2e`,
    expires_in: 3600,
    refresh_expires_in: 86400,
    token_type: 'Bearer',
    scope: 'openid profile email',
    saved_at: Date.now(),
  }
}

/**
 * Mock токены Keycloak (формат как в sessionStorage).
 * Использует функцию для создания свежего saved_at
 */
export const mockKeycloakTokens = createMockKeycloakTokensForRole('admin')

/**
 * Устанавливает mock токены в sessionStorage.
 * Вызывается перед загрузкой страницы.
 */
export async function setupMockAuth(page: Page): Promise<void> {
  await page.addInitScript((tokens) => {
    sessionStorage.setItem('keycloak_tokens', JSON.stringify(tokens))
  }, mockKeycloakTokens)
}

/**
 * Устанавливает mock авторизацию с указанной ролью (Story 14.6, Task 1.3)
 * Используется для RBAC тестов developer и security ролей.
 */
export async function setupMockAuthWithRole(page: Page, role: AppRole): Promise<void> {
  const tokens = createMockKeycloakTokensForRole(role)
  await page.addInitScript((t) => {
    sessionStorage.setItem('keycloak_tokens', JSON.stringify(t))
  }, tokens)
}

/**
 * Очищает auth state.
 */
export async function clearAuth(page: Page): Promise<void> {
  await page.addInitScript(() => {
    sessionStorage.removeItem('keycloak_tokens')
  })
}

/**
 * Extended test с pre-configured authenticated page.
 * Использование: import { test } from './fixtures/auth.fixture'
 */
export const test = base.extend<{ authenticatedPage: Page }>({
  authenticatedPage: async ({ page }, use) => {
    // Устанавливаем mock auth перед загрузкой
    await setupMockAuth(page)
    await use(page)
  },
})

export { expect } from '@playwright/test'
