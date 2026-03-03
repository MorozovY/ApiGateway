// E2E API Fixture — Mock API responses для изолированных тестов
// Story 13.15: E2E тесты с чистого листа, CI-first подход
// Story 14.6: Добавлены consumer API mocks и error simulation
// Использует Playwright route interception вместо MSW

import { type Page, type Route as PlaywrightRoute } from '@playwright/test'

// ========================================
// MOCK DATA
// ========================================

/**
 * Mock маршруты для тестов
 */
export const mockRoutes = [
  {
    id: 'route-001',
    path: '/api/v1/users',
    upstreamUrl: 'http://users-service.local:8080',
    methods: ['GET', 'POST'],
    description: 'Users API endpoint',
    status: 'published',
    createdBy: 'test-admin-id-12345',
    creatorUsername: 'admin',
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-20T14:30:00Z',
    rateLimitId: 'rl-001',
    rateLimit: { id: 'rl-001', name: 'Standard', requestsPerSecond: 100, burstSize: 10 },
    authRequired: true,
    allowedConsumers: null,
  },
  {
    id: 'route-002',
    path: '/api/v1/products',
    upstreamUrl: 'http://products-service.local:8080',
    methods: ['GET'],
    description: 'Products catalog',
    status: 'draft',
    createdBy: 'test-admin-id-12345',
    creatorUsername: 'admin',
    createdAt: '2026-01-18T09:00:00Z',
    updatedAt: '2026-01-18T09:00:00Z',
    rateLimitId: null,
    rateLimit: null,
    authRequired: false,
    allowedConsumers: null,
  },
  {
    id: 'route-003',
    path: '/api/v1/orders',
    upstreamUrl: 'http://orders-service.local:8080',
    methods: ['GET', 'POST', 'PUT'],
    description: 'Orders management',
    status: 'pending',
    createdBy: 'user-dev-001',
    creatorUsername: 'developer',
    createdAt: '2026-01-19T11:00:00Z',
    updatedAt: '2026-01-19T15:00:00Z',
    rateLimitId: 'rl-002',
    rateLimit: { id: 'rl-002', name: 'Premium', requestsPerSecond: 500, burstSize: 50 },
    authRequired: true,
    allowedConsumers: ['consumer-001'],
  },
  {
    id: 'route-004',
    path: '/api/v1/reports',
    upstreamUrl: 'http://reports-service.local:8080',
    methods: ['GET'],
    description: 'Analytics reports',
    status: 'rejected',
    createdBy: 'user-dev-001',
    creatorUsername: 'developer',
    createdAt: '2026-01-17T08:00:00Z',
    updatedAt: '2026-01-18T10:00:00Z',
    rateLimitId: null,
    rateLimit: null,
    rejectionReason: 'Missing authentication configuration',
    rejectorUsername: 'security',
    rejectedAt: '2026-01-18T10:00:00Z',
    authRequired: false,
    allowedConsumers: null,
  },
]

/**
 * Mock users для тестов
 */
export const mockUsers = [
  {
    id: 'test-admin-id-12345',
    username: 'admin',
    email: 'admin@example.com',
    role: 'admin',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'user-dev-001',
    username: 'developer',
    email: 'dev@example.com',
    role: 'developer',
    active: true,
    createdAt: '2026-01-05T00:00:00Z',
  },
  {
    id: 'user-sec-001',
    username: 'security',
    email: 'security@example.com',
    role: 'security',
    active: true,
    createdAt: '2026-01-03T00:00:00Z',
  },
]

/**
 * Mock rate limits для тестов
 */
