// Тесты для страницы формы создания/редактирования маршрута (Story 3.5)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, fireEvent, cleanup, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RouteFormPage } from './RouteFormPage'

// Мок navigate функция
const mockNavigate = vi.fn()

// Мок useParams — будем устанавливать значение перед каждым тестом
let mockParamsValue: Record<string, string> = {}

// Мок localStorage для тестов WorkflowIndicator (Story 16.10)
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: () => {
      store = {}
    },
  }
})()
vi.stubGlobal('localStorage', localStorageMock)

// Мок useLocation для тестов WorkflowIndicator (Story 16.10)
let mockLocationValue = { pathname: '/routes/new' }

// Мок react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => mockParamsValue,
    useLocation: () => mockLocationValue,
  }
})

// Мок API модуля
const mockCheckPathExists = vi.fn().mockResolvedValue(false)
vi.mock('../api/routesApi', () => ({
  fetchRouteById: vi.fn(),
  createRoute: vi.fn(),
  updateRoute: vi.fn(),
  checkPathExists: (...args: unknown[]) => mockCheckPathExists(...args),
}))

// Мок для Rate Limits (используется в RouteForm)
vi.mock('@features/rate-limits', () => ({
  useRateLimits: () => ({
    data: { items: [], total: 0 },
    isLoading: false,
    error: null,
  }),
}))

// Мок для React Query hooks — будем устанавливать значение перед каждым тестом
let mockRouteData: unknown = undefined
let mockIsLoadingRoute = false
let mockCreateMutateAsync = vi.fn()
let mockUpdateMutateAsync = vi.fn()
let mockCreateIsPending = false
let mockUpdateIsPending = false

vi.mock('../hooks/useRoutes', () => ({
  useRoute: () => ({
    data: mockRouteData,
    isLoading: mockIsLoadingRoute,
    error: null,
  }),
  useCreateRoute: () => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: mockCreateIsPending,
    reset: vi.fn(),
  }),
  useUpdateRoute: () => ({
    mutateAsync: mockUpdateMutateAsync,
    isPending: mockUpdateIsPending,
    reset: vi.fn(),
  }),
}))

