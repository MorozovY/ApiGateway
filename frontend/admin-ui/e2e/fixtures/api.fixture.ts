// E2E API Fixture — Mock API responses для изолированных тестов
// Story 13.15: E2E тесты с чистого листа, CI-first подход
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

// ========================================
// API HANDLERS
// ========================================

// Изменяемое состояние для тестов (создание/удаление)
let routesState = [...mockRoutes]
let rateLimitsState = [...mockRateLimits]

/**
 * Сбрасывает состояние mock данных в исходное
 */
export function resetMockState(): void {
  routesState = [...mockRoutes]
  rateLimitsState = [...mockRateLimits]
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
