import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * Уникальный суффикс для изоляции маршрутов между тест-ранами.
 */
const TIMESTAMP = Date.now()

/**
 * Вспомогательная функция: логинится под developer,
 * создаёт маршрут через API и отправляет его на согласование.
 * Возвращает ID созданного маршрута.
 */
async function createAndSubmitRoute(page: Page, pathSuffix: string): Promise<string> {
  // Создаём маршрут через API (без UI, чтобы тест был стабильным)
  const createResponse = await page.request.post('http://localhost:8081/api/v1/routes', {
    data: {
      path: `/e2e-approval-${pathSuffix}`,
      upstreamUrl: 'http://approval-test:8000',
      methods: ['GET'],
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createdRoute = await createResponse.json() as { id: string }

  // Отправляем на согласование через API
  const submitResponse = await page.request.post(`http://localhost:8081/api/v1/routes/${createdRoute.id}/submit`)
  expect(submitResponse.ok()).toBeTruthy()

  return createdRoute.id
}

/**
 * Epic 4 — Approval Workflow.
 * Проверяет отправку на согласование, просмотр, одобрение и отклонение маршрутов.
 */
test.describe('Epic 4: Approval Workflow', () => {
  test('Developer отправляет маршрут на согласование', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')

    // Создаём draft маршрут через API
    const createResponse = await page.request.post('http://localhost:8081/api/v1/routes', {
      data: {
        path: `/e2e-submit-${TIMESTAMP}`,
        upstreamUrl: 'http://submit-test:8000',
        methods: ['GET'],
      },
    })
    expect(createResponse.ok()).toBeTruthy()
    const createdRoute = await createResponse.json() as { id: string }

    // Открываем страницу деталей маршрута
    await page.goto(`/routes/${createdRoute.id}`)

    // Кнопка "Отправить на согласование" должна быть видна (draft + owner)
    await expect(page.locator('button:has-text("Отправить на согласование")')).toBeVisible()

    // Нажимаем кнопку
    await page.locator('button:has-text("Отправить на согласование")').click()

    // Появляется модальное окно подтверждения
    await expect(page.locator('.ant-modal-title:has-text("Отправить на согласование")')).toBeVisible()

    // Подтверждаем отправку
    await page.locator('.ant-modal-footer button:has-text("Отправить")').click()

    // Модальное окно закрывается
    await expect(page.locator('.ant-modal-title:has-text("Отправить на согласование")')).not.toBeVisible()

    // Статус маршрута должен смениться на "Ожидает одобрения"
    await expect(page.locator('text=Ожидает одобрения')).toBeVisible()
  })

  test('Security видит маршрут в списке /approvals', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')
    const routeId = await createAndSubmitRoute(page, `security-view-${TIMESTAMP}`)

    // Переходим под security пользователя
    await page.goto('/login')
    await login(page, 'test-security', 'Test1234!')

    // Открываем страницу согласования
    await page.goto('/approvals')

    // Заголовок страницы должен отображаться
    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()

    // Маршрут должен присутствовать в таблице
    await expect(page.locator(`text=/e2e-approval-security-view-${TIMESTAMP}`)).toBeVisible()

    // Сохраняем routeId для потенциальной очистки
    void routeId
  })

  test('Security одобряет маршрут — статус становится published', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')
    const routeId = await createAndSubmitRoute(page, `approve-${TIMESTAMP}`)

    // Переходим под security
    await page.goto('/login')
    await login(page, 'test-security', 'Test1234!')
    await page.goto('/approvals')

    // Находим строку с нашим маршрутом
    const routeRow = page.locator(`tr:has-text("/e2e-approval-approve-${TIMESTAMP}")`)
    await expect(routeRow).toBeVisible()

    // Нажимаем кнопку Approve
    await routeRow.locator('button:has-text("Approve")').click()

    // Маршрут должен исчезнуть из списка pending (одобрён)
    await expect(routeRow).not.toBeVisible({ timeout: 10_000 })

    // Проверяем статус через API
    const routeResponse = await page.request.get(`http://localhost:8081/api/v1/routes/${routeId}`)
    const route = await routeResponse.json() as { status: string }
    expect(route.status).toBe('published')
  })

  test('Security отклоняет маршрут с причиной', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')
    const routeId = await createAndSubmitRoute(page, `reject-${TIMESTAMP}`)

    // Переходим под security
    await page.goto('/login')
    await login(page, 'test-security', 'Test1234!')
    await page.goto('/approvals')

    // Находим строку с нашим маршрутом
    const routeRow = page.locator(`tr:has-text("/e2e-approval-reject-${TIMESTAMP}")`)
    await expect(routeRow).toBeVisible()

    // Нажимаем кнопку Reject
    await routeRow.locator('button:has-text("Reject")').click()

    // Появляется модальное окно с полем причины
    await expect(page.locator('.ant-modal-title:has-text("Отклонить:")')).toBeVisible()

    // Заполняем причину отклонения
    const rejectReason = 'E2E тест: маршрут не соответствует требованиям безопасности'
    await page.locator('.ant-modal textarea').fill(rejectReason)

    // Подтверждаем отклонение
    await page.locator('.ant-modal-footer button:has-text("Отклонить")').click()

    // Модальное окно закрывается
    await expect(page.locator('.ant-modal-title:has-text("Отклонить:")')).not.toBeVisible()

    // Маршрут исчезает из списка pending
    await expect(routeRow).not.toBeVisible({ timeout: 10_000 })

    // Проверяем статус через API
    const routeResponse = await page.request.get(`http://localhost:8081/api/v1/routes/${routeId}`)
    const route = await routeResponse.json() as { status: string; rejectionReason: string }
    expect(route.status).toBe('rejected')
    expect(route.rejectionReason).toBe(rejectReason)
  })

  test('Admin может открыть /approvals и видит pending маршруты', async ({ page }) => {
    // Этот тест ловит баг: ранее /approvals давал blank page для admin
    await login(page, 'test-developer', 'Test1234!')
    await createAndSubmitRoute(page, `admin-view-${TIMESTAMP}`)

    // Переходим под admin
    await page.goto('/login')
    await login(page, 'test-admin', 'Test1234!')

    // Admin должен иметь доступ к /approvals (роль admin включена в allowedRoles)
    await page.goto('/approvals')

    // Страница должна корректно загружаться (не blank page)
    await expect(page.locator('text=Согласование маршрутов')).toBeVisible()

    // Pending маршруты должны отображаться
    await expect(page.locator(`text=/e2e-approval-admin-view-${TIMESTAMP}`)).toBeVisible()
  })
})