export const mockRateLimits = [
  {
    id: 'rl-001',
    name: 'Standard',
    requestsPerSecond: 100,
    burstSize: 10,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'rl-002',
    name: 'Premium',
    requestsPerSecond: 500,
    burstSize: 50,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'rl-003',
    name: 'Burst',
    requestsPerSecond: 1000,
    burstSize: 200,
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
]

/**
 * Mock audit logs для тестов
 */
export const mockAuditLogs = [
  {
    id: 'audit-001',
    timestamp: '2026-01-20T14:30:00Z',
    userId: 'test-admin-id-12345',
    username: 'admin',
    action: 'route.created',
    entityType: 'route',
    entityId: 'route-001',
    details: { path: '/api/v1/users' },
  },
  {
    id: 'audit-002',
    timestamp: '2026-01-19T15:00:00Z',
    userId: 'user-sec-001',
    username: 'security',
    action: 'route.rejected',
    entityType: 'route',
    entityId: 'route-004',
    details: { reason: 'Missing authentication configuration' },
  },
  {
    id: 'audit-003',
    timestamp: '2026-01-18T10:00:00Z',
    userId: 'user-dev-001',
    username: 'developer',
    action: 'route.submitted',
    entityType: 'route',
    entityId: 'route-003',
    details: {},
  },
]

/**
 * Mock consumers для тестов (Story 14.6, Task 2.1)
 * Consumer — внешний клиент API с Client Credentials authentication
 */
export const mockConsumers = [
  {
    clientId: 'partner-service-001',
    description: 'Partner Service Integration',
    enabled: true,
    createdTimestamp: 1705312800000, // 2024-01-15
    rateLimit: {
      id: 'crl-001',
      consumerId: 'partner-service-001',
      requestsPerSecond: 100,
      burstSize: 20,
      createdAt: '2026-01-15T10:00:00Z',
      updatedAt: '2026-01-15T10:00:00Z',
      createdBy: { id: 'test-admin-id-12345', username: 'admin' },
    },
  },
  {
    clientId: 'mobile-app-backend',
    description: 'Mobile App Backend Service',
    enabled: true,
    createdTimestamp: 1705399200000, // 2024-01-16
    rateLimit: null,
  },
  {
    clientId: 'legacy-integration',
    description: 'Legacy System Integration (deprecated)',
    enabled: false,
    createdTimestamp: 1704967200000, // 2024-01-11
    rateLimit: {
      id: 'crl-002',
      consumerId: 'legacy-integration',
      requestsPerSecond: 50,
      burstSize: 10,
      createdAt: '2026-01-11T10:00:00Z',
      updatedAt: '2026-01-20T15:00:00Z',
      createdBy: { id: 'test-admin-id-12345', username: 'admin' },
    },
  },
  {
    clientId: 'analytics-collector',
    description: 'Analytics Data Collector',
    enabled: true,
    createdTimestamp: 1705572000000, // 2024-01-18
    rateLimit: null,
  },
]

// ========================================
// API HANDLERS
// ========================================

// Изменяемое состояние для тестов (создание/удаление)
let routesState = [...mockRoutes]
let rateLimitsState = [...mockRateLimits]
let consumersState = [...mockConsumers]

// Состояние симуляции ошибок (Story 14.6, Task 3)
let errorSimulation: { statusCode: number; endpoint?: string } | null = null

/**
 * Сбрасывает состояние mock данных в исходное
 */
export function resetMockState(): void {
  routesState = [...mockRoutes]
  rateLimitsState = [...mockRateLimits]
  consumersState = [...mockConsumers]
  errorSimulation = null
}

/**
 * Обработчик API запроса.
 * Возвращает mock response на основе URL и метода.
 */
async function handleApiRequest(
  route: PlaywrightRoute,
  request: { url: () => string; method: () => string; postData: () => string | null }
): Promise<void> {
  const url = new URL(request.url())
  const method = request.method()
  const pathname = url.pathname

  // ========================================
  // ERROR SIMULATION (Story 14.6, Task 3)
  // ========================================

  // Проверяем, нужно ли симулировать ошибку
  if (errorSimulation) {
    const { statusCode, endpoint } = errorSimulation

    // Если endpoint задан — проверяем совпадение; иначе применяем ко всем
    if (!endpoint || pathname.includes(endpoint)) {
      const errorBody = getErrorBody(statusCode)
      return route.fulfill({
        status: statusCode,
        contentType: 'application/json',
        body: JSON.stringify(errorBody),
      })
    }
  }

  // Routes check-path API (для проверки уникальности path)
  if (pathname === '/api/v1/routes/check-path') {
    const pathParam = url.searchParams.get('path')
    const exists = routesState.some((r) => r.path === pathParam)
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ exists }),
    })
  }

  // Routes API
  if (pathname === '/api/v1/routes') {
    if (method === 'GET') {
      const status = url.searchParams.get('status')
      const search = url.searchParams.get('search')
      let filtered = routesState

      if (status) {
        filtered = filtered.filter((r) => r.status === status)
      }
      if (search) {
        const s = search.toLowerCase()
        filtered = filtered.filter(
          (r) => r.path.toLowerCase().includes(s) || r.description?.toLowerCase().includes(s)
        )
      }

      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: filtered,
          total: filtered.length,
          offset: 0,
          limit: 20,
        }),
      })
    }

    if (method === 'POST') {
      const body = JSON.parse(request.postData() || '{}')
      const newRoute = {
        id: `route-new-${Date.now()}`,
        ...body,
        status: 'draft',
        createdBy: 'test-admin-id-12345',
        creatorUsername: 'admin',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        rateLimit: null,
      }
      routesState.push(newRoute)

      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(newRoute),
      })
    }
  }

  // Routes pending (approvals)
  if (pathname === '/api/v1/routes/pending') {
    const pending = routesState.filter((r) => r.status === 'pending')
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: pending, total: pending.length, offset: 0, limit: 100 }),
    })
  }

  // Single route by ID
  const routeMatch = pathname.match(/^\/api\/v1\/routes\/([^/]+)$/)
  if (routeMatch) {
    const routeId = routeMatch[1]
    const found = routesState.find((r) => r.id === routeId)

    if (method === 'GET') {
      if (!found) {
        return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ detail: 'Route not found' }) })
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (method === 'PUT') {
      if (!found) {
        return route.fulfill({ status: 404 })
      }
      const body = JSON.parse(request.postData() || '{}')
      Object.assign(found, body, { updatedAt: new Date().toISOString() })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (method === 'DELETE') {
      routesState = routesState.filter((r) => r.id !== routeId)
      return route.fulfill({ status: 204 })
    }
  }

  // Route actions (approve, reject, submit)
  const routeActionMatch = pathname.match(/^\/api\/v1\/routes\/([^/]+)\/(approve|reject|submit|history)$/)
  if (routeActionMatch) {
    const [, routeId, action] = routeActionMatch
    const found = routesState.find((r) => r.id === routeId)

    if (!found) {
      return route.fulfill({ status: 404 })
    }

    if (action === 'history') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          routeId,
          currentPath: found.path,
          history: [
            { timestamp: found.createdAt, action: 'created', user: { id: found.createdBy, username: found.creatorUsername }, changes: null },
          ],
        }),
      })
    }

    if (action === 'approve') {
      found.status = 'published'
      found.updatedAt = new Date().toISOString()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (action === 'reject') {
      const body = JSON.parse(request.postData() || '{}')
      found.status = 'rejected'
      found.rejectionReason = body.reason
      found.rejectorUsername = 'admin'
      found.rejectedAt = new Date().toISOString()
      found.updatedAt = new Date().toISOString()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (action === 'submit') {
      found.status = 'pending'
      found.updatedAt = new Date().toISOString()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }
  }

  // Users API
  if (pathname === '/api/v1/users') {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: mockUsers, total: mockUsers.length, offset: 0, limit: 20 }),
    })
  }

  if (pathname === '/api/v1/users/options') {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: mockUsers.map((u) => ({ id: u.id, username: u.username })) }),
    })
  }

  // Rate Limits API
  if (pathname === '/api/v1/rate-limits') {
    if (method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: rateLimitsState, total: rateLimitsState.length, offset: 0, limit: 20 }),
      })
    }

    if (method === 'POST') {
      const body = JSON.parse(request.postData() || '{}')
      const newRateLimit = {
        id: `rl-new-${Date.now()}`,
        ...body,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      rateLimitsState.push(newRateLimit)
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newRateLimit) })
    }
  }

  const rateLimitMatch = pathname.match(/^\/api\/v1\/rate-limits\/([^/]+)$/)
  if (rateLimitMatch) {
    const rlId = rateLimitMatch[1]
    const found = rateLimitsState.find((r) => r.id === rlId)

    if (method === 'GET') {
      if (!found) return route.fulfill({ status: 404 })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (method === 'PUT') {
      if (!found) return route.fulfill({ status: 404 })
      const body = JSON.parse(request.postData() || '{}')
      Object.assign(found, body, { updatedAt: new Date().toISOString() })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }

    if (method === 'DELETE') {
      rateLimitsState = rateLimitsState.filter((r) => r.id !== rlId)
      return route.fulfill({ status: 204 })
    }
  }

  // ========================================
  // CONSUMERS API (Story 14.6, Task 2.2-2.7)
  // ========================================

  // Consumers list: GET /api/v1/consumers
  if (pathname === '/api/v1/consumers' && method === 'GET') {
    const search = url.searchParams.get('search')
    const status = url.searchParams.get('status') // AC3: status filter (Active/Disabled)
    let filtered = consumersState

    // Task 2.2: search/filter support
    if (search) {
      const s = search.toLowerCase()
      filtered = filtered.filter((c) => c.clientId.toLowerCase().includes(s))
    }

    // AC3: status filter — 'active' или 'disabled'
    if (status) {
      const isEnabled = status.toLowerCase() === 'active'
      filtered = filtered.filter((c) => c.enabled === isEnabled)
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: filtered,
        total: filtered.length,
        offset: 0,
        limit: 100,
      }),
    })
  }

  // Task 2.3: Create consumer: POST /api/v1/consumers
  if (pathname === '/api/v1/consumers' && method === 'POST') {
    const body = JSON.parse(request.postData() || '{}')
    const newConsumer = {
      clientId: body.clientId,
      description: body.description || null,
      enabled: true,
      createdTimestamp: Date.now(),
      rateLimit: null,
    }
    consumersState.push(newConsumer)

    // Response includes secret (показывается только один раз)
    return route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        clientId: newConsumer.clientId,
        clientSecret: 'mock-secret-' + Date.now(),
        description: newConsumer.description,
      }),
    })
  }

  // Task 2.4: Rotate secret: POST /api/v1/consumers/:id/rotate-secret
  const rotateSecretMatch = pathname.match(/^\/api\/v1\/consumers\/([^/]+)\/rotate-secret$/)
  if (rotateSecretMatch && method === 'POST') {
    const clientId = rotateSecretMatch[1]
    const found = consumersState.find((c) => c.clientId === clientId)

    if (!found) {
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Consumer not found' }),
      })
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        clientId,
        clientSecret: 'rotated-secret-' + Date.now(),
      }),
    })
  }

  // Task 2.5: Disable consumer: POST /api/v1/consumers/:id/disable
  const disableMatch = pathname.match(/^\/api\/v1\/consumers\/([^/]+)\/disable$/)
  if (disableMatch && method === 'POST') {
    const clientId = disableMatch[1]
    const found = consumersState.find((c) => c.clientId === clientId)

    if (!found) {
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Consumer not found' }),
      })
    }

    found.enabled = false
    return route.fulfill({ status: 204 })
  }

  // Task 2.6: Enable consumer: POST /api/v1/consumers/:id/enable
  const enableMatch = pathname.match(/^\/api\/v1\/consumers\/([^/]+)\/enable$/)
  if (enableMatch && method === 'POST') {
    const clientId = enableMatch[1]
    const found = consumersState.find((c) => c.clientId === clientId)

    if (!found) {
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Consumer not found' }),
      })
    }

    found.enabled = true
    return route.fulfill({ status: 204 })
  }

  // Task 2.7: Set rate limit: PUT /api/v1/consumers/:id/rate-limit
  const rateLimitSetMatch = pathname.match(/^\/api\/v1\/consumers\/([^/]+)\/rate-limit$/)
  if (rateLimitSetMatch && method === 'PUT') {
    const clientId = rateLimitSetMatch[1]
    const found = consumersState.find((c) => c.clientId === clientId)

    if (!found) {
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Consumer not found' }),
      })
    }

    const body = JSON.parse(request.postData() || '{}')
    found.rateLimit = {
      id: `crl-new-${Date.now()}`,
      consumerId: clientId,
      requestsPerSecond: body.requestsPerSecond,
      burstSize: body.burstSize,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      createdBy: { id: 'test-admin-id-12345', username: 'admin' },
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(found.rateLimit),
    })
  }

  // Audit API
  if (pathname === '/api/v1/audit') {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: mockAuditLogs, total: mockAuditLogs.length, offset: 0, limit: 50 }),
    })
  }

  // Fallback — пропускаем неизвестные запросы
  return route.continue()
}

