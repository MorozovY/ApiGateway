// Unit тесты для UpstreamsTable (Story 7.6, AC3, AC9; Story 11.1 expandable rows)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { UpstreamsTable } from './UpstreamsTable'
import type { UpstreamsResponse } from '../types/audit.types'
import type { RouteListResponse } from '@features/routes/types/route.types'

// Мок для API
const mockFetchUpstreams = vi.fn()
vi.mock('../api/upstreamsApi', () => ({
  fetchUpstreams: () => mockFetchUpstreams(),
}))

// Мок для routes API (Story 11.1)
const mockFetchRoutes = vi.fn()
vi.mock('@features/routes/api/routesApi', () => ({
  fetchRoutes: (params: { upstream: string }) => mockFetchRoutes(params),
}))

// Тестовые данные
const mockUpstreams: UpstreamsResponse = {
  upstreams: [
    { host: 'user-service:8080', routeCount: 12 },
    { host: 'order-service:8080', routeCount: 5 },
    { host: 'payment-service:8080', routeCount: 3 },
  ],
}

// Auth value
const adminAuth = {
  user: { userId: '1', username: 'admin', role: 'admin' as const },
  isAuthenticated: true,
}

describe('UpstreamsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает таблицу с upstream сервисами (AC3)', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    expect(screen.getByText('order-service:8080')).toBeInTheDocument()
    expect(screen.getByText('payment-service:8080')).toBeInTheDocument()

    // Проверяем отображение количества маршрутов
    expect(screen.getByText('12 маршрутов')).toBeInTheDocument()
    expect(screen.getByText('5 маршрутов')).toBeInTheDocument()
    expect(screen.getByText('3 маршрута')).toBeInTheDocument()
  })

  it('фильтрует по host name (AC3)', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    const user = userEvent.setup()
    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    // Вводим поисковый запрос
    const searchInput = screen.getByPlaceholderText('Поиск по host...')
    await user.type(searchInput, 'order')

    // Должен остаться только order-service
    await waitFor(() => {
      expect(screen.getByText('order-service:8080')).toBeInTheDocument()
      expect(screen.queryByText('user-service:8080')).not.toBeInTheDocument()
      expect(screen.queryByText('payment-service:8080')).not.toBeInTheDocument()
    })
  })

  it('показывает empty state когда нет upstream сервисов (AC9)', async () => {
    mockFetchUpstreams.mockResolvedValue({ upstreams: [] })

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Нет данных о внешних сервисах')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Создайте маршруты с upstream URL для отображения интеграций')
    ).toBeInTheDocument()
  })

  it('показывает spinner во время загрузки', async () => {
    mockFetchUpstreams.mockReturnValue(new Promise(() => {}))

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    expect(document.querySelector('.ant-spin')).toBeInTheDocument()
  })

  it('показывает ошибку при неудачной загрузке', async () => {
    mockFetchUpstreams.mockRejectedValue(new Error('Network error'))

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
    })
  })

  // ========================================
  // Story 11.1: Expandable rows tests
  // ========================================

  describe('expandable rows (Story 11.1)', () => {
    // Тестовые данные для маршрутов
    const mockRoutes: RouteListResponse = {
      items: [
        {
          id: 'route-1',
          path: '/api/users',
          upstreamUrl: 'http://user-service:8080/api/users',
          methods: ['GET', 'POST'],
          description: 'User routes',
          status: 'published',
          createdBy: 'user-1',
          createdAt: '2026-02-20T10:00:00Z',
          updatedAt: '2026-02-20T10:00:00Z',
          rateLimitId: 'rate-1',
          rateLimit: { id: 'rate-1', name: 'Standard', requestsPerSecond: 100, burstSize: 10 },
        },
        {
          id: 'route-2',
          path: '/api/users/:id',
          upstreamUrl: 'http://user-service:8080/api/users',
          methods: ['PUT', 'DELETE'],
          description: null,
          status: 'draft',
          createdBy: 'user-1',
          createdAt: '2026-02-20T10:00:00Z',
          updatedAt: '2026-02-20T10:00:00Z',
          rateLimitId: null,
          rateLimit: null,
        },
      ],
      total: 2,
      offset: 0,
      limit: 20,
    }

    it('показывает иконку expand для upstream строки (AC1)', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)

      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      // Проверяем наличие иконок expand
      const expandIcons = screen.getAllByTestId('expand-icon')
      expect(expandIcons.length).toBeGreaterThan(0)
    })

    it('отображает таблицу маршрутов при раскрытии строки (AC1, AC3)', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      // Кликаем на иконку expand первой строки
      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      // Ждём загрузки маршрутов и проверяем отображение nested table
      await waitFor(() => {
        expect(screen.getByText('/api/users')).toBeInTheDocument()
      })

      expect(screen.getByText('/api/users/:id')).toBeInTheDocument()
    })

    it('показывает колонки path, status, methods, rate limit (AC3)', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      await waitFor(() => {
        expect(screen.getByText('/api/users')).toBeInTheDocument()
      })

      // Проверяем заголовки колонок
      expect(screen.getByText('Path')).toBeInTheDocument()
      expect(screen.getByText('Статус')).toBeInTheDocument()
      expect(screen.getByText('Методы')).toBeInTheDocument()
      expect(screen.getByText('Rate Limit')).toBeInTheDocument()

      // Проверяем данные маршрутов
      expect(screen.getByText('Опубликован')).toBeInTheDocument() // status tag
      expect(screen.getByText('Черновик')).toBeInTheDocument() // draft status
      expect(screen.getByText('GET, POST')).toBeInTheDocument() // methods
      expect(screen.getByText('PUT, DELETE')).toBeInTheDocument()
      expect(screen.getByText('Standard')).toBeInTheDocument() // rate limit name
    })

    it('сворачивает строку при повторном клике (AC2)', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      // Раскрываем строку
      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      await waitFor(() => {
        expect(screen.getByText('/api/users')).toBeInTheDocument()
      })

      // После раскрытия иконка меняется на collapse
      const collapseIcon = screen.getByTestId('collapse-icon')
      expect(collapseIcon).toBeInTheDocument()

      // Сворачиваем строку
      await user.click(collapseIcon)

      // После сворачивания иконка снова expand
      // Ant Design Table скрывает content через CSS, но не удаляет из DOM
      await waitFor(() => {
        const newExpandIcons = screen.getAllByTestId('expand-icon')
        expect(newExpandIcons.length).toBe(3) // все три upstream теперь имеют expand icon
      })
    })

    it('корректно обрабатывает загрузку маршрутов', async () => {
      // Тест проверяет что при раскрытии строки происходит загрузка маршрутов
      // и данные отображаются корректно после загрузки
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      // После загрузки данные отображаются
      await waitFor(() => {
        expect(screen.getByText('/api/users')).toBeInTheDocument()
      })

      // Проверяем что fetchRoutes был вызван с правильным параметром
      expect(mockFetchRoutes).toHaveBeenCalledWith({ upstream: 'user-service:8080' })
    })

    it('показывает empty state если маршрутов нет', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue({ items: [], total: 0, offset: 0, limit: 20 })

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      await waitFor(() => {
        expect(screen.getByText('Нет маршрутов для этого upstream')).toBeInTheDocument()
      })
    })

    it('показывает — для маршрута без rate limit', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      await waitFor(() => {
        expect(screen.getByText('/api/users/:id')).toBeInTheDocument()
      })

      // Второй маршрут не имеет rate limit — должен показать "—"
      // Ищем все ячейки с "—"
      const dashes = screen.getAllByText('—')
      expect(dashes.length).toBeGreaterThan(0)
    })

    it('path является ссылкой на детальную страницу маршрута', async () => {
      mockFetchUpstreams.mockResolvedValue(mockUpstreams)
      mockFetchRoutes.mockResolvedValue(mockRoutes)

      const user = userEvent.setup()
      renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      const expandIcons = screen.getAllByTestId('expand-icon')
      await user.click(expandIcons[0]!)

      await waitFor(() => {
        expect(screen.getByText('/api/users')).toBeInTheDocument()
      })

      // Проверяем что path является ссылкой на /routes/{id}
      const pathLink = screen.getByRole('link', { name: '/api/users' })
      expect(pathLink).toHaveAttribute('href', '/routes/route-1')
    })
  })
})
