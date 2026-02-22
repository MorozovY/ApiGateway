// Unit тесты для MainLayout (Story 10.7 — Quick Start Guide, collapse button)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../test/test-utils'
import MainLayout from './MainLayout'

// Мок для usePendingRoutesCount
vi.mock('@features/approval', () => ({
  usePendingRoutesCount: () => 0,
}))

// Мок для useThemeContext
vi.mock('@shared/providers', () => ({
  useThemeContext: () => ({ isDark: false }),
}))

// Мок для ThemeSwitcher
vi.mock('@shared/components', () => ({
  ThemeSwitcher: () => <button data-testid="theme-switcher">Theme</button>,
}))

// Мок для ChangePasswordModal
vi.mock('@features/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@features/auth')>()
  return {
    ...actual,
    ChangePasswordModal: () => null,
  }
})

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

describe('MainLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'localStorage', { value: localStorageMock })
    localStorageMock.getItem.mockReturnValue(null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Quick Start Guide link (Story 10.7)', () => {
    it('отображает ссылку на Quick Start Guide для admin', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toBeInTheDocument()
    })

    it('отображает ссылку на Quick Start Guide для developer', () => {
      renderWithMockAuth(<MainLayout />, { authValue: developerAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toBeInTheDocument()
    })

    it('отображает ссылку на Quick Start Guide для security', () => {
      renderWithMockAuth(<MainLayout />, { authValue: securityAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toBeInTheDocument()
    })

    it('ссылка открывается в новой вкладке', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toHaveAttribute('target', '_blank')
      expect(guideLink).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('ссылка ведёт на /docs/quick-start-guide.html', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toHaveAttribute('href', '/docs/quick-start-guide.html')
    })

    it('имеет aria-label для accessibility', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const guideLink = screen.getByTestId('quick-start-guide-link')
      expect(guideLink).toHaveAttribute('aria-label', 'Открыть руководство пользователя')
    })
  })

  describe('Sidebar collapse button', () => {
    it('отображает кнопку сворачивания sidebar', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const collapseButton = screen.getByTestId('sidebar-collapse-button')
      expect(collapseButton).toBeInTheDocument()
    })

    it('по умолчанию sidebar развёрнут (collapsed=false)', () => {
      localStorageMock.getItem.mockReturnValue(null)
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      // Логотип "API Gateway" должен быть видим
      expect(screen.getByText('API Gateway')).toBeInTheDocument()
    })

    it('читает collapsed state из localStorage', () => {
      localStorageMock.getItem.mockReturnValue('true')
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      // При collapsed sidebar логотип текст не показывается
      expect(screen.queryByText('API Gateway')).not.toBeInTheDocument()
    })

    it('сохраняет collapsed state в localStorage при клике', async () => {
      localStorageMock.getItem.mockReturnValue(null)
      const user = userEvent.setup()
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const collapseButton = screen.getByTestId('sidebar-collapse-button')
      await user.click(collapseButton)

      expect(localStorageMock.setItem).toHaveBeenCalledWith('sidebar-collapsed', 'true')
    })

    it('переключает collapsed state при клике (expand)', async () => {
      localStorageMock.getItem.mockReturnValue('true')
      const user = userEvent.setup()
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      const collapseButton = screen.getByTestId('sidebar-collapse-button')
      await user.click(collapseButton)

      expect(localStorageMock.setItem).toHaveBeenCalledWith('sidebar-collapsed', 'false')
    })
  })

  describe('Header elements', () => {
    it('отображает заголовок Admin Panel', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      expect(screen.getByText('Admin Panel')).toBeInTheDocument()
    })

    it('отображает ThemeSwitcher', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      expect(screen.getByTestId('theme-switcher')).toBeInTheDocument()
    })

    it('отображает имя пользователя', () => {
      renderWithMockAuth(<MainLayout />, { authValue: adminAuth })

      expect(screen.getByText('admin')).toBeInTheDocument()
    })
  })
})
