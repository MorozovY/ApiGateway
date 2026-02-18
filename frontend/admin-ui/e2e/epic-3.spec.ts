import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * Уникальный суффикс для изоляции маршрутов между тест-ранами.
 * Используется для генерации уникальных path.
 */
const TIMESTAMP = Date.now()

/**
 * Epic 3 — Route Management.
 * Проверяет создание, редактирование, клонирование и удаление маршрутов.
 */
test.describe('Epic 3: Route Management', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')
  })

  test('Developer создаёт маршрут (draft) через форму', async ({ page }) => {
    await page.goto('/routes')
    await expect(page.locator('h2:has-text("Routes")')).toBeVisible()

    // Открываем форму создания
    await page.locator('button:has-text("New Route")').click()
    await expect(page).toHaveURL(/\/routes\/new/)

    // Заполняем форму маршрута
    const routePath = `e2e-route-${TIMESTAMP}`
    await page.locator('input[placeholder="api/service"]').fill(routePath)
    await page.locator('input[placeholder="http://service:8080"]').fill('http://test-service:9000')

    // Выбираем HTTP методы в Ant Design Select
    await page.locator('.ant-select').click()
    await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("GET")').click()
    await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("POST")').click()
    // Закрываем дропдаун
    await page.keyboard.press('Escape')

    // Отправляем форму
    await page.locator('button:has-text("Save as Draft")').click()

    // Ожидаем редирект на список маршрутов или детали
    await expect(page).toHaveURL(/\/routes/)

    // Новый маршрут должен отображаться
    await expect(page.locator(`text=/${routePath}`)).toBeVisible()
  })

  test('Developer редактирует существующий маршрут', async ({ page }) => {
    await page.goto('/routes')

    // Создаём маршрут через API для надёжности теста
    const routePath = `e2e-edit-${TIMESTAMP}`
    const createResponse = await page.request.post('http://localhost:8081/api/v1/routes', {
      data: {
        path: `/${routePath}`,
        upstreamUrl: 'http://original-service:8000',
        methods: ['GET'],
      },
    })
    expect(createResponse.ok()).toBeTruthy()
    const createdRoute = await createResponse.json() as { id: string }

    // Открываем страницу редактирования
    await page.goto(`/routes/${createdRoute.id}/edit`)
    await expect(page.locator('button:has-text("Save as Draft")')).toBeVisible()

    // Изменяем upstream URL
    const upstreamInput = page.locator('input[placeholder="http://service:8080"]')
    await upstreamInput.clear()
    await upstreamInput.fill('http://updated-service:9000')

    // Сохраняем изменения
    await page.locator('button:has-text("Save as Draft")').click()

    // Ожидаем сохранения и редиректа
    await expect(page).toHaveURL(/\/routes/)

    // Проверяем обновлённый маршрут в деталях
    await page.goto(`/routes/${createdRoute.id}`)
    await expect(page.locator('text=http://updated-service:9000')).toBeVisible()
  })

  test('Developer клонирует маршрут', async ({ page }) => {
    // Создаём маршрут через API
    const routePath = `e2e-clone-${TIMESTAMP}`
    const createResponse = await page.request.post('http://localhost:8081/api/v1/routes', {
      data: {
        path: `/${routePath}`,
        upstreamUrl: 'http://clone-source:8000',
        methods: ['GET', 'POST'],
      },
    })
    expect(createResponse.ok()).toBeTruthy()
    const createdRoute = await createResponse.json() as { id: string }

    // Открываем детали маршрута
    await page.goto(`/routes/${createdRoute.id}`)
    await expect(page.locator('button:has-text("Клонировать")')).toBeVisible()

    // Клонируем маршрут
    await page.locator('button:has-text("Клонировать")').click()

    // Ожидаем редиректа на редактирование клона
    await expect(page).toHaveURL(/\/routes\/.*\/edit/)

    // Форма должна содержать данные клона (upstream URL из оригинала)
    await expect(page.locator('input[placeholder="http://service:8080"]')).toHaveValue('http://clone-source:8000')
  })

  test('Developer удаляет draft маршрут', async ({ page }) => {
    await page.goto('/routes')

    // Создаём маршрут через API
    const routePath = `e2e-delete-${TIMESTAMP}`
    const createResponse = await page.request.post('http://localhost:8081/api/v1/routes', {
      data: {
        path: `/${routePath}`,
        upstreamUrl: 'http://delete-me:8000',
        methods: ['DELETE'],
      },
    })
    expect(createResponse.ok()).toBeTruthy()

    // Обновляем страницу для отображения нового маршрута
    await page.reload()
    await expect(page.locator(`text=/${routePath}`)).toBeVisible()

    // Нажимаем кнопку удаления (иконка корзины в строке маршрута)
    const routeRow = page.locator(`tr:has-text("/${routePath}")`)
    await routeRow.locator('button[aria-label="delete"]').click()

    // Подтверждаем удаление в Popconfirm
    await page.locator('.ant-popconfirm button:has-text("Да")').click()

    // Маршрут должен исчезнуть из таблицы
    await expect(page.locator(`text=/${routePath}`)).not.toBeVisible()
  })
})