/**
 * Настраивает mock API для страницы.
 * Перехватывает все запросы к /api/* и возвращает mock данные.
 */
export async function setupMockApi(page: Page): Promise<void> {
  // Сбрасываем состояние перед каждым тестом
  resetMockState()

  // Перехватываем все API запросы
  await page.route('**/api/**', async (route, request) => {
    await handleApiRequest(route, request)
  })
}

/**
 * Добавляет новый route в mock state (для динамических тестов)
 */
export function addMockRoute(route: typeof mockRoutes[0]): void {
  routesState.push(route)
}

/**
 * Получает текущее состояние routes
 */
export function getMockRoutes(): typeof mockRoutes {
  return [...routesState]
}

// ========================================
// ERROR SIMULATION (Story 14.6, Task 3)
// ========================================

/**
 * Генерирует тело ошибки для указанного статуса (RFC 7807)
 */
function getErrorBody(statusCode: number): object {
  switch (statusCode) {
    case 401:
      return {
        type: 'about:blank',
        title: 'Unauthorized',
        status: 401,
        detail: 'Authentication required',
      }
    case 403:
      return {
        type: 'about:blank',
        title: 'Forbidden',
        status: 403,
        detail: 'Access denied. You do not have permission to perform this action.',
      }
    case 500:
      return {
        type: 'about:blank',
        title: 'Internal Server Error',
        status: 500,
        detail: 'An unexpected error occurred. Please try again later.',
      }
    default:
      return {
        type: 'about:blank',
        title: 'Error',
        status: statusCode,
        detail: 'An error occurred',
      }
  }
}

