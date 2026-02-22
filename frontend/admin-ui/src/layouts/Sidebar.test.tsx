// Unit тесты для Sidebar (Story 7.6, AC7, AC8; Story 9.3 — Role-based menu filtering)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../test/test-utils'
import Sidebar from './Sidebar'

// Мок для usePendingRoutesCount
vi.mock('@features/approval', () => ({
  usePendingRoutesCount: () => 0,
}))

// localStorage mock
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
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

describe('Sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'localStorage', { value: localStorageMock })
    localStorageMock.getItem.mockReturnValue(null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Navigation items (AC7)', () => {
    it('показывает Integrations пункт для security роли', async () => {
      renderWithMockAuth(<Sidebar />, { authValue: securityAuth })

      // Integrations должен отображаться
      expect(screen.getByText('Integrations')).toBeInTheDocument()
    })

    it('показывает Integrations пункт для admin роли', async () => {
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      expect(screen.getByText('Integrations')).toBeInTheDocument()
    })

    it('НЕ показывает Integrations для developer роли', async () => {
      renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

      expect(screen.queryByText('Integrations')).not.toBeInTheDocument()
    })

    it('показывает Audit Logs пункт', async () => {
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      expect(screen.getByText('Audit Logs')).toBeInTheDocument()
    })
  })

  describe('Collapsible sidebar (AC8)', () => {
    it('по умолчанию sidebar развёрнут', async () => {
      localStorageMock.getItem.mockReturnValue(null)

      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      // Заголовок "API Gateway" должен быть видим
      expect(screen.getByText('API Gateway')).toBeInTheDocument()
    })

    it('читает collapsed state из localStorage', async () => {
      localStorageMock.getItem.mockReturnValue('true')

      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      // При collapsed sidebar логотип текст не показывается
      expect(screen.queryByText('API Gateway')).not.toBeInTheDocument()
    })

    it('сохраняет collapsed state в localStorage при клике', async () => {
      localStorageMock.getItem.mockReturnValue(null)

      const user = userEvent.setup()
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      // Находим все кнопки и кликаем на последнюю (collapse trigger)
      const buttons = screen.getAllByRole('button')
      const collapseButton = buttons[buttons.length - 1]!
      await user.click(collapseButton)

      // Проверяем что localStorage обновился
      expect(localStorageMock.setItem).toHaveBeenCalledWith('sidebar-collapsed', 'true')
    })

    it('переключает collapsed state при клике', async () => {
      localStorageMock.getItem.mockReturnValue('true')

      const user = userEvent.setup()
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      // Находим все кнопки и кликаем на последнюю (expand trigger)
      const buttons = screen.getAllByRole('button')
      const expandButton = buttons[buttons.length - 1]!
      await user.click(expandButton)

      expect(localStorageMock.setItem).toHaveBeenCalledWith('sidebar-collapsed', 'false')
    })
  })

  // Story 9.3 — Role-based menu visibility
  // Story 10.7 — Quick Start Guide link
  describe('Quick Start Guide link (Story 10.7)', () => {
    it('отображает ссылку на Quick Start Guide', () => {
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toBeInTheDocument()
    })

    it('ссылка открывается в новой вкладке', () => {
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toHaveAttribute('target', '_blank')
      expect(guideLink).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('ссылка ведёт на /docs/quick-start-guide.html', () => {
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toHaveAttribute('href', '/docs/quick-start-guide.html')
    })

    it('показывает текст "Руководство" когда sidebar развёрнут', () => {
      localStorageMock.getItem.mockReturnValue(null)
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      expect(screen.getByText('Руководство')).toBeInTheDocument()
    })

    it('скрывает текст когда sidebar свёрнут', () => {
      localStorageMock.getItem.mockReturnValue('true')
      renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

      expect(screen.queryByText('Руководство')).not.toBeInTheDocument()
    })
  })

  describe('Role-based menu visibility (Story 9.3)', () => {
    describe('AC1 — Developer видит только доступные пункты', () => {
      it('показывает Dashboard, Routes, Metrics для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        // Видимые пункты для developer
        expect(screen.getByText('Dashboard')).toBeInTheDocument()
        expect(screen.getByText('Routes')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
      })

      it('НЕ показывает Rate Limits для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        expect(screen.queryByText('Rate Limits')).not.toBeInTheDocument()
      })

      it('НЕ показывает Approvals для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        expect(screen.queryByText('Approvals')).not.toBeInTheDocument()
      })

      it('НЕ показывает Audit Logs для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        expect(screen.queryByText('Audit Logs')).not.toBeInTheDocument()
      })

      it('НЕ показывает Test для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        expect(screen.queryByText('Test')).not.toBeInTheDocument()
      })

      it('НЕ показывает Users для developer', () => {
        renderWithMockAuth(<Sidebar />, { authValue: developerAuth })

        expect(screen.queryByText('Users')).not.toBeInTheDocument()
      })
    })

    describe('AC2 — Security видит расширенный набор меню', () => {
      it('показывает Dashboard, Routes, Approvals, Audit, Integrations, Metrics для security', () => {
        renderWithMockAuth(<Sidebar />, { authValue: securityAuth })

        expect(screen.getByText('Dashboard')).toBeInTheDocument()
        expect(screen.getByText('Routes')).toBeInTheDocument()
        expect(screen.getByText('Approvals')).toBeInTheDocument()
        expect(screen.getByText('Audit Logs')).toBeInTheDocument()
        expect(screen.getByText('Integrations')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
      })

      it('НЕ показывает Users для security', () => {
        renderWithMockAuth(<Sidebar />, { authValue: securityAuth })

        expect(screen.queryByText('Users')).not.toBeInTheDocument()
      })

      it('НЕ показывает Rate Limits для security', () => {
        renderWithMockAuth(<Sidebar />, { authValue: securityAuth })

        expect(screen.queryByText('Rate Limits')).not.toBeInTheDocument()
      })

      it('НЕ показывает Test для security', () => {
        renderWithMockAuth(<Sidebar />, { authValue: securityAuth })

        expect(screen.queryByText('Test')).not.toBeInTheDocument()
      })
    })

    describe('AC3 — Admin видит все пункты меню', () => {
      it('показывает все пункты меню для admin', () => {
        renderWithMockAuth(<Sidebar />, { authValue: adminAuth })

        expect(screen.getByText('Dashboard')).toBeInTheDocument()
        expect(screen.getByText('Users')).toBeInTheDocument()
        expect(screen.getByText('Routes')).toBeInTheDocument()
        expect(screen.getByText('Rate Limits')).toBeInTheDocument()
        expect(screen.getByText('Approvals')).toBeInTheDocument()
        expect(screen.getByText('Audit Logs')).toBeInTheDocument()
        expect(screen.getByText('Integrations')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
        expect(screen.getByText('Test')).toBeInTheDocument()
      })
    })
  })
})
