import { test, expect, type Page } from '@playwright/test'
import { login, logout, apiRequest } from './helpers/auth'

// =============================================================================
// Константы для timeouts
// =============================================================================

/** Timeout для сложных тестов (несколько операций, load generator) */
const TEST_TIMEOUT_LONG = 90_000

/** Ожидание синхронизации UI после операций */
const UI_SYNC_DELAY = 2000

/** Timeout для появления UI элементов */
const UI_ELEMENT_TIMEOUT = 15_000

/** Timeout для загрузки таблиц с данными */
const TABLE_LOAD_TIMEOUT = 30_000

/** Ожидание синхронизации gateway-core с новыми маршрутами (увеличено для стабильности) */
const GATEWAY_SYNC_DELAY = 5000

// =============================================================================
// Helper функции для SPA навигации через меню
// =============================================================================

/**
 * Переходит на страницу через клик по меню.
 * Решает проблему с page.goto() в SPA (React Router не реагирует на goto).
 */
async function navigateToMenu(page: Page, menuItemText: string | RegExp): Promise<void> {
  const menuItem = page.locator('[role="menuitem"]').filter({ hasText: menuItemText })
  await menuItem.click()
  // Ждём завершения SPA навигации — проверяем что menuItem стал активным
  await expect(menuItem).toHaveClass(/ant-menu-item-selected/, { timeout: 5000 })
}

// =============================================================================
// Интерфейсы для изоляции тестовых данных
// =============================================================================

/** Хранилище ресурсов для cleanup после тестов */
interface TestResources {
  routeIds: string[]
}

// =============================================================================
// Helper функции для работы с Routes API
// =============================================================================

/**
 * Создаёт маршрут через API.
 * Автоматически регистрирует ресурс для cleanup.
 *
 * @param page - Playwright Page объект
 * @param pathSuffix - Суффикс для path (уникальное имя)
 * @param resources - Объект для хранения ID созданных ресурсов
 * @param upstreamUrl - URL upstream сервиса (опционально)
 * @returns ID созданного маршрута
 */
async function createRoute(
  page: Page,
  pathSuffix: string,
  resources: TestResources,
  upstreamUrl = 'http://httpbin.org/anything'
): Promise<string> {
  const response = await apiRequest(page, 'POST', '/api/v1/routes', {
      path: `/e2e-epic8-${pathSuffix}`,
      upstreamUrl,
      methods: ['GET'],
      authRequired: false, // Public route для Gateway testing
  })
  expect(response.ok(), `Не удалось создать маршрут: ${await response.text()}`).toBeTruthy()
  const route = (await response.json()) as { id: string }
  resources.routeIds.push(route.id)
  return route.id
}

/**
 * Отправляет маршрут на согласование.
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function submitRoute(page: Page, routeId: string): Promise<void> {
  const response = await apiRequest(page, 'POST', `/api/v1/routes/${routeId}/submit`)
  expect(response.ok(), `Не удалось отправить маршрут на согласование: ${await response.text()}`).toBeTruthy()
}

/**
 * Одобряет маршрут (требует security или admin роль).
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function approveRoute(page: Page, routeId: string): Promise<void> {
  const response = await apiRequest(page, 'POST', `/api/v1/routes/${routeId}/approve`)
  expect(response.ok(), `Не удалось одобрить маршрут: ${await response.text()}`).toBeTruthy()
}

/**
 * Удаляет маршрут через API.
 * Идемпотентная операция — не падает на 404 или 409.
 *
 * Примечание: Published маршруты возвращают 409 Conflict ("Only draft routes can be deleted").
 * Это ожидаемое поведение для cleanup — такие маршруты удаляются global-setup перед следующим запуском.
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function deleteRoute(page: Page, routeId: string): Promise<void> {
  const response = await apiRequest(page, 'DELETE', `/api/v1/routes/${routeId}`)
  // 200, 204 — успех; 404 — уже удалён; 409 — published маршрут (нельзя удалить, cleanup в global-setup)
  expect([200, 204, 404, 409].includes(response.status())).toBeTruthy()
}

/**
 * Создаёт и публикует маршрут (submit + approve).
 * Требует admin/security роль для approve.
 *
 * @param page - Playwright Page объект
 * @param pathSuffix - Суффикс для path
 * @param resources - Объект для хранения ID
 * @param upstreamUrl - URL upstream сервиса
 * @returns ID созданного маршрута
 */
async function createPublishedRoute(
  page: Page,
  pathSuffix: string,
  resources: TestResources,
  upstreamUrl = 'http://httpbin.org/anything'
): Promise<string> {
  const routeId = await createRoute(page, pathSuffix, resources, upstreamUrl)
  await submitRoute(page, routeId)
  await approveRoute(page, routeId)
  return routeId
}