/**
 * Устанавливает симуляцию API ошибки (Story 14.6, Task 3.1)
 * После вызова все API запросы будут возвращать указанный статус.
 *
 * @param page Playwright page
 * @param statusCode HTTP статус код (401, 403, 500, etc.)
 * @param endpoint опционально — только для указанного endpoint
 */
export async function simulateApiError(
  page: Page,
  statusCode: number,
  endpoint?: string
): Promise<void> {
  errorSimulation = { statusCode, endpoint }
}

/**
 * Симулирует network error (Story 14.6, Task 3.2)
 * После вызова все API запросы будут abort'ить соединение.
 *
 * @param page Playwright page
 * @param endpoint опционально — только для указанного endpoint
 */
export async function simulateNetworkError(
  page: Page,
  endpoint?: string
): Promise<void> {
  // Добавляем route который abort'ит запросы
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!endpoint || pathname.includes(endpoint)) {
      await route.abort('connectionfailed')
    } else {
      await route.continue()
    }
  })
}

/**
 * Очищает симуляцию ошибок (Story 14.6, Task 3.3)
 *
 * @param page Playwright page
 */
export async function clearErrorSimulation(page: Page): Promise<void> {
  errorSimulation = null
  // Переинициализируем mock API без ошибок
  await page.unrouteAll({ behavior: 'wait' })
  await setupMockApi(page)
}
