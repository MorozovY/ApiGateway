// Тесты для ConsumerRateLimitModal (Story 12.9, AC8)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsumerRateLimitModal from './ConsumerRateLimitModal'
import * as consumersApi from '../api/consumersApi'
import type { ConsumerRateLimitResponse } from '../types/consumer.types'

// Мокаем API
vi.mock('../api/consumersApi', () => ({
  fetchConsumers: vi.fn(),
  fetchConsumer: vi.fn(),
  createConsumer: vi.fn(),
  rotateSecret: vi.fn(),
  disableConsumer: vi.fn(),
  enableConsumer: vi.fn(),
  getConsumerRateLimit: vi.fn(),
  setConsumerRateLimit: vi.fn(),
  deleteConsumerRateLimit: vi.fn(),
}))

const mockGetConsumerRateLimit = consumersApi.getConsumerRateLimit as ReturnType<typeof vi.fn>
const mockSetConsumerRateLimit = consumersApi.setConsumerRateLimit as ReturnType<typeof vi.fn>
const mockDeleteConsumerRateLimit = consumersApi.deleteConsumerRateLimit as ReturnType<typeof vi.fn>

describe('ConsumerRateLimitModal', () => {
  const mockOnClose = vi.fn()
  const testConsumerId = 'test-consumer'

  beforeEach(() => {
    vi.clearAllMocks()
    // По умолчанию нет rate limit
    mockGetConsumerRateLimit.mockResolvedValue(null)
  })

  it('рендерит модальное окно с заголовком "Rate Limit для {consumerId}" (AC8)', async () => {
    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByText(`Rate Limit для ${testConsumerId}`)).toBeInTheDocument()
    })
  })

  it('показывает форму с полями Requests per Second и Burst Size (AC8)', async () => {
    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByLabelText(/requests per second/i)).toBeInTheDocument()
    })

    expect(screen.getByLabelText(/burst size/i)).toBeInTheDocument()
  })

  it('показывает кнопку "Set Rate Limit" если rate limit отсутствует (AC8)', async () => {
    mockGetConsumerRateLimit.mockResolvedValue(null)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /set rate limit/i })).toBeInTheDocument()
    })
  })

  it('показывает кнопку "Update Rate Limit" если rate limit существует (AC8)', async () => {
    const existingRateLimit: ConsumerRateLimitResponse = {
      requestsPerSecond: 100,
      burstSize: 150,
    }
    mockGetConsumerRateLimit.mockResolvedValue(existingRateLimit)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /update rate limit/i })).toBeInTheDocument()
    })
  })

  it('показывает кнопку "Remove Rate Limit" если rate limit существует (AC8)', async () => {
    const existingRateLimit: ConsumerRateLimitResponse = {
      requestsPerSecond: 100,
      burstSize: 150,
    }
    mockGetConsumerRateLimit.mockResolvedValue(existingRateLimit)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /remove rate limit/i })).toBeInTheDocument()
    })
  })

  it('инициализирует форму текущими значениями rate limit (AC8)', async () => {
    const existingRateLimit: ConsumerRateLimitResponse = {
      requestsPerSecond: 200,
      burstSize: 300,
    }
    mockGetConsumerRateLimit.mockResolvedValue(existingRateLimit)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      const rpsInput = screen.getByLabelText(/requests per second/i) as HTMLInputElement
      const burstInput = screen.getByLabelText(/burst size/i) as HTMLInputElement

      expect(rpsInput.value).toBe('200')
      expect(burstInput.value).toBe('300')
    })
  })

  it('валидирует Requests per Second — required (AC8)', async () => {
    const user = userEvent.setup()
    mockGetConsumerRateLimit.mockResolvedValue(null)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /set rate limit/i })).toBeInTheDocument()
    })

    const setButton = screen.getByRole('button', { name: /set rate limit/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(screen.getByText(/укажите лимит запросов в секунду/i)).toBeInTheDocument()
    })
  })

  it('валидирует Burst Size — required (AC8)', async () => {
    const user = userEvent.setup()
    mockGetConsumerRateLimit.mockResolvedValue(null)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /set rate limit/i })).toBeInTheDocument()
    })

    const setButton = screen.getByRole('button', { name: /set rate limit/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(screen.getByText(/укажите burst size/i)).toBeInTheDocument()
    })
  })

  it('отправляет форму при заполнении валидных значений (AC8)', async () => {
    const user = userEvent.setup()
    mockGetConsumerRateLimit.mockResolvedValue(null)
    mockSetConsumerRateLimit.mockResolvedValue({
      requestsPerSecond: 150,
      burstSize: 200,
    })

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByLabelText(/requests per second/i)).toBeInTheDocument()
    })

    const rpsInput = screen.getByLabelText(/requests per second/i)
    const burstInput = screen.getByLabelText(/burst size/i)

    // Вводим значения
    await user.type(rpsInput, '150')
    await user.type(burstInput, '200')

    const setButton = screen.getByRole('button', { name: /set rate limit/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(mockSetConsumerRateLimit).toHaveBeenCalledWith(testConsumerId, {
        requestsPerSecond: 150,
        burstSize: 200,
      })
    })
  })

  it('вызывает deleteConsumerRateLimit при клике на Remove Rate Limit (AC8)', async () => {
    const user = userEvent.setup()
    const existingRateLimit: ConsumerRateLimitResponse = {
      requestsPerSecond: 100,
      burstSize: 150,
    }
    mockGetConsumerRateLimit.mockResolvedValue(existingRateLimit)
    mockDeleteConsumerRateLimit.mockResolvedValue(undefined)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /remove rate limit/i })).toBeInTheDocument()
    })

    const removeButton = screen.getByRole('button', { name: /remove rate limit/i })
    await user.click(removeButton)

    await waitFor(() => {
      expect(mockDeleteConsumerRateLimit).toHaveBeenCalledWith(testConsumerId)
    })
  })

  it('закрывает модальное окно при клике Cancel (AC8)', async () => {
    const user = userEvent.setup()
    mockGetConsumerRateLimit.mockResolvedValue(null)

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument()
    })

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
  })

  it('закрывает модальное окно после успешного создания rate limit (AC8)', async () => {
    const user = userEvent.setup()
    mockGetConsumerRateLimit.mockResolvedValue(null)
    mockSetConsumerRateLimit.mockResolvedValue({
      requestsPerSecond: 50,
      burstSize: 75,
    })

    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await waitFor(() => {
      expect(screen.getByLabelText(/requests per second/i)).toBeInTheDocument()
    })

    const rpsInput = screen.getByLabelText(/requests per second/i)
    const burstInput = screen.getByLabelText(/burst size/i)

    await user.type(rpsInput, '50')
    await user.type(burstInput, '75')

    const setButton = screen.getByRole('button', { name: /set rate limit/i })
    await user.click(setButton)

    // Сначала ждём вызов API, затем onClose (увеличен таймаут для CI)
    await waitFor(() => {
      expect(mockSetConsumerRateLimit).toHaveBeenCalled()
    }, { timeout: 3000 })

    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    }, { timeout: 3000 })
  })
})
