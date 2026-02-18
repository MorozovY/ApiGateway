import type { Page } from '@playwright/test'

/**
 * Выполняет вход пользователя через форму логина.
 *
 * Заполняет поля username и password, нажимает кнопку входа,
 * ожидает редиректа с /login.
 */
export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')

  await page.locator('[data-testid="username-input"]').fill(username)
  await page.locator('[data-testid="password-input"]').fill(password)
  await page.locator('[data-testid="login-button"]').click()

  // Ожидаем редирект после успешного логина
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10_000 })
}

/**
 * Выполняет выход пользователя через кнопку Logout в header.
 *
 * После выхода ожидает редиректа на /login.
 */
export async function logout(page: Page): Promise<void> {
  await page.locator('button:has-text("Logout")').click()
  await page.waitForURL(/\/login/, { timeout: 10_000 })
}
