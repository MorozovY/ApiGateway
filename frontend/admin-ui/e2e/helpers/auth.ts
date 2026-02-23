import type { Page } from '@playwright/test'

/**
 * Очищает сессию, удаляя cookies.
 * Используется перед повторным login() чтобы гарантировать чистое состояние.
 */
export async function clearSession(page: Page): Promise<void> {
  await page.context().clearCookies()
}

/**
 * Выполняет вход через форму логина с поддержкой returnUrl механизма.
 *
 * Алгоритм:
 * 1. Переходим на landingUrl — ProtectedRoute редиректит на /login с returnUrl
 * 2. Ждём появления формы логина
 * 3. Заполняем credentials и нажимаем войти
 * 4. React Router возвращает на landingUrl (через returnUrl state)
 *
 * Это гарантирует, что React-состояние (AuthContext) корректно инициализировано
 * и не сбрасывается при последующей навигации внутри SPA.
 */
export async function login(
  page: Page,
  username: string,
  password: string,
  landingUrl = '/dashboard'
): Promise<void> {
  // Очищаем сессию перед новым логином (важно для тестов со сменой пользователя)
  await clearSession(page)

  // Переходим на целевую страницу — вызовет редирект на /login с returnUrl
  await page.goto(landingUrl)

  // Ждём появления страницы логина (может быть уже там или после редиректа)
  await page.waitForURL(/\/login/, { timeout: 10_000 })

  await page.locator('[data-testid="username-input"]').fill(username)
  await page.locator('[data-testid="password-input"]').fill(password)
  await page.locator('[data-testid="login-button"]').click()

  // Ждём редиректа обратно на landingUrl (через returnUrl) или на /dashboard
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10_000 })
}

/**
 * Выполняет выход через dropdown menu в header.
 *
 * Story 9.4 изменила UI: теперь Logout — пункт в dropdown меню пользователя,
 * а не отдельная кнопка. Нужно кликнуть на dropdown, затем на "Выйти".
 */
export async function logout(page: Page): Promise<void> {
  // Кликаем на dropdown button (показывает username и роль)
  // Ищем кнопку в header, которая содержит DownOutlined иконку
  await page.getByRole('banner').locator('button').filter({ hasText: /developer|security|admin/i }).click()

  // Ждём появления dropdown menu и кликаем на "Выйти"
  await page.getByRole('menuitem', { name: /выйти/i }).click()

  await page.waitForURL(/\/login/, { timeout: 10_000 })
}
