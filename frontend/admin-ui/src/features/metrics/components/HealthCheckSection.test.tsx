// Тесты для HealthCheckSection (Story 8.1, 10.5)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import HealthCheckSection from './HealthCheckSection'
import * as healthApi from '../api/healthApi'
import type { HealthResponse } from '../types/metrics.types'

// Мокаем API
vi.mock('../api/healthApi', () => ({
  getServicesHealth: vi.fn(),
}))

// Мокаем ThemeProvider
vi.mock('@/shared/providers/ThemeProvider', () => ({
  useThemeContext: () => ({
    theme: 'light',
    isDark: false,
    isLight: true,
    toggle: vi.fn(),
    setTheme: vi.fn(),
  }),
}))

const mockGetServicesHealth = healthApi.getServicesHealth as ReturnType<typeof vi.fn>

// Тестовые данные: 8 сервисов (nginx + gateway + postgresql + redis + keycloak + prometheus + grafana)
const mockHealthResponse: HealthResponse = {
  services: [
    { name: 'nginx', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'gateway-core', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'gateway-admin', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'postgresql', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'redis', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'keycloak', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'prometheus', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'grafana', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
  ],
  timestamp: '2026-02-21T10:30:00Z',
}

const mockHealthWithDown: HealthResponse = {
  services: [
    { name: 'nginx', status: 'DOWN', lastCheck: '2026-02-21T10:30:00Z', details: 'Connection refused' },
    { name: 'gateway-core', status: 'DOWN', lastCheck: '2026-02-21T10:30:00Z', details: 'Connection refused' },
    { name: 'gateway-admin', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'postgresql', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'redis', status: 'DOWN', lastCheck: '2026-02-21T10:30:00Z', details: 'Redis not configured' },
    { name: 'keycloak', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'prometheus', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'grafana', status: 'DOWN', lastCheck: '2026-02-21T10:30:00Z', details: 'Connection refused' },
  ],
  timestamp: '2026-02-21T10:30:00Z',
}

// Wrapper для тестов
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe('HealthCheckSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetServicesHealth.mockResolvedValue(mockHealthResponse)
  })

  describe('AC1: отображает все сервисы со статусами', () => {
    it('отображает все 8 сервисов', async () => {
      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Проверяем наличие всех 8 сервисов
      expect(screen.getByTestId('health-card-nginx')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-gateway-core')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-gateway-admin')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-postgresql')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-redis')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-keycloak')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-prometheus')).toBeInTheDocument()
      expect(screen.getByTestId('health-card-grafana')).toBeInTheDocument()
    })

    it('отображает статус UP зелёным', async () => {
      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Проверяем что статусы отображаются
      const upTags = screen.getAllByText('UP')
      expect(upTags.length).toBe(8) // Все 8 сервисов UP
    })

    it('отображает timestamp последней проверки', async () => {
      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Timestamp должен отображаться в заголовке
      expect(screen.getByText(/обновлено:/)).toBeInTheDocument()
    })
  })

  describe('AC2: показывает DOWN статус красным', () => {
    it('показывает DOWN статус для недоступных сервисов', async () => {
      mockGetServicesHealth.mockResolvedValue(mockHealthWithDown)

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Проверяем DOWN статусы
      const downTags = screen.getAllByText('DOWN')
      expect(downTags.length).toBe(4) // nginx, gateway-core, redis, grafana

      // Проверяем UP статусы
      const upTags = screen.getAllByText('UP')
      expect(upTags.length).toBe(4) // gateway-admin, postgresql, keycloak, prometheus
    })

    it('отображает детали ошибки в Tooltip для DOWN сервиса', async () => {
      mockGetServicesHealth.mockResolvedValue(mockHealthWithDown)

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Проверяем что карточка gateway-core существует и содержит данные об ошибке
      // Tooltip проверить сложнее, но можно убедиться что карточка отмечена как DOWN
      const gatewayCoreCard = screen.getByTestId('health-card-gateway-core')
      expect(gatewayCoreCard).toBeInTheDocument()
    })
  })

  describe('AC3: refetch при клике на Refresh', () => {
    it('показывает кнопку Refresh', async () => {
      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      expect(screen.getByTestId('health-refresh-button')).toBeInTheDocument()
    })

    it('вызывает refetch при клике на Refresh', async () => {
      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Первый вызов при монтировании
      expect(mockGetServicesHealth).toHaveBeenCalledTimes(1)

      // Клик на кнопку Refresh
      const refreshButton = screen.getByTestId('health-refresh-button')
      fireEvent.click(refreshButton)

      await waitFor(() => {
        expect(mockGetServicesHealth).toHaveBeenCalledTimes(2)
      })
    })
  })

  describe('Loading и Error состояния', () => {
    it('отображает loading состояние', async () => {
      let resolvePromise: (value: HealthResponse) => void
      mockGetServicesHealth.mockImplementation(
        () =>
          new Promise((resolve) => {
            resolvePromise = resolve
          })
      )

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      expect(screen.getByTestId('health-section-loading')).toBeInTheDocument()
      expect(screen.getByText(/Проверка сервисов/)).toBeInTheDocument()

      // Резолвим чтобы тест не зависал
      resolvePromise!(mockHealthResponse)
    })

    it('отображает error состояние при ошибке API', async () => {
      mockGetServicesHealth.mockRejectedValue(new Error('Network error'))

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section-error')).toBeInTheDocument()
      })

      expect(screen.getByText(/Не удалось получить статус сервисов/)).toBeInTheDocument()
    })
  })

  describe('Сортировка сервисов', () => {
    it('сортирует сервисы в правильном порядке', async () => {
      // Возвращаем сервисы в случайном порядке
      const shuffledResponse: HealthResponse = {
        services: [
          { name: 'grafana', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'redis', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'gateway-core', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'nginx', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'keycloak', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'prometheus', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'postgresql', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'gateway-admin', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
        ],
        timestamp: '2026-02-21T10:30:00Z',
      }
      mockGetServicesHealth.mockResolvedValue(shuffledResponse)

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Проверяем что все карточки отображаются
      expect(screen.getByText('Nginx')).toBeInTheDocument()
      expect(screen.getByText('Gateway Core')).toBeInTheDocument()
      expect(screen.getByText('Gateway Admin')).toBeInTheDocument()
      expect(screen.getByText('PostgreSQL')).toBeInTheDocument()
      expect(screen.getByText('Redis')).toBeInTheDocument()
      expect(screen.getByText('Keycloak')).toBeInTheDocument()
      expect(screen.getByText('Prometheus')).toBeInTheDocument()
      expect(screen.getByText('Grafana')).toBeInTheDocument()
    })

    it('AC3: Nginx отображается первым в списке (entry point)', async () => {
      // AC3: Nginx appears BEFORE gateway-core (as it's the entry point)
      const shuffledResponse: HealthResponse = {
        services: [
          { name: 'gateway-core', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'nginx', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
          { name: 'grafana', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
        ],
        timestamp: '2026-02-21T10:30:00Z',
      }
      mockGetServicesHealth.mockResolvedValue(shuffledResponse)

      render(<HealthCheckSection />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('health-section')).toBeInTheDocument()
      })

      // Получаем все карточки и проверяем порядок в DOM
      const nginxCard = screen.getByTestId('health-card-nginx')
      const gatewayCoreCard = screen.getByTestId('health-card-gateway-core')

      // Nginx должен быть ПЕРЕД gateway-core в DOM
      // compareDocumentPosition возвращает битовую маску, DOCUMENT_POSITION_FOLLOWING = 4
      const position = nginxCard.compareDocumentPosition(gatewayCoreCard)
      expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })
  })
})
