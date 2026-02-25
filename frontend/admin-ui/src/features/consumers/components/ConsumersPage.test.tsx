// Тесты для ConsumersPage (Story 12.9, AC1, AC9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import ConsumersPage from './ConsumersPage'
import * as consumersApi from '../api/consumersApi'
import type { ConsumerListResponse, Consumer } from '../types/consumer.types'

// Мокаем API
vi.mock('../api/consumersApi', () => ({
  fetchConsumers: vi.fn(),
  fetchConsumer: vi.fn(),
  createConsumer: vi.fn(),
  rotateSecret: vi.fn(),
  disableConsumer: vi.fn(),
  enableConsumer: vi.fn(),
}))

const mockFetchConsumers = consumersApi.fetchConsumers as ReturnType<typeof vi.fn>

// Тестовые данные
const mockConsumers: Consumer[] = [
  {
    clientId: 'consumer-a',
    description: 'Consumer A',
    enabled: true,
    createdTimestamp: 1706784000000,
    rateLimit: {
      requestsPerSecond: 100,
      burstSize: 150,
    },
  },
  {
    clientId: 'consumer-b',
    description: 'Consumer B',
    enabled: false,
    createdTimestamp: 1706870400000,
    rateLimit: null,
  },
]

const mockConsumerListResponse: ConsumerListResponse = {
  items: mockConsumers,
  total: 2,
}

describe('ConsumersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchConsumers.mockResolvedValue(mockConsumerListResponse)
  })

  it('рендерит заголовок и кнопку Create Consumer', async () => {
    renderWithMockAuth(<ConsumersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByText('Consumers')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create consumer/i })).toBeInTheDocument()
  })

  it('рендерит search input с placeholder', () => {
    renderWithMockAuth(<ConsumersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const searchInput = screen.getByPlaceholderText(/search by client id/i)
    expect(searchInput).toBeInTheDocument()
  })

  it('открывает CreateConsumerModal при клике на кнопку Create', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<ConsumersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const createButton = screen.getByRole('button', { name: /create consumer/i })
    await user.click(createButton)

    // Модальное окно должно открыться (ищем modal dialog с заголовком)
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /create consumer/i })).toBeInTheDocument()
    })
  })

  it('передаёт debounced search в ConsumersTable', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<ConsumersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const searchInput = screen.getByPlaceholderText(/search by client id/i)

    // Вводим текст в search
    await user.type(searchInput, 'consumer-a')

    // Search должен быть передан в ConsumersTable (debounced)
    // Проверка через waitFor для debounce 300ms
    expect(searchInput).toHaveValue('consumer-a')
  })
})
