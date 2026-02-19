// Тесты для RateLimitsTable компонента (Story 5.4)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import RateLimitsTable from './RateLimitsTable'
import * as rateLimitsApi from '../api/rateLimitsApi'
import type { RateLimitListResponse, RateLimit } from '../types/rateLimit.types'

// Мокаем API
vi.mock('../api/rateLimitsApi', () => ({
  getRateLimits: vi.fn(),
  getRoutesByRateLimitId: vi.fn(),
}))

const mockGetRateLimits = rateLimitsApi.getRateLimits as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRateLimits: RateLimit[] = [
  {
    id: '1',
    name: 'Basic Limit',
    description: 'Базовое ограничение для API',
    requestsPerSecond: 10,
    burstSize: 20,
    usageCount: 5,
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
    usageCount: 0,
    createdBy: 'admin-1',
    createdAt: '2026-02-10T10:00:00Z',
    updatedAt: '2026-02-10T10:00:00Z',
  },
  {
    id: '3',
    name: 'Enterprise Limit',
    description: 'Для корпоративных клиентов',
    requestsPerSecond: 1000,
    burstSize: 2000,
    usageCount: 12,
    createdBy: 'admin-2',
    createdAt: '2026-02-15T10:00:00Z',
    updatedAt: '2026-02-15T10:00:00Z',
  },
]

const mockRateLimitListResponse: RateLimitListResponse = {
  items: mockRateLimits,
  total: 3,
  offset: 0,
  limit: 10,
}

describe('RateLimitsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetRateLimits.mockResolvedValue(mockRateLimitListResponse)
  })

  it('рендерит таблицу с политиками (AC1)', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Ждём загрузку данных
    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Проверяем что все политики отображаются
    expect(screen.getByText('Premium Limit')).toBeInTheDocument()
    expect(screen.getByText('Enterprise Limit')).toBeInTheDocument()
  })

  it('отображает все колонки (AC1)', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Проверяем заголовки колонок
    expect(screen.getByText('Name')).toBeInTheDocument()
    expect(screen.getByText('Description')).toBeInTheDocument()
    expect(screen.getByText('Requests/sec')).toBeInTheDocument()
    expect(screen.getByText('Burst Size')).toBeInTheDocument()
    expect(screen.getByText('Used By')).toBeInTheDocument()
    expect(screen.getByText('Actions')).toBeInTheDocument()
  })

  it('отображает значения requestsPerSecond и burstSize', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('10')).toBeInTheDocument() // requestsPerSecond для Basic
    })

    expect(screen.getByText('20')).toBeInTheDocument() // burstSize для Basic
    expect(screen.getByText('100')).toBeInTheDocument() // requestsPerSecond для Premium
    expect(screen.getByText('200')).toBeInTheDocument() // burstSize для Premium
  })

  it('отображает description или "—" если null', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Базовое ограничение для API')).toBeInTheDocument()
    })

    // Premium Limit имеет null description — отображается "—"
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('отображает usageCount для каждой политики', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument() // Basic Limit usageCount
    })

    expect(screen.getByText('12')).toBeInTheDocument() // Enterprise Limit usageCount
  })

  it('делает usageCount > 0 кликабельным для AC7', async () => {
    const onViewRoutes = vi.fn()

    renderWithMockAuth(<RateLimitsTable onViewRoutes={onViewRoutes} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Находим кнопку с числом 5 (usageCount для Basic Limit)
    const usageButton = screen.getByRole('button', { name: '5' })
    await userEvent.click(usageButton)

    expect(onViewRoutes).toHaveBeenCalledWith(mockRateLimits[0])
  })

  it('показывает кнопки Edit и Delete когда onEdit и onDelete переданы (AC8)', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()

    renderWithMockAuth(
      <RateLimitsTable onEdit={onEdit} onDelete={onDelete} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // У каждой строки есть Edit и Delete кнопки — ищем по иконкам Ant Design
    const editIcons = document.querySelectorAll('.anticon-edit')
    const deleteIcons = document.querySelectorAll('.anticon-delete')

    expect(editIcons).toHaveLength(3)
    expect(deleteIcons).toHaveLength(3)
  })

  it('скрывает кнопки Edit и Delete когда onEdit и onDelete не переданы (AC8)', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'developer', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Кнопки Edit и Delete не отображаются — ищем по иконкам
    const editIcons = document.querySelectorAll('.anticon-edit')
    const deleteIcons = document.querySelectorAll('.anticon-delete')

    expect(editIcons).toHaveLength(0)
    expect(deleteIcons).toHaveLength(0)
  })

  it('вызывает onEdit при клике на Edit', async () => {
    const onEdit = vi.fn()

    renderWithMockAuth(<RateLimitsTable onEdit={onEdit} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Basic Limit')).toBeInTheDocument()
    })

    // Ищем кнопку Edit по иконке и кликаем на родительскую кнопку
    const editIcons = document.querySelectorAll('.anticon-edit')
    const editButton = editIcons[0]?.closest('button')
    expect(editButton).toBeTruthy()
    await userEvent.click(editButton!)

    expect(onEdit).toHaveBeenCalledWith(mockRateLimits[0])
  })

  it('показывает пагинацию с общим количеством', async () => {
    renderWithMockAuth(<RateLimitsTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText(/всего 3 политики/i)).toBeInTheDocument()
    })
  })
})
