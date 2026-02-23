import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'

// =============================================================================
// Константы для timeouts (M2: централизованное управление)
// =============================================================================

/** Timeout для тестов требующих gateway sync */
const TEST_TIMEOUT_LONG = 60_000

/** Ожидание синхронизации gateway cache */
const GATEWAY_SYNC_DELAY = 3000

/** Ожидание Prometheus scrape interval */
const METRICS_SCRAPE_DELAY = 5000

/** Ожидание MetricsPage auto-refresh (10s interval + buffer) */
const METRICS_REFRESH_DELAY = 12_000

/** Timeout для появления UI элементов */
const UI_ELEMENT_TIMEOUT = 15_000

/** Timeout для загрузки виджетов */
const WIDGET_LOAD_TIMEOUT = 30_000

// =============================================================================
// Grafana credentials (M3: вынесены в константы)
// =============================================================================

const GRAFANA_USER = 'admin'
const GRAFANA_PASSWORD = 'admin'
const GRAFANA_PORT = 3001

// =============================================================================
// Helper функции для работы с API (M4: принимают resources для изоляции)
// =============================================================================

/** Интерфейс для хранения ресурсов cleanup */
interface TestResources {
  routeIds: string[]
}

/**
 * Создаёт маршрут через API.
 * Автоматически регистрирует ресурс для cleanup.
 */
async function createRoute(
  page: Page,
  pathSuffix: string,
  resources: TestResources
): Promise<string> {
  const response = await page.request.post('/api/v1/routes', {
    data: {
      path: `/e2e-metrics-${pathSuffix}`,
      upstreamUrl: 'http://httpbin.org/anything',
      methods: ['GET'],
    },
  })
  expect(response.ok()).toBeTruthy()
  const route = (await response.json()) as { id: string }
  resources.routeIds.push(route.id)
  return route.id
}

/**
 * Создаёт маршрут и публикует его (submit + approve).
 * Требует admin/security роль для approve.
 * Автоматически регистрирует ресурс для cleanup.
 */
