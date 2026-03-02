// E2E тест: Создание маршрута
// Story 13.15: P0 тест — создание маршрута и появление в списке

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi } from '../fixtures/api.fixture'

test.describe('Создание маршрута', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('кнопка New Route ведёт на форму создания', async ({ page }) => {
    await page.goto('/routes')

    // Кликаем "New Route"
    await page.getByRole('button', { name: /new route/i }).click()

    // Переход на страницу создания
    await expect(page).toHaveURL('/routes/new')

    // Заголовок страницы
    await expect(page.getByRole('heading', { name: 'Create Route' })).toBeVisible()
  })

  test('форма создания отображает все поля', async ({ page }) => {
    await page.goto('/routes/new')

    // Проверяем наличие полей
    await expect(page.getByLabel('Path')).toBeVisible()
    await expect(page.getByLabel('Upstream URL')).toBeVisible()
    await expect(page.getByLabel('HTTP Methods')).toBeVisible()
    await expect(page.getByLabel('Rate Limit Policy')).toBeVisible()
    await expect(page.getByLabel('Description')).toBeVisible()

    // Кнопки
    await expect(page.getByRole('button', { name: /save as draft/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /cancel/i })).toBeVisible()
  })

  test('создание маршрута → появление в списке', async ({ page }) => {
    await page.goto('/routes/new')

    // Заполняем форму
    await page.getByLabel('Path').fill('api/v1/new-service')
    await page.getByLabel('Upstream URL').fill('http://new-service.local:8080')

    // Выбираем методы — используем locator для multiselect
    const methodsSelect = page.getByTestId('methods-select')
    await methodsSelect.click()
    // Ждём появления dropdown и выбираем опции
    await page.locator('.ant-select-item-option-content').filter({ hasText: 'GET' }).click()
    await page.locator('.ant-select-item-option-content').filter({ hasText: 'POST' }).click()
    // Закрываем dropdown нажатием Escape
    await page.keyboard.press('Escape')

    // Опциональные поля
    await page.getByLabel('Description').fill('New test service')

    // Сохраняем
    await page.getByRole('button', { name: /save as draft/i }).click()

    // Редирект на страницу деталей созданного маршрута
    await expect(page).toHaveURL(/\/routes\/route-new-/)

    // Возвращаемся к списку
    await page.goto('/routes')

    // Новый маршрут виден в списке
    await expect(page.getByRole('link', { name: '/api/v1/new-service' })).toBeVisible()
  })

  test('валидация обязательных полей', async ({ page }) => {
    await page.goto('/routes/new')

    // Пытаемся сохранить пустую форму
    await page.getByRole('button', { name: /save as draft/i }).click()

    // Сообщения об ошибках
    await expect(page.getByText('Path обязателен')).toBeVisible()
    await expect(page.getByText('Upstream URL обязателен')).toBeVisible()
    await expect(page.getByText('Выберите минимум один метод')).toBeVisible()
  })

  test('Cancel возвращает к списку маршрутов', async ({ page }) => {
    await page.goto('/routes/new')

    // Нажимаем Cancel
    await page.getByRole('button', { name: /cancel/i }).click()

    // Возврат к списку
    await expect(page).toHaveURL('/routes')
  })

  test('создание маршрута со статусом draft', async ({ page }) => {
    await page.goto('/routes/new')

    // Минимальное заполнение
    await page.getByLabel('Path').fill('api/v1/draft-test')
    await page.getByLabel('Upstream URL').fill('http://draft-service.local:8080')
    await page.getByTestId('methods-select').click()
    await page.locator('.ant-select-item-option-content').filter({ hasText: 'GET' }).click()
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: /save as draft/i }).click()

    // Переход на страницу деталей
    await expect(page).toHaveURL(/\/routes\/route-new-/)

    // На странице деталей статус draft (Черновик — на русском)
    await expect(page.getByText('Черновик')).toBeVisible()
  })
})
