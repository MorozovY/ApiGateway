// Тесты для колонки Auth в RoutesTable (Story 12.7)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, cleanup } from '@testing-library/react'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RoutesTable } from './RoutesTable'
import type { Route, RouteListResponse } from '../types/route.types'

// Мок react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useSearchParams: () => [new URLSearchParams(), vi.fn()],
    useNavigate: () => vi.fn(),
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
      <a href={to}>{children}</a>
    ),
  }
})

// Мок данные маршрутов
const mockRoutes: Route[] = [
  {
    id: 'route-1',
    path: '/api/protected',
    upstreamUrl: 'http://protected-service:8080',
    methods: ['GET', 'POST'],
    description: 'Protected route',
    status: 'published',
    createdBy: 'user-1',
    creatorUsername: 'developer',
    createdAt: '2026-02-18T10:00:00Z',
    updatedAt: '2026-02-18T10:00:00Z',
    rateLimitId: null,
    rateLimit: null,
    authRequired: true,
    allowedConsumers: null,
  },
  {
    id: 'route-2',
    path: '/api/public',
    upstreamUrl: 'http://public-service:8080',
    methods: ['GET'],
    description: 'Public route',
    status: 'published',
    createdBy: 'user-1',
    creatorUsername: 'developer',
    createdAt: '2026-02-18T11:00:00Z',
    updatedAt: '2026-02-18T11:00:00Z',
    rateLimitId: null,
    rateLimit: null,
    authRequired: false,
    allowedConsumers: null,
  },
]

const mockRoutesResponse: RouteListResponse = {
  items: mockRoutes,
  total: 2,
  offset: 0,
  limit: 20,
}

// Мок useRoutes hook
vi.mock('../hooks/useRoutes', () => ({
  useRoutes: () => ({
    data: mockRoutesResponse,
    isLoading: false,
    error: null,
  }),
  useDeleteRoute: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}))

describe('колонка Auth в RoutesTable (Story 12.7)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it('отображает заголовок колонки Auth', () => {
    renderWithMockAuth(
      <RoutesTable />,
      {
        authValue: {
          isAuthenticated: true,
          user: { id: 'user-1', username: 'developer', role: 'developer' },
        },
      }
    )

    // Проверяем наличие заголовка колонки
    expect(screen.getByText('Auth')).toBeInTheDocument()
  })

  it('отображает "Protected" badge для authRequired=true', () => {
    renderWithMockAuth(
      <RoutesTable />,
      {
        authValue: {
          isAuthenticated: true,
          user: { id: 'user-1', username: 'developer', role: 'developer' },
        },
      }
    )

    // Проверяем наличие Protected badge (может быть несколько элементов с этим текстом)
    const protectedBadges = screen.getAllByText('Protected')
    expect(protectedBadges.length).toBeGreaterThan(0)
  })

  it('отображает "Public" badge для authRequired=false', () => {
    renderWithMockAuth(
      <RoutesTable />,
      {
        authValue: {
          isAuthenticated: true,
          user: { id: 'user-1', username: 'developer', role: 'developer' },
        },
      }
    )

    // Проверяем наличие Public badge
    const publicBadges = screen.getAllByText('Public')
    expect(publicBadges.length).toBeGreaterThan(0)
  })
})
