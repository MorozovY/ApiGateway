// Unit тесты для IntegrationsPage (Story 7.6, AC3, AC5, AC6, AC9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { IntegrationsPage } from './IntegrationsPage'
import type { UpstreamsResponse } from '../types/audit.types'

// Story 10.9: Navigate компонент используется для редиректа вместо useNavigate hook.
// Мокаем Navigate чтобы отследить редирект.
const mockNavigateComponent = vi.fn(() => null)
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    Navigate: (props: { to: string; replace?: boolean }) => {
      mockNavigateComponent(props.to, { replace: props.replace })
      return null
    },
  }
})

// Мок для API
const mockFetchUpstreams = vi.fn()
vi.mock('../api/upstreamsApi', () => ({
  fetchUpstreams: () => mockFetchUpstreams(),
}))

// Мок для exportUpstreamReport
const mockExportUpstreamReport = vi.fn()
vi.mock('../utils/exportUpstreamReport', () => ({
  exportUpstreamReport: (...args: unknown[]) => mockExportUpstreamReport(...args),
}))

// Тестовые данные
const mockUpstreams: UpstreamsResponse = {
  upstreams: [
    { host: 'user-service:8080', routeCount: 12 },
    { host: 'order-service:8080', routeCount: 5 },
  ],
}

// Auth value для разных ролей
const developerAuth = {
  user: { userId: '1', username: 'dev', role: 'developer' as const },
  isAuthenticated: true,
}

const securityAuth = {
  user: { userId: '1', username: 'sec', role: 'security' as const },
  isAuthenticated: true,
}

const adminAuth = {
  user: { userId: '1', username: 'admin', role: 'admin' as const },
  isAuthenticated: true,
}

describe('IntegrationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)
  })

  describe('Role-based access (AC6)', () => {
    it('редиректит developer на главную с сообщением', async () => {
      renderWithMockAuth(<IntegrationsPage />, { authValue: developerAuth })

      // Story 10.9: Navigate компонент используется для редиректа
      await waitFor(() => {
        expect(mockNavigateComponent).toHaveBeenCalledWith('/', { replace: true })
      })
    })

    it('показывает страницу для security роли', async () => {
      renderWithMockAuth(<IntegrationsPage />, { authValue: securityAuth })

      await waitFor(() => {
        expect(screen.getByText('Integrations Report')).toBeInTheDocument()
      })

      expect(mockNavigateComponent).not.toHaveBeenCalled()
    })

    it('показывает страницу для admin роли', async () => {
      renderWithMockAuth(<IntegrationsPage />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('Integrations Report')).toBeInTheDocument()
      })

      expect(mockNavigateComponent).not.toHaveBeenCalled()
    })
  })

  describe('Page content (AC3)', () => {
    it('отображает заголовок и описание', async () => {
      renderWithMockAuth(<IntegrationsPage />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('Integrations Report')).toBeInTheDocument()
      })

      expect(
        screen.getByText(/Обзор внешних сервисов.*upstream.*маршрутов/i)
      ).toBeInTheDocument()
    })

    it('отображает таблицу upstream сервисов', async () => {
      renderWithMockAuth(<IntegrationsPage />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('user-service:8080')).toBeInTheDocument()
      })

      expect(screen.getByText('order-service:8080')).toBeInTheDocument()
    })
  })

  describe('Export functionality (AC5)', () => {
    it('вызывает exportUpstreamReport при клике на Export', async () => {
      mockExportUpstreamReport.mockResolvedValue(undefined)

      const user = userEvent.setup()
      renderWithMockAuth(<IntegrationsPage />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('Export Report')).toBeInTheDocument()
      })

      await user.click(screen.getByText('Export Report'))

      await waitFor(() => {
        // Story 10.9: exportUpstreamReport теперь принимает messageApi вторым аргументом
        expect(mockExportUpstreamReport).toHaveBeenCalledWith(
          mockUpstreams.upstreams,
          expect.objectContaining({ success: expect.any(Function) })
        )
      })
    })

    it('кнопка Export disabled когда нет данных (AC9)', async () => {
      mockFetchUpstreams.mockResolvedValue({ upstreams: [] })

      renderWithMockAuth(<IntegrationsPage />, { authValue: adminAuth })

      await waitFor(() => {
        expect(screen.getByText('Export Report')).toBeInTheDocument()
      })

      const exportButton = screen.getByText('Export Report').closest('button')
      expect(exportButton).toBeDisabled()
    })
  })
})
