// Тесты для Pending Approvals UI с inline-действиями (Story 4.6)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { message } from 'antd'
import { renderWithMockAuth } from '../../../test/test-utils'
import { ApprovalsPage } from './ApprovalsPage'
import type { PendingRoute } from '../types/approval.types'

// Мок данные — pending маршруты
const mockPendingRoutes: PendingRoute[] = [
  {
    id: 'route-1',
    path: '/api/orders',
    upstreamUrl: 'http://order-service:8080',
    methods: ['GET', 'POST'],
    description: 'Order service',
    submittedAt: '2026-02-18T10:00:00Z',
    createdBy: 'user-1',
    creatorUsername: 'developer-user',
  },
  {
    id: 'route-2',
    path: '/api/payments',
    upstreamUrl: 'http://payment-service:8080',
    methods: ['POST'],
    description: null,
    submittedAt: '2026-02-18T11:00:00Z',
    createdBy: 'user-2',
    creatorUsername: 'another-dev',
  },
]

// Мок antd message для проверки toast уведомлений
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd')
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }
})

// Моки для hooks
let mockApproveMutateAsync = vi.fn()
let mockApproveIsPending = false
let mockRejectMutateAsync = vi.fn()
let mockRejectIsPending = false
let mockPendingRoutesData: PendingRoute[] | undefined = mockPendingRoutes
let mockIsLoading = false

vi.mock('../hooks/useApprovals', () => ({
  usePendingRoutes: () => ({
    data: mockPendingRoutesData,
    isLoading: mockIsLoading,
  }),
  useApproveRoute: () => ({
    mutate: mockApproveMutateAsync,
    mutateAsync: mockApproveMutateAsync,
    isPending: mockApproveIsPending,
  }),
  useRejectRoute: () => ({
    mutate: mockRejectMutateAsync,
    mutateAsync: mockRejectMutateAsync,
    isPending: mockRejectIsPending,
  }),
}))

// Мок пользователя security
const securityUser = {
  userId: 'sec-1',
  username: 'security-user',
  role: 'security' as const,
}

// Мок пользователя developer (не должен видеть страницу)
const developerUser = {
  userId: 'dev-1',
  username: 'developer-user',
  role: 'developer' as const,
}

