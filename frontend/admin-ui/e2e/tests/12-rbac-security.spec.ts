// E2E тест: RBAC Security — доступ и ограничения для роли security
// Story 14.6: AC2 — Security role access (3 теста)

import { test, expect } from '@playwright/test'
import { setupMockAuthWithRole } from '../fixtures/auth.fixture'
import { setupMockApi, mockRoutes } from '../fixtures/api.fixture'

test.describe('RBAC Security', () => {
  test.beforeEach(async ({ page }) => {
    // Устанавливаем авторизацию с ролью security
    await setupMockAuthWithRole(page, 'security')
    await setupMockApi(page)
  })

  test('Security видит и использует Approvals', async ({ page }) => {
    await page.goto('/dashboard')

    // Sidebar должен быть виден
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Dashboard')).toBeVisible()

    // Approvals должен быть виден в sidebar для security
    await expect(sidebar.getByText('Approvals')).toBeVisible()

    // Переходим на страницу Approvals
    await sidebar.getByText('Approvals').click()

    // Страница Approvals должна загрузиться (Story 15.6: английские заголовки)
    await expect(page.getByRole('heading', { name: 'Approvals' })).toBeVisible()

    // Pending маршрут из mock данных отображается
    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (pendingRoute) {
      await expect(page.getByText(pendingRoute.path)).toBeVisible()
    }
  })

  test('Security не видит Users в sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    // Ждём загрузки sidebar
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Dashboard')).toBeVisible()

    // Users не должен отображаться для security
    await expect(sidebar.getByText('Users')).not.toBeVisible()

    // Consumers также не доступен для security (только admin)
    await expect(sidebar.getByText('Consumers')).not.toBeVisible()
  })

  test('Security не видит Rate Limits create/delete', async ({ page }) => {
    await page.goto('/dashboard')

    // Ждём загрузки sidebar
    const sidebar = page.locator('.ant-layout-sider')
    await expect(sidebar.getByText('Dashboard')).toBeVisible()

    // Rate Limits не должен отображаться в sidebar для security
    await expect(sidebar.getByText('Rate Limits')).not.toBeVisible()

    // Проверяем что Audit доступен (как и должно быть для security)
    await expect(sidebar.getByText('Audit Logs')).toBeVisible()
  })
})
