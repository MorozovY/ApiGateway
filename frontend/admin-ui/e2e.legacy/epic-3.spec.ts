import { test, expect, type Page } from '@playwright/test'
import { login, apiRequest } from './helpers/auth'

/**
 * Уникальный суффикс для изоляции маршрутов между тест-ранами.
 * Генерируется per-test для изоляции.
 */
let TIMESTAMP: number

/**
 * Хранилище для cleanup ресурсов после тестов (Story 15.5).
 */
const createdRouteIds: string[] = []

/**
 * Вспомогательная функция заполнения формы маршрута.
 * Используется в нескольких тестах.
 */
async function fillRouteForm(page: Page, routePath: string, upstreamUrl: string): Promise<void> {
  // Ждём загрузки формы (поля должны быть интерактивными)
  const pathInput = page.locator('input[placeholder="api/service"]')
  const upstreamInput = page.locator('input[placeholder="http://service:8080"]')

  await pathInput.waitFor({ state: 'visible' })
  await upstreamInput.waitFor({ state: 'visible' })

  // Заполняем поля и проверяем что значения записались
  await pathInput.fill(routePath)
  await expect(pathInput).toHaveValue(routePath, { timeout: 5_000 })

  await upstreamInput.fill(upstreamUrl)
  await expect(upstreamInput).toHaveValue(upstreamUrl, { timeout: 5_000 })

  // Выбор методов в Ant Design Select (multiple mode)
  await page.getByTestId('methods-select').locator('.ant-select-selector').click()
  // Ждём появления dropdown перед кликом по опциям
  await page.locator('.ant-select-dropdown').waitFor({ state: 'visible' })
  await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("GET")').click()
  await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("POST")').click()
  // Закрываем дропдаун и ждём его исчезновения
  await page.keyboard.press('Escape')
  await page.locator('.ant-select-dropdown').waitFor({ state: 'hidden' })
}

/**
 * Epic 3 — Route Management.
 * Проверяет создание, редактирование, клонирование и удаление маршрутов через UI.
 *
 * Все тесты используют только SPA-навигацию после первоначального login(),
 * чтобы не сбрасывать React-состояние (AuthContext) при page.goto().
 *
 * Story 15.5: Добавлен afterEach cleanup для удаления созданных маршрутов.
 */
