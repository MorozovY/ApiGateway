// Unit тесты для Sidebar (Story 7.6, AC7; Story 9.3 — Role-based menu filtering)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithMockAuth } from '../test/test-utils'
import Sidebar from './Sidebar'

// Мок для usePendingRoutesCount
vi.mock('@features/approval', () => ({
  usePendingRoutesCount: () => 0,
}))

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
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Navigation items (AC7)', () => {
    it('показывает Integrations пункт для security роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

      expect(screen.getByText('Integrations')).toBeInTheDocument()
    })

    it('показывает Integrations пункт для admin роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

      expect(screen.getByText('Integrations')).toBeInTheDocument()
    })

    it('НЕ показывает Integrations для developer роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

      expect(screen.queryByText('Integrations')).not.toBeInTheDocument()
    })

    it('показывает Audit Logs пункт', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

      expect(screen.getByText('Audit Logs')).toBeInTheDocument()
    })
  })

  describe('Collapsible sidebar', () => {
    it('показывает логотип "API Gateway" когда sidebar развёрнут', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

      expect(screen.getByText('API Gateway')).toBeInTheDocument()
    })

    it('скрывает логотип "API Gateway" когда sidebar свёрнут', async () => {
      renderWithMockAuth(<Sidebar collapsed={true} />, { authValue: adminAuth })

      expect(screen.queryByText('API Gateway')).not.toBeInTheDocument()
    })
  })

  describe('Role-based menu visibility (Story 9.3)', () => {
    describe('AC1 — Developer видит только доступные пункты', () => {
      it('показывает Dashboard, Routes, Metrics для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.getByText('Dashboard')).toBeInTheDocument()
        expect(screen.getByText('Routes')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
      })

      it('НЕ показывает Rate Limits для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Rate Limits')).not.toBeInTheDocument()
      })

      it('НЕ показывает Approvals для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Approvals')).not.toBeInTheDocument()
      })

      it('НЕ показывает Audit Logs для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Audit Logs')).not.toBeInTheDocument()
      })

      it('НЕ показывает Test для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Test')).not.toBeInTheDocument()
      })

      it('НЕ показывает Users для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Users')).not.toBeInTheDocument()
      })
    })

    describe('AC2 — Security видит расширенный набор меню', () => {
      it('показывает Dashboard, Routes, Approvals, Audit, Integrations, Metrics для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.getByText('Dashboard')).toBeInTheDocument()
        expect(screen.getByText('Routes')).toBeInTheDocument()
        expect(screen.getByText('Approvals')).toBeInTheDocument()
        expect(screen.getByText('Audit Logs')).toBeInTheDocument()
        expect(screen.getByText('Integrations')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
      })

      it('НЕ показывает Users для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Users')).not.toBeInTheDocument()
      })

      it('НЕ показывает Rate Limits для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Rate Limits')).not.toBeInTheDocument()
      })

      it('НЕ показывает Test для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Test')).not.toBeInTheDocument()
      })
    })

    describe('AC3 — Admin видит все пункты меню', () => {
      it('показывает все пункты меню для admin', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

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
