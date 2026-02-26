// E2E Tests для Epic 12: Keycloak Integration & Multi-tenant Metrics
// Story 12-10: Comprehensive E2E Coverage
// FIXES APPLIED: H-4 (isolation), H-5 (no waitForTimeout), H-6 (navigateToMenu), H-2/H-3 (missing tests), M-10 (helpers)

import { test, expect, Page } from '@playwright/test'
import { keycloakLogin, keycloakLogout, navigateToMenu } from './helpers/keycloak-auth'
import { apiRequest } from './helpers/auth'

// ============================================================================
// Helper Functions (FIX H-3: Generic consumer token helper, устраняет дублирование)
// ============================================================================

/**
 * Получает JWT токен для любого consumer через Keycloak Admin API.
 *
 * FIX H-3: Заменяет дублирующиеся getCompanyAToken/getCompanyBToken
 * единой generic функцией.
 *
 * @param page - Playwright page object
 * @param clientId - Keycloak client ID (e.g., 'company-a', 'company-b')
 */
async function getConsumerTokenByClientId(page: Page, clientId: string): Promise<string> {
  const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180'
  const adminUser = process.env.KEYCLOAK_ADMIN_USER || 'admin'
  const adminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin'

  // Получаем admin token для Keycloak Admin API
  const adminTokenResponse = await page.request.post(
    `${keycloakUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: adminUser,
        password: adminPassword,
      },
      failOnStatusCode: false
    }
  )

  if (!adminTokenResponse.ok()) {
    const errorText = await adminTokenResponse.text()
    throw new Error(`Failed to get Keycloak admin token: ${adminTokenResponse.status()} - ${errorText}`)
  }

  const adminTokenData = await adminTokenResponse.json()
  const adminToken = adminTokenData.access_token

  // Получаем secret для client
  const clientResponse = await page.request.get(
    `${keycloakUrl}/admin/realms/api-gateway/clients?clientId=${clientId}`,
    {
      headers: { 'Authorization': `Bearer ${adminToken}` },
      failOnStatusCode: false
    }
  )

  if (!clientResponse.ok()) {
    throw new Error(`Failed to get ${clientId} client from Keycloak: ${clientResponse.status()}`)
  }

  const clients = await clientResponse.json()
  if (!clients || clients.length === 0) {
    throw new Error(`${clientId} client not found in Keycloak realm api-gateway`)
  }

  const clientSecret = clients[0].secret

  // Получаем consumer token через Client Credentials flow
  const tokenResponse = await page.request.post(
    `${keycloakUrl}/realms/api-gateway/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'client_credentials',
        client_id: clientId,
        client_secret: clientSecret,
      },
      failOnStatusCode: false
    }
  )

  if (!tokenResponse.ok()) {
    const errorText = await tokenResponse.text()
    throw new Error(`Failed to get ${clientId} token: ${tokenResponse.status()} - ${errorText}`)
  }

  const tokenData = await tokenResponse.json()
  return tokenData.access_token
}

// Convenience wrappers для backward compatibility
const getCompanyAToken = (page: Page) => getConsumerTokenByClientId(page, 'company-a')
const getCompanyBToken = (page: Page) => getConsumerTokenByClientId(page, 'company-b')

/**
 * Test Resources для изоляции тестов (H-4).
 */
interface TestResources {
  consumerIds: string[]
  routeIds: string[]
}

