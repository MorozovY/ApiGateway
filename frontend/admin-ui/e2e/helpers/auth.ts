import type { Page } from '@playwright/test'
import { keycloakLogin as keycloakLoginImpl } from './keycloak-auth'

/**
 * Очищает сессию, удаляя cookies и sessionStorage.
 * Используется перед повторным login() чтобы гарантировать чистое состояние.
 */
export async function clearSession(page: Page): Promise<void> {
  await page.context().clearCookies()

  // SessionStorage доступен только если page загружена
  // Игнорируем SecurityError если page ещё не имеет origin (about:blank)
  try {
    await page.evaluate(() => sessionStorage.clear())
  } catch (error) {
    // Игнорируем SecurityError — sessionStorage будет пустой на новой странице
    if (!(error instanceof Error) || !error.message.includes('SecurityError')) {
      throw error
    }
  }
}

/**
 * USERNAME mapping — НЕ конвертируем в email!
 *
 * Story 13.13 Fix: Keycloak принимает USERNAME в Direct Access Grants,
 * а не email. Конвертация username → email ломала аутентификацию в CI.
 *
 * loginWithEmailAllowed=true в realm означает что МОЖНО логиниться по email,
 * но это не значит что email ОБЯЗАТЕЛЕН. Username работает всегда.
 */

/**
 * Выполняет вход через Keycloak Direct Access Grants.
 *
 * MIGRATION NOTE (Story 12.2):
 * - Старый auth flow (self-issued JWT) УДАЛЁН
 * - Теперь используется Keycloak authentication
 * - Функция делегирует на keycloakLogin() для backward compatibility
 *
 * BREAKING CHANGE для Epic 2-8 тестов:
 * - Keycloak требует EMAIL вместо username
 * - Добавлен USERNAME_TO_EMAIL_MAP для конвертации username → email
 *
 * Алгоритм:
 * 1. Конвертируем username в email (если нужно)
 * 2. Делегируем на keycloakLogin() (Keycloak Direct Access Grants flow)
 * 3. Ожидаем redirect на landingUrl
 */
export async function login(
  page: Page,
  username: string,
  password: string,
  landingUrl = '/dashboard'
): Promise<void> {
  // Очищаем сессию перед новым логином (важно для тестов со сменой пользователя)
  await clearSession(page)

  // Story 13.13 Fix: Используем username напрямую, без конвертации в email.
  // Keycloak Direct Access Grants принимает username, не email.
  console.log(`[E2E] login("${username}") → keycloakLogin("${username}")`)

  // Делегируем на Keycloak authentication
  return keycloakLoginImpl(page, username, password, landingUrl)
}

/**
 * Выполняет выход через dropdown menu в header.
 *
 * MIGRATION NOTE (Story 12.2):
 * - Keycloak logout flow используется
 * - Делегируем на keycloakLogout() для consistency
 */
export async function logout(page: Page): Promise<void> {
  const { keycloakLogout } = await import('./keycloak-auth')
  return keycloakLogout(page)
}

/**
 * Получает Keycloak JWT access token из sessionStorage.
 *
 * MIGRATION NOTE (Story 12.2):
 * - Keycloak JWT хранится в sessionStorage как keycloak_tokens
 * - Используется для authenticated API requests в Epic 3-8 tests
 */
export async function getAuthToken(page: Page): Promise<string> {
  const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
  if (!tokensStr) {
    throw new Error('Keycloak tokens not found in sessionStorage. Did you call login() first?')
  }

  const tokens = JSON.parse(tokensStr)
  if (!tokens.access_token) {
    throw new Error('access_token not found in keycloak_tokens')
  }

  return tokens.access_token
}

/**
 * Делает authenticated API request с Keycloak JWT token.
 *
 * MIGRATION NOTE (Story 12.2):
 * - Автоматически добавляет Authorization header с Keycloak JWT
 * - Используется в Epic 3-8 tests для API calls
 * - Заменяет прямые page.request calls которые больше не работают с Keycloak
 *
 * @example
 * ```typescript
 * const response = await apiRequest(page, 'POST', '/api/v1/routes', {
 *   path: '/test',
 *   upstreamUrl: 'http://example.com',
 *   methods: ['GET']
 * })
 * ```
 */
export async function apiRequest(
  page: Page,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH',
  url: string,
  data?: unknown,
  options?: Record<string, unknown>
): Promise<Response> {
  const token = await getAuthToken(page)
  const apiBase = process.env.API_BASE || 'http://localhost:8081'
  const baseUrl = url.startsWith('http') ? '' : apiBase

  return page.request.fetch(`${baseUrl}${url}`, {
    method,
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...((options as { headers?: Record<string, string> })?.headers || {})
    },
    data,
    ...options
  })
}
