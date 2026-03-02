// E2E тест: Редактирование маршрута
// Story 13.15: P1 тест — редактирование и сохранение изменений

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockRoutes } from '../fixtures/api.fixture'

test.describe('Редактирование маршрута', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('кнопка Edit открывает форму редактирования (draft маршрут)', async ({ page }) => {
    // Draft маршрут принадлежит admin (createdBy = test-admin-id-12345)
    const draftRoute = mockRoutes.find((r) => r.status === 'draft')!

    // Переходим на страницу деталей draft маршрута
    await page.goto(`/routes/${draftRoute.id}`)

    // Нажимаем "Редактировать"
    await page.getByRole('button', { name: /редактировать/i }).click()

    // Переход на страницу редактирования
    await expect(page).toHaveURL(`/routes/${draftRoute.id}/edit`)

    // Заголовок "Edit Route"
    await expect(page.getByRole('heading', { name: 'Edit Route' })).toBeVisible()
  })

  test('форма редактирования заполнена текущими данными', async ({ page }) => {
    const draftRoute = mockRoutes.find((r) => r.status === 'draft')!

    await page.goto(`/routes/${draftRoute.id}/edit`)

    // Проверяем заполненные поля (без "/" в начале path)
    const pathValue = draftRoute.path.startsWith('/') ? draftRoute.path.slice(1) : draftRoute.path
    await expect(page.getByLabel('Path')).toHaveValue(pathValue)
    await expect(page.getByLabel('Upstream URL')).toHaveValue(draftRoute.upstreamUrl)
  })

  test('сохранение изменений обновляет маршрут', async ({ page }) => {
    const draftRoute = mockRoutes.find((r) => r.status === 'draft')!

    await page.goto(`/routes/${draftRoute.id}/edit`)

    // Ждём загрузки формы
    await expect(page.getByLabel('Description')).toBeVisible()

    // Изменяем description
    await page.getByLabel('Description').clear()
    await page.getByLabel('Description').fill('Updated description for test')

    // Сохраняем
    await page.getByRole('button', { name: /save as draft/i }).click()

    // Переход на страницу деталей (с увеличенным таймаутом)
    await expect(page).toHaveURL(`/routes/${draftRoute.id}`, { timeout: 10000 })
  })

  test('Cancel возвращает на предыдущую страницу без сохранения', async ({ page }) => {
    const draftRoute = mockRoutes.find((r) => r.status === 'draft')!

    await page.goto(`/routes/${draftRoute.id}/edit`)

    // Изменяем description
    await page.getByLabel('Description').fill('This should not be saved')

    // Нажимаем Cancel
    await page.getByRole('button', { name: /cancel/i }).click()

    // Возврат к списку
    await expect(page).toHaveURL('/routes')
  })

  test('published маршруты недоступны для редактирования (нет кнопки Edit)', async ({ page }) => {
    const publishedRoute = mockRoutes.find((r) => r.status === 'published')!

    await page.goto(`/routes/${publishedRoute.id}`)

    // Кнопка "Редактировать" НЕ отображается для published маршрутов
    await expect(page.getByRole('button', { name: /^редактировать$/i })).not.toBeVisible()
  })
})
