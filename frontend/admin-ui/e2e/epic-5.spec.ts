import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * Уникальный суффикс для изоляции данных между тест-ранами.
 */
const TIMESTAMP = Date.now()

/**
 * Создаёт политику rate limit через API.
 * Использует cookie текущей сессии (после login).
 */
async function createRateLimitPolicy(
  page: Page,
  name: string,
  requestsPerSecond: number,
  burstSize: number
): Promise<string> {
  const response = await page.request.post('http://localhost:3000/api/v1/rate-limits', {
    data: {
      name,
      description: 'E2E тестовая политика',
      requestsPerSecond,
      burstSize,
    },
  })
  expect(response.ok()).toBeTruthy()
  const policy = (await response.json()) as { id: string }
  return policy.id
}

/**
 * Создаёт маршрут с rate limit через API.
 * Не публикует — только создаёт draft.
 */
async function createRouteWithRateLimit(
  page: Page,
  pathSuffix: string,
  rateLimitId: string
): Promise<string> {
  const response = await page.request.post('http://localhost:3000/api/v1/routes', {
    data: {
      path: `/e2e-rl-${pathSuffix}`,
      upstreamUrl: 'http://httpbin.org/get',
      methods: ['GET'],
      rateLimitId,
    },
  })
  expect(response.ok()).toBeTruthy()
  const route = (await response.json()) as { id: string }
  return route.id
}

/**
 * Создаёт маршрут с rate limit и публикует его (submit + approve).
 * Требует admin/security роль для approve.
 */
async function createPublishedRouteWithRateLimit(
  page: Page,
  pathSuffix: string,
  rateLimitId: string
): Promise<string> {
  // Создаём маршрут
  const routeId = await createRouteWithRateLimit(page, pathSuffix, rateLimitId)

  // Submit на согласование
  const submitResponse = await page.request.post(
    `http://localhost:3000/api/v1/routes/${routeId}/submit`
  )
  expect(submitResponse.ok()).toBeTruthy()

  // Approve (admin или security роль)
  const approveResponse = await page.request.post(
    `http://localhost:3000/api/v1/routes/${routeId}/approve`
  )
  expect(approveResponse.ok()).toBeTruthy()

  return routeId
}

/**
 * Удаляет политику rate limit через API.
 */
async function deleteRateLimitPolicy(page: Page, policyId: string): Promise<void> {
  const response = await page.request.delete(`http://localhost:3000/api/v1/rate-limits/${policyId}`)
  expect(response.ok()).toBeTruthy()
}

/**
 * Удаляет маршрут через API.
 */
async function deleteRoute(page: Page, routeId: string): Promise<void> {
  const response = await page.request.delete(`http://localhost:3000/api/v1/routes/${routeId}`)
  expect(response.ok()).toBeTruthy()
}

/**
 * Epic 5 — Rate Limiting.
 * E2E тесты для happy path сценариев Rate Limiting:
 * - Admin создаёт политику
 * - Developer назначает политику на маршрут
 * - Gateway применяет rate limiting
 * - Admin редактирует и удаляет политику
 */
