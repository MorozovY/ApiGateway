import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * Уникальный суффикс для изоляции маршрутов между тест-ранами.
 */
const TIMESTAMP = Date.now()

/**
 * Создаёт маршрут и отправляет его на согласование через API.
 *
 * Вызывается после login() — использует cookie текущей сессии.
 * Запросы идут через Vite proxy (localhost:3000), чтобы cookie были включены.
 * Возвращает ID созданного маршрута.
 */
async function createAndSubmitRoute(page: Page, pathSuffix: string): Promise<string> {
  // Создаём маршрут через proxy (cookie текущего пользователя включены автоматически)
  const createResponse = await page.request.post('http://localhost:3000/api/v1/routes', {
    data: {
      path: `/e2e-approval-${pathSuffix}`,
      upstreamUrl: 'http://approval-test.local:8000',
      methods: ['GET'],
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createdRoute = await createResponse.json() as { id: string }

  // Отправляем маршрут на согласование
  const submitResponse = await page.request.post(
    `http://localhost:3000/api/v1/routes/${createdRoute.id}/submit`
  )
  expect(submitResponse.ok()).toBeTruthy()

  return createdRoute.id
}

/**
 * Epic 4 — Approval Workflow.
 * Проверяет отправку на согласование, просмотр, одобрение и отклонение маршрутов.
 *
 * Для смены пользователя используется login() с landingUrl защищённой страницы:
 * page.goto(landingUrl) → fresh React state → ProtectedRoute → /login (returnUrl) →
 * новые credentials → redirect обратно на landingUrl.
 */
test.describe('Epic 4: Approval Workflow', () => {
  test('Developer отправляет маршрут на согласование', async ({ page }) => {
    // Создаём маршрут через UI (login → /routes → форма → детали)
    await login(page, 'test-developer', 'Test1234!', '/routes')
    await page.locator('button:has-text("New Route")').click()
    await expect(page).toHaveURL(/\/routes\/new/)

    const routePath = `e2e-submit-${TIMESTAMP}`
    await page.locator('input[placeholder="api/service"]').fill(routePath)
    await page.locator('input[placeholder="http://service:8080"]').fill('http://submit-test.local:8000')
    await page.getByTestId('methods-select').locator('.ant-select-selector').click()
    // Ждём появления dropdown перед кликом по опциям
    await page.locator('.ant-select-dropdown').waitFor({ state: 'visible' })
    await page.locator('.ant-select-dropdown .ant-select-item-option-content:has-text("GET")').click()
    // Закрываем дропдаун и ждём его исчезновения
    await page.keyboard.press('Escape')
    await page.locator('.ant-select-dropdown').waitFor({ state: 'hidden' })
    await page.locator('button:has-text("Save as Draft")').click()

    // На странице деталей — кнопка "Отправить на согласование" видна для draft + owner
    await expect(page).toHaveURL(/\/routes\/[a-f0-9-]+$/, { timeout: 10_000 })
    await expect(page.locator('button:has-text("Отправить на согласование")')).toBeVisible()

    // Нажимаем кнопку — появляется модальное окно подтверждения
    await page.locator('button:has-text("Отправить на согласование")').click()

    // Ждём появления модального окна (antd Modal.confirm)
    const modal = page.locator('.ant-modal-confirm')
    await expect(modal).toBeVisible({ timeout: 10_000 })

    // Подтверждаем отправку
    await modal.locator('button:has-text("Отправить")').click()
    await expect(modal).not.toBeVisible({ timeout: 10_000 })

    // Статус меняется на "Ожидает одобрения"
    await expect(page.locator('text=Ожидает одобрения')).toBeVisible()
  })

  test('Security видит маршрут в списке /approvals', async ({ page }) => {
    // Логинимся как developer и создаём pending маршрут через API
    await login(page, 'test-developer', 'Test1234!', '/dashboard')
    await createAndSubmitRoute(page, `security-view-${TIMESTAMP}`)

    // Переходим под security — login() с landingUrl='/approvals' делает full reload,
    // new React state, ProtectedRoute redirects to /login (returnUrl=/approvals),
    // security логинится, возвращается на /approvals
    await login(page, 'test-security', 'Test1234!', '/approvals')

    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()
    await expect(page.locator(`text=/e2e-approval-security-view-${TIMESTAMP}`)).toBeVisible({ timeout: 10_000 })
  })

  test('Security одобряет маршрут — статус становится published', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!', '/dashboard')
    const routeId = await createAndSubmitRoute(page, `approve-${TIMESTAMP}`)

    // Переключаемся на security
    await login(page, 'test-security', 'Test1234!', '/approvals')
    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()

    // Находим строку с нашим маршрутом
    const routeRow = page.locator(`tr:has-text("/e2e-approval-approve-${TIMESTAMP}")`)
    await expect(routeRow).toBeVisible({ timeout: 10_000 })

    // Одобряем маршрут — маршрут исчезает из списка pending
    // ant-btn-primary: только primary кнопка Approve (path-ссылка — ant-btn-link, не primary)
    await routeRow.locator('button.ant-btn-primary').click()
    await expect(routeRow).not.toBeVisible({ timeout: 10_000 })

    // Проверяем статус через API
    const routeResponse = await page.request.get(`http://localhost:3000/api/v1/routes/${routeId}`)
    const route = await routeResponse.json() as { status: string }
    expect(route.status).toBe('published')
  })

  test('Security отклоняет маршрут с причиной', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!', '/dashboard')
    const routeId = await createAndSubmitRoute(page, `reject-${TIMESTAMP}`)

    // Переключаемся на security
    await login(page, 'test-security', 'Test1234!', '/approvals')
    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()

    const routeRow = page.locator(`tr:has-text("/e2e-approval-reject-${TIMESTAMP}")`)
    await expect(routeRow).toBeVisible({ timeout: 10_000 })

    // Открываем модал отклонения
    // ant-btn-dangerous: только danger кнопка Reject (path-ссылка — ant-btn-link, не dangerous)
    await routeRow.locator('button.ant-btn-dangerous').click()
    await expect(page.locator('.ant-modal-title:has-text("Отклонить:")')).toBeVisible()

    // Заполняем причину
    const rejectReason = 'E2E тест: маршрут не соответствует требованиям безопасности'
    await page.locator('.ant-modal textarea').fill(rejectReason)

    // Подтверждаем отклонение
    await page.locator('.ant-modal-footer button:has-text("Отклонить")').click()
    await expect(page.locator('.ant-modal-title:has-text("Отклонить:")')).not.toBeVisible({ timeout: 10_000 })

    // Маршрут исчезает из pending списка
    await expect(routeRow).not.toBeVisible({ timeout: 10_000 })

    // Проверяем статус и причину через API
    const routeResponse = await page.request.get(`http://localhost:3000/api/v1/routes/${routeId}`)
    const route = await routeResponse.json() as { status: string; rejectionReason: string }
    expect(route.status).toBe('rejected')
    expect(route.rejectionReason).toBe(rejectReason)
  })

  test('Admin может открыть /approvals и видит pending маршруты', async ({ page }) => {
    // Этот тест ловит баг: ранее /approvals давал blank page для admin
    await login(page, 'test-developer', 'Test1234!', '/dashboard')
    await createAndSubmitRoute(page, `admin-view-${TIMESTAMP}`)

    // Переключаемся на admin — admin имеет доступ к /approvals (requiredRole=['security','admin'])
    await login(page, 'test-admin', 'Test1234!', '/approvals')

    // Страница корректно загружается (не blank page)
    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()

    // Pending маршруты видны
    await expect(page.locator(`text=/e2e-approval-admin-view-${TIMESTAMP}`)).toBeVisible({ timeout: 10_000 })
  })
})