// =============================================================================
// Epic 8 — UX Improvements & Testing Tools
// E2E тесты для happy path сценариев:
// - AC1: Metrics Health Check отображается
// - AC2: Users search фильтрует по username и email
// - AC3: Routes search фильтрует по Upstream URL
// - AC4: Load Generator работает (start/stop/summary)
// =============================================================================

test.describe('Epic 8: UX Improvements & Testing Tools', () => {
  // Локальные переменные для изоляции при parallel запуске
  let TIMESTAMP: number
  const resources: TestResources = { routeIds: [] }

  // Генерируем уникальный timestamp для каждого теста
  test.beforeEach(() => {
    TIMESTAMP = Date.now()
    // Очищаем ресурсы перед каждым тестом
    resources.routeIds = []
  })

  // Cleanup ресурсов после каждого теста
  test.afterEach(async ({ page }) => {
    // Удаляем созданные маршруты
    for (const routeId of resources.routeIds) {
      await deleteRoute(page, routeId)
    }
    resources.routeIds = []
  })

  /**
   * AC1: Metrics Health Check отображается.
   *
   * Сценарий:
   * 1. Admin логинится и переходит на /metrics
   * 2. Проверяем видимость HealthCheckSection
   * 3. Проверяем статус-индикаторы для сервисов
   */
  test('Health Check отображается на странице Metrics', async ({ page }) => {
    // Шаг 1: Login как admin
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Шаг 2: Навигация на /metrics через меню
    await navigateToMenu(page, /Metrics/)

    // Шаг 3: Ожидание загрузки страницы Metrics
    await expect(page.locator('[data-testid="metrics-page"]')).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Шаг 4: Проверка HealthCheckSection
    const healthSection = page.locator('[data-testid="health-section"]')
    await expect(healthSection).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Шаг 5: Проверка что заголовок "Health Check" виден
    await expect(healthSection.locator('text=Health Check')).toBeVisible()

    // Шаг 6: Проверка статус-индикаторов для основных сервисов
    // gateway-core, gateway-admin, PostgreSQL, Redis
    const gatewayCore = page.locator('[data-testid="health-card-gateway-core"]')
    await expect(gatewayCore).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    const gatewayAdmin = page.locator('[data-testid="health-card-gateway-admin"]')
    await expect(gatewayAdmin).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    const postgresql = page.locator('[data-testid="health-card-postgresql"]')
    await expect(postgresql).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    const redis = page.locator('[data-testid="health-card-redis"]')
    await expect(redis).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Шаг 7: Проверка что хотя бы один сервис показывает UP статус
    // (в тестовом окружении все сервисы должны работать)
    const upStatuses = page.locator('.ant-tag:has-text("UP")')
    await expect(upStatuses.first()).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Шаг 8: Проверка кнопки ручного обновления
    const refreshButton = page.locator('[data-testid="health-refresh-button"]')
    await expect(refreshButton).toBeVisible()
    await expect(refreshButton).toBeEnabled()
  })

  /**
   * AC2: Users search фильтрует по username и email.
   *
   * Сценарий:
   * 1. Admin логинится и переходит на /users
   * 2. Вводит username в поле поиска
   * 3. Проверяет фильтрацию таблицы
   * 4. Проверяет поиск по email
   */
  test('Admin ищет пользователей по username и email', async ({ page }) => {
    // Шаг 1: Login как admin
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Шаг 2: Навигация на /users
    await navigateToMenu(page, /Users/)

    // Шаг 3: Ожидание загрузки таблицы
    await page.waitForSelector('table tbody tr', { timeout: TABLE_LOAD_TIMEOUT })

    // Шаг 4: Поиск по username "admin"
    const searchInput = page.locator('[data-testid="users-search-input"]')
    await expect(searchInput).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    await searchInput.fill('admin')

    // Ждём debounce (300ms) и обновления таблицы
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 5: Проверка что результаты отфильтрованы
    const rows = page.locator('table tbody tr')
    // Должен быть хотя бы один результат с "admin" в username
    await expect(rows.first()).toContainText(/admin/i)

    // Шаг 6: Очистка и поиск по email
    await searchInput.clear()
    await searchInput.fill('test')

    // Ждём debounce и обновления таблицы
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Проверяем что результаты снова отфильтрованы
    const rowsAfterEmail = await page.locator('table tbody tr').count()
    expect(rowsAfterEmail).toBeGreaterThan(0)

    // Шаг 7: Очистка поиска — должны показаться все пользователи
    await searchInput.clear()
    await page.waitForTimeout(500)
    await page.waitForTimeout(UI_SYNC_DELAY)
  })

  /**
   * AC3: Routes search фильтрует по Upstream URL.
   *
   * Сценарий:
   * 1. Создаём маршрут с уникальным upstream
   * 2. Навигируем на /routes
   * 3. Вводим upstream URL в поле поиска
   * 4. Проверяем что маршрут найден
   * 5. Проверяем что поиск по path также работает
   */
  test('Поиск маршрутов по Upstream URL', async ({ page }) => {
    // Шаг 1: Login и создание маршрута с уникальным upstream
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    const upstreamHost = `e2e-upstream-${TIMESTAMP}.local`
    const upstreamUrl = `http://${upstreamHost}:8080`
    await createRoute(page, `search-${TIMESTAMP}`, resources, upstreamUrl)

    // Ждём синхронизации
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 2: Навигация на /routes
    await navigateToMenu(page, /Routes/)

    // Ждём загрузки таблицы
    await page.waitForSelector('table tbody tr', { timeout: TABLE_LOAD_TIMEOUT })

    // Шаг 3: Поиск по upstream
    const searchInput = page.locator('[data-testid="routes-search-input"]')
    await expect(searchInput).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    await searchInput.fill(upstreamHost)
    await page.waitForTimeout(500) // debounce
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 4: Проверка что маршрут найден
    const row = page.locator(`tr:has-text("${upstreamHost}")`)
    await expect(row).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Шаг 5: Проверка что поиск по path также работает
    await searchInput.clear()
    const pathSearch = `e2e-epic8-search-${TIMESTAMP}`
    await searchInput.fill(pathSearch)
    await page.waitForTimeout(500)
    await page.waitForTimeout(UI_SYNC_DELAY)

    const rowByPath = page.locator(`tr:has-text("${pathSearch}")`)
    await expect(rowByPath).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })
  })

  /**
   * AC4: Load Generator работает.
   *
   * Сценарий:
   * 1. Создаём и публикуем маршрут
   * 2. Навигируем на /test
   * 3. Выбираем маршрут в dropdown
   * 4. Нажимаем Start и проверяем progress
   * 5. Нажимаем Stop и проверяем summary
   */
  test('Load Generator работает', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Шаг 1: Login и создание published маршрута
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    const routePath = `load-gen-${TIMESTAMP}`
    await createPublishedRoute(page, routePath, resources)

    // Ждём синхронизации gateway-core
    await page.waitForTimeout(GATEWAY_SYNC_DELAY)

    // Шаг 2: Навигация на /test
    await navigateToMenu(page, /Test/)

    // Ждём загрузки страницы
    await expect(page.locator('[data-testid="test-page"]')).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Проверяем форму
    await expect(page.locator('[data-testid="load-generator-form"]')).toBeVisible()

    // Шаг 3: Выбор маршрута в dropdown
    const routeSelector = page.locator('[data-testid="route-selector"]')
    await expect(routeSelector).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Открываем dropdown
    await routeSelector.click()

    // Ждём появления dropdown
    await page.waitForSelector('.ant-select-dropdown', { state: 'visible' })

    // Выбираем наш маршрут (ищем по path)
    const routeOption = page.locator(`.ant-select-item:has-text("${routePath}")`)
    await expect(routeOption).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })
    await routeOption.click()

    // Шаг 4: Установка RPS (низкое значение для теста)
    const rpsInput = page.locator('[data-testid="rps-input"]')
    await rpsInput.clear()
    await rpsInput.fill('5')

    // Шаг 5: Нажатие Start
    const startButton = page.locator('[data-testid="start-button"]')
    await expect(startButton).toBeEnabled()
    await startButton.click()

    // Шаг 6: Проверка progress
    const progress = page.locator('[data-testid="load-generator-progress"]')
    await expect(progress).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Проверка что счётчик sent увеличивается
    // Используем polling вместо фиксированного timeout для надёжности
    const sentStat = page.locator('[data-testid="stat-sent"]')
    await expect(sentStat).toBeVisible()

    // Ждём пока sent > 0 (polling каждые 500ms, до 10 секунд)
    await expect(async () => {
      const sentValue = await sentStat.locator('.ant-statistic-content-value').textContent()
      const sentNumber = parseInt(sentValue || '0', 10)
      expect(sentNumber).toBeGreaterThan(0)
    }).toPass({ timeout: 10_000 })

    // Шаг 7: Нажатие Stop
    const stopButton = page.locator('[data-testid="stop-button"]')
    await expect(stopButton).toBeVisible()
    await stopButton.click()

    // Шаг 8: Проверка summary
    const summary = page.locator('[data-testid="load-generator-summary"]')
    await expect(summary).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Проверка Total Requests
    const totalRequests = page.locator('[data-testid="summary-total"]')
    await expect(totalRequests).toBeVisible()

    const totalValue = await totalRequests.locator('.ant-statistic-content-value').textContent()
    const totalNumber = parseInt(totalValue || '0', 10)
    expect(totalNumber).toBeGreaterThan(0)

    // Проверка Success Rate
    const successRate = page.locator('[data-testid="summary-success-rate"]')
    await expect(successRate).toBeVisible()
  })
})
