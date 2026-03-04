// Unit тесты для Sidebar (Story 7.6, AC7; Story 9.3 — Role-based menu filtering; Story 16.1 — локализация на русский)
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
    it('показывает Интеграции пункт для security роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

      expect(screen.getByText('Интеграции')).toBeInTheDocument()
    })

    it('показывает Интеграции пункт для admin роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

      expect(screen.getByText('Интеграции')).toBeInTheDocument()
    })

    it('НЕ показывает Интеграции для developer роли', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

      expect(screen.queryByText('Интеграции')).not.toBeInTheDocument()
    })

    it('показывает Аудит пункт', async () => {
      renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

      expect(screen.getByText('Аудит')).toBeInTheDocument()
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
      it('показывает Главная, Маршруты, Метрики для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.getByText('Главная')).toBeInTheDocument()
        expect(screen.getByText('Маршруты')).toBeInTheDocument()
        expect(screen.getByText('Метрики')).toBeInTheDocument()
      })

      it('НЕ показывает Лимиты для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Лимиты')).not.toBeInTheDocument()
      })

      it('НЕ показывает Согласования для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Согласования')).not.toBeInTheDocument()
      })

      it('НЕ показывает Аудит для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Аудит')).not.toBeInTheDocument()
      })

      it('НЕ показывает Тестирование для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Тестирование')).not.toBeInTheDocument()
      })

      it('НЕ показывает Пользователи для developer', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: developerAuth })

        expect(screen.queryByText('Пользователи')).not.toBeInTheDocument()
      })
    })

    describe('AC2 — Security видит расширенный набор меню', () => {
      it('показывает Главная, Маршруты, Согласования, Аудит, Интеграции, Метрики для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.getByText('Главная')).toBeInTheDocument()
        expect(screen.getByText('Маршруты')).toBeInTheDocument()
        expect(screen.getByText('Согласования')).toBeInTheDocument()
        expect(screen.getByText('Аудит')).toBeInTheDocument()
        expect(screen.getByText('Интеграции')).toBeInTheDocument()
        expect(screen.getByText('Метрики')).toBeInTheDocument()
      })

      it('НЕ показывает Пользователи для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Пользователи')).not.toBeInTheDocument()
      })

      it('НЕ показывает Лимиты для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Лимиты')).not.toBeInTheDocument()
      })

      it('НЕ показывает Тестирование для security', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: securityAuth })

        expect(screen.queryByText('Тестирование')).not.toBeInTheDocument()
      })
    })

    describe('AC3 — Admin видит все пункты меню', () => {
      it('показывает все пункты меню для admin', () => {
        renderWithMockAuth(<Sidebar collapsed={false} />, { authValue: adminAuth })

        expect(screen.getByText('Главная')).toBeInTheDocument()
        expect(screen.getByText('Пользователи')).toBeInTheDocument()
        expect(screen.getByText('Потребители')).toBeInTheDocument()
        expect(screen.getByText('Маршруты')).toBeInTheDocument()
        expect(screen.getByText('Лимиты')).toBeInTheDocument()
        expect(screen.getByText('Согласования')).toBeInTheDocument()
        expect(screen.getByText('Аудит')).toBeInTheDocument()
        expect(screen.getByText('Интеграции')).toBeInTheDocument()
        expect(screen.getByText('Метрики')).toBeInTheDocument()
        expect(screen.getByText('Тестирование')).toBeInTheDocument()
      })
    })
  })
})
