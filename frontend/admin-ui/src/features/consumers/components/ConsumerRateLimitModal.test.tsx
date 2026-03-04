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

  it('рендерит модальное окно с заголовком "Лимит для {consumerId}" (AC8)', async () => {
    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByText(`Лимит для ${testConsumerId}`)).toBeInTheDocument()
    })
  })

  it('показывает форму с полями Запросов в секунду и Размер всплеска (AC8)', async () => {
    renderWithMockAuth(
      <ConsumerRateLimitModal open={true} consumerId={testConsumerId} onClose={mockOnClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    // Русские названия согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByLabelText(/запросов в секунду/i)).toBeInTheDocument()
    })

    expect(screen.getByLabelText(/размер всплеска/i)).toBeInTheDocument()
  })

  it('показывает кнопку "Установить" если rate limit отсутствует (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /установить/i })).toBeInTheDocument()
    })
  })

  it('показывает кнопку "Обновить" если rate limit существует (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /обновить/i })).toBeInTheDocument()
    })
  })

  it('показывает кнопку "Удалить лимит" если rate limit существует (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /удалить лимит/i })).toBeInTheDocument()
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

    // Русские названия согласно локализации Story 16.1
    await waitFor(() => {
      const rpsInput = screen.getByLabelText(/запросов в секунду/i) as HTMLInputElement
      const burstInput = screen.getByLabelText(/размер всплеска/i) as HTMLInputElement

      expect(rpsInput.value).toBe('200')
      expect(burstInput.value).toBe('300')
    })
  })

  it('валидирует Запросов в секунду — required (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /установить/i })).toBeInTheDocument()
    })

    const setButton = screen.getByRole('button', { name: /установить/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(screen.getByText(/укажите лимит запросов в секунду/i)).toBeInTheDocument()
    })
  })

  it('валидирует Размер всплеска — required (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /установить/i })).toBeInTheDocument()
    })

    const setButton = screen.getByRole('button', { name: /установить/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(screen.getByText(/укажите размер всплеска/i)).toBeInTheDocument()
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

    // Русские названия согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByLabelText(/запросов в секунду/i)).toBeInTheDocument()
    })

    const rpsInput = screen.getByLabelText(/запросов в секунду/i)
    const burstInput = screen.getByLabelText(/размер всплеска/i)

    // Вводим значения
    await user.type(rpsInput, '150')
    await user.type(burstInput, '200')

    const setButton = screen.getByRole('button', { name: /установить/i })
    await user.click(setButton)

    await waitFor(() => {
      expect(mockSetConsumerRateLimit).toHaveBeenCalledWith(testConsumerId, {
        requestsPerSecond: 150,
        burstSize: 200,
      })
    })
  })

  it('вызывает deleteConsumerRateLimit при клике на Удалить лимит (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /удалить лимит/i })).toBeInTheDocument()
    })

    const removeButton = screen.getByRole('button', { name: /удалить лимит/i })
    await user.click(removeButton)

    await waitFor(() => {
      expect(mockDeleteConsumerRateLimit).toHaveBeenCalledWith(testConsumerId)
    })
  })

  it('закрывает модальное окно при клике Отмена (AC8)', async () => {
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

    // Русское название согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /отмена/i })).toBeInTheDocument()
    })

    const cancelButton = screen.getByRole('button', { name: /отмена/i })
    await user.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
  })

  it('закрывает модальное окно после успешного создания rate limit (AC8)', async () => {
    // Увеличен timeout для CI — тест содержит множественные waitFor
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

    // Русские названия согласно локализации Story 16.1
    await waitFor(() => {
      expect(screen.getByLabelText(/запросов в секунду/i)).toBeInTheDocument()
    }, { timeout: 5000 })

    const rpsInput = screen.getByLabelText(/запросов в секунду/i)
    const burstInput = screen.getByLabelText(/размер всплеска/i)

    // Вводим значения
    await user.type(rpsInput, '50')
    await user.type(burstInput, '75')

    const setButton = screen.getByRole('button', { name: /установить/i })
    await user.click(setButton)

    // Увеличен таймаут для CI — форма Ant Design может быть медленной
    await waitFor(() => {
      expect(mockSetConsumerRateLimit).toHaveBeenCalled()
    }, { timeout: 10000 })

    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    }, { timeout: 5000 })
  }, 20000)
})