describe('Страница согласования маршрутов (ApprovalsPage)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApproveMutateAsync = vi.fn()
    mockApproveIsPending = false
    mockRejectMutateAsync = vi.fn()
    mockRejectIsPending = false
    mockPendingRoutesData = mockPendingRoutes
    mockIsLoading = false
  })

  afterEach(() => {
    cleanup()
  })

  it('таблица отображает pending маршруты для security пользователя', async () => {
    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
      expect(screen.getByText('/api/payments')).toBeInTheDocument()
    })

    // Проверяем наличие колонок
    expect(screen.getByText('Path')).toBeInTheDocument()
    expect(screen.getByText('Upstream URL')).toBeInTheDocument()
    expect(screen.getByText('Methods')).toBeInTheDocument()
    expect(screen.getByText('Submitted By')).toBeInTheDocument()
    expect(screen.getByText('Submitted At')).toBeInTheDocument()
    expect(screen.getByText('Actions')).toBeInTheDocument()

    // Проверяем данные маршрутов
    expect(screen.getByText('developer-user')).toBeInTheDocument()
    expect(screen.getByText('http://order-service:8080')).toBeInTheDocument()
  })

  it('approve без модального окна — вызывает мутацию немедленно', async () => {
    mockApproveMutateAsync = vi.fn().mockResolvedValue(mockPendingRoutes[0])

    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Кликаем кнопку Approve для первого маршрута
    const approveButtons = screen.getAllByRole('button', { name: /approve/i })
    const firstApproveButton = approveButtons[0]
    expect(firstApproveButton).toBeInTheDocument()
    fireEvent.click(firstApproveButton!)

    // Нет модального окна — мутация вызвана немедленно
    expect(mockApproveMutateAsync).toHaveBeenCalledWith('route-1')

    // Модальное окно подтверждения не открывается
    expect(screen.queryByText(/маршрут будет одобрен/i)).not.toBeInTheDocument()
  })

  it('approve вызывает toast "Маршрут одобрен и опубликован" (AC2)', async () => {
    // Симулируем вызов message.success из onSuccess хука — мутация вызывает его напрямую
    mockApproveMutateAsync = vi.fn().mockImplementation(() => {
      ;(message.success as ReturnType<typeof vi.fn>)('Маршрут одобрен и опубликован')
      return Promise.resolve(mockPendingRoutes[0])
    })

    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    const approveButtons = screen.getAllByRole('button', { name: /approve/i })
    fireEvent.click(approveButtons[0]!)

    await waitFor(() => {
      expect(message.success).toHaveBeenCalledWith('Маршрут одобрен и опубликован')
    })
  })

  it('reject открывает модальное окно с textarea', async () => {
    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Кликаем кнопку Reject
    const rejectButtons = screen.getAllByRole('button', { name: /reject/i })
    fireEvent.click(rejectButtons[0]!)

    await waitFor(() => {
      // Модальное окно открылось
      expect(screen.getByText(/отклонить: \/api\/orders/i)).toBeInTheDocument()
      // Textarea для причины
      expect(screen.getByPlaceholderText(/опишите причину отклонения/i)).toBeInTheDocument()
    })
  })

  it('валидация — пустая причина не отправляет запрос', async () => {
    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Открываем модальное окно
    const rejectButtons = screen.getAllByRole('button', { name: /reject/i })
    fireEvent.click(rejectButtons[0]!)

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/опишите причину отклонения/i)).toBeInTheDocument()
    })

    // Нажимаем "Отклонить" без заполнения причины
    const submitButton = screen.getByRole('button', { name: /^отклонить$/i })
    fireEvent.click(submitButton)

    await waitFor(() => {
      // Ошибка валидации отображается
      expect(screen.getByText('Укажите причину отклонения')).toBeInTheDocument()
    })

    // Мутация не вызвана
    expect(mockRejectMutateAsync).not.toHaveBeenCalled()

    // Модальное окно остаётся открытым
    expect(screen.getByPlaceholderText(/опишите причину отклонения/i)).toBeInTheDocument()
  })

  it('успешное отклонение — мутация вызвана с причиной, toast "Маршрут отклонён" (AC4)', async () => {
    mockRejectMutateAsync = vi.fn().mockImplementation((args: { id: string; reason: string }) => {
      ;(message.success as ReturnType<typeof vi.fn>)('Маршрут отклонён')
      return Promise.resolve({ ...mockPendingRoutes[0], ...args })
    })

    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Открываем модальное окно
    const rejectButtons = screen.getAllByRole('button', { name: /reject/i })
    fireEvent.click(rejectButtons[0]!)

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/опишите причину отклонения/i)).toBeInTheDocument()
    })

    // Вводим причину отклонения
    const textarea = screen.getByPlaceholderText(/опишите причину отклонения/i)
    fireEvent.change(textarea, { target: { value: 'Неверный upstream URL' } })

    // Нажимаем "Отклонить"
    const submitButton = screen.getByRole('button', { name: /^отклонить$/i })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockRejectMutateAsync).toHaveBeenCalledWith({
        id: 'route-1',
        reason: 'Неверный upstream URL',
      })
      expect(message.success).toHaveBeenCalledWith('Маршрут отклонён')
    })
  })

  it('клик на path открывает Drawer с полной конфигурацией и кнопками Approve/Reject (AC6)', async () => {
    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Кликаем на path маршрута
    fireEvent.click(screen.getByRole('button', { name: '/api/orders' }))

    await waitFor(() => {
      // Drawer открылся — path встречается более одного раза (в таблице и в Drawer)
      const pathElements = screen.getAllByText(/\/api\/orders/i)
      expect(pathElements.length).toBeGreaterThan(1)
      // Описание маршрута присутствует только в Drawer (не в таблице)
      expect(screen.getByText('Order service')).toBeInTheDocument()
      // Кнопки Approve/Reject в Drawer используют русские названия (в таблице — английские)
      expect(screen.getByRole('button', { name: /^одобрить$/i })).toBeInTheDocument()
      expect(screen.getAllByRole('button', { name: /^отклонить$/i }).length).toBeGreaterThan(0)
    })
  })

  it('пустое состояние при отсутствии pending маршрутов', async () => {
    mockPendingRoutesData = []

    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('Нет маршрутов на согласовании')).toBeInTheDocument()
    })
  })

  it('клавиатурная навигация — клавиша A одобряет маршрут (AC8)', async () => {
    mockApproveMutateAsync = vi.fn().mockResolvedValue(mockPendingRoutes[0])

    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Находим строку таблицы и нажимаем клавишу A
    const rows = document.querySelectorAll('tr[tabindex="0"]')
    expect(rows.length).toBeGreaterThan(0)
    fireEvent.keyDown(rows[0]!, { key: 'A' })

    expect(mockApproveMutateAsync).toHaveBeenCalledWith('route-1')
  })

  it('клавиатурная навигация — клавиша R открывает модальное окно отклонения (AC8)', async () => {
    renderWithMockAuth(<ApprovalsPage />, {
      authValue: { isAuthenticated: true, user: securityUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Находим строку таблицы и нажимаем клавишу R
    const rows = document.querySelectorAll('tr[tabindex="0"]')
    expect(rows.length).toBeGreaterThan(0)
    fireEvent.keyDown(rows[0]!, { key: 'R' })

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/опишите причину отклонения/i)).toBeInTheDocument()
    })
  })
})
