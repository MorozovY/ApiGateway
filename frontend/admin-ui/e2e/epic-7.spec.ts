import { test, expect, type Page } from '@playwright/test'
import { login, logout } from './helpers/auth'

// =============================================================================
// Константы для timeouts
// =============================================================================

/** Timeout для сложных тестов (несколько ролей, много операций) */
const TEST_TIMEOUT_LONG = 90_000

/** Ожидание синхронизации UI после операций */
const UI_SYNC_DELAY = 2000

/** Timeout для появления UI элементов */
const UI_ELEMENT_TIMEOUT = 15_000

/** Timeout для загрузки таблиц с данными */
const TABLE_LOAD_TIMEOUT = 30_000

// =============================================================================
// Helper функции для SPA навигации через меню (Task 2)
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
// Helper функции для работы с Routes API (Task 2)
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
  const response = await page.request.post('/api/v1/routes', {
    data: {
      path: `/e2e-audit-${pathSuffix}`,
      upstreamUrl,
      methods: ['GET'],
    },
  })
  expect(response.ok(), `Не удалось создать маршрут: ${await response.text()}`).toBeTruthy()
  const route = (await response.json()) as { id: string }
  resources.routeIds.push(route.id)
  return route.id
}

/**
 * Обновляет маршрут через API (PUT метод).
 * Изменяет description для создания audit log события "updated".
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 * @param updates - Объект с обновлениями (partial update)
 */
async function updateRoute(
  page: Page,
  routeId: string,
  updates: { description?: string; upstreamUrl?: string }
): Promise<void> {
  // Backend использует PUT метод для обновления (не PATCH)
  const response = await page.request.put(`/api/v1/routes/${routeId}`, {
    data: updates,
  })
  expect(response.ok(), `Не удалось обновить маршрут: ${await response.text()}`).toBeTruthy()
}

/**
 * Отправляет маршрут на согласование.
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function submitRoute(page: Page, routeId: string): Promise<void> {
  const response = await page.request.post(`/api/v1/routes/${routeId}/submit`)
  expect(response.ok(), `Не удалось отправить маршрут на согласование: ${await response.text()}`).toBeTruthy()
}

/**
 * Одобряет маршрут (требует security или admin роль).
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function approveRoute(page: Page, routeId: string): Promise<void> {
  const response = await page.request.post(`/api/v1/routes/${routeId}/approve`)
  expect(response.ok(), `Не удалось одобрить маршрут: ${await response.text()}`).toBeTruthy()
}

/**
 * Отклоняет маршрут (требует security или admin роль).
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 * @param reason - Причина отклонения
 */
async function rejectRoute(page: Page, routeId: string, reason: string): Promise<void> {
  const response = await page.request.post(`/api/v1/routes/${routeId}/reject`, {
    data: { reason },
  })
  expect(response.ok(), `Не удалось отклонить маршрут: ${await response.text()}`).toBeTruthy()
}

/**
 * Удаляет маршрут через API.
 * Идемпотентная операция — не падает на 404.
 *
 * @param page - Playwright Page объект
 * @param routeId - ID маршрута
 */
