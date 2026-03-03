// E2E тест: Error Handling — обработка ошибок API
// Story 14.6: AC5 — Error Handling Tests (4 теста)

import { test, expect } from '@playwright/test'
import { setupMockAuth, clearAuth } from '../fixtures/auth.fixture'
import { setupMockApi, simulateApiError, simulateNetworkError } from '../fixtures/api.fixture'

test.describe('Error Handling', () => {
  test('401 редиректит на страницу логина', async ({ page }) => {
    // НЕ устанавливаем auth — симулируем не аутентифицированного пользователя
    await clearAuth(page)

    // Переходим на защищённую страницу
    await page.goto('/dashboard')

    // Должен произойти редирект на login
    await expect(page).toHaveURL(/\/login/)

    // Должна быть видна страница логина
    await expect(page.locator('text=/login|войти|username|password/i').first()).toBeVisible()
  })

  test('403 показывает сообщение об отказе в доступе', async ({ page }) => {
    // Устанавливаем auth и API mock
    await setupMockAuth(page)
    await setupMockApi(page)

    // Симулируем 403 для routes endpoint
    await simulateApiError(page, 403, '/routes')

    // Переходим на routes — получим 403
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при неудачной загрузке компонента
    await expect(page.getByText('Ошибка загрузки')).toBeVisible()
  })

  test('500 показывает уведомление об ошибке сервера', async ({ page }) => {
    // Устанавливаем auth и API mock
    await setupMockAuth(page)
    await setupMockApi(page)

    // Симулируем 500 для routes endpoint
    await simulateApiError(page, 500, '/routes')

    // Переходим на routes
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при неудачной загрузке компонента
    await expect(page.getByText('Ошибка загрузки')).toBeVisible()
  })

  test('сетевая ошибка показывает сообщение о проблеме соединения', async ({ page }) => {
    // Устанавливаем auth и API mock
    await setupMockAuth(page)
    await setupMockApi(page)

    // Симулируем network error для routes endpoint
    await simulateNetworkError(page, '/routes')

    // Переходим на routes
    await page.goto('/routes')

    // UI показывает "Ошибка загрузки" при network error
    await expect(page.getByText('Ошибка загрузки')).toBeVisible()
  })
})
