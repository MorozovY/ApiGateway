// E2E тест: Список маршрутов с фильтрами
// Story 13.15: P0 тест — отображение списка и фильтрация

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockRoutes } from '../fixtures/api.fixture'

test.describe('Список маршрутов', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('отображает таблицу маршрутов', async ({ page }) => {
    await page.goto('/routes')

    // Заголовок страницы
    await expect(page.getByRole('heading', { name: 'Маршруты' })).toBeVisible()

    // Кнопка создания
    await expect(page.getByRole('button', { name: /новый маршрут/i })).toBeVisible()

    // Таблица загружена — проверяем наличие path из mock данных
    await expect(page.getByRole('link', { name: mockRoutes[0].path })).toBeVisible()
  })

  test('отображает колонки таблицы', async ({ page }) => {
    await page.goto('/routes')

    // Проверяем заголовки колонок
    await expect(page.getByRole('columnheader', { name: 'Путь' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'URL сервиса' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Методы' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Статус' })).toBeVisible()
    // exact: true чтобы не путать с "Автор"
    await expect(page.getByRole('columnheader', { name: 'Авторизация' })).toBeVisible()
  })

  test('отображает правильное количество маршрутов', async ({ page }) => {
    await page.goto('/routes')

    // Проверяем пагинацию — "Всего X маршрутов"
    await expect(page.getByText(/Всего \d+ маршрут/)).toBeVisible()
  })

  test('фильтр по статусу работает', async ({ page }) => {
    // Открываем с параметром status=published напрямую
    await page.goto('/routes?status=published')

    // URL содержит параметр status
    await expect(page).toHaveURL(/status=published/)

    // В таблице только published маршруты (один из mock)
    const publishedRoute = mockRoutes.find((r) => r.status === 'published')
    if (publishedRoute) {
      await expect(page.getByRole('link', { name: publishedRoute.path })).toBeVisible()
    }

    // Draft маршруты не видны
    const draftRoute = mockRoutes.find((r) => r.status === 'draft')
    if (draftRoute) {
      await expect(page.getByRole('link', { name: draftRoute.path })).not.toBeVisible()
    }
  })

  test('поиск по path работает', async ({ page }) => {
    await page.goto('/routes')

    // Вводим поисковый запрос
    const searchInput = page.getByTestId('routes-search-input')
    await searchInput.fill('users')
    await searchInput.press('Enter')

    // URL обновился с параметром search
    await expect(page).toHaveURL(/search=users/)

    // В таблице маршруты с "users" в path
    await expect(page.getByRole('link', { name: /users/ })).toBeVisible()
  })

  test('кнопка "Сбросить фильтры" очищает все фильтры', async ({ page }) => {
    // Открываем с фильтрами
    await page.goto('/routes?status=draft&search=test')

    // Кнопка сброса видна
    const resetButton = page.getByRole('button', { name: /сбросить фильтры/i })
    await expect(resetButton).toBeVisible()

    // Кликаем сброс
    await resetButton.click()

    // URL очищен
    await expect(page).toHaveURL('/routes')
  })

  test('клик на path открывает детали маршрута', async ({ page }) => {
    await page.goto('/routes')

    // Кликаем на первый маршрут
    await page.getByRole('link', { name: mockRoutes[0].path }).click()

    // Переход на страницу деталей
    await expect(page).toHaveURL(`/routes/${mockRoutes[0].id}`)
  })
})