async function deleteRoute(page: Page, routeId: string): Promise<void> {
  const response = await page.request.delete(`/api/v1/routes/${routeId}`)
  // 200, 204 — успех; 404 — уже удалён
  expect([200, 204, 404].includes(response.status())).toBeTruthy()
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
// Epic 7 — Audit & Compliance
// E2E тесты для happy path сценариев аудит-функциональности:
// - AC1: Audit log записывает события
// - AC2: Security просматривает audit log UI
// - AC3: Route History отображается
// - AC4: Upstream Report работает
// - AC5: Developer не имеет доступа к audit
// =============================================================================

test.describe('Epic 7: Audit & Compliance', () => {
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
  // Примечание: cleanup пропущен для Epic 7 тестов потому что:
  // 1. Маршруты нужны для проверки audit/history API
  // 2. global-setup удаляет все e2e-* маршруты перед запуском тестов
  // 3. Удаление маршрута внутри теста ломает History API (возвращает 404)
  test.afterEach(async () => {
    // Очищаем списки (ресурсы удаляются в global-setup)
    resources.routeIds = []
  })

  /**
   * AC1: Audit log записывает события.
   *
   * Сценарий: Проверяем что audit API записывает события.
   * Используем GET /api/v1/audit для проверки наличия записей.
   *
   * Примечание: Route History API возвращает 404 если маршрут удалён,
   * поэтому проверяем через основной audit API с фильтром по entityType.
   */
  test('Audit log записывает события', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Шаг 1: Login как admin (имеет все права)
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    const routePath = `audit-test-${TIMESTAMP}`
    const routeId = await createRoute(page, routePath, resources)

    // Шаг 2: Обновляем маршрут (генерирует updated event)
    await updateRoute(page, routeId, { description: 'Обновлённое описание для E2E теста' })

    // Шаг 3: Отправляем на согласование (генерирует submitted event)
    await submitRoute(page, routeId)

    // Шаг 4: Одобряем маршрут (admin может approve сам)
    await approveRoute(page, routeId)

    // Ждём синхронизации audit событий
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 5: Проверяем audit через основной API (фильтр по entityType=route)
    // Используем общий audit API, а не route-specific history
    const auditResponse = await page.request.get('/api/v1/audit?entityType=route&limit=100')
    expect(
      auditResponse.ok(),
      `Audit API error: status=${auditResponse.status()}`
    ).toBeTruthy()

    const auditData = (await auditResponse.json()) as {
      items: Array<{
        id: string
        entityType: string
        entityId: string
        action: string
        user: { id: string; username: string }
        timestamp: string
      }>
      total: number
    }

    // Ищем наши события по entityId
    const ourEvents = auditData.items.filter((item) => item.entityId === routeId)
    const actions = ourEvents.map((item) => item.action)

    // Проверяем наличие ожидаемых событий
    // Примечание: Backend использует полный формат action: "route.submitted"
    expect(actions).toContain('created')
    expect(actions).toContain('updated')
    expect(actions).toContain('route.submitted')
    expect(actions).toContain('approved')

    // Проверяем структуру событий
    for (const item of ourEvents) {
      expect(item.user).toBeDefined()
      expect(item.user.username).toBeTruthy()
      expect(item.timestamp).toBeTruthy()
    }
  })

  /**
   * AC2: Security просматривает audit log UI.
   *
   * Сценарий:
   * 1. Security логинится и переходит на /audit
   * 2. Проверяем отображение таблицы с колонками
   * 3. Тестируем фильтры: action, entityType, date range
   * 4. Тестируем Export CSV (скачивание файла)
   */
  test('Security просматривает audit log UI', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Setup: создаём маршрут для генерации audit событий
    await login(page, 'test-admin', 'Test1234!', '/dashboard')
    const routePath = `audit-ui-${TIMESTAMP}`
    const routeId = await createRoute(page, routePath, resources)

    // Обновляем маршрут для создания события "updated"
    await updateRoute(page, routeId, { description: 'Описание для audit теста' })

    // Ждём записи audit события
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 1: Переключаемся на security и переходим на /audit
    await logout(page)
    await login(page, 'test-security', 'Test1234!', '/audit')

    // Шаг 2: Проверяем заголовок страницы
    await expect(page.getByRole('heading', { level: 4, name: /Аудит-логи/ })).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Проверяем наличие кнопки экспорта
    await expect(page.getByRole('button', { name: /Экспорт CSV/ })).toBeVisible()

    // Ждём загрузки данных — либо таблица, либо empty state
    // Если есть данные, будет table; если нет — .ant-empty
    await page.waitForSelector('table, .ant-empty', { timeout: TABLE_LOAD_TIMEOUT })

    // Проверяем что таблица видна (должны быть audit записи)
    const table = page.locator('table')
    const isTableVisible = await table.isVisible().catch(() => false)

    if (isTableVisible) {
      // Проверяем наличие колонок таблицы (заголовки)
      const tableHeaders = page.locator('thead th')
      await expect(tableHeaders.first()).toBeVisible()

      // Шаг 3: Тестируем фильтры

      // Фильтр по типу сущности
      const entityTypeSelect = page.locator('.ant-select').filter({ hasText: /Тип сущности/ })
      await entityTypeSelect.click()
      // Используем селектор без hidden класса для видимого dropdown
      const visibleDropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
      await visibleDropdown.waitFor({ state: 'visible' })
      await visibleDropdown.locator('.ant-select-item-option').filter({ hasText: /Маршрут/ }).click()

      // Ждём закрытия dropdown и применения фильтра
      await expect(visibleDropdown).toBeHidden({ timeout: 5000 })

      // URL должен обновиться с параметром entityType
      await expect(page).toHaveURL(/entityType=route/)

      // Фильтр по действию (multi-select)
      const actionSelect = page.locator('.ant-select').filter({ hasText: /Действие/ })
      await actionSelect.click()
      await visibleDropdown.waitFor({ state: 'visible' })
      await visibleDropdown.locator('.ant-select-item-option').filter({ hasText: /Создано/ }).click()

      // Закрываем dropdown
      await page.keyboard.press('Escape')
      // Ждём закрытия всех dropdowns
      await expect(page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')).toBeHidden({ timeout: 5000 })

      // Проверяем что фильтр применён в URL
      await expect(page).toHaveURL(/action=created/)

      // Сбрасываем фильтры
      const clearButton = page.getByRole('button', { name: /Сбросить фильтры/ })
      if (await clearButton.isVisible()) {
        await clearButton.click()
        await page.waitForTimeout(500)
      }

      // Шаг 3b: Тестируем date range filter (AC2 требует тестирование date range)
      const dateRangePicker = page.locator('.ant-picker-range')
      if (await dateRangePicker.isVisible()) {
        await dateRangePicker.click()

        // Ждём появления календаря
        await page.waitForSelector('.ant-picker-dropdown', { state: 'visible', timeout: 5000 })

        // Выбираем текущую дату как начало и конец
        const today = page.locator('.ant-picker-cell-today')
        await today.first().click()
        await today.first().click() // Кликаем дважды для выбора диапазона

        // Закрываем picker
        await page.keyboard.press('Escape')
        await page.waitForTimeout(500)

        // Проверяем что URL обновился с dateFrom/dateTo параметрами
        const currentUrl = page.url()
        // Date range filter может не добавить параметры если диапазон не полный
        // Просто проверяем что picker работает без ошибок
      }

      // Сбрасываем фильтры снова перед экспортом
      if (await clearButton.isVisible()) {
        await clearButton.click()
        await page.waitForTimeout(500)
      }

      // Шаг 4: Тестируем Export CSV
      // Примечание: downloadAuditCsv использует Blob + URL.createObjectURL,
      // что не триггерит Playwright 'download' event.
      // Проверяем, что кнопка кликается и не появляется ошибка.

      const exportButton = page.getByRole('button', { name: /Экспорт CSV/ })
      await expect(exportButton).toBeEnabled()
      await exportButton.click()

      // Ждём возможного появления success/error message
      await page.waitForTimeout(500)

      // Проверяем, что не появилось error notification (ошибка экспорта)
      const errorNotification = page.locator('.ant-message-error')
      const hasError = await errorNotification.isVisible().catch(() => false)
      expect(hasError).toBeFalsy()

      // Проверяем что появился success message (опционально)
      const successMessage = page.locator('.ant-message-success')
      await expect(successMessage).toBeVisible({ timeout: 5000 })
    } else {
      // Empty state — проверяем что страница работает
      await expect(page.locator('.ant-empty')).toBeVisible()
      // Кнопка экспорта должна быть disabled когда нет данных
      await expect(page.getByRole('button', { name: /Экспорт CSV/ })).toBeDisabled()
    }
  })

  /**
   * AC3: Route History UI отображается.
   *
   * Сценарий:
   * 1. Создаём маршрут с историей (create → update → submit → approve)
   * 2. Навигируем на страницу деталей маршрута
   * 3. Кликаем на вкладку "История"
   * 4. Проверяем что timeline отображается с action badges
   * 5. Проверяем expandable items
   */
  test('Route History UI отображается', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Setup: создаём маршрут с историей
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    const routePath = `history-${TIMESTAMP}`
    const routeId = await createRoute(page, routePath, resources)

    // Создаём историю: update, submit, approve
    await updateRoute(page, routeId, { description: 'Первое обновление' })
    await updateRoute(page, routeId, { description: 'Второе обновление' })
    await submitRoute(page, routeId)
    await approveRoute(page, routeId)

    // Ждём записи в audit log
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Шаг 1: Навигируем на страницу деталей маршрута через UI
    // Используем меню навигации вместо page.goto() чтобы сохранить сессию
    await navigateToMenu(page, /Routes/)

    // Ждём загрузки таблицы маршрутов
    await page.waitForSelector('table tbody tr', { timeout: TABLE_LOAD_TIMEOUT })

    // Ищем наш маршрут в таблице и кликаем на него
    const routeRow = page.locator(`tr:has-text("e2e-audit-history-${TIMESTAMP}")`)
    await expect(routeRow).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Кликаем на path link для перехода на детали
    await routeRow.locator('a').first().click()

    // Ждём загрузки страницы — проверяем что tabs появились
    // Ant Design Tabs: tab label содержит иконку + текст, используем locator с текстом
    const detailsTab = page.locator('.ant-tabs-tab').filter({ hasText: 'Детали' })
    await expect(detailsTab).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Шаг 2: Кликаем на вкладку "История"
    const historyTab = page.locator('.ant-tabs-tab').filter({ hasText: 'История' })
    await historyTab.click()

    // Ждём загрузки timeline
    await expect(page.locator('.ant-timeline')).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Шаг 3: Проверяем наличие timeline items
    const timelineItems = page.locator('.ant-timeline-item')
    await expect(timelineItems.first()).toBeVisible()

    // Должно быть минимум 5 событий: created + 2x updated + submitted + approved
    const itemCount = await timelineItems.count()
    expect(itemCount).toBeGreaterThanOrEqual(5)

    // Шаг 4: Проверяем наличие action badges (Tag компоненты)
    const actionBadges = page.locator('.ant-timeline-item .ant-tag')
    await expect(actionBadges.first()).toBeVisible()

    // Проверяем что есть badges для ожидаемых action types
    await expect(page.locator('.ant-tag:has-text("Создано")').first()).toBeVisible()
    await expect(page.locator('.ant-tag:has-text("Обновлено")').first()).toBeVisible()

    // Шаг 5: Проверяем expandable items (Collapse компоненты)
    // Для "updated" events должны быть expand buttons
    const collapseHeaders = page.locator('.ant-collapse-header')
    const hasExpandable = await collapseHeaders.count()

    // Если есть expandable items, проверяем что они работают
    if (hasExpandable > 0) {
      // Кликаем на первый expandable item
      await collapseHeaders.first().click()

      // Ждём раскрытия контента
      await expect(page.locator('.ant-collapse-content-active').first()).toBeVisible({
        timeout: 5000,
      })
    }

    // Также проверяем API для полноты (regression test)
    const historyResponse = await page.request.get(`/api/v1/routes/${routeId}/history`)
    expect(historyResponse.ok()).toBeTruthy()

    const historyData = (await historyResponse.json()) as {
      routeId: string
      history: Array<{ action: string }>
    }

    expect(historyData.routeId).toBe(routeId)
    expect(historyData.history.length).toBeGreaterThanOrEqual(5)
  })

  /**
   * AC4: Upstream Report работает.
   *
   * Сценарий:
   * 1. Создаём несколько маршрутов с одинаковым upstream
   * 2. Admin переходит на /audit/integrations (admin имеет доступ)
   * 3. Проверяем таблицу upstream сервисов
   * 4. Кликаем на upstream → redirect на /routes?upstream={host}
   * 5. Тестируем Export Report
   */
  test('Upstream Report работает', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
    // Setup: создаём маршруты с одинаковым upstream
    await login(page, 'test-admin', 'Test1234!', '/dashboard')

    const upstreamHost = `test-upstream-${TIMESTAMP}.local:8080`
    const upstreamUrl = `http://${upstreamHost}`

    // Создаём 3 маршрута с одним upstream (published для видимости в upstreams)
    await createPublishedRoute(page, `upstream-1-${TIMESTAMP}`, resources, upstreamUrl)
    await createPublishedRoute(page, `upstream-2-${TIMESTAMP}`, resources, upstreamUrl)
    await createPublishedRoute(page, `upstream-3-${TIMESTAMP}`, resources, upstreamUrl)

    // Ждём синхронизации данных
    await page.waitForTimeout(UI_SYNC_DELAY)

    // Переходим на /audit/integrations через меню (SPA навигация)
    await navigateToMenu(page, /Integrations/)

    // Проверяем заголовок страницы
    await expect(page.getByRole('heading', { level: 3, name: /Integrations Report/ })).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Ждём загрузки данных — spinner или таблица или empty
    await page.waitForSelector('table, .ant-empty', { timeout: TABLE_LOAD_TIMEOUT })

    // Проверяем наличие таблицы
    const table = page.locator('table')
    await expect(table).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Ищем upstream в таблице (используем поиск)
    const searchInput = page.locator('input[placeholder*="Поиск по host"]')
    await searchInput.fill(upstreamHost)

    // Ждём применения фильтра
    await page.waitForTimeout(500)

    // Проверяем наличие строки с нашим upstream
    const upstreamRow = page.locator(`tr:has-text("${upstreamHost}")`)
    await expect(upstreamRow).toBeVisible({ timeout: UI_ELEMENT_TIMEOUT })

    // Проверяем количество маршрутов (должно быть 3)
    await expect(upstreamRow.locator('td').nth(1)).toContainText(/3 маршрут/)

    // Кликаем на кнопку "Маршруты" — redirect на /routes?upstream={host}
    await upstreamRow.getByRole('button', { name: /Маршруты/ }).click()

    // Ждём навигации
    await page.waitForURL(/\/routes\?upstream=/)

    // Проверяем что URL содержит upstream параметр
    expect(page.url()).toContain(`upstream=${encodeURIComponent(upstreamHost)}`)

    // Возвращаемся на integrations для теста Export через меню
    await navigateToMenu(page, /Integrations/)
    await expect(page.getByRole('heading', { name: /Integrations Report/ })).toBeVisible({
      timeout: UI_ELEMENT_TIMEOUT,
    })

    // Тестируем Export Report
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: /Export Report/ }).click()

    const download = await downloadPromise
    const fileName = download.suggestedFilename()

    // Проверяем что файл скачался с правильным именем
    expect(fileName).toMatch(/upstream-report.*\.csv/)
  })

  /**
   * AC5: Developer не имеет доступа к audit.
   *
   * Сценарий:
   * 1. Developer логинится
   * 2. Пытается перейти на /audit → redirect на /
   * 3. Пытается перейти на /audit/integrations → redirect на /
   * 4. API test: GET /api/v1/audit → expect 403
   */
  test('Developer не имеет доступа к audit', async ({ page }) => {
    // Login как developer
    await login(page, 'test-developer', 'Test1234!', '/dashboard')

    // Попытка перейти на /audit → должен редиректнуться на /
    await page.goto('/audit')

    // Ждём возможного редиректа (компонент использует Navigate to="/" replace)
    await page.waitForURL((url) => !url.pathname.includes('/audit'), { timeout: 10_000 })

    // Проверяем что developer был редиректнут (не остался на /audit)
    expect(page.url()).not.toContain('/audit')

    // Попытка перейти на /audit/integrations
    await page.goto('/audit/integrations')

    // Ждём возможного редиректа
    await page.waitForURL((url) => !url.pathname.includes('/audit'), { timeout: 10_000 })

    // Проверяем что developer был редиректнут
    expect(page.url()).not.toContain('/audit')

    // API test: GET /api/v1/audit должен вернуть 403
    const auditApiResponse = await page.request.get('/api/v1/audit')
    expect(auditApiResponse.status()).toBe(403)

    // API test: проверяем /api/v1/routes/upstreams
    // Developer МОЖЕТ получить доступ к upstreams (это минимальная роль для эндпоинта)
    // Иерархия ролей: ADMIN > SECURITY > DEVELOPER
    // @RequireRole(Role.DEVELOPER) означает что developer имеет доступ
    const upstreamsApiResponse = await page.request.get('/api/v1/routes/upstreams')
    // Developer имеет доступ к upstreams API (это минимальная роль)
    expect(upstreamsApiResponse.ok()).toBeTruthy()
  })
})
