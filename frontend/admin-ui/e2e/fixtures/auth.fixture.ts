// E2E Auth Fixture — Mock авторизация для изолированных тестов
// Story 13.15: E2E тесты с чистого листа, CI-first подход

import { test as base, type Page } from '@playwright/test'

/**
 * Mock данные пользователя admin
 */
export const mockAdminUser = {
  userId: 'test-admin-id-12345',
  username: 'admin',
  role: 'admin',
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
 * Mock access token с claims для admin пользователя.
 * Использует правильные Keycloak role names: admin-ui:admin, admin-ui:security, admin-ui:developer
 */
export const mockAccessToken = createMockJwt({
  sub: mockAdminUser.userId,
  preferred_username: mockAdminUser.username,
  email: 'admin@example.com',
  realm_access: {
    // Keycloak роли — используются admin-ui:* prefix для маппинга в AuthContext
    roles: ['admin-ui:admin', 'admin-ui:developer', 'default-roles-gateway'],
  },
  resource_access: {
    'gateway-admin': {
      roles: ['admin'],
    },
  },
  exp: Math.floor(Date.now() / 1000) + 3600, // +1 час
  iat: Math.floor(Date.now() / 1000),
})

/**
 * Mock токены Keycloak (формат как в sessionStorage).
 */
export const mockKeycloakTokens = {
  access_token: mockAccessToken,
  refresh_token: 'mock-refresh-token-for-e2e',
  expires_in: 3600,
  refresh_expires_in: 86400,
  token_type: 'Bearer',
  scope: 'openid profile email',
  saved_at: Date.now(),
}

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
