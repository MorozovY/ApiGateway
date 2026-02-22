// Unit тесты для RouteHistoryTimeline (Story 7.6, AC1, AC2, AC9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RouteHistoryTimeline } from './RouteHistoryTimeline'
import type { RouteHistoryResponse } from '../types/audit.types'

// Мокаем ThemeProvider (Story 10.8: ChangesViewer использует тему)
vi.mock('@/shared/providers/ThemeProvider', () => ({
  useThemeContext: () => ({
    theme: 'light',
    isDark: false,
    isLight: true,
    toggle: vi.fn(),
    setTheme: vi.fn(),
  }),
}))

// Мок для API
const mockFetchRouteHistory = vi.fn()
vi.mock('@features/routes/api/routesApi', () => ({
  fetchRouteHistory: (...args: unknown[]) => mockFetchRouteHistory(...args),
}))

// Тестовые данные
const mockRouteHistory: RouteHistoryResponse = {
  routeId: 'route-1',
  currentPath: '/api/orders',
  history: [
    {
      timestamp: '2026-02-11T11:00:00Z',
      action: 'approved',
      user: { id: '2', username: 'dmitry' },
      changes: null,
    },
    {
      timestamp: '2026-02-11T10:05:00Z',
      action: 'updated',
      user: { id: '1', username: 'maria' },
      changes: {
        before: { upstreamUrl: 'http://v1:8080' },
        after: { upstreamUrl: 'http://v2:8080' },
      },
    },
    {
      timestamp: '2026-02-11T10:00:00Z',
      action: 'created',
      user: { id: '1', username: 'maria' },
      changes: { after: { path: '/api/orders' } },
    },
  ],
}

// Auth value
const adminAuth = {
  user: { userId: '1', username: 'admin', role: 'admin' as const },
  isAuthenticated: true,
}

describe('RouteHistoryTimeline', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('показывает skeleton во время загрузки (AC1)', async () => {
    // Создаём промис который не резолвится сразу
    mockFetchRouteHistory.mockReturnValue(new Promise(() => {}))

    renderWithMockAuth(<RouteHistoryTimeline routeId="route-1" />, { authValue: adminAuth })

    // Skeleton должен отображаться
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument()
  })

  it('отображает timeline с записями истории (AC1)', async () => {
    mockFetchRouteHistory.mockResolvedValue(mockRouteHistory)

    renderWithMockAuth(<RouteHistoryTimeline routeId="route-1" />, { authValue: adminAuth })

    // Ждём загрузки
    await waitFor(() => {
      expect(screen.getByText('dmitry')).toBeInTheDocument()
    })

    // Проверяем отображение usernames (maria появляется дважды в списке)
    expect(screen.getAllByText('maria').length).toBeGreaterThanOrEqual(1)

    // Проверяем action badges
    expect(screen.getByText('Одобрено')).toBeInTheDocument()
    expect(screen.getByText('Обновлено')).toBeInTheDocument()
    expect(screen.getByText('Создано')).toBeInTheDocument()
  })

  it('показывает empty state когда история пустая (AC9)', async () => {
    mockFetchRouteHistory.mockResolvedValue({
      routeId: 'route-1',
      currentPath: '/api/empty',
      history: [],
    })

    renderWithMockAuth(<RouteHistoryTimeline routeId="route-1" />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('История изменений отсутствует')).toBeInTheDocument()
    })
  })

  it('показывает ошибку при неудачной загрузке', async () => {
    mockFetchRouteHistory.mockRejectedValue(new Error('Network error'))

    renderWithMockAuth(<RouteHistoryTimeline routeId="route-1" />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Ошибка загрузки истории')).toBeInTheDocument()
    })
  })

  it('expandable items для просмотра изменений (AC2)', async () => {
    mockFetchRouteHistory.mockResolvedValue(mockRouteHistory)

    const user = userEvent.setup()
    renderWithMockAuth(<RouteHistoryTimeline routeId="route-1" />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Обновлено')).toBeInTheDocument()
    })

    // Находим кнопку "Показать изменения"
    const expandButtons = screen.getAllByText('Показать изменения')
    expect(expandButtons.length).toBeGreaterThan(0)

    // Кликаем чтобы развернуть
    await user.click(expandButtons[0]!)

    // После раскрытия должны видеть данные изменений (ChangesViewer покажет JSON)
    await waitFor(() => {
      // ChangesViewer показывает JSON данные
      expect(document.body.textContent).toMatch(/v1:8080|v2:8080/i)
    })
  })
})
