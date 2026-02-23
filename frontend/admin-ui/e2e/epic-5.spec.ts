import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'
import { filterTableByName } from './helpers/table'

/**
 * Уникальный суффикс для изоляции данных между тест-ранами.
 * Генерируется per-test для поддержки параллельного запуска в будущем.
 */
let TIMESTAMP: number

/**
 * Хранилище для cleanup ресурсов после тестов.
 */
interface TestResources {
  policyIds: string[]
  routeIds: string[]
}

const resources: TestResources = {
  policyIds: [],
  routeIds: [],
}

/**
 * Создаёт политику rate limit через API.
 * Использует cookie текущей сессии (после login).
 * Автоматически регистрирует ресурс для cleanup.
 */
async function createRateLimitPolicy(
  page: Page,
  name: string,
  requestsPerSecond: number,
  burstSize: number
): Promise<string> {
  const response = await page.request.post('/api/v1/rate-limits', {
    data: {
      name,
      description: 'E2E тестовая политика',
      requestsPerSecond,
      burstSize,
    },
  })
  expect(response.ok()).toBeTruthy()
  const policy = (await response.json()) as { id: string }
  resources.policyIds.push(policy.id)
  return policy.id
}

/**
 * Создаёт маршрут с rate limit через API.
 * Не публикует — только создаёт draft.
 * Автоматически регистрирует ресурс для cleanup.
 */
async function createRouteWithRateLimit(
  page: Page,
  pathSuffix: string,
  rateLimitId: string
): Promise<string> {
  // Используем /anything/* — httpbin возвращает 200 для любого пути после /anything
  const response = await page.request.post('/api/v1/routes', {
    data: {
      path: `/e2e-rl-${pathSuffix}`,
      upstreamUrl: 'http://httpbin.org/anything',
      methods: ['GET'],
      rateLimitId,
    },
  })
  expect(response.ok()).toBeTruthy()
  const route = (await response.json()) as { id: string }
  resources.routeIds.push(route.id)
  return route.id
}

/**
 * Создаёт маршрут с rate limit и публикует его (submit + approve).
 * Требует admin/security роль для approve.
 * Автоматически регистрирует ресурс для cleanup.
 */
async function createPublishedRouteWithRateLimit(
  page: Page,
  pathSuffix: string,
  rateLimitId: string
): Promise<string> {
  // Создаём маршрут
  const routeId = await createRouteWithRateLimit(page, pathSuffix, rateLimitId)

  // Submit на согласование
  const submitResponse = await page.request.post(`/api/v1/routes/${routeId}/submit`)
  expect(submitResponse.ok()).toBeTruthy()

  // Approve (admin или security роль)
  const approveResponse = await page.request.post(`/api/v1/routes/${routeId}/approve`)
  expect(approveResponse.ok()).toBeTruthy()

  return routeId
}

/**
 * Удаляет политику rate limit через API.
 * Идемпотентная операция — не падает на 404.
 */
async function deleteRateLimitPolicy(page: Page, policyId: string): Promise<void> {
  const response = await page.request.delete(`/api/v1/rate-limits/${policyId}`)
  // 200, 204 — успех; 404 — уже удалено; 409 — используется (игнорируем при cleanup)
  expect([200, 204, 404, 409].includes(response.status())).toBeTruthy()
}

/**
 * Удаляет маршрут через API.
 * Идемпотентная операция — не падает на 404.
 */
