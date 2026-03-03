// E2E тест: Login → Dashboard redirect
// Story 13.15: P0 тест — базовый flow авторизации

import { test, expect } from '@playwright/test'
import { setupMockAuth, mockAdminUser } from '../fixtures/auth.fixture'
import { setupMockApi } from '../fixtures/api.fixture'

test.describe('Логин', () => {
  test.beforeEach(async ({ page }) => {
    // Настраиваем mock API (без auth — тестируем login flow)
    await setupMockApi(page)
  })

  test('редирект на /login если не авторизован', async ({ page }) => {
    // Открываем dashboard без авторизации
    await page.goto('/dashboard')

    // Должен редиректнуть на login
    await expect(page).toHaveURL('/login')

    // Форма логина отображается
    await expect(page.getByTestId('username-input')).toBeVisible()
    await expect(page.getByTestId('password-input')).toBeVisible()
    await expect(page.getByTestId('login-button')).toBeVisible()
  })

  test('логин admin → редирект на Dashboard', async ({ page }) => {
    // Открываем страницу логина
    await page.goto('/login')

    // Mock Keycloak token endpoint
    await page.route('**/realms/*/protocol/openid-connect/token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          access_token: 'mock-access-token-for-e2e',
          refresh_token: 'mock-refresh-token',
          expires_in: 3600,
          refresh_expires_in: 86400,
          token_type: 'Bearer',
          scope: 'openid profile email',
        }),
      })
    })

    // Заполняем форму
    await page.getByTestId('username-input').fill('admin')
    await page.getByTestId('password-input').fill('password')

    // Нажимаем "Войти"
    await page.getByTestId('login-button').click()

    // Должен редиректнуть на dashboard
    await expect(page).toHaveURL('/dashboard')

    // Dashboard отображается
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  test('авторизованный пользователь сразу видит Dashboard', async ({ page }) => {
    // Устанавливаем mock auth
    await setupMockAuth(page)

    // Открываем корень сайта
    await page.goto('/')

    // Должен редиректнуть на dashboard (не на login)
    await expect(page).toHaveURL('/dashboard')

    // Приветствие с username
    await expect(page.getByText(`Welcome, ${mockAdminUser.username}!`)).toBeVisible()
  })

  // Story 15.1: проверка ссылки на Swagger UI
  test('ссылка на Swagger API документацию присутствует на странице логина', async ({ page }) => {
    // Открываем страницу логина
    await page.goto('/login')

    // Проверяем что ссылка на Swagger существует с правильным href
    const swaggerLink = page.locator('a[href="/swagger-ui.html"]')
    await expect(swaggerLink).toBeVisible()
    await expect(swaggerLink).toContainText('Swagger')
  })
})
