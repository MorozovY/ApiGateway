// E2E тест: RBAC Developer — ограничения доступа для роли developer
// Story 14.6: AC1 — Developer role restrictions (3 теста)

import { test, expect } from '@playwright/test'
import { setupMockAuthWithRole } from '../fixtures/auth.fixture'
import { setupMockApi } from '../fixtures/api.fixture'

test.describe('RBAC Developer', () => {
  test.beforeEach(async ({ page }) => {
    // Устанавливаем авторизацию с ролью developer
    await setupMockAuthWithRole(page, 'developer')
    await setupMockApi(page)
  })

  test('Developer не видит Users в sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    // Ждём загрузки sidebar через locator
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Главная')).toBeVisible()

    // Users не должен отображаться для developer
    await expect(sidebar.getByText('Пользователи')).not.toBeVisible()

    // Проверяем что другие пункты видимы (Routes, Metrics)
    await expect(sidebar.getByText('Маршруты')).toBeVisible()
    await expect(sidebar.getByText('Метрики')).toBeVisible()
  })

  test('Developer не видит Rate Limits в sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    // Ждём загрузки sidebar
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Главная')).toBeVisible()

    // Rate Limits не должен отображаться для developer
    await expect(sidebar.getByText('Лимиты')).not.toBeVisible()

    // Также не должно быть Consumers (только для admin)
    await expect(sidebar.getByText('Потребители')).not.toBeVisible()

    // Approvals также не доступен для developer
    await expect(sidebar.getByText('Согласования')).not.toBeVisible()
  })

  test('Developer redirect из /approvals', async ({ page }) => {
    // Пытаемся перейти на страницу Approvals
    await page.goto('/approvals')

    // Developer должен быть редиректнут на /dashboard
    await expect(page).toHaveURL(/\/dashboard/)

    // Подтверждаем что контент страницы Approvals не виден
    await expect(page.getByRole('heading', { name: /согласование маршрутов/i })).not.toBeVisible()
  })
})
