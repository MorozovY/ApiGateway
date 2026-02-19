// Тесты для RateLimitsPage компонента (Story 5.4)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import RateLimitsPage from './RateLimitsPage'
import * as rateLimitsApi from '../api/rateLimitsApi'
import type { RateLimitListResponse, RateLimit } from '../types/rateLimit.types'

// Мокаем API
vi.mock('../api/rateLimitsApi', () => ({
  getRateLimits: vi.fn(),
  deleteRateLimit: vi.fn(),
  createRateLimit: vi.fn(),
  updateRateLimit: vi.fn(),
  getRoutesByRateLimitId: vi.fn(),
}))

const mockGetRateLimits = rateLimitsApi.getRateLimits as ReturnType<typeof vi.fn>
const mockDeleteRateLimit = rateLimitsApi.deleteRateLimit as ReturnType<typeof vi.fn>
const mockGetRoutesByRateLimitId = rateLimitsApi.getRoutesByRateLimitId as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRateLimits: RateLimit[] = [
  {
    id: '1',
    name: 'Basic Limit',
    description: 'Базовое ограничение для API',
    requestsPerSecond: 10,
    burstSize: 20,
    usageCount: 5, // Используется маршрутами
    createdBy: 'admin-1',
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-01T10:00:00Z',
  },
  {
    id: '2',
    name: 'Premium Limit',
    description: null,
    requestsPerSecond: 100,
    burstSize: 200,
    usageCount: 0, // Не используется
    createdBy: 'admin-1',
    createdAt: '2026-02-10T10:00:00Z',
    updatedAt: '2026-02-10T10:00:00Z',
  },
]

const mockRateLimitListResponse: RateLimitListResponse = {
  items: mockRateLimits,
  total: 2,
  offset: 0,
  limit: 10,
}

describe('RateLimitsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetRateLimits.mockResolvedValue(mockRateLimitListResponse)
    mockDeleteRateLimit.mockResolvedValue(undefined)
    mockGetRoutesByRateLimitId.mockResolvedValue([])
  })

  it('рендерит заголовок "Rate Limit Policies" (AC1)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByText('Rate Limit Policies')).toBeInTheDocument()
  })

  it('показывает кнопку "New Policy" для admin (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByRole('button', { name: /new policy/i })).toBeInTheDocument()
  })

  it('скрывает кнопку "New Policy" для developer (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'developer', role: 'developer' },
        isAuthenticated: true,
      },
    })

    expect(screen.queryByRole('button', { name: /new policy/i })).not.toBeInTheDocument()
  })

  it('скрывает кнопку "New Policy" для security (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'security', role: 'security' },
        isAuthenticated: true,
      },
    })

    expect(screen.queryByRole('button', { name: /new policy/i })).not.toBeInTheDocument()
  })

  it('показывает таблицу с политиками (AC1)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    expect(screen.getByText('Premium Limit')).toBeInTheDocument()
  })

  it('открывает модальное окно создания при клике на "New Policy"', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Ждём загрузку данных
    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByRole('button', { name: /new policy/i }))

    // Модальное окно появляется с заголовком — проверяем в document.body
    await waitFor(() => {
      const modalTitle = document.querySelector('.ant-modal-title')
      expect(modalTitle).toHaveTextContent('New Policy')
    })
  })

  it('показывает Edit и Delete для admin (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Для каждой строки есть Edit и Delete — ищем по иконкам
    const editIcons = document.querySelectorAll('.anticon-edit')
    const deleteIcons = document.querySelectorAll('.anticon-delete')

    expect(editIcons).toHaveLength(2)
    expect(deleteIcons).toHaveLength(2)
  })

  it('скрывает Edit и Delete для developer (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'developer', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Таблица отображается, но кнопок Edit/Delete нет — ищем по иконкам
    const editIcons = document.querySelectorAll('.anticon-edit')
    const deleteIcons = document.querySelectorAll('.anticon-delete')

    expect(editIcons).toHaveLength(0)
    expect(deleteIcons).toHaveLength(0)
  })

  it('скрывает Edit и Delete для security (AC8)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'security', role: 'security' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Ищем по иконкам
    const editIcons = document.querySelectorAll('.anticon-edit')
    const deleteIcons = document.querySelectorAll('.anticon-delete')

    expect(editIcons).toHaveLength(0)
    expect(deleteIcons).toHaveLength(0)
  })

  it('открывает модальное окно редактирования при клике Edit (AC4)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Ищем кнопку Edit по иконке
    const editIcons = document.querySelectorAll('.anticon-edit')
    const editButton = editIcons[0]?.closest('button')
    expect(editButton).toBeTruthy()
    await userEvent.click(editButton!)

    await waitFor(() => {
      expect(screen.getByText('Edit Policy')).toBeInTheDocument()
    })
  })

  it('usageCount > 0 отображается как кликабельная ссылка (AC7)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // usageCount 5 должен быть кнопкой
    const usageButton = screen.getByRole('button', { name: '5' })
    expect(usageButton).toBeInTheDocument()
  })

  it('открывает модальное окно маршрутов при клике на usageCount (AC7)', async () => {
    mockGetRoutesByRateLimitId.mockResolvedValue([
      {
        id: 'route-1',
        path: '/api/v1/users',
        upstreamUrl: 'http://users-service:8080',
        methods: ['GET', 'POST'],
        description: null,
        status: 'published',
        createdBy: 'user-1',
        createdAt: '2026-02-01T10:00:00Z',
        updatedAt: '2026-02-01T10:00:00Z',
        rateLimitId: '1',
      },
    ])

    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    const usageButton = screen.getByRole('button', { name: '5' })
    await userEvent.click(usageButton)

    await waitFor(() => {
      expect(screen.getByText(/routes using "basic limit"/i)).toBeInTheDocument()
    })
  })

  it('удаляет политику с usageCount = 0 (AC5)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Premium Limit')).toBeInTheDocument()
    })

    // Находим кнопку Delete для Premium Limit (usageCount = 0) — вторая строка
    const deleteIcons = document.querySelectorAll('.anticon-delete')
    const deleteButton = deleteIcons[1]?.closest('button')
    expect(deleteButton).toBeTruthy()
    await userEvent.click(deleteButton!)

    // Подтверждаем удаление в Popconfirm
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await userEvent.click(confirmButton)

    // Проверяем что API был вызван с правильным ID
    await waitFor(() => {
      expect(mockDeleteRateLimit).toHaveBeenCalledWith('2')
    })
  })

  it('показывает ошибку при попытке удалить используемую политику (AC6)', async () => {
    renderWithMockAuth(<RateLimitsPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Находим кнопку Delete для Basic Limit (usageCount = 5) — первая строка
    const deleteIcons = document.querySelectorAll('.anticon-delete')
    const deleteButton = deleteIcons[0]?.closest('button')
    expect(deleteButton).toBeTruthy()
    await userEvent.click(deleteButton!)

    // Подтверждаем удаление в Popconfirm
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await userEvent.click(confirmButton)

    // Проверяем что API НЕ был вызван — клиентская проверка блокирует
    expect(mockDeleteRateLimit).not.toHaveBeenCalled()
  })
})
