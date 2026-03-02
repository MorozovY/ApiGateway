// E2E тест: Детали маршрута с историей
// Story 13.15: P1 тест — отображение деталей и история изменений

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockRoutes } from '../fixtures/api.fixture'

test.describe('Детали маршрута', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('отображает основную информацию о маршруте', async ({ page }) => {
    const route = mockRoutes[0]

    await page.goto(`/routes/${route.id}`)

    // Path отображается в заголовке
    await expect(page.getByRole('heading', { name: route.path })).toBeVisible()

    // Tabs: Детали и История
    await expect(page.getByRole('tab', { name: /детали/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /история/i })).toBeVisible()
  })

  test('отображает конфигурацию маршрута', async ({ page }) => {
    const route = mockRoutes[0]

    await page.goto(`/routes/${route.id}`)

    // Upstream URL
    await expect(page.getByText(route.upstreamUrl)).toBeVisible()

    // Methods
    for (const method of route.methods) {
      await expect(page.getByText(method, { exact: true })).toBeVisible()
    }
  })

  test('tab История показывает историю изменений', async ({ page }) => {
    const route = mockRoutes[0]

    await page.goto(`/routes/${route.id}`)

    // Переключаемся на tab История
    await page.getByRole('tab', { name: /история/i }).click()

    // URL обновился с hash
    await expect(page).toHaveURL(new RegExp(`#history`))

    // Tab История активен
    await expect(page.getByRole('tab', { name: /история/i })).toHaveAttribute('aria-selected', 'true')
  })

  test('кнопка Назад возвращает на список маршрутов', async ({ page }) => {
    const route = mockRoutes[0]

    // Переходим со страницы списка
    await page.goto('/routes')
    await page.getByRole('link', { name: route.path }).click()

    // Проверяем что на странице деталей
    await expect(page).toHaveURL(`/routes/${route.id}`)

    // Нажимаем Назад
    await page.getByRole('button', { name: /назад/i }).click()

    // Возврат на список
    await expect(page).toHaveURL('/routes')
  })

  test('rejected маршрут показывает статус Отклонён', async ({ page }) => {
    const rejectedRoute = mockRoutes.find((r) => r.status === 'rejected')!

    await page.goto(`/routes/${rejectedRoute.id}`)

    // Статус rejected (Отклонён)
    await expect(page.getByText('Отклонён')).toBeVisible()

    // Заметка: причина отклонения видна только владельцу (canResubmit)
    // В mock данных rejected route создан другим пользователем
  })

  test('published маршрут показывает кнопку Rollback для admin', async ({ page }) => {
    const publishedRoute = mockRoutes.find((r) => r.status === 'published')!

    await page.goto(`/routes/${publishedRoute.id}`)

    // Статус published (Опубликован)
    await expect(page.getByText('Опубликован')).toBeVisible()

    // Кнопка Rollback видна для admin
    await expect(page.getByRole('button', { name: /откатить/i })).toBeVisible()
  })
})