test.describe('Epic 3: Route Management', () => {
  test.beforeEach(async ({ page }) => {
    // Генерируем уникальный timestamp для каждого теста
    TIMESTAMP = Date.now()

    // Переходим на /routes через returnUrl механизм
    await login(page, 'test-developer', 'Test1234!', '/routes')
    await expect(page.locator('h2:has-text("Routes")')).toBeVisible()

    // Ждём инициализации AuthContext — user menu button появляется после setTokenGetter
    await expect(page.locator('[data-testid="user-menu-button"]')).toBeVisible({ timeout: 5000 })
  })

  // Story 15.5: Cleanup созданных маршрутов после каждого теста
  test.afterEach(async ({ page }) => {
    try {
      // Используем текущую сессию для удаления (developer может удалять draft маршруты)
      for (const routeId of createdRouteIds) {
        const response = await apiRequest(page, 'DELETE', `/api/v1/routes/${routeId}`)
        // 200, 204 — успех; 404 — уже удалён (в тесте delete или через global-teardown)
        if (![200, 204, 404].includes(response.status())) {
          console.warn(`[E2E Cleanup] Не удалось удалить маршрут ${routeId}: ${response.status()}`)
        }
      }
    } catch {
      // Игнорируем ошибки cleanup — тесты не должны падать из-за cleanup
      console.warn('[E2E Cleanup] Ошибка при очистке маршрутов')
    } finally {
      // Очищаем список
      createdRouteIds.length = 0
    }
  })

  test('Developer создаёт маршрут (draft) через форму', async ({ page }) => {
    const routePath = `e2e-create-${TIMESTAMP}`

    // Открываем форму создания (SPA-навигация)
    await page.locator('button:has-text("New Route")').click()
    await expect(page).toHaveURL(/\/routes\/new/)

    await fillRouteForm(page, routePath, 'http://new-service.local:9000')
    await page.locator('button:has-text("Save as Draft")').click()

    // После сохранения — редирект на страницу деталей маршрута (SPA)
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })

    // Story 15.5: Извлекаем ID маршрута для cleanup
    const url = page.url()
    const routeId = url.split('/routes/')[1]
    if (routeId) {
      createdRouteIds.push(routeId)
    }

    // Маршрут создан и отображается на странице деталей
    await expect(page.locator(`text=/${routePath}`)).toBeVisible()
  })

  test('Developer редактирует существующий маршрут', async ({ page }) => {
    const routePath = `e2e-edit-${TIMESTAMP}`

    // Создаём маршрут через UI
    await page.locator('button:has-text("New Route")').click()
    await fillRouteForm(page, routePath, 'http://original-service.local:8000')
    await page.locator('button:has-text("Save as Draft")').click()
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })

    // Story 15.5: Извлекаем ID маршрута для cleanup
    const createUrl = page.url()
    const routeId = createUrl.split('/routes/')[1]
    if (routeId) {
      createdRouteIds.push(routeId)
    }

    // Открываем редактирование (SPA-навигация через кнопку на детальной странице)
    await page.locator('button:has-text("Редактировать")').click()
    await expect(page).toHaveURL(/\/routes\/.*\/edit/)

    // Изменяем upstream URL
    const upstreamInput = page.locator('input[placeholder="http://service:8080"]')
    await upstreamInput.clear()
    await upstreamInput.fill('http://updated-service.local:9001')

    await page.locator('button:has-text("Save as Draft")').click()

    // После сохранения — обратно на детали маршрута (SPA)
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })
    await expect(page.locator('text=http://updated-service.local:9001')).toBeVisible()
  })

  test('Developer клонирует маршрут', async ({ page }) => {
    const routePath = `e2e-clone-${TIMESTAMP}`

    // Создаём маршрут через UI
    await page.locator('button:has-text("New Route")').click()
    await fillRouteForm(page, routePath, 'http://clone-source.local:8001')
    await page.locator('button:has-text("Save as Draft")').click()
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })

    // Story 15.5: Извлекаем ID оригинального маршрута для cleanup
    const createUrl = page.url()
    const originalRouteId = createUrl.split('/routes/')[1]
    if (originalRouteId) {
      createdRouteIds.push(originalRouteId)
    }

    // Клонируем маршрут (SPA-навигация)
    await page.locator('button:has-text("Клонировать")').click()

    // Ожидаем редиректа на редактирование клона
    await expect(page).toHaveURL(/\/routes\/.*\/edit/, { timeout: 10_000 })

    // Story 15.5: Извлекаем ID клонированного маршрута для cleanup
    const cloneUrl = page.url()
    const cloneRouteId = cloneUrl.split('/routes/')[1]?.replace('/edit', '')
    if (cloneRouteId) {
      createdRouteIds.push(cloneRouteId)
    }

    // Форма клона содержит upstream URL из оригинала
    await expect(page.locator('input[placeholder="http://service:8080"]')).toHaveValue(
      'http://clone-source.local:8001'
    )
  })

  test('Developer удаляет draft маршрут', async ({ page }) => {
    const routePath = `e2e-delete-${TIMESTAMP}`

    // Создаём маршрут через API (более надёжно чем UI)
    const createResponse = await apiRequest(page, 'POST', '/api/v1/routes', {
      path: `/${routePath}`,
      upstreamUrl: 'http://delete-me.local:8002',
      methods: ['GET', 'POST'],
    })
    expect(createResponse.ok()).toBeTruthy()

    // Story 15.5: Маршрут будет удалён в тесте, но добавляем в cleanup на случай падения теста
    const routeData = await createResponse.json() as { id: string }
    createdRouteIds.push(routeData.id)

    // Перезагружаем страницу чтобы увидеть новый маршрут
    await page.reload()
    await expect(page.locator('h2:has-text("Routes")')).toBeVisible()

    // Маршрут появляется в таблице
    await expect(page.locator(`a:has-text("/${routePath}")`)).toBeVisible({ timeout: 10_000 })

    // Нажимаем кнопку удаления в строке маршрута
    const routeRow = page.locator(`tr:has-text("/${routePath}")`)
    await routeRow.locator('button:has(.anticon-delete)').click()

    // Подтверждаем удаление в Popconfirm
    await page.locator('.ant-popconfirm button:has-text("Да")').click()

    // Маршрут исчезает из таблицы
    await expect(page.locator(`text=/${routePath}`)).not.toBeVisible({ timeout: 10_000 })
  })
})
