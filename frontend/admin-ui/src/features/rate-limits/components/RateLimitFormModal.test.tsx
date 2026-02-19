// Тесты для RateLimitFormModal компонента (Story 5.4)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import RateLimitFormModal from './RateLimitFormModal'
import * as rateLimitsApi from '../api/rateLimitsApi'
import type { RateLimit } from '../types/rateLimit.types'

// Мокаем API
vi.mock('../api/rateLimitsApi', () => ({
  createRateLimit: vi.fn(),
  updateRateLimit: vi.fn(),
}))

const mockCreateRateLimit = rateLimitsApi.createRateLimit as ReturnType<typeof vi.fn>
const mockUpdateRateLimit = rateLimitsApi.updateRateLimit as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRateLimit: RateLimit = {
  id: '1',
  name: 'Basic Limit',
  description: 'Базовое ограничение',
  requestsPerSecond: 10,
  burstSize: 20,
  usageCount: 5,
  createdBy: 'admin-1',
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
}

describe('RateLimitFormModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateRateLimit.mockResolvedValue(mockRateLimit)
    mockUpdateRateLimit.mockResolvedValue(mockRateLimit)
  })

  describe('режим создания (AC2)', () => {
    it('отображает заголовок "New Policy" в режиме создания', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      expect(screen.getByText('New Policy')).toBeInTheDocument()
    })

    it('отображает все поля формы (AC2)', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      expect(screen.getByLabelText('Name')).toBeInTheDocument()
      expect(screen.getByLabelText('Description')).toBeInTheDocument()
      expect(screen.getByLabelText('Requests per Second')).toBeInTheDocument()
      expect(screen.getByLabelText('Burst Size')).toBeInTheDocument()
    })

    it('имеет кнопку Create', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument()
    })

    it('валидирует что name обязателен (AC2)', async () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Кликаем Create без заполнения name
      await userEvent.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Name обязателен')).toBeInTheDocument()
      })
    })

    it('валидирует что requestsPerSecond обязателен (AC2)', async () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Заполняем имя и очищаем requestsPerSecond
      await userEvent.type(screen.getByLabelText('Name'), 'Test Policy')

      const requestsInput = screen.getByLabelText('Requests per Second')
      await userEvent.clear(requestsInput)

      // Кликаем Create — валидация должна сработать
      await userEvent.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Requests per second обязателен')).toBeInTheDocument()
      })
    })

    it('валидирует что burstSize >= requestsPerSecond (AC2)', async () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Заполняем форму с burstSize < requestsPerSecond
      await userEvent.type(screen.getByLabelText('Name'), 'Test Policy')

      const requestsInput = screen.getByLabelText('Requests per Second')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '50')

      const burstInput = screen.getByLabelText('Burst Size')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '10')

      await userEvent.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Burst size должен быть не меньше requests/sec')).toBeInTheDocument()
      })
    })

    it('вызывает createRateLimit с правильными данными (AC3)', async () => {
      const onClose = vi.fn()

      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={onClose} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Заполняем форму
      await userEvent.type(screen.getByLabelText('Name'), 'New Policy')
      await userEvent.type(screen.getByLabelText('Description'), 'Test description')

      const requestsInput = screen.getByLabelText('Requests per Second')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '25')

      const burstInput = screen.getByLabelText('Burst Size')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '50')

      await userEvent.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(mockCreateRateLimit).toHaveBeenCalledWith({
          name: 'New Policy',
          description: 'Test description',
          requestsPerSecond: 25,
          burstSize: 50,
        })
      })
    })

    it('закрывает модальное окно после успешного создания (AC3)', async () => {
      const onClose = vi.fn()

      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={onClose} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Заполняем форму
      await userEvent.type(screen.getByLabelText('Name'), 'New Policy')

      const requestsInput = screen.getByLabelText('Requests per Second')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '25')

      const burstInput = screen.getByLabelText('Burst Size')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '50')

      await userEvent.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(onClose).toHaveBeenCalled()
      })
    })
  })

  describe('режим редактирования (AC4)', () => {
    it('отображает заголовок "Edit Policy" в режиме редактирования', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      expect(screen.getByText('Edit Policy')).toBeInTheDocument()
    })

    it('заполняет форму текущими значениями (AC4)', async () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      await waitFor(() => {
        expect(screen.getByDisplayValue('Basic Limit')).toBeInTheDocument()
      })

      expect(screen.getByDisplayValue('Базовое ограничение')).toBeInTheDocument()
      expect(screen.getByDisplayValue('10')).toBeInTheDocument()
      expect(screen.getByDisplayValue('20')).toBeInTheDocument()
    })

    it('имеет кнопку Save', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
    })

    it('вызывает updateRateLimit с изменёнными данными (AC4)', async () => {
      const onClose = vi.fn()

      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={onClose} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Ждём заполнения формы
      await waitFor(() => {
        expect(screen.getByDisplayValue('Basic Limit')).toBeInTheDocument()
      })

      // Меняем название
      const nameInput = screen.getByLabelText('Name')
      await userEvent.clear(nameInput)
      await userEvent.type(nameInput, 'Updated Limit')

      await userEvent.click(screen.getByRole('button', { name: /save/i }))

      await waitFor(() => {
        expect(mockUpdateRateLimit).toHaveBeenCalledWith(mockRateLimit.id, {
          name: 'Updated Limit',
        })
      })
    })
  })

  it('вызывает onClose при нажатии Cancel', async () => {
    const onClose = vi.fn()

    renderWithMockAuth(
      <RateLimitFormModal open={true} rateLimit={null} onClose={onClose} />,
      {
        authValue: {
          user: { userId: '1', username: 'admin', role: 'admin' },
          isAuthenticated: true,
        },
      }
    )

    await userEvent.click(screen.getByRole('button', { name: /cancel/i }))

    expect(onClose).toHaveBeenCalled()
  })
})
