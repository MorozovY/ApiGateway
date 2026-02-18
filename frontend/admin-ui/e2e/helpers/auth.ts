import type { Page } from '@playwright/test'

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
 * Выполняет выход через кнопку Logout в header.
 *
 * Использует точный селектор header-кнопки чтобы избежать конфликтов
 * с другими элементами, содержащими слово "Logout".
 */
export async function logout(page: Page): Promise<void> {
  // Кнопка Logout находится в banner (header) зоне MainLayout
  await page.getByRole('banner').getByRole('button', { name: /logout/i }).click()
  await page.waitForURL(/\/login/, { timeout: 10_000 })
}
