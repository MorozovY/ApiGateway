// Тесты для PageBreadcrumbs компонента (Story 16.6)
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { PageBreadcrumbs } from './PageBreadcrumbs'
import { ThemeContext } from '@shared/providers'

// Создание тестового QueryClient с возможностью задать route в кэш
function createTestQueryClient(routeData?: { id: string; path: string; status: string }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  // Устанавливаем route в кэш с ключом как в useRoute hook
  if (routeData) {
    queryClient.setQueryData(['routes', routeData.id], routeData)
  }

  return queryClient
}

// Обёртка для тестов с необходимыми провайдерами
interface RenderOptions {
  initialEntries?: string[]
  queryClient?: QueryClient
  isDark?: boolean
}

function renderWithProviders(
  ui: React.ReactElement,
  { initialEntries = ['/'], queryClient, isDark = false }: RenderOptions = {}
) {
  const client = queryClient || createTestQueryClient()
  const themeValue = {
    isDark,
    toggleTheme: vi.fn(),
  }

  // Определяем роуты для правильной работы useParams
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <ThemeContext.Provider value={themeValue}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path="/dashboard" element={ui} />
              <Route path="/routes" element={ui} />
              <Route path="/routes/new" element={ui} />
              <Route path="/routes/:id" element={ui} />
              <Route path="/routes/:id/edit" element={ui} />
              <Route path="/users" element={ui} />
              <Route path="/audit" element={ui} />
              <Route path="/audit/integrations" element={ui} />
              <Route path="/metrics" element={ui} />
              <Route path="*" element={ui} />
            </Routes>
          </MemoryRouter>
        </ThemeContext.Provider>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

describe('PageBreadcrumbs', () => {
  describe('Top-level страницы (без breadcrumbs)', () => {
    it('не отображает breadcrumbs на Dashboard', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/dashboard'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })

    it('не отображает breadcrumbs на Routes list', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })

    it('не отображает breadcrumbs на Users', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/users'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })

    it('не отображает breadcrumbs на Audit', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/audit'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })

    it('не отображает breadcrumbs на Metrics', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/metrics'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })
  })

  describe('Routes breadcrumbs', () => {
    it('отображает breadcrumbs для /routes/new (AC3)', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      expect(breadcrumbs).toBeInTheDocument()

      // Проверяем текст "Маршруты" и "Новый маршрут"
      expect(screen.getByText('Маршруты')).toBeInTheDocument()
      expect(screen.getByText('Новый маршрут')).toBeInTheDocument()
    })

    it('"Маршруты" — ссылка на /routes для /routes/new', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      const routesLink = screen.getByRole('link', { name: 'Маршруты' })
      expect(routesLink).toHaveAttribute('href', '/routes')
    })

    it('отображает breadcrumbs для /routes/:id с route.path из кэша (AC2)', () => {
      const queryClient = createTestQueryClient({ id: '123', path: '/api/users', status: 'published' })

      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/123'],
        queryClient,
      })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      expect(breadcrumbs).toBeInTheDocument()

      expect(screen.getByText('Маршруты')).toBeInTheDocument()
      expect(screen.getByText('/api/users')).toBeInTheDocument()
    })

    it('отображает fallback "Маршрут" когда route не в кэше', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/999'] })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      expect(breadcrumbs).toBeInTheDocument()

      expect(screen.getByText('Маршруты')).toBeInTheDocument()
      expect(screen.getByText('Маршрут')).toBeInTheDocument()
    })

    it('отображает breadcrumbs для /routes/:id/edit (AC1)', () => {
      const queryClient = createTestQueryClient({ id: '456', path: '/api/products', status: 'draft' })

      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/456/edit'],
        queryClient,
      })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      expect(breadcrumbs).toBeInTheDocument()

      expect(screen.getByText('Маршруты')).toBeInTheDocument()
      expect(screen.getByText('/api/products')).toBeInTheDocument()
      expect(screen.getByText('Редактирование')).toBeInTheDocument()
    })

    it('все элементы кроме последнего кликабельны для /routes/:id/edit (AC1)', () => {
      const queryClient = createTestQueryClient({ id: '456', path: '/api/products', status: 'draft' })

      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/456/edit'],
        queryClient,
      })

      // "Маршруты" — ссылка на /routes
      const routesLink = screen.getByRole('link', { name: 'Маршруты' })
      expect(routesLink).toHaveAttribute('href', '/routes')

      // "/api/products" — ссылка на /routes/456
      const routeLink = screen.getByRole('link', { name: '/api/products' })
      expect(routeLink).toHaveAttribute('href', '/routes/456')

      // "Редактирование" — не ссылка
      const editText = screen.getByText('Редактирование')
      expect(editText.tagName).not.toBe('A')
      expect(editText.closest('a')).toBeNull()
    })
  })

  describe('Audit breadcrumbs', () => {
    it('отображает breadcrumbs для /audit/integrations (AC4)', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/audit/integrations'] })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      expect(breadcrumbs).toBeInTheDocument()

      expect(screen.getByText('Журнал аудита')).toBeInTheDocument()
      expect(screen.getByText('Интеграции')).toBeInTheDocument()
    })

    it('"Журнал аудита" — ссылка на /audit для /audit/integrations', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/audit/integrations'] })

      const auditLink = screen.getByRole('link', { name: 'Журнал аудита' })
      expect(auditLink).toHaveAttribute('href', '/audit')
    })

    it('"Интеграции" — последний элемент, не ссылка', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/audit/integrations'] })

      const integrationsText = screen.getByText('Интеграции')
      expect(integrationsText.tagName).not.toBe('A')
      expect(integrationsText.closest('a')).toBeNull()
    })
  })

  describe('Стилизация (AC5)', () => {
    it('использует Ant Design Breadcrumb компонент', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      // Ant Design Breadcrumb добавляет определённые классы
      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      const antBreadcrumb = breadcrumbs.querySelector('.ant-breadcrumb')
      expect(antBreadcrumb).toBeInTheDocument()
    })

    it('имеет inline стили для padding', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      // Проверяем через атрибут style напрямую
      expect(breadcrumbs.getAttribute('style')).toContain('padding: 12px 24px')
    })

    it('применяет светлую тему по умолчанию', () => {
      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/new'],
        isDark: false,
      })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      // Проверяем цвет фона в формате rgb
      expect(breadcrumbs.getAttribute('style')).toContain('background: rgb(250, 250, 250)')
    })

    it('применяет тёмную тему когда isDark=true', () => {
      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/new'],
        isDark: true,
      })

      const breadcrumbs = screen.getByTestId('page-breadcrumbs')
      // Проверяем цвет фона в формате rgb
      expect(breadcrumbs.getAttribute('style')).toContain('background: rgb(31, 31, 31)')
    })
  })

  describe('data-testid', () => {
    it('имеет data-testid="page-breadcrumbs" для E2E тестов', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      expect(screen.getByTestId('page-breadcrumbs')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('имеет aria-label на nav элементе', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      const nav = screen.getByRole('navigation', { name: 'Навигация' })
      expect(nav).toBeInTheDocument()
    })

    it('последний элемент имеет aria-current="page"', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/routes/new'] })

      const currentPage = screen.getByText('Новый маршрут')
      expect(currentPage).toHaveAttribute('aria-current', 'page')
    })

    it('промежуточные элементы не имеют aria-current', () => {
      const queryClient = createTestQueryClient({ id: '123', path: '/api/users', status: 'published' })

      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/123/edit'],
        queryClient,
      })

      // "Маршруты" — первый элемент, не должен иметь aria-current
      const routesLink = screen.getByRole('link', { name: 'Маршруты' })
      expect(routesLink).not.toHaveAttribute('aria-current')
    })
  })

  describe('Edge cases', () => {
    it('корректно отображает route.path со спецсимволами', () => {
      const queryClient = createTestQueryClient({
        id: '789',
        path: '/api/users/{id}/profile',
        status: 'published',
      })

      renderWithProviders(<PageBreadcrumbs />, {
        initialEntries: ['/routes/789'],
        queryClient,
      })

      expect(screen.getByText('/api/users/{id}/profile')).toBeInTheDocument()
    })

    it('не отображает breadcrumbs для несуществующего маршрута', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/unknown/deep/path'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })

    it('не отображает breadcrumbs для /settings', () => {
      renderWithProviders(<PageBreadcrumbs />, { initialEntries: ['/settings'] })

      expect(screen.queryByTestId('page-breadcrumbs')).not.toBeInTheDocument()
    })
  })
})
