import { test, expect } from '@playwright/test'

/**
 * Epic 1 — Infrastructure.
 * Проверяет базовую доступность backend и frontend.
 */
test.describe('Epic 1: Infrastructure', () => {
  test('API доступен: GET /actuator/health возвращает 200', async ({ request }) => {
    const response = await request.get('http://localhost:8081/actuator/health')
    expect(response.status()).toBe(200)
  })

  test('Фронтенд загружается и отображает страницу логина', async ({ page }) => {
    await page.goto('/')

    // Ожидаем редирект на /login
    await expect(page).toHaveURL(/\/login/)

    // Форма логина отображается
    await expect(page.locator('[data-testid="login-button"]')).toBeVisible()
    await expect(page.locator('[data-testid="username-input"]')).toBeVisible()
    await expect(page.locator('[data-testid="password-input"]')).toBeVisible()
  })
})
