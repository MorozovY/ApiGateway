// Unit тесты для UpstreamsTable (Story 7.6, AC3, AC4, AC9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { UpstreamsTable } from './UpstreamsTable'
import type { UpstreamsResponse } from '../types/audit.types'

// Мок для navigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Мок для API
const mockFetchUpstreams = vi.fn()
vi.mock('../api/upstreamsApi', () => ({
  fetchUpstreams: () => mockFetchUpstreams(),
}))

// Тестовые данные
const mockUpstreams: UpstreamsResponse = {
  upstreams: [
    { host: 'user-service:8080', routeCount: 12 },
    { host: 'order-service:8080', routeCount: 5 },
    { host: 'payment-service:8080', routeCount: 3 },
  ],
}

// Auth value
const adminAuth = {
  user: { userId: '1', username: 'admin', role: 'admin' as const },
  isAuthenticated: true,
}

describe('UpstreamsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает таблицу с upstream сервисами (AC3)', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    expect(screen.getByText('order-service:8080')).toBeInTheDocument()
    expect(screen.getByText('payment-service:8080')).toBeInTheDocument()

    // Проверяем отображение количества маршрутов
    expect(screen.getByText('12 маршрутов')).toBeInTheDocument()
    expect(screen.getByText('5 маршрутов')).toBeInTheDocument()
    expect(screen.getByText('3 маршрута')).toBeInTheDocument()
  })

  it('фильтрует по host name (AC3)', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    const user = userEvent.setup()
    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    // Вводим поисковый запрос
    const searchInput = screen.getByPlaceholderText('Поиск по host...')
    await user.type(searchInput, 'order')

    // Должен остаться только order-service
    await waitFor(() => {
      expect(screen.getByText('order-service:8080')).toBeInTheDocument()
      expect(screen.queryByText('user-service:8080')).not.toBeInTheDocument()
      expect(screen.queryByText('payment-service:8080')).not.toBeInTheDocument()
    })
  })

  it('навигация на /routes?upstream={host} при клике (AC4)', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    const user = userEvent.setup()
    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    // Находим кнопки "Маршруты"
    const viewButtons = screen.getAllByText('Маршруты')
    await user.click(viewButtons[0]!)

    // Проверяем навигацию
    expect(mockNavigate).toHaveBeenCalledWith('/routes?upstream=user-service%3A8080')
  })

  it('вызывает onUpstreamClick callback при клике', async () => {
    mockFetchUpstreams.mockResolvedValue(mockUpstreams)

    const handleClick = vi.fn()
    const user = userEvent.setup()
    renderWithMockAuth(<UpstreamsTable onUpstreamClick={handleClick} />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('user-service:8080')).toBeInTheDocument()
    })

    const viewButtons = screen.getAllByText('Маршруты')
    await user.click(viewButtons[0]!)

    // onUpstreamClick должен быть вызван вместо navigate
    expect(handleClick).toHaveBeenCalledWith('user-service:8080')
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('показывает empty state когда нет upstream сервисов (AC9)', async () => {
    mockFetchUpstreams.mockResolvedValue({ upstreams: [] })

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Нет данных о внешних сервисах')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Создайте маршруты с upstream URL для отображения интеграций')
    ).toBeInTheDocument()
  })

  it('показывает spinner во время загрузки', async () => {
    mockFetchUpstreams.mockReturnValue(new Promise(() => {}))

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    expect(document.querySelector('.ant-spin')).toBeInTheDocument()
  })

  it('показывает ошибку при неудачной загрузке', async () => {
    mockFetchUpstreams.mockRejectedValue(new Error('Network error'))

    renderWithMockAuth(<UpstreamsTable />, { authValue: adminAuth })

    await waitFor(() => {
      expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
    })
  })
})