async function deleteRoute(page: Page, routeId: string): Promise<void> {
  const response = await page.request.delete(`/api/v1/routes/${routeId}`)
  // 200, 204 — успех; 404 — уже удалён
  expect([200, 204, 404].includes(response.status())).toBeTruthy()
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
  // Генерируем уникальный timestamp для каждого теста
  test.beforeEach(() => {
    TIMESTAMP = Date.now()
  })

  // Cleanup ресурсов после каждого теста
  test.afterEach(async ({ page }) => {
    // Логинимся как admin для cleanup (имеет права на удаление)
    try {
      await login(page, 'test-admin', 'Test1234!', '/dashboard')

      // Удаляем маршруты (сначала, т.к. они могут использовать политики)
      for (const routeId of resources.routeIds) {
        await deleteRoute(page, routeId)
      }

      // Удаляем политики
      for (const policyId of resources.policyIds) {
        await deleteRateLimitPolicy(page, policyId)
      }
    } catch {
      // Игнорируем ошибки cleanup — тесты не должны падать из-за cleanup
    } finally {
      // Очищаем списки
      resources.routeIds = []
      resources.policyIds = []
    }
  })

  test('Admin создаёт политику rate limit', async ({ page }) => {
    // AC1: Admin логинится и создаёт политику через UI
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')

    // Проверяем загрузку страницы
    await expect(page.locator('text=Rate Limit Policies')).toBeVisible({ timeout: 10_000 })

    // Нажимаем "New Policy" — открывается модальное окно
    await page.locator('button:has-text("New Policy")').click()
    await expect(page.locator('.ant-modal-title:has-text("New Policy")')).toBeVisible()

    // Заполняем форму используя data-testid
    const policyName = `e2e-create-${TIMESTAMP}`
    const modal = page.locator('.ant-modal')
    await modal.locator('[data-testid="policy-name-input"]').fill(policyName)
    await modal.locator('[data-testid="policy-requests-input"]').fill('10')
    await modal.locator('[data-testid="policy-burst-input"]').fill('15')

    // Сохраняем — модал закрывается
    await modal.locator('button:has-text("Create")').click()
    await expect(page.locator('.ant-modal-title:has-text("New Policy")')).not.toBeVisible({
      timeout: 10_000,
    })

    // Фильтруем таблицу по имени политики для изоляции от других данных
    await filterTableByName(page, policyName)

    // Проверяем появление политики в таблице
    const policyRow = page.locator(`tr:has-text("${policyName}")`)
    await expect(policyRow).toBeVisible({ timeout: 10_000 })

    // Получаем ID политики для cleanup (из кнопки delete)
    const deleteButton = policyRow.locator('button[data-testid^="delete-policy-"]')
    const testId = await deleteButton.getAttribute('data-testid')
    if (testId) {
      const policyId = testId.replace('delete-policy-', '')
      resources.policyIds.push(policyId)
    }
  })

  test('Developer назначает политику на маршрут', async ({ page }) => {
    // AC2: Setup — создаём политику через API (admin)
    await login(page, 'test-admin', 'Test1234!', '/dashboard')
    const policyName = `e2e-assign-${TIMESTAMP}`
    // policyId автоматически регистрируется для cleanup в createRateLimitPolicy
    await createRateLimitPolicy(page, policyName, 5, 10)

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

    // Выбираем Rate Limit Policy в dropdown используя data-testid
    const rateLimitSelect = page.locator('[data-testid="rate-limit-select"]')
    await rateLimitSelect.click()

    // Ждём видимый dropdown и выбираем политику по тексту
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      state: 'visible',
    })

    // Кликаем по опции содержащей имя политики
    const policyOption = page.locator(`.ant-select-item-option:has-text("${policyName}")`)
    await expect(policyOption).toBeVisible({ timeout: 10_000 })
    await policyOption.click()

    // Проверяем что dropdown закрылся и значение выбрано
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      state: 'hidden',
      timeout: 5_000,
    })
    await expect(rateLimitSelect.locator('.ant-select-selection-item')).toContainText(policyName)

    // Сохраняем маршрут
    await page.locator('button:has-text("Save as Draft")').click()

    // Переходим на страницу деталей и извлекаем routeId из URL
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })
    const url = page.url()
    const routeId = url.split('/').pop()!
    resources.routeIds.push(routeId)

    // Проверяем отображение rate limit info в секции деталей
    await expect(page.locator(`text=${policyName}`)).toBeVisible()
  })

  // Story 5.8: Rate limit политики синхронизируются немедленно через Redis pub/sub
  // Gateway-core подписан на канал ratelimit-cache-invalidation и обновляет кэш
  // Fallback: если политика не в кэше, загружается напрямую из БД
  test('Rate limiting применяется в Gateway', { timeout: 90_000 }, async ({ page }) => {
    // AC3: Setup — создаём published маршрут с rate limit
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Создаём политику с низким лимитом для быстрого теста
    // requestsPerSecond=2, burstSize=2 — позволяет ~4-5 запросов до 429
    const policyId = await createRateLimitPolicy(page, `e2e-gateway-${TIMESTAMP}`, 2, 2)

    // Создаём и публикуем маршрут
    const routePath = `gateway-${TIMESTAMP}`
    await createPublishedRouteWithRateLimit(page, routePath, policyId)

    // Ждём синхронизации кэша через Redis pub/sub (Story 5.8)
    // Политика должна быть доступна в течение 5 секунд (AC1)
    await page.waitForTimeout(3000)

    // Gateway URL для тестирования rate limit
    const gatewayUrl = `http://localhost:8080/e2e-rl-${routePath}`
    let firstResponse: Awaited<ReturnType<typeof page.request.get>>

    // Ждём синхронизации gateway с retry (fallback на Caffeine TTL если Redis недоступен)
    // Проверяем что маршрут доступен И rate limiting активен (X-RateLimit-Limit присутствует)
    await expect(async () => {
      firstResponse = await page.request.get(gatewayUrl, { failOnStatusCode: false })
      expect(firstResponse.status()).toBe(200)
      // Важно: ждём пока rate limit headers появятся (AC2)
      expect(firstResponse.headers()['x-ratelimit-limit']).toBe('2')
    }).toPass({ timeout: 30_000 })

    // Проверяем заголовки X-RateLimit-* (AC2)
    const headers = firstResponse!.headers()
    expect(headers['x-ratelimit-limit']).toBe('2')
    expect(Number(headers['x-ratelimit-remaining'])).toBeGreaterThanOrEqual(0)
    expect(headers['x-ratelimit-reset']).toBeTruthy()

    // Превышаем лимит — отправляем запросы ПАРАЛЛЕЛЬНО чтобы исчерпать bucket быстрее
    // При rate limit 2 req/s, burst 2 — bucket восполняется медленно,
    // поэтому параллельные запросы исчерпают его до восполнения
    const parallelRequests = Array.from({ length: 15 }, () =>
      page.request.get(gatewayUrl, { failOnStatusCode: false })
    )
    const responses = await Promise.all(parallelRequests)

    // Проверяем что хотя бы один запрос получил 429
    const response429 = responses.find(r => r.status() === 429)
    expect(response429).toBeTruthy()

    // Проверяем заголовок Retry-After
    const retryAfter = response429!.headers()['retry-after']
    expect(retryAfter).toBeTruthy()
  })

  // Story 5.9: Тест включён после добавления staleTime: 0 + refetchOnMount: 'always' в useRateLimits
  test('Admin редактирует и удаляет политику', async ({ page }) => {
    // AC4: Setup — создаём политику через API
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')
    const policyName = `e2e-edit-${TIMESTAMP}`
    const policyId = await createRateLimitPolicy(page, policyName, 10, 20)

    // Навигация через sidebar меню для триггера refetch (SPA навигация сохраняет auth state)
    // Story 5.9: staleTime: 0 гарантирует refetch при каждом mount компонента
    await page.getByRole('menuitem', { name: 'Rate Limits' }).click()
    await page.waitForURL(/\/rate-limits/)
    await expect(page.locator('text=Rate Limit Policies')).toBeVisible({ timeout: 10_000 })

    // --- Редактирование политики ---
    // Фильтруем таблицу для изоляции тестовых данных
    await filterTableByName(page, policyName)

    // Находим строку с политикой и нажимаем Edit
    const policyRow = page.locator(`tr:has-text("${policyName}")`)
    await expect(policyRow).toBeVisible({ timeout: 10_000 })
    await policyRow.locator(`[data-testid="edit-policy-${policyId}"]`).click()

    // Ждём открытия модального окна редактирования
    await expect(page.locator('.ant-modal-title:has-text("Edit Policy")')).toBeVisible()

    // Изменяем requestsPerSecond
    const modal = page.locator('.ant-modal')
    await modal.locator('[data-testid="policy-requests-input"]').clear()
    await modal.locator('[data-testid="policy-requests-input"]').fill('15')

    // Сохраняем
    await modal.locator('button:has-text("Save")').click()
    await expect(page.locator('.ant-modal-title:has-text("Edit Policy")')).not.toBeVisible({
      timeout: 10_000,
    })

    // Проверяем обновлённое значение в таблице (колонка Requests/sec)
    await expect(policyRow.locator('td').nth(2)).toContainText('15')

    // --- Попытка удаления используемой политики (должна показать ошибку) ---
    // Создаём маршрут, использующий эту политику (ID регистрируется автоматически для cleanup)
    await createRouteWithRateLimit(page, `used-${TIMESTAMP}`, policyId)

    // Навигация на другую страницу и обратно для триггера refetch (обновление usageCount)
    await page.getByRole('menuitem', { name: 'Dashboard' }).click()
    await page.waitForURL(/\/dashboard/)
    await page.getByRole('menuitem', { name: 'Rate Limits' }).click()
    await page.waitForURL(/\/rate-limits/)

    // Фильтруем таблицу для изоляции тестовых данных
    await filterTableByName(page, policyName)
    await expect(page.locator(`tr:has-text("${policyName}")`)).toBeVisible({ timeout: 10_000 })

    // Пытаемся удалить — нажимаем Delete
    const usedPolicyRow = page.locator(`tr:has-text("${policyName}")`)
    await usedPolicyRow.locator(`[data-testid="delete-policy-${policyId}"]`).click()

    // Подтверждаем удаление в Popconfirm
    await page.locator('[data-testid="confirm-delete-policy"]').click()

    // Ожидаем ошибку (политика используется) — проверяем notification
    await expect(
      page.locator('.ant-message-error, .ant-notification-notice-error')
    ).toBeVisible({ timeout: 10_000 })

    // --- Удаление неиспользуемой политики ---
    // Создаём новую политику для удаления
    const unusedPolicyName = `e2e-delete-${TIMESTAMP}`
    const unusedPolicyId = await createRateLimitPolicy(page, unusedPolicyName, 5, 10)

    // Навигация на другую страницу и обратно для триггера refetch (отображение новой политики)
    await page.getByRole('menuitem', { name: 'Dashboard' }).click()
    await page.waitForURL(/\/dashboard/)
    await page.getByRole('menuitem', { name: 'Rate Limits' }).click()
    await page.waitForURL(/\/rate-limits/)

    // Фильтруем таблицу по новой политике
    await filterTableByName(page, unusedPolicyName)
    await expect(page.locator(`tr:has-text("${unusedPolicyName}")`)).toBeVisible({ timeout: 10_000 })

    // Удаляем неиспользуемую политику
    const unusedRow = page.locator(`tr:has-text("${unusedPolicyName}")`)
    await unusedRow.locator(`[data-testid="delete-policy-${unusedPolicyId}"]`).click()
    await page.locator('[data-testid="confirm-delete-policy"]').click()

    // Политика исчезает из таблицы
    await expect(page.locator(`tr:has-text("${unusedPolicyName}")`)).not.toBeVisible({
      timeout: 10_000,
    })

    // Убираем из cleanup т.к. уже удалено через UI
    resources.policyIds = resources.policyIds.filter((id) => id !== unusedPolicyId)

    // Cleanup: удаляем маршрут, затем политику (в afterEach)
    // Маршрут usedRouteId уже в resources.routeIds
    // Политика policyId уже в resources.policyIds
  })
})
