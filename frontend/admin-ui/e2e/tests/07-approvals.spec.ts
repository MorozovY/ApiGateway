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

    // Заголовок страницы
    await expect(page.getByRole('heading', { name: 'Согласования' })).toBeVisible()

    // Pending маршрут из mock данных отображается
    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (pendingRoute) {
      await expect(page.getByRole('button', { name: pendingRoute.path })).toBeVisible()
    }
  })

  test('таблица содержит колонки Path, Upstream URL, Methods, Actions', async ({ page }) => {
    await page.goto('/approvals')

    // Проверяем заголовки колонок
    await expect(page.getByRole('columnheader', { name: 'Путь' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'URL сервиса' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Методы' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Действия' })).toBeVisible()
  })

  test('кнопка Approve одобряет маршрут', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    // Находим строку с pending маршрутом
    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })

    // Нажимаем Одобрить
    await row.getByRole('button', { name: /одобрить/i }).click()

    // После approve маршрут исчезает из списка (становится published)
    await expect(page.getByText(pendingRoute.path)).not.toBeVisible()
  })

  test('кнопка Reject открывает модальное окно', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })

    // Нажимаем Отклонить
    await row.getByRole('button', { name: /отклонить/i }).click()

    // Модальное окно появляется
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText(/причина отклонения/i)).toBeVisible()
  })

  test('Reject требует указать причину', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })
    await row.getByRole('button', { name: /отклонить/i }).click()

    // Пытаемся подтвердить отклонение без причины (кнопка "Отклонить" в модале)
    await page.getByRole('dialog').getByRole('button', { name: /отклонить/i }).click()

    // Ошибка валидации
    await expect(page.getByText(/укажите причину/i)).toBeVisible()
  })

  test('Reject с причиной отклоняет маршрут', async ({ page }) => {
    await page.goto('/approvals')

    const pendingRoute = mockRoutes.find((r) => r.status === 'pending')
    if (!pendingRoute) return

    const row = page.getByRole('row').filter({ hasText: pendingRoute.path })
    await row.getByRole('button', { name: /отклонить/i }).click()

    // Вводим причину
    await page.getByRole('textbox').fill('Security requirements not met')

    // Подтверждаем нажатием кнопки "Отклонить" в модальном окне (okText в Modal)
    await page.getByRole('dialog').getByRole('button', { name: /отклонить/i }).click()

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
    await row.getByRole('button', { name: /одобрить/i }).click()

    // Пустое состояние отображается
    await expect(page.getByText(/нет маршрутов на согласование/i)).toBeVisible()
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
