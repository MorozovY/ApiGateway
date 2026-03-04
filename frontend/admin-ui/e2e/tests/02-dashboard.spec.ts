// E2E тест: Dashboard — отображение после логина
// Story 13.15: P0 тест — Dashboard виджеты и навигация

import { test, expect } from '@playwright/test'
import { setupMockAuth, mockAdminUser } from '../fixtures/auth.fixture'
import { setupMockApi } from '../fixtures/api.fixture'

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Устанавливаем mock auth и API
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('отображает приветствие с username и role', async ({ page }) => {
    await page.goto('/dashboard')

    // Приветствие с именем пользователя
    await expect(page.getByText(`Добро пожаловать, ${mockAdminUser.username}!`)).toBeVisible()

    // Роль пользователя (Story 16.1: локализация на русский)
    await expect(page.getByText(/Роль:\s*Администратор/i)).toBeVisible()
  })

  // Story 16.3: тест для кнопки Logout удалён — Logout теперь доступен через user dropdown в header
  test('отображает user menu с опцией Logout', async ({ page }) => {
    await page.goto('/dashboard')

    // User menu button видна в header
    const userMenuButton = page.getByTestId('user-menu-button')
    await expect(userMenuButton).toBeVisible()

    // Открываем dropdown
    await userMenuButton.click()

    // Пункт "Выйти" виден в dropdown
    await expect(page.getByText('Выйти')).toBeVisible()
  })

  test('Sidebar содержит основные пункты меню', async ({ page }) => {
    await page.goto('/dashboard')

    // Проверяем наличие основных пунктов меню (через текст в sidebar)
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Главная')).toBeVisible()
    await expect(sidebar.getByText('Маршруты')).toBeVisible()
  })

  test('Sidebar показывает admin-only пункты для admin', async ({ page }) => {
    await page.goto('/dashboard')

    const sidebar = page.locator('.ant-layout-sider')

    // Admin видит Users и Rate Limits (admin-only)
    await expect(sidebar.getByText('Пользователи')).toBeVisible()
    await expect(sidebar.getByText('Лимиты')).toBeVisible()

    // Admin также видит Approvals и Audit (security + admin)
    await expect(sidebar.getByText('Согласования')).toBeVisible()
    await expect(sidebar.getByText('Аудит')).toBeVisible()
  })

  test('навигация на Routes через Sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    // Кликаем на Routes в меню
    const sidebar = page.locator('.ant-layout-sider')
    await sidebar.getByText('Маршруты').click()

    // URL изменился
    await expect(page).toHaveURL('/routes')
  })

  test('Logout через user dropdown редиректит на страницу логина', async ({ page }) => {
    await page.goto('/dashboard')

    // Mock Keycloak logout endpoint
    await page.route('**/realms/*/protocol/openid-connect/logout', async (route) => {
      await route.fulfill({ status: 204 })
    })

    // Открываем user dropdown (Story 16.3 — logout через header)
    await page.getByTestId('user-menu-button').click()

    // Кликаем "Выйти" в dropdown
    await page.getByText('Выйти').click()

    // Редирект на login
    await expect(page).toHaveURL('/login')
  })
})