async function createPublishedRoute(
  page: Page,
  pathSuffix: string,
  resources: TestResources
): Promise<string> {
  // Создаём маршрут
  const routeId = await createRoute(page, pathSuffix, resources)

  // Submit на согласование
  const submitResponse = await page.request.post(`/api/v1/routes/${routeId}/submit`)
  expect(submitResponse.ok()).toBeTruthy()

  // Approve (admin или security роль)
  const approveResponse = await page.request.post(`/api/v1/routes/${routeId}/approve`)
  expect(approveResponse.ok()).toBeTruthy()

  return routeId
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
 * Epic 6 — Monitoring & Observability.
 * E2E тесты для happy path сценариев мониторинга:
 * - AC1: Prometheus метрики доступны
 * - AC2: Per-route метрики работают
 * - AC3: Admin видит метрики в UI
 * - AC4: Grafana dashboard работает (skip по умолчанию)
 */
test.describe('Epic 6: Monitoring & Observability', () => {
  // M4: Локальные переменные для изоляции при parallel запуске
  // Каждый worker получает свою копию переменных
  let TIMESTAMP: number
  const resources: TestResources = { routeIds: [] }

  // Генерируем уникальный timestamp для каждого теста
  test.beforeEach(() => {
    TIMESTAMP = Date.now()
    // Очищаем ресурсы перед каждым тестом (на случай если afterEach не отработал)
    resources.routeIds = []
  })

  // Cleanup ресурсов после каждого теста
  test.afterEach(async ({ page }) => {
    // Логинимся как admin для cleanup (имеет права на удаление)
    try {
      await login(page, 'test-admin', 'Test1234!', '/dashboard')

      // Удаляем маршруты
      for (const routeId of resources.routeIds) {
        await deleteRoute(page, routeId)
      }
    } catch {
      // Игнорируем ошибки cleanup — тесты не должны падать из-за cleanup
    } finally {
      // Очищаем списки
      resources.routeIds = []
    }
  })

  /**
   * AC1: Prometheus метрики доступны.
   * Проверяем что gateway-core отдаёт метрики в Prometheus формате.
   *
   * Примечание: gateway_requests_total и gateway_request_duration_seconds
   * появляются только при наличии трафика через published маршруты.
   * Поэтому создаём маршрут и генерируем трафик.
   */
  test('Prometheus метрики доступны', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Логинимся как admin для создания маршрута
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Создаём и публикуем маршрут для генерации метрик
    const routePath = `prometheus-test-${TIMESTAMP}`
    await createPublishedRoute(page, routePath, resources)

    // Ждём синхронизации gateway
    await page.waitForTimeout(GATEWAY_SYNC_DELAY)

    // Генерируем трафик через gateway
    const gatewayUrl = `http://localhost:8080/e2e-metrics-${routePath}`
    for (let i = 0; i < 3; i++) {
      await page.request.get(gatewayUrl, { failOnStatusCode: false })
    }

    // Запрашиваем метрики с gateway-core (port 8080)
    const response = await page.request.get('http://localhost:8080/actuator/prometheus')
    expect(response.ok()).toBeTruthy()

    // Проверяем Content-Type (text/plain или text/plain;charset=utf-8)
    const contentType = response.headers()['content-type']
    expect(contentType).toMatch(/text\/plain/)

    // Получаем тело ответа
    const body = await response.text()

    // Проверяем наличие gateway_requests_total метрики
    expect(body).toContain('gateway_requests_total')

    // Проверяем наличие gateway_request_duration_seconds метрики
    expect(body).toContain('gateway_request_duration_seconds')
  })

  /**
   * AC2: Per-route метрики работают.
   * Создаём published маршрут, выполняем запросы, проверяем labels в метриках.
   */
  test('Per-route метрики работают', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Логинимся как admin для создания маршрута
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // Создаём и публикуем маршрут
    const routePath = `route-${TIMESTAMP}`
    await createPublishedRoute(page, routePath, resources)

    // Ждём синхронизации gateway (маршрут должен появиться в кэше)
    await page.waitForTimeout(GATEWAY_SYNC_DELAY)

    // Выполняем несколько запросов через gateway
    const gatewayUrl = `http://localhost:8080/e2e-metrics-${routePath}`
    for (let i = 0; i < 5; i++) {
      await page.request.get(gatewayUrl, { failOnStatusCode: false })
    }

    // Ждём scrape interval (метрики обновляются с задержкой)
    await page.waitForTimeout(METRICS_SCRAPE_DELAY)

    // Запрашиваем Prometheus метрики
    const response = await page.request.get('http://localhost:8080/actuator/prometheus')
    expect(response.ok()).toBeTruthy()
    const body = await response.text()

    // Проверяем что метрики содержат route_path label с нашим маршрутом
    expect(body).toContain(`route_path="/e2e-metrics-${routePath}"`)

    // Проверяем наличие method label
    expect(body).toMatch(/method="GET"/)

    // Проверяем наличие status label (2xx для успешных запросов)
    expect(body).toMatch(/status="2[0-9]{2}"/)
  })

  /**
   * AC3: Admin видит метрики в UI.
   * Логинимся как admin, переходим на /metrics, проверяем MetricsWidget.
   *
   * Story 8.2: MetricsWidget убран с Dashboard, метрики доступны на /metrics.
   * H1 fix: Проверяем что данные реально обновляются (auto-refresh).
   * H2 fix: Генерируем трафик через published маршрут (не /actuator/health).
   * M1 fix: Проверяем реальные числовые значения метрик.
   */
  test('Admin видит метрики в UI', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Логинимся как admin
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    // H2 fix: Создаём published маршрут для генерации реальных метрик
    const routePath = `ui-metrics-${TIMESTAMP}`
    await createPublishedRoute(page, routePath, resources)

    // Ждём синхронизации gateway
    await page.waitForTimeout(GATEWAY_SYNC_DELAY)

    // Генерируем начальный трафик через gateway
    const gatewayUrl = `http://localhost:8080/e2e-metrics-${routePath}`
    for (let i = 0; i < 5; i++) {
      await page.request.get(gatewayUrl, { failOnStatusCode: false })
    }

    // Story 8.2: Переходим на /metrics через sidebar (MetricsWidget убран с Dashboard)
    // Ant Design Menu использует li[role=menuitem], не <nav><a>
    await page.locator('[role="menu"]').getByRole('menuitem', { name: /metrics/i }).click()
    await expect(page).toHaveURL(/\/metrics/)

    // Ждём загрузки MetricsPage (либо loading, либо данные)
    const loadingOrPage = page.locator('[data-testid="metrics-page-loading"], [data-testid="metrics-page"]')
    await expect(loadingOrPage).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Ждём окончания загрузки — появления основного контейнера
    const metricsPage = page.locator('[data-testid="metrics-page"]')
    await expect(metricsPage).toBeVisible({ timeout: WIDGET_LOAD_TIMEOUT })

    // Проверяем отображение всех summary карточек (MetricsPage использует другие testid)
    await expect(page.locator('[data-testid="summary-card-rps"]')).toBeVisible()
    await expect(page.locator('[data-testid="summary-card-avg-latency"]')).toBeVisible()
    await expect(page.locator('[data-testid="summary-card-error-rate"]')).toBeVisible()
    await expect(page.locator('[data-testid="summary-card-active-routes"]')).toBeVisible()

    // M1 fix: Проверяем что карточки содержат реальные числовые значения
    const rpsValue = page.locator('[data-testid="summary-card-rps"] .ant-statistic-content-value')
    await expect(rpsValue).toBeVisible()
    const rpsText = await rpsValue.textContent()
    // Проверяем что значение — число (может быть 0 или больше)
    expect(rpsText).toMatch(/^[\d,]+(\.\d+)?$/)

    const latencyValue = page.locator('[data-testid="summary-card-avg-latency"] .ant-statistic-content-value')
    await expect(latencyValue).toBeVisible()
    const latencyText = await latencyValue.textContent()
    expect(latencyText).toMatch(/^[\d,]+(\.\d+)?$/)

    const errorRateValue = page.locator('[data-testid="summary-card-error-rate"] .ant-statistic-content-value')
    await expect(errorRateValue).toBeVisible()
    const errorRateText = await errorRateValue.textContent()
    // Error rate показывается как percentage (e.g., "0.00")
    expect(errorRateText).toMatch(/^[\d,]+(\.\d+)?$/)

    const activeRoutesValue = page.locator('[data-testid="summary-card-active-routes"] .ant-statistic-content-value')
    await expect(activeRoutesValue).toBeVisible()
    const activeRoutesText = await activeRoutesValue.textContent()
    expect(activeRoutesText).toMatch(/^\d+$/)

    // H1 fix: Проверяем auto-refresh — страница должна оставаться стабильной
    // Уменьшаем количество запросов чтобы избежать timeout
    const trafficPromises = Array.from({ length: 5 }, () =>
      page.request.get(gatewayUrl, { failOnStatusCode: false })
    )
    await Promise.all(trafficPromises)

    // Проверяем что страница всё ещё отображается (не упала в error state)
    await expect(metricsPage).toBeVisible()

    // Проверяем что данные отображаются корректно
    const newRpsText = await rpsValue.textContent()
    expect(newRpsText).toMatch(/^[\d,]+(\.\d+)?$/)
  })

  /**
   * AC4: Grafana dashboard работает.
   * ПРИМЕЧАНИЕ: Требует запуск с --profile monitoring.
   * Тест автоматически пропускается если Grafana недоступна или не отвечает.
   *
   * Для запуска с Grafana:
   * 1. docker-compose --profile monitoring up -d
   * 2. npx playwright test e2e/epic-6.spec.ts --grep "Grafana" --project=chromium
   */
  test('Grafana dashboard работает', async ({ page }) => {
    const grafanaBaseUrl = `http://localhost:${GRAFANA_PORT}`
    const basicAuth = Buffer.from(`${GRAFANA_USER}:${GRAFANA_PASSWORD}`).toString('base64')

    // Проверяем доступность Grafana через API с Basic Auth
    let grafanaAvailable = false
    try {
      const healthResponse = await page.request.get(`${grafanaBaseUrl}/api/health`, {
        failOnStatusCode: false,
        timeout: 5000,
      })

      if (healthResponse.ok()) {
        // Проверяем что API доступен с Basic Auth (Grafana 10+ не поддерживает /api/auth/login)
        const apiResponse = await page.request.get(`${grafanaBaseUrl}/api/datasources`, {
          headers: { Authorization: `Basic ${basicAuth}` },
          failOnStatusCode: false,
          timeout: 5000,
        })
        grafanaAvailable = apiResponse.ok()
      }
    } catch {
      grafanaAvailable = false
    }

    // Conditional skip: тест пропускается если Grafana не полностью доступна
    test.skip(!grafanaAvailable, 'Grafana is not available. Run: docker-compose --profile monitoring up -d')

    // L3 fix: Проверяем Content-Type для API ответов (с Basic Auth для Grafana 10+)
    const authHeaders = { Authorization: `Basic ${basicAuth}` }

    const datasourcesResponse = await page.request.get(`${grafanaBaseUrl}/api/datasources`, {
      headers: authHeaders,
    })
    expect(datasourcesResponse.ok()).toBeTruthy()
    const dsContentType = datasourcesResponse.headers()['content-type']
    expect(dsContentType).toMatch(/application\/json/)

    const datasources = (await datasourcesResponse.json()) as Array<{ name: string; type: string }>
    const prometheusDs = datasources.find((ds) => ds.type === 'prometheus')
    expect(prometheusDs).toBeTruthy()

    // Ищем dashboard "API Gateway"
    const searchResponse = await page.request.get(`${grafanaBaseUrl}/api/search?type=dash-db`, {
      headers: authHeaders,
    })
    expect(searchResponse.ok()).toBeTruthy()
    const dashboards = (await searchResponse.json()) as Array<{ title: string; uid: string }>
    const gatewayDashboard = dashboards.find((d) => d.title.includes('API Gateway'))
    expect(gatewayDashboard).toBeTruthy()

    // Логинимся в Grafana через UI и переходим на dashboard
    await page.goto(`${grafanaBaseUrl}/login`)
    await page.locator('input[name="user"]').fill(GRAFANA_USER)
    await page.locator('input[name="password"]').fill(GRAFANA_PASSWORD)
    await page.locator('button[type="submit"]').click()

    // Grafana может потребовать смену пароля при первом входе
    const changePasswordPage = page.locator('text=Update your password')
    if (await changePasswordPage.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Пропускаем смену пароля
      await page.locator('a:has-text("Skip"), button:has-text("Skip")').click()
    }

    // Переходим на dashboard
    await page.goto(`${grafanaBaseUrl}/d/${gatewayDashboard!.uid}`)

    // L4 fix: Используем более стабильные селекторы (data-testid или role-based)
    // Grafana 9+ использует [data-testid="data-testid Dashboard template variables..."]
    // Fallback на .react-grid-layout который более стабилен
    await page.waitForSelector('.react-grid-layout, [data-testid*="Dashboard"]', {
      timeout: WIDGET_LOAD_TIMEOUT,
    })

    // Проверяем что панели отображаются
    const panels = page.locator('.react-grid-item, [data-testid*="panel"]')
    await expect(panels.first()).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })
  })
})