describe('RouteFormPage', () => {
  beforeEach(() => {
    // Сбрасываем все моки перед каждым тестом
    vi.clearAllMocks()
    localStorageMock.clear()
    mockParamsValue = {}
    mockLocationValue = { pathname: '/routes/new' }
    mockRouteData = undefined
    mockIsLoadingRoute = false
    mockCreateMutateAsync = vi.fn()
    mockUpdateMutateAsync = vi.fn()
    mockCreateIsPending = false
    mockUpdateIsPending = false
    mockCheckPathExists.mockResolvedValue(false)
  })

  afterEach(() => {
    cleanup()
  })

  describe('режим создания', () => {
    it('отображает форму создания маршрута с заголовком "Создание маршрута"', () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Русское название согласно локализации Story 16.1
      expect(screen.getByText('Создание маршрута')).toBeInTheDocument()
    })

    it('отображает все обязательные поля формы', () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Проверяем наличие полей по label (русские названия согласно локализации Story 16.1)
      expect(screen.getByText('Путь')).toBeInTheDocument()
      expect(screen.getByText('Upstream URL')).toBeInTheDocument()
      expect(screen.getByText('HTTP методы')).toBeInTheDocument()
      expect(screen.getByText('Описание')).toBeInTheDocument()
    })

    it('отображает кнопки "Сохранить" и "Cancel"', () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      expect(screen.getByRole('button', { name: /Сохранить/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Отмена/i })).toBeInTheDocument()
    })
  })

  describe('режим редактирования', () => {
    it('отображает форму редактирования с заголовком "Edit Route"', () => {
      mockParamsValue = { id: 'test-route-id' }
      mockRouteData = {
        id: 'test-route-id',
        path: '/api/test',
        upstreamUrl: 'http://test:8080',
        methods: ['GET', 'POST'],
        description: 'Test route',
        status: 'draft',
        createdBy: 'user-1',
        createdAt: '2026-02-18T10:00:00Z',
        updatedAt: '2026-02-18T10:00:00Z',
        rateLimitId: null,
      }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/test-route-id/edit'],
      })

      expect(screen.getByText('Редактирование маршрута')).toBeInTheDocument()
    })

    it('заполняет поля формы текущими значениями маршрута', async () => {
      mockParamsValue = { id: 'test-route-id' }
      mockRouteData = {
        id: 'test-route-id',
        path: '/api/orders',
        upstreamUrl: 'http://order-service:8080',
        methods: ['GET', 'POST'],
        description: 'Order service endpoints',
        status: 'draft',
        createdBy: 'user-1',
        createdAt: '2026-02-18T10:00:00Z',
        updatedAt: '2026-02-18T10:00:00Z',
        rateLimitId: null,
      }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/test-route-id/edit'],
      })

      await waitFor(() => {
        // Path отображается без префикса "/" в input
        expect(screen.getByDisplayValue('api/orders')).toBeInTheDocument()
        expect(screen.getByDisplayValue('http://order-service:8080')).toBeInTheDocument()
        expect(screen.getByDisplayValue('Order service endpoints')).toBeInTheDocument()
      })
    })
  })

  describe('валидация', () => {
    it('показывает ошибку при пустом path', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      const submitButton = screen.getByRole('button', { name: /Сохранить/i })
      await userEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/Path обязателен/i)).toBeInTheDocument()
      })
    })

    it('показывает ошибку при невалидном URL формате', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      const urlInput = screen.getByPlaceholderText('http://service:8080')
      await userEvent.type(urlInput, 'not-a-valid-url')
      fireEvent.blur(urlInput)

      await waitFor(() => {
        expect(screen.getByText(/Некорректный формат URL/i)).toBeInTheDocument()
      })
    })

    it('показывает ошибку при отсутствии выбранных методов', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      const submitButton = screen.getByRole('button', { name: /Сохранить/i })
      await userEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/Выберите минимум один метод/i)).toBeInTheDocument()
      })
    })
  })

  describe('проверка уникальности path', () => {
    it('показывает ошибку "Path already exists" при дублирующемся path', async () => {
      mockParamsValue = {}
      mockCheckPathExists.mockResolvedValue(true)

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      const pathInput = screen.getByPlaceholderText('api/service')
      await userEvent.type(pathInput, 'existing-path')

      // Ждём debounce (500ms) + обработку
      await waitFor(
        () => {
          expect(screen.getByText(/Path already exists/i)).toBeInTheDocument()
        },
        { timeout: 1500 }
      )
    })
  })

  describe('отправка формы', () => {
    it('показывает loading spinner на кнопке во время отправки', () => {
      mockParamsValue = {}
      mockCreateIsPending = true

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Кнопка должна иметь loading state
      const submitButton = screen.getByRole('button', { name: /Сохранить/i })
      expect(submitButton).toHaveClass('ant-btn-loading')
    })

    it('блокирует отправку формы без обязательных полей', async () => {
      mockParamsValue = {}
      mockCreateMutateAsync = vi.fn()

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Пытаемся отправить пустую форму
      const submitButton = screen.getByRole('button', { name: /Сохранить/i })
      await userEvent.click(submitButton)

      // Форма не должна быть отправлена — показываются ошибки валидации
      await waitFor(() => {
        expect(screen.getByText(/Path обязателен/i)).toBeInTheDocument()
      })

      // createMutation не должен быть вызван
      expect(mockCreateMutateAsync).not.toHaveBeenCalled()
    })

    it('редиректит после успешного обновления в режиме редактирования', async () => {
      mockParamsValue = { id: 'existing-route-id' }
      mockRouteData = {
        id: 'existing-route-id',
        path: '/api/users',
        upstreamUrl: 'http://localhost:8081',
        methods: ['GET'],
        description: 'User service',
        status: 'draft',
        createdBy: 'user-1',
        createdAt: '2026-02-18T10:00:00Z',
        updatedAt: '2026-02-18T10:00:00Z',
        rateLimitId: null,
      }
      mockUpdateMutateAsync = vi.fn().mockResolvedValue({
        id: 'existing-route-id',
        path: '/api/users',
        upstreamUrl: 'http://localhost:8081',
        methods: ['GET'],
        status: 'draft',
      })

      await act(async () => {
        renderWithMockAuth(<RouteFormPage />, {
          authValue: { isAuthenticated: true },
          initialEntries: ['/routes/existing-route-id/edit'],
        })
      })

      // Ждём загрузки данных в форму и полной синхронизации Ant Design Form
      await waitFor(
        () => {
          expect(screen.getByDisplayValue('api/users')).toBeInTheDocument()
          expect(screen.getByDisplayValue('http://localhost:8081')).toBeInTheDocument()
        },
        { timeout: 2000 }
      )

      // Дополнительная пауза для синхронизации Ant Design Form state
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 100))
      })

      const submitButton = screen.getByRole('button', { name: /Сохранить/i })
      await act(async () => {
        await userEvent.click(submitButton)
      })

      // Ждём вызова mutation и редиректа
      await waitFor(
        () => {
          expect(mockUpdateMutateAsync).toHaveBeenCalled()
        },
        { timeout: 3000 }
      )

      expect(mockNavigate).toHaveBeenCalledWith('/routes/existing-route-id')
    })
  })

  describe('навигация', () => {
    it('переходит к списку маршрутов при нажатии Cancel', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      const cancelButton = screen.getByRole('button', { name: /Отмена/i })
      await userEvent.click(cancelButton)

      expect(mockNavigate).toHaveBeenCalledWith('/routes')
    })

    it('переходит к списку маршрутов при нажатии кнопки назад', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Ищем кнопку назад по aria-label иконки
      const backButton = screen.getByRole('button', { name: /arrow-left/i })
      await userEvent.click(backButton)

      expect(mockNavigate).toHaveBeenCalledWith('/routes')
    })
  })

  describe('keyboard shortcuts', () => {
    it('обрабатывает нажатие Ctrl+Enter для отправки формы', async () => {
      mockParamsValue = {}
      mockCreateMutateAsync = vi.fn().mockResolvedValue({
        id: 'new-route-id',
        path: '/api/test',
        upstreamUrl: 'http://test:8080',
        methods: ['GET'],
        status: 'draft',
      })

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Заполняем валидную форму
      const pathInput = screen.getByPlaceholderText('api/service')
      await userEvent.type(pathInput, 'api/test')

      const urlInput = screen.getByPlaceholderText('http://service:8080')
      await userEvent.type(urlInput, 'http://test:8080')

      // Нажимаем Ctrl+Enter (форма без methods — валидация не пройдёт)
      fireEvent.keyDown(document, { key: 'Enter', ctrlKey: true })

      // Проверяем что keyboard shortcut был обработан (форма пытается отправиться)
      // Ошибка валидации методов показывается — значит форма пыталась отправиться
      await waitFor(() => {
        expect(screen.getByText(/Выберите минимум один метод/i)).toBeInTheDocument()
      })
    })

    it('успешно отправляет форму по Ctrl+Enter в режиме редактирования', async () => {
      mockParamsValue = { id: 'keyboard-route-id' }
      mockRouteData = {
        id: 'keyboard-route-id',
        path: '/api/keyboard',
        upstreamUrl: 'http://localhost:8080',
        methods: ['GET'],
        description: '',
        status: 'draft',
        createdBy: 'user-1',
        createdAt: '2026-02-18T10:00:00Z',
        updatedAt: '2026-02-18T10:00:00Z',
        rateLimitId: null,
      }
      mockUpdateMutateAsync = vi.fn().mockResolvedValue({
        id: 'keyboard-route-id',
        path: '/api/keyboard',
        upstreamUrl: 'http://localhost:8080',
        methods: ['GET'],
        status: 'draft',
      })

      await act(async () => {
        renderWithMockAuth(<RouteFormPage />, {
          authValue: { isAuthenticated: true },
          initialEntries: ['/routes/keyboard-route-id/edit'],
        })
      })

      // Ждём загрузки данных в форму и полной синхронизации Ant Design Form
      await waitFor(
        () => {
          expect(screen.getByDisplayValue('api/keyboard')).toBeInTheDocument()
          expect(screen.getByDisplayValue('http://localhost:8080')).toBeInTheDocument()
        },
        { timeout: 2000 }
      )

      // Дополнительная пауза для синхронизации Ant Design Form state
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 100))
      })

      // Нажимаем Ctrl+Enter
      await act(async () => {
        fireEvent.keyDown(document, { key: 'Enter', ctrlKey: true })
      })

      // Ждём вызова mutation и редиректа
      await waitFor(
        () => {
          expect(mockUpdateMutateAsync).toHaveBeenCalled()
        },
        { timeout: 3000 }
      )

      expect(mockNavigate).toHaveBeenCalledWith('/routes/keyboard-route-id')
    })

    it('обрабатывает нажатие Cmd+Enter на Mac', async () => {
      mockParamsValue = {}

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Нажимаем Cmd+Enter (metaKey)
      fireEvent.keyDown(document, { key: 'Enter', metaKey: true })

      // Форма пытается отправиться — показывается валидация
      await waitFor(() => {
        expect(screen.getByText(/Path обязателен/i)).toBeInTheDocument()
      })
    })
  })

  // Story 16.10: WorkflowIndicator
  describe('WorkflowIndicator (Story 16.10)', () => {
    it('отображает кнопку toggle для WorkflowIndicator (AC3)', () => {
      mockParamsValue = {}
      mockLocationValue = { pathname: '/routes/new' }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      expect(screen.getByTestId('workflow-toggle')).toBeInTheDocument()
    })

    it('WorkflowIndicator скрыт по умолчанию (AC5)', () => {
      mockParamsValue = {}
      mockLocationValue = { pathname: '/routes/new' }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Индикатор скрыт по умолчанию
      expect(screen.queryByTestId('workflow-indicator')).not.toBeInTheDocument()
    })

    it('показывает WorkflowIndicator при клике на toggle (AC1)', async () => {
      mockParamsValue = {}
      mockLocationValue = { pathname: '/routes/new' }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Кликаем на кнопку toggle
      await userEvent.click(screen.getByTestId('workflow-toggle'))

      // Теперь индикатор должен быть виден
      await waitFor(() => {
        expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()
      })

      // Проверяем что отображаются 4 шага
      expect(screen.getByText('Создание')).toBeInTheDocument()
      expect(screen.getByText('Отправка')).toBeInTheDocument()
      expect(screen.getByText('Согласование')).toBeInTheDocument()
      expect(screen.getByText('Публикация')).toBeInTheDocument()
    })

    it('сохраняет состояние WorkflowIndicator в localStorage (AC4)', async () => {
      mockParamsValue = {}
      mockLocationValue = { pathname: '/routes/new' }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Кликаем на toggle чтобы показать
      await userEvent.click(screen.getByTestId('workflow-toggle'))

      // Проверяем что состояние сохранено в localStorage
      await waitFor(() => {
        expect(localStorageMock.setItem).toHaveBeenCalledWith(
          'workflow-indicator-visible',
          'true'
        )
      })
    })

    it('показывает текущий шаг 0 (Создание) для /routes/new (AC2)', async () => {
      mockParamsValue = {}
      mockLocationValue = { pathname: '/routes/new' }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/new'],
      })

      // Показываем WorkflowIndicator
      await userEvent.click(screen.getByTestId('workflow-toggle'))

      await waitFor(() => {
        expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()
      })

      // Проверяем что шаг "Создание" активен (первый шаг, индекс 0)
      const indicator = screen.getByTestId('workflow-indicator')
      const steps = indicator.querySelectorAll('.ant-steps-item')
      expect(steps[0]).toHaveClass('ant-steps-item-active')
    })

    it('показывает текущий шаг 1 (Отправка) для /routes/:id/edit с draft статусом (AC2)', async () => {
      mockParamsValue = { id: 'draft-route-id' }
      mockLocationValue = { pathname: '/routes/draft-route-id/edit' }
      mockRouteData = {
        id: 'draft-route-id',
        path: '/api/test',
        upstreamUrl: 'http://test:8080',
        methods: ['GET'],
        description: 'Test route',
        status: 'draft',
        createdBy: 'user-1',
        createdAt: '2026-02-18T10:00:00Z',
        updatedAt: '2026-02-18T10:00:00Z',
        rateLimitId: null,
      }

      renderWithMockAuth(<RouteFormPage />, {
        authValue: { isAuthenticated: true },
        initialEntries: ['/routes/draft-route-id/edit'],
      })

      // Показываем WorkflowIndicator
      await userEvent.click(screen.getByTestId('workflow-toggle'))

      await waitFor(() => {
        expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()
      })

      // Проверяем что шаг "Отправка" активен (второй шаг, индекс 1)
      const indicator = screen.getByTestId('workflow-indicator')
      const steps = indicator.querySelectorAll('.ant-steps-item')
      expect(steps[1]).toHaveClass('ant-steps-item-active')
    })
  })
})
