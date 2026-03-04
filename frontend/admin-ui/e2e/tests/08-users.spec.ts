// E2E тест: Users — список пользователей (admin only)
// Story 13.15: P2 тест — базовый CRUD пользователей

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockUsers } from '../fixtures/api.fixture'

test.describe('Users', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('страница пользователей доступна для admin', async ({ page }) => {
    await page.goto('/users')

    // Заголовок страницы
    await expect(page.getByRole('heading', { name: /users|пользователи/i })).toBeVisible()
  })

  test('таблица отображает список пользователей', async ({ page }) => {
    await page.goto('/users')

    // Пользователи из mock данных видны в таблице
    const table = page.locator('.ant-table')
    for (const user of mockUsers) {
      await expect(table.getByText(user.email)).toBeVisible()
    }
  })

  test('таблица содержит колонки Username, Email, Role', async ({ page }) => {
    await page.goto('/users')

    await expect(page.getByRole('columnheader', { name: /пользователь/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /email/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /роль/i })).toBeVisible()
  })

  test('навигация на Users через Sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    const sidebar = page.locator('.ant-layout-sider')
    await sidebar.getByText('Пользователи').click()

    await expect(page).toHaveURL('/users')
  })
})
