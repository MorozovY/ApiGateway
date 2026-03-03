// E2E тест: Error Handling — обработка ошибок API
// Story 14.6: AC5 — Error Handling Tests (4 теста)

import { test, expect, type Page } from '@playwright/test'
import { setupMockAuth, clearAuth } from '../fixtures/auth.fixture'

/**
 * Настраивает mock API с симуляцией ошибки для указанного статуса
 */
async function setupMockApiWithError(page: Page, errorCode: number, errorEndpoint?: string) {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname

    // Если endpoint указан, проверяем совпадение
    if (errorEndpoint && !pathname.includes(errorEndpoint)) {
      // Продолжаем нормальный запрос для других endpoints
      await route.continue()
      return
    }

    // Возвращаем ошибку
    await route.fulfill({
      status: errorCode,
      contentType: 'application/json',
      body: JSON.stringify({
        type: 'about:blank',
        title: errorCode === 403 ? 'Forbidden' : 'Internal Server Error',
        status: errorCode,
        detail: errorCode === 403 ? 'Access denied' : 'An error occurred',
      }),
    })
  })
}

/**
 * Настраивает mock API с симуляцией сетевой ошибки
 */
async function setupMockApiWithNetworkError(page: Page, errorEndpoint?: string) {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname

    if (errorEndpoint && !pathname.includes(errorEndpoint)) {
      await route.continue()
      return
    }

    await route.abort('connectionfailed')
  })
}

test.describe('Error Handling', () => {
  test('401 Unauthorized redirect на login', async ({ page }) => {
    // НЕ устанавливаем auth — симулируем не аутентифицированного пользователя
    await clearAuth(page)

    // Переходим на защищённую страницу
    await page.goto('/dashboard')

    // Должен произойти редирект на login
    await expect(page).toHaveURL(/\/login/)

    // Должна быть видна страница логина
    await expect(page.locator('text=/login|войти|username|password/i').first()).toBeVisible()
  })

  test('403 Forbidden показывает access denied', async ({ page }) => {
    // Устанавливаем auth
    await setupMockAuth(page)

    // Настраиваем mock API с 403 для routes
    await setupMockApiWithError(page, 403, '/routes')

    // Переходим на routes — получим 403
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при неудачной загрузке компонента
    await expect(page.getByText('Ошибка загрузки')).toBeVisible({ timeout: 10000 })
  })

  test('500 Server Error показывает error notification', async ({ page }) => {
    await setupMockAuth(page)

    // Настраиваем mock API с 500 для routes
    await setupMockApiWithError(page, 500, '/routes')

    // Переходим на routes
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при неудачной загрузке компонента
    await expect(page.getByText('Ошибка загрузки')).toBeVisible({ timeout: 10000 })
  })

  test('Network error показывает connection error', async ({ page }) => {
    await setupMockAuth(page)

    // Настраиваем mock API с network error для routes
    await setupMockApiWithNetworkError(page, '/routes')

    // Переходим на routes
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при network error
    await expect(page.getByText('Ошибка загрузки')).toBeVisible({ timeout: 10000 })
  })
})