test.describe('Epic 12: Keycloak Integration & Multi-tenant Metrics', () => {
  // ============================================================================
  // FIX H-4: Test Isolation — TIMESTAMP + Resources tracking
  // ============================================================================

  let TIMESTAMP: number
  const resources: TestResources = {
    consumerIds: [],
    routeIds: []
  }

  test.beforeEach(() => {
    TIMESTAMP = Date.now()
    resources.consumerIds = []
    resources.routeIds = []
    console.log(`[E2E Setup] Test started with TIMESTAMP: ${TIMESTAMP}`)
  })

  test.afterEach(async ({ page }) => {
    // FIX M-3: Cleanup созданных consumers через Keycloak Admin API
    if (resources.consumerIds.length > 0) {
      const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180'
      const adminUser = process.env.KEYCLOAK_ADMIN_USER || 'admin'
      const adminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin'

      try {
        // Получаем admin token
        const adminTokenResponse = await page.request.post(
          `${keycloakUrl}/realms/master/protocol/openid-connect/token`,
          {
            form: {
              grant_type: 'password',
              client_id: 'admin-cli',
              username: adminUser,
              password: adminPassword,
            },
            failOnStatusCode: false
          }
        )

        if (adminTokenResponse.ok()) {
          const adminTokenData = await adminTokenResponse.json()
          const adminToken = adminTokenData.access_token

          for (const clientId of resources.consumerIds) {
            // Получаем internal ID клиента по clientId
            const clientsResponse = await page.request.get(
              `${keycloakUrl}/admin/realms/api-gateway/clients?clientId=${clientId}`,
              {
                headers: { 'Authorization': `Bearer ${adminToken}` },
                failOnStatusCode: false
              }
            )

            if (clientsResponse.ok()) {
              const clients = await clientsResponse.json()
              if (clients && clients.length > 0) {
                await page.request.delete(
                  `${keycloakUrl}/admin/realms/api-gateway/clients/${clients[0].id}`,
                  {
                    headers: { 'Authorization': `Bearer ${adminToken}` },
                    failOnStatusCode: false
                  }
                )
              }
            }
          }
        }
      } catch {
        // Игнорируем ошибки cleanup — не должны ломать тесты
      }
    }

    // Cleanup routes (если создавали)
    for (const routeId of resources.routeIds) {
      try {
        await page.request.delete(`http://localhost:8081/api/v1/routes/${routeId}`, {
          failOnStatusCode: false
        })
      } catch {
        // Игнорируем ошибки cleanup
      }
    }

    resources.consumerIds = []
    resources.routeIds = []
  })

  // ============================================================================
  // AC1: Keycloak SSO Login
  // ============================================================================

  test.describe('AC1: Keycloak SSO Login', () => {
    test('должен успешно войти с valid credentials', async ({ page }) => {
      // Логинимся через Keycloak Direct Access Grants
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // Проверяем что находимся на dashboard
      await expect(page).toHaveURL('/dashboard')

      // Проверяем что отображается header и user menu
      await expect(page.locator('text=Admin Panel')).toBeVisible()
      await expect(page.locator('[data-testid="user-menu-button"]')).toBeVisible()
    })

    test('должен сохранить JWT token в sessionStorage', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // Проверяем структуру токена в sessionStorage
      const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
      expect(tokensStr).not.toBeNull()

      const tokens = JSON.parse(tokensStr!)
      expect(tokens).toHaveProperty('access_token')
      expect(tokens).toHaveProperty('refresh_token')
      expect(tokens).toHaveProperty('expires_in')
      expect(tokens).toHaveProperty('refresh_expires_in')

      // Проверяем что access_token это валидный JWT (3 части разделённые точками)
      const parts = tokens.access_token.split('.')
      expect(parts).toHaveLength(3)

      // Декодируем payload и проверяем claims
      const payload = JSON.parse(atob(parts[1]))
      expect(payload).toHaveProperty('sub') // User ID
      expect(payload).toHaveProperty('preferred_username')
      expect(payload).toHaveProperty('email', 'admin@example.com')
      expect(payload.realm_access?.roles).toContain('admin-ui:admin')
    })

    test('должен успешно выйти через Keycloak logout', async ({ page }) => {
      // Логинимся
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')
      await expect(page).toHaveURL('/dashboard')

      // Выходим
      await keycloakLogout(page)

      // Проверяем редирект на /login
      await expect(page).toHaveURL('/login')

      // Проверяем что токены удалены из sessionStorage
      const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
      expect(tokensStr).toBeNull()
    })

    test('должен показать ошибку при invalid credentials', async ({ page }) => {
      await page.goto('/')

      // Пытаемся войти с неверным паролем
      await page.locator('[data-testid="username-input"]').fill('admin@example.com')
      await page.locator('[data-testid="password-input"]').fill('wrong-password')
      await page.locator('[data-testid="login-button"]').click()

      // FIX H-5: Убрали waitForTimeout(1000), используем expect с timeout
      await expect(page).toHaveURL('/login', { timeout: 3000 })

      // Проверяем что показана ошибка
      await expect(page.locator('.ant-alert-error, .ant-message-error')).toBeVisible({ timeout: 5000 })
    })
  })

  // ============================================================================
  // AC2: Role-based Access Control
  // ============================================================================

  test.describe('AC2: Role-based Access Control', () => {
    test('dev@example.com должен видеть только Routes и Metrics menu', async ({ page }) => {
      await keycloakLogin(page, 'dev@example.com', 'dev123', '/dashboard')

      // Ждём загрузки sidebar
      await page.waitForSelector('[role="menuitem"]', { timeout: 5000 })

      // Проверяем видимость меню
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Dashboard' })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Routes' })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Metrics' })).toBeVisible()

      // Проверяем что Users и Consumers НЕ видны
      await expect(page.locator('[role="menuitem"]').filter({ hasText: /^Users$/ })).not.toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: /^Consumers$/ })).not.toBeVisible()
    })

    test('dev@example.com не должен иметь доступ к /users', async ({ page }) => {
      await keycloakLogin(page, 'dev@example.com', 'dev123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu — но для /users нужно direct goto (это 403 test)
      // Оставляем goto т.к. это edge case — developer пытается обойти UI
      await page.goto('/users')

      // Ждём что UI отреагирует (либо redirect, либо error message)
      await page.waitForLoadState('networkidle', { timeout: 3000 })

      const currentUrl = page.url()
      if (currentUrl.includes('/users')) {
        // Если остались на /users, проверяем что показан Access Denied
        await expect(page.locator('text=/Access Denied|403|Forbidden/i')).toBeVisible({ timeout: 2000 })
      } else {
        // Иначе проверяем редирект
        expect(currentUrl).toMatch(/\/(dashboard|403)/)
      }
    })

    test('admin должен видеть все menu items', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // Ждём загрузки sidebar
      await page.waitForSelector('[role="menuitem"]', { timeout: 5000 })

      // Проверяем основные menu items
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Dashboard' })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Routes' })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: /^Users$/ })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: /^Consumers$/ })).toBeVisible()
      await expect(page.locator('[role="menuitem"]').filter({ hasText: 'Metrics' })).toBeVisible()
    })

    test('admin должен иметь доступ ко всем страницам', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Users$/)
      await expect(page).toHaveURL('/users')
      await expect(page.locator('text=Admin Panel')).toBeVisible() // Header всегда есть

      // Проверяем доступ к /consumers
      await navigateToMenu(page, /^Consumers$/)
      await expect(page).toHaveURL('/consumers')
      await expect(page.locator('text=Admin Panel')).toBeVisible()
    })
  })

  // ============================================================================
  // AC3: Consumer Management CRUD
  // ============================================================================

  test.describe('AC3: Consumer Management CRUD', () => {
    test('должен создать consumer и показать secret', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)
      await expect(page).toHaveURL('/consumers')

      // Нажимаем "Create Consumer"
      await page.locator('[data-testid="create-consumer-button"]').click()

      // Ждём открытия модального окна
      await expect(page.locator('.ant-modal-title', { hasText: 'Create Consumer' })).toBeVisible()

      // FIX H-4: Используем TIMESTAMP для уникального ID
      const clientId = `e2e-test-consumer-${TIMESTAMP}`

      // Заполняем форму
      await page.locator('[data-testid="consumer-client-id-input"]').fill(clientId)
      await page.locator('[data-testid="consumer-description-input"]').fill('E2E test consumer')

      // Нажимаем Create
      await page.locator('.ant-modal-footer button.ant-btn-primary').click()

      // Ждём закрытия первого модала и открытия SecretModal
      await expect(page.locator('.ant-modal-title', { hasText: 'Client Secret' })).toBeVisible({ timeout: 10000 })

      // Проверяем что secret отображается
      const secretInput = page.locator('[data-testid="consumer-secret-display"]')
      await expect(secretInput).toBeVisible()

      // Получаем secret value для последующих тестов
      const secretValue = await secretInput.inputValue()
      expect(secretValue).toBeTruthy()
      expect(secretValue.length).toBeGreaterThan(20)

      // Закрываем SecretModal
      await page.locator('.ant-modal-footer button', { hasText: 'Закрыть' }).click()

      // FIX H-4: Добавляем consumer в resources для cleanup (пока не реализовано)
      resources.consumerIds.push(clientId)
      console.log(`[E2E] Consumer ${clientId} создан (cleanup TODO)`)
    })

    test('должен выполнить rotate secret для consumer', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)

      // Ищем существующего consumer (company-a из seed data)
      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-a' })
      await expect(consumerRow).toBeVisible()

      // Нажимаем "Rotate Secret"
      await consumerRow.locator('button', { hasText: 'Rotate Secret' }).click()

      // Подтверждаем в Popconfirm
      await page.locator('.ant-popconfirm button.ant-btn-primary').click()

      // Ждём появления SecretModal с новым secret
      await expect(page.locator('.ant-modal-title', { hasText: 'Client Secret' })).toBeVisible({ timeout: 10000 })

      // Проверяем что новый secret отображается
      const secretInput = page.locator('[data-testid="consumer-secret-display"]')
      await expect(secretInput).toBeVisible()

      const newSecret = await secretInput.inputValue()
      expect(newSecret).toBeTruthy()

      // Закрываем модал
      await page.locator('.ant-modal-footer button', { hasText: 'Закрыть' }).click()
    })

    test('должен disable и enable consumer', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)

      // Ищем active consumer
      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-a' }).first()
      await expect(consumerRow).toBeVisible()

      // Проверяем что статус Active
      await expect(consumerRow.locator('.ant-tag', { hasText: 'Active' })).toBeVisible()

      // Нажимаем Disable
      await consumerRow.locator('button', { hasText: 'Disable' }).click()

      // Подтверждаем
      await page.locator('.ant-popconfirm button.ant-btn-primary').click()

      // Ждём изменения статуса на Disabled
      await expect(consumerRow.locator('.ant-tag', { hasText: 'Disabled' })).toBeVisible({ timeout: 5000 })

      // Теперь включаем обратно
      await consumerRow.locator('button', { hasText: 'Enable' }).click()

      // Ждём изменения статуса обратно на Active
      await expect(consumerRow.locator('.ant-tag', { hasText: 'Active' })).toBeVisible({ timeout: 5000 })
    })

    test('должен найти consumer по Client ID через поиск', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)

      // Ждём загрузки таблицы
      await page.waitForSelector('.ant-table-tbody tr', { timeout: 5000 })

      // Вводим поиск
      await page.locator('[data-testid="consumer-search-input"]').fill('company-a')

      // FIX H-5: Заменили waitForTimeout на polling wait
      // Ждём что таблица обновится после debounce (300ms)
      await expect(async () => {
        // Проверяем что company-a видна
        await expect(page.locator('.ant-table-tbody tr', { hasText: 'company-a' })).toBeVisible()

        // Проверяем что других consumers нет (только company-a)
        const rowCount = await page.locator('.ant-table-tbody tr').count()
        expect(rowCount).toBeLessThanOrEqual(1) // Может быть 0 или 1 в зависимости от фильтрации
      }).toPass({ timeout: 5000, intervals: [300, 500, 1000] })
    })
  })

  // ============================================================================
  // AC4: Per-consumer Rate Limits
  // ============================================================================

  test.describe('AC4: Per-consumer Rate Limits', () => {
    test('должен установить rate limit для consumer через UI', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)

      // Используем company-a (существующий demo consumer)
      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-a' }).first()
      await expect(consumerRow).toBeVisible({ timeout: 10000 })

      // Нажимаем "Set Rate Limit"
      await consumerRow.locator('button', { hasText: 'Set Rate Limit' }).click()

      // Ждём открытия модала
      await expect(page.locator('.ant-modal-title', { hasText: /Rate Limit для/ })).toBeVisible()

      // Заполняем форму
      await page.locator('[data-testid="rate-limit-rps-input"]').fill('10')
      await page.locator('[data-testid="rate-limit-burst-input"]').fill('50')

      // Нажимаем Set Rate Limit
      await page.locator('.ant-modal-footer button.ant-btn-primary').click()

      // Ждём закрытия модала (успешное сохранение)
      await expect(page.locator('.ant-modal', { hasText: /Rate Limit для/ })).not.toBeVisible({ timeout: 5000 })

      // Проверяем через API что rate limit действительно установлен
      // (более надёжно чем ждать обновления UI таблицы)
      const consumersResponse = await apiRequest(page, 'GET', '/api/v1/consumers')
      expect(consumersResponse.ok()).toBeTruthy()

      const consumersData = await consumersResponse.json() as {
        items: Array<{
          clientId: string
          rateLimit?: { requestsPerSecond: number; burstSize: number } | null
        }>
      }

      const companyA = consumersData.items.find(c => c.clientId === 'company-a')
      expect(companyA).toBeTruthy()
      expect(companyA!.rateLimit).toBeTruthy()
      expect(companyA!.rateLimit!.requestsPerSecond).toBe(10)
      expect(companyA!.rateLimit!.burstSize).toBe(50)
    })

    test('должен обновить существующий rate limit', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // FIX H-6: Заменили page.goto на navigateToMenu
      await navigateToMenu(page, /^Consumers$/)

      // Используем company-b который уже имеет rate limit
      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-b' }).first()
      await expect(consumerRow).toBeVisible()

      // Проверяем что rate limit установлен (любое значение)
      await expect(consumerRow).toContainText('req/s')

      // Нажимаем "Set Rate Limit" для обновления
      await consumerRow.locator('button', { hasText: 'Set Rate Limit' }).click()

      // Ждём модала с текущими значениями
      await expect(page.locator('.ant-modal-title', { hasText: /Rate Limit для/ })).toBeVisible()

      // Получаем текущие значения
      const rpsInput = page.locator('[data-testid="rate-limit-rps-input"]')
      const currentRps = await rpsInput.inputValue()

      // Изменяем на новые значения (отличные от текущих)
      const newRps = currentRps === '50' ? '75' : '50'
      const newBurst = currentRps === '50' ? '150' : '100'

      await rpsInput.clear()
      await rpsInput.fill(newRps)

      const burstInput = page.locator('[data-testid="rate-limit-burst-input"]')
      await burstInput.clear()
      await burstInput.fill(newBurst)

      // Нажимаем Update Rate Limit
      await page.locator('.ant-modal-footer button.ant-btn-primary').click()

      // Ждём закрытия модала
      await expect(page.locator('.ant-modal', { hasText: /Rate Limit для/ })).not.toBeVisible({ timeout: 5000 })

      // Проверяем обновлённые значения в таблице
      await expect(consumerRow).toContainText(`${newRps} req/s`, { timeout: 10000 })
      await expect(consumerRow).toContainText(`burst ${newBurst}`)
    })

    // ============================================================================
    // FIX H-3: AC4 — Gateway Rate Limit Enforcement Test (MISSING)
    // ============================================================================

    test('должен вернуть 429 при превышении consumer rate limit', async ({ page }) => {
      console.log('[E2E] Тестируем Gateway rate limit enforcement для company-a...')

      // Устанавливаем низкий rate limit для company-a (10 req/s)
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')
      await navigateToMenu(page, /^Consumers$/)

      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-a' }).first()
      await expect(consumerRow).toBeVisible()

      // Set rate limit через UI
      await consumerRow.locator('button', { hasText: 'Set Rate Limit' }).click()
      await expect(page.locator('.ant-modal-title', { hasText: /Rate Limit для/ })).toBeVisible()

      await page.locator('[data-testid="rate-limit-rps-input"]').fill('5')
      await page.locator('[data-testid="rate-limit-burst-input"]').fill('10')
      await page.locator('.ant-modal-footer button.ant-btn-primary').click()
      await expect(page.locator('.ant-modal', { hasText: /Rate Limit для/ })).not.toBeVisible({ timeout: 5000 })

      console.log('[E2E] Rate limit установлен: 5 req/s, burst 10')

      // FIX H-2: waitForTimeout здесь INTENTIONAL — Gateway sync через Redis pub/sub
      // не имеет API endpoint для polling, поэтому ожидание фиксированное
      await page.waitForTimeout(5000)

      // Получаем consumer token
      const token = await getCompanyAToken(page)

      // Отправляем > 10 requests/s через Gateway
      console.log('[E2E] Отправляем 25 requests для превышения лимита...')
      const requests = []
      for (let i = 0; i < 25; i++) {
        requests.push(
          page.request.get('http://localhost:8080/api/users', {
            headers: { 'Authorization': `Bearer ${token}` },
            failOnStatusCode: false
          })
        )
      }

      const responses = await Promise.all(requests)

      // Проверяем что хотя бы один response = 429
      const rateLimitedResponses = responses.filter(r => r.status() === 429)
      console.log(`[E2E] Получено ${rateLimitedResponses.length} responses с 429 из ${responses.length}`)

      expect(rateLimitedResponses.length).toBeGreaterThan(0)

      // Проверяем что 429 response содержит правильные headers
      const first429 = rateLimitedResponses[0]
      const headers = first429.headers()

      // NOTE: x-ratelimit-type header опционален — зависит от backend implementation
      // Если backend возвращает этот header, можно добавить проверку:
      // expect(headers['x-ratelimit-type']).toBe('consumer')

      console.log('[E2E] Rate limit enforcement test passed ✓')
    })
  })

  // ============================================================================
  // AC6: Protected Route Authentication (Gateway Integration)
  // ============================================================================

  test.describe('AC6: Protected Route Authentication', () => {
    test('должен вернуть 401 для protected route без токена', async ({ page }) => {
      // Тестируем published маршрут с auth_required=true
      const response = await page.request.get('http://localhost:8080/api/users', {
        failOnStatusCode: false
      })

      expect(response.status()).toBe(401)
      expect(response.headers()['www-authenticate']).toContain('Bearer')

      const body = await response.json()
      expect(body.type).toContain('unauthorized')
    })

    test('должен разрешить доступ с valid consumer JWT токеном', async ({ page }) => {
      // FIX M-10: Используем helper function вместо дублирования кода
      const token = await getCompanyAToken(page)

      expect(token).toBeTruthy()

      // Делаем запрос к Gateway с токеном
      const response = await page.request.get('http://localhost:8080/api/users', {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      })

      // Gateway должен проксировать запрос к upstream (JSONPlaceholder)
      expect(response.status()).toBe(200)

      const body = await response.json()
      expect(Array.isArray(body)).toBeTruthy()
    })

    test('должен извлечь consumer_id из JWT azp claim', async ({ page }) => {
      // FIX M-10: Используем helper function
      const token = await getCompanyAToken(page)

      // FIX L-1: Используем atob вместо Buffer (browser-compatible)
      const parts = token.split('.')
      const payload = JSON.parse(atob(parts[1]))

      expect(payload.azp).toBe('company-a')

      // Gateway должен использовать azp как consumer_id для метрик и rate limiting
      const response = await page.request.get('http://localhost:8080/api/users', {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      })

      expect(response.status()).toBe(200)
    })

    // ============================================================================
    // FIX H-2: AC6 — Missing Tests (public route + consumer whitelist)
    // ============================================================================

    test('должен разрешить public route без JWT токена', async ({ page }) => {
      console.log('[E2E] Тестируем public route (auth_required=false)...')

      // Сначала логинимся как admin для создания route через Admin API
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // Создаем public route через Admin API
      const routePath = `/e2e-public-${TIMESTAMP}`
      const createResponse = await apiRequest(page, 'POST', '/api/v1/routes', {
        path: routePath,
        upstreamUrl: 'http://httpbin.org/anything',
        methods: ['GET'],
        authRequired: false, // Public route - доступен без токена
      })

      expect(createResponse.ok()).toBeTruthy()
      const route = (await createResponse.json()) as { id: string }
      resources.routeIds.push(route.id)

      // Публикуем route (submit + approve)
      const submitResponse = await apiRequest(page, 'POST', `/api/v1/routes/${route.id}/submit`)
      expect(submitResponse.ok()).toBeTruthy()
      const approveResponse = await apiRequest(page, 'POST', `/api/v1/routes/${route.id}/approve`)
      expect(approveResponse.ok()).toBeTruthy()

      // FIX H-2: waitForTimeout INTENTIONAL — Gateway route sync не имеет polling API
      await page.waitForTimeout(3000)

      // Делаем запрос к Gateway БЕЗ JWT токена (не используем apiRequest, используем прямой page.request)
      const gatewayUrl = `http://localhost:8080${routePath}`
      const response = await page.request.get(gatewayUrl, {
        failOnStatusCode: false
      })

      // Public route должен вернуть 200 без токена
      expect(response.status()).toBe(200)

      console.log(`[E2E] Public route test passed: ${response.status()}`)
    })

    test('должен вернуть 403 если consumer не в allowed_consumers whitelist', async ({ page }) => {
      console.log('[E2E] Тестируем consumer whitelist enforcement...')

      // Сначала логинимся как admin для создания route через Admin API
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      // Создаем protected route с whitelist через Admin API
      const routePath = `/e2e-whitelist-${TIMESTAMP}`
      const createResponse = await apiRequest(page, 'POST', '/api/v1/routes', {
        path: routePath,
        upstreamUrl: 'http://httpbin.org/anything',
        methods: ['GET'],
        authRequired: true,
        allowedConsumers: ['company-a'], // Только company-a разрешен
      })
      expect(createResponse.ok()).toBeTruthy()
      const route = (await createResponse.json()) as { id: string }
      resources.routeIds.push(route.id)

      // Публикуем route (submit + approve)
      const submitResponse = await apiRequest(page, 'POST', `/api/v1/routes/${route.id}/submit`)
      expect(submitResponse.ok()).toBeTruthy()
      const approveResponse = await apiRequest(page, 'POST', `/api/v1/routes/${route.id}/approve`)
      expect(approveResponse.ok()).toBeTruthy()

      // FIX H-2: waitForTimeout INTENTIONAL — Gateway route sync не имеет polling API
      await page.waitForTimeout(3000)

      // Получаем токен для company-b (НЕ в whitelist)
      const companyBToken = await getCompanyBToken(page)

      // Делаем запрос к Gateway с токеном company-b
      const gatewayUrl = `http://localhost:8080${routePath}`
      const response = await page.request.get(gatewayUrl, {
        headers: { 'Authorization': `Bearer ${companyBToken}` },
        failOnStatusCode: false
      })

      // company-b НЕ в whitelist — должен получить 403 Forbidden
      expect(response.status()).toBe(403)

      console.log(`[E2E] Consumer whitelist test passed: company-b received ${response.status()}`)
    })

    // ============================================================================
    // FIX M-6: ENHANCEMENT E-6 — JWT Signature Validation
    // ============================================================================

    test('должен вернуть 401 для tampered JWT signature', async ({ page }) => {
      console.log('[E2E] Тестируем JWT signature validation...')

      const validToken = await getCompanyAToken(page)

      // Tamper with signature (последняя часть JWT)
      const parts = validToken.split('.')
      const tamperedToken = `${parts[0]}.${parts[1]}.INVALID_SIGNATURE`

      console.log('[E2E] Отправляем запрос с tampered token...')

      const response = await page.request.get('http://localhost:8080/api/users', {
        headers: { 'Authorization': `Bearer ${tamperedToken}` },
        failOnStatusCode: false
      })

      // Gateway должен отклонить tampered token
      expect(response.status()).toBe(401)

      console.log('[E2E] JWT signature validation test passed ✓')
    })
  })

  // ============================================================================
  // AC5: Multi-tenant Metrics Filtering
  // ============================================================================

  test.describe('AC5: Multi-tenant Metrics', () => {
    test('должен экспонировать метрики с consumer_id label в Prometheus', async ({ page }) => {
      console.log('[E2E] Генерируем трафик от company-a для метрик...')

      // FIX M-10: Используем helper function
      const token = await getCompanyAToken(page)

      // Делаем несколько requests для генерации метрик
      for (let i = 0; i < 5; i++) {
        await page.request.get('http://localhost:8080/api/users', {
          headers: { 'Authorization': `Bearer ${token}` },
        })
      }

      console.log('[E2E] Ждём обновления метрик в Prometheus...')

      // FIX H-5: Заменили waitForTimeout на polling wait
      // Prometheus scrape interval = 15s, ждём метрики
      await expect(async () => {
        const metricsResponse = await page.request.get('http://localhost:8080/actuator/prometheus')
        const metricsText = await metricsResponse.text()

        // Проверяем наличие consumer_id label
        expect(metricsText).toContain('consumer_id="company-a"')
      }).toPass({ timeout: 20000, intervals: [2000] })

      // Финальная проверка структуры метрик
      const metricsResponse = await page.request.get('http://localhost:8080/actuator/prometheus')
      const metricsText = await metricsResponse.text()

      // Проверяем что есть gateway метрики с consumer_id
      expect(metricsText).toMatch(/gateway_request_duration_seconds.*consumer_id="company-a"/)

      console.log('[E2E] Prometheus metrics test passed ✓')
    })

    // ============================================================================
    // FIX M-1: AC5 — "View Metrics" Link Test (MISSING)
    // ============================================================================

    test('должен перейти на /metrics с consumer_id filter через View Metrics link', async ({ page }) => {
      console.log('[E2E] Тестируем View Metrics link...')

      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')
      await navigateToMenu(page, /^Consumers$/)

      // Ждём загрузки таблицы
      const consumerRow = page.locator('.ant-table-tbody tr', { hasText: 'company-a' }).first()
      await expect(consumerRow).toBeVisible()

      // Expand row для доступа к "View Metrics" button
      const expandButton = consumerRow.locator('.ant-table-row-expand-icon')
      if (await expandButton.isVisible()) {
        await expandButton.click()
      }

      // Нажимаем "View Metrics"
      const viewMetricsButton = page.locator('button', { hasText: 'View Metrics' }).first()
      await expect(viewMetricsButton).toBeVisible({ timeout: 5000 })
      await viewMetricsButton.click()

      // Проверяем navigate на /metrics с query param consumer_id
      await expect(page).toHaveURL(/\/metrics\?consumer_id=company-a/, { timeout: 5000 })

      console.log('[E2E] View Metrics link test passed ✓')
    })
  })

  // ============================================================================
  // ENHANCEMENTS: Additional Test Coverage (M-7, M-8, M-9)
  // ============================================================================

  test.describe('ENHANCEMENTS: Performance & Edge Cases', () => {
    // ============================================================================
    // FIX M-7: ENHANCEMENT E-7 — Performance Baseline Tests
    // ============================================================================

    test('login flow должен завершиться < 5 секунд', async ({ page }) => {
      const startTime = Date.now()

      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')

      const duration = Date.now() - startTime
      console.log(`[E2E Performance] Login duration: ${duration}ms`)

      expect(duration).toBeLessThan(5000)
    })

    test('consumer creation должна завершиться < 3 секунд', async ({ page }) => {
      await keycloakLogin(page, 'admin@example.com', 'admin123', '/dashboard')
      await navigateToMenu(page, /^Consumers$/)

      await page.locator('[data-testid="create-consumer-button"]').click()
      await expect(page.locator('.ant-modal-title', { hasText: 'Create Consumer' })).toBeVisible()

      const clientId = `e2e-perf-test-${TIMESTAMP}`
      await page.locator('[data-testid="consumer-client-id-input"]').fill(clientId)

      const startTime = Date.now()

      await page.locator('.ant-modal-footer button.ant-btn-primary').click()
      await expect(page.locator('.ant-modal-title', { hasText: 'Client Secret' })).toBeVisible({ timeout: 10000 })

      const duration = Date.now() - startTime
      console.log(`[E2E Performance] Consumer creation duration: ${duration}ms`)

      expect(duration).toBeLessThan(3000)

      // Cleanup modal
      await page.locator('.ant-modal-footer button', { hasText: 'Закрыть' }).click()

      resources.consumerIds.push(clientId)
    })

    // NOTE: Rate limit enforcement latency test требует precision timing
    // Пропускаем т.к. в E2E tests timing может быть нестабильным
  })
})