test.describe('Epic 5: Rate Limiting', () => {
  test('Admin создаёт политику rate limit', async ({ page }) => {
    // AC1: Admin логинится и создаёт политику через UI
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')

    // Проверяем загрузку страницы
    await expect(page.locator('text=Rate Limit Policies')).toBeVisible({ timeout: 10_000 })

    // Нажимаем "New Policy" — открывается модальное окно
    await page.locator('button:has-text("New Policy")').click()
    await expect(page.locator('.ant-modal-title:has-text("New Policy")')).toBeVisible()

    // Заполняем форму
    const policyName = `e2e-create-${TIMESTAMP}`
    const modal = page.locator('.ant-modal')
    await modal.locator('input#name').fill(policyName)
    await modal.locator('input#requestsPerSecond').fill('10')
    await modal.locator('input#burstSize').fill('15')

    // Сохраняем — модал закрывается
    await modal.locator('button:has-text("Create")').click()
    await expect(page.locator('.ant-modal-title:has-text("New Policy")')).not.toBeVisible({
      timeout: 10_000,
    })

    // Проверяем появление политики в таблице
    const policyRow = page.locator(`tr:has-text("${policyName}")`)
    await expect(policyRow).toBeVisible({ timeout: 10_000 })

    // Cleanup: удаляем созданную политику через UI
    await policyRow.locator('button[type="button"]:has(.anticon-delete)').click()
    await page.locator('.ant-popconfirm button:has-text("Да")').click()
    await expect(policyRow).not.toBeVisible({ timeout: 10_000 })
  })

  // TODO: Ant Design Select interaction needs investigation - rateLimitId not being saved
  test.skip('Developer назначает политику на маршрут', async ({ page }) => {
    // AC2: Setup — создаём политику через API (admin)
    await login(page, 'test-admin', 'Test1234!', '/dashboard')
    const policyName = `e2e-assign-${TIMESTAMP}`
    const policyId = await createRateLimitPolicy(page, policyName, 5, 10)

    // Переключаемся на developer
    await login(page, 'test-developer', 'Test1234!', '/routes')
    await expect(page.locator('h2:has-text("Routes")')).toBeVisible({ timeout: 10_000 })

    // Создаём маршрут через UI
    await page.locator('button:has-text("New Route")').click()
    await expect(page).toHaveURL(/\/routes\/new/)

    // Заполняем форму маршрута
    const routePath = `e2e-assign-route-${TIMESTAMP}`
    await page.locator('input[placeholder="api/service"]').fill(routePath)
    await page.locator('input[placeholder="http://service:8080"]').fill('http://assign-test.local:8000')

    // Выбираем HTTP метод
    await page.locator('.ant-select-selector').first().click()
    await page.locator('.ant-select-dropdown').first().waitFor({ state: 'visible' })
    await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("GET")').click()
    await page.keyboard.press('Escape')
    await page.locator('.ant-select-dropdown').first().waitFor({ state: 'hidden' })

    // Выбираем Rate Limit Policy в dropdown (Story 5.5)
    // Используем Form.Item контейнер для поиска select
    const rateLimitFormItem = page.locator('.ant-form-item:has-text("Rate Limit Policy")')
    const rateLimitSelect = rateLimitFormItem.locator('.ant-select')
    await rateLimitSelect.click()
    // Ждём видимый dropdown
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { state: 'visible' })
    // Ждём пока политика появится и кликаем
    const policyOption = page.locator(`.ant-select-item-option[title*="${policyName}"]`)
    await expect(policyOption).toBeVisible({ timeout: 10_000 })
    await policyOption.click()
    // Проверяем что dropdown закрылся и значение выбрано
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { state: 'hidden', timeout: 5_000 })
    await expect(rateLimitSelect.locator('.ant-select-selection-item')).toContainText(policyName)

    // Сохраняем маршрут
    await page.locator('button:has-text("Save as Draft")').click()

    // Переходим на страницу деталей и извлекаем routeId из URL
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })
    const url = page.url()
    const routeId = url.split('/').pop()!

    // Проверяем отображение rate limit info в секции деталей
    await expect(page.locator(`text=${policyName}`)).toBeVisible()

    // Cleanup: удаляем маршрут, затем политику
    await login(page, 'test-admin', 'Test1234!', '/dashboard')
    await deleteRoute(page, routeId)
    await deleteRateLimitPolicy(page, policyId)
  })

  // TODO: Требует настройки Redis pub/sub для синхронизации gateway-core
  test.skip('Rate limiting применяется в Gateway', { timeout: 90_000 }, async ({ page }) => {
    // AC3: Setup — создаём published маршрут с rate limit
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Создаём политику с низким лимитом для быстрого теста
    const policyId = await createRateLimitPolicy(page, `e2e-gateway-${TIMESTAMP}`, 3, 5)

    // Создаём и публикуем маршрут
    const routePath = `gateway-${TIMESTAMP}`
    const routeId = await createPublishedRouteWithRateLimit(page, routePath, policyId)

    // Ожидаем синхронизации gateway с retry вместо фиксированной задержки
    const gatewayUrl = `http://localhost:8080/e2e-rl-${routePath}`
    let firstResponse: Awaited<ReturnType<typeof page.request.get>>

    // Ждём синхронизации gateway (TTL кеша 60 сек, если Redis pub/sub не работает)
    await expect(async () => {
      firstResponse = await page.request.get(gatewayUrl, { failOnStatusCode: false })
      expect(firstResponse.status()).toBe(200)
    }).toPass({ timeout: 65_000 })

    // Проверяем заголовки X-RateLimit-* (обязательно, не conditional)
    const headers = firstResponse!.headers()
    expect(headers['x-ratelimit-limit']).toBe('3')
    expect(Number(headers['x-ratelimit-remaining'])).toBeGreaterThanOrEqual(0)
    expect(headers['x-ratelimit-reset']).toBeTruthy()

    // Превышаем лимит — отправляем много запросов подряд
    for (let i = 0; i < 10; i++) {
      await page.request.get(gatewayUrl, { failOnStatusCode: false })
    }

    // Финальный запрос — ожидаем HTTP 429
    const overLimitResponse = await page.request.get(gatewayUrl, { failOnStatusCode: false })
    expect(overLimitResponse.status()).toBe(429)

    // Проверяем заголовок Retry-After
    const retryAfter = overLimitResponse.headers()['retry-after']
    expect(retryAfter).toBeTruthy()

    // Cleanup: удаляем маршрут и политику
    await deleteRoute(page, routeId)
    await deleteRateLimitPolicy(page, policyId)
  })

  // TODO: API requests via page.request may not have proper auth for rateLimitId assignment
  test.skip('Admin редактирует и удаляет политику', async ({ page }) => {
    // AC4: Setup — создаём политику через API
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')
    const policyName = `e2e-edit-${TIMESTAMP}`
    const policyId = await createRateLimitPolicy(page, policyName, 10, 20)

    // После API запроса нужен повторный login (сессия сбрасывается)
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')
    await expect(page.locator('text=Rate Limit Policies')).toBeVisible({ timeout: 10_000 })

    // --- Редактирование политики ---
    // Находим строку с политикой и нажимаем Edit
    const policyRow = page.locator(`tr:has-text("${policyName}")`)
    await expect(policyRow).toBeVisible({ timeout: 10_000 })
    await policyRow.locator('button[type="button"]:has(.anticon-edit)').click()

    // Ждём открытия модального окна редактирования
    await expect(page.locator('.ant-modal-title:has-text("Edit Policy")')).toBeVisible()

    // Изменяем requestsPerSecond
    const modal = page.locator('.ant-modal')
    await modal.locator('input#requestsPerSecond').clear()
    await modal.locator('input#requestsPerSecond').fill('15')

    // Сохраняем
    await modal.locator('button:has-text("Save")').click()
    await expect(page.locator('.ant-modal-title:has-text("Edit Policy")')).not.toBeVisible({
      timeout: 10_000,
    })

    // Проверяем обновлённое значение в таблице (колонка Requests/sec)
    await expect(policyRow.locator('td').nth(2)).toContainText('15')

    // --- Попытка удаления используемой политики (должна показать ошибку) ---
    // Создаём маршрут, использующий эту политику
    const usedRouteId = await createRouteWithRateLimit(page, `used-${TIMESTAMP}`, policyId)

    // После API запроса нужен login (сессия сбрасывается)
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')
    await expect(page.locator(`tr:has-text("${policyName}")`)).toBeVisible({ timeout: 10_000 })

    // Пытаемся удалить — нажимаем Delete
    const usedPolicyRow = page.locator(`tr:has-text("${policyName}")`)
    await usedPolicyRow.locator('button[type="button"]:has(.anticon-delete)').click()

    // Подтверждаем удаление в Popconfirm
    await page.locator('.ant-popconfirm button:has-text("Да")').click()

    // Ожидаем ошибку (политика используется) — проверяем notification
    await expect(
      page.locator('.ant-message-error, .ant-notification-notice-error')
    ).toBeVisible({ timeout: 10_000 })

    // --- Удаление неиспользуемой политики ---
    // Создаём новую политику для удаления
    const unusedPolicyName = `e2e-delete-${TIMESTAMP}`
    await createRateLimitPolicy(page, unusedPolicyName, 5, 10)

    // Обновляем страницу
    await page.reload()
    await expect(page.locator(`tr:has-text("${unusedPolicyName}")`)).toBeVisible({ timeout: 10_000 })

    // Удаляем неиспользуемую политику
    const unusedRow = page.locator(`tr:has-text("${unusedPolicyName}")`)
    await unusedRow.locator('button[type="button"]:has(.anticon-delete)').click()
    await page.locator('.ant-popconfirm button:has-text("Да")').click()

    // Политика исчезает из таблицы
    await expect(page.locator(`tr:has-text("${unusedPolicyName}")`)).not.toBeVisible({
      timeout: 10_000,
    })

    // Cleanup: удаляем маршрут, затем политику
    await deleteRoute(page, usedRouteId)
    await deleteRateLimitPolicy(page, policyId)
  })
})
