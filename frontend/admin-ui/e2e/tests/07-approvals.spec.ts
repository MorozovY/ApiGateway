// E2E тест: Approvals — согласование маршрутов
// Story 13.15: P1 тест — список pending, approve/reject

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockRoutes } from '../fixtures/api.fixture'

test.describe('Approvals', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('страница согласования отображает pending маршруты', async ({ page }) => {
    await page.goto('/approvals')

    // Заголовок страницы (Story 15.6: английские заголовки)
    await expect(page.getByRole('heading', { name: 'Approvals' })).toBeVisible()

    // Pending маршрут из mock данных отображается
    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (pendingRoute) {
      await expect(page.getByRole('button', { name: pendingRoute.path })).toBeVisible()
    }
  })

  test('таблица содержит колонки Path, Upstream URL, Methods, Actions', async ({ page }) => {
    await page.goto('/approvals')

    // Проверяем заголовки колонок
    await expect(page.getByRole('columnheader', { name: 'Path' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Upstream URL' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Methods' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Actions' })).toBeVisible()
  })

  test('кнопка Approve одобряет маршрут', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    // Находим строку с pending маршрутом
    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })

    // Нажимаем Approve
    await row.getByRole('button', { name: /approve/i }).click()

    // После approve маршрут исчезает из списка (становится published)
    await expect(page.getByText(pendingRoute.path)).not.toBeVisible()
  })

  test('кнопка Reject открывает модальное окно', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })

    // Нажимаем Reject
    await row.getByRole('button', { name: /reject/i }).click()

    // Модальное окно появляется
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText(/причина отклонения/i)).toBeVisible()
  })

  test('Reject требует указать причину', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })
    await row.getByRole('button', { name: /reject/i }).click()

    // Пытаемся отклонить без причины
    await page.getByRole('button', { name: /отклонить/i }).click()

    // Ошибка валидации
    await expect(page.getByText(/укажите причину/i)).toBeVisible()
  })

  test('Reject с причиной отклоняет маршрут', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })
    await row.getByRole('button', { name: /reject/i }).click()

    // Вводим причину
    await page.getByRole('textbox').fill('Security requirements not met')

    // Подтверждаем
    await page.getByRole('button', { name: /отклонить/i }).click()

    // Ждём закрытия модального окна
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Маршрут исчезает из таблицы (проверяем в таблице, не во всей странице)
    const table = page.locator('.ant-table')
    await expect(table.getByRole('button', { name: pendingRoute.path })).not.toBeVisible()
  })

  test('пустое состояние когда нет pending маршрутов', async ({ page }) => {
    // Сначала одобряем единственный pending маршрут
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })
    await row.getByRole('button', { name: /approve/i }).click()

    // Пустое состояние отображается
    await expect(page.getByText(/нет маршрутов на согласовании/i)).toBeVisible()
  })

  test('кнопка Обновить перезагружает список', async ({ page }) => {
    await page.goto('/approvals')

    // Нажимаем Обновить
    await page.getByTestId('refresh-button').click()

    // Список обновился (проверяем что pending маршрут всё ещё виден)
    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (pendingRoute) {
      await expect(page.getByRole('button', { name: pendingRoute.path })).toBeVisible()
    }
  })
})
