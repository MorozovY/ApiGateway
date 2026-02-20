// Unit тесты для Sidebar (Story 7.6, AC7, AC8)
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

    it('toggle collapsed state при клике', async () => {
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
})
