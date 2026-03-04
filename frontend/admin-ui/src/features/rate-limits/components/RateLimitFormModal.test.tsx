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
    it('отображает заголовок "Новый лимит" в режиме создания', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Русское название согласно локализации Story 16.1
      expect(screen.getByText('Новый лимит')).toBeInTheDocument()
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

      // Русские названия согласно локализации Story 16.1
      expect(screen.getByLabelText('Название')).toBeInTheDocument()
      expect(screen.getByLabelText('Описание')).toBeInTheDocument()
      expect(screen.getByLabelText('Запросов/сек')).toBeInTheDocument()
      expect(screen.getByLabelText('Размер всплеска')).toBeInTheDocument()
    })

    it('имеет кнопку Создать', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Русское название согласно локализации Story 16.1
      expect(screen.getByRole('button', { name: /создать/i })).toBeInTheDocument()
    })

    it('валидирует что название обязательно (AC2)', async () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={null} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Кликаем Создать без заполнения названия
      await userEvent.click(screen.getByRole('button', { name: /создать/i }))

      await waitFor(() => {
        expect(screen.getByText('Название обязательно')).toBeInTheDocument()
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

      // Заполняем название и очищаем requestsPerSecond
      await userEvent.type(screen.getByLabelText('Название'), 'Test Policy')

      const requestsInput = screen.getByLabelText('Запросов/сек')
      await userEvent.clear(requestsInput)

      // Кликаем Создать — валидация должна сработать
      await userEvent.click(screen.getByRole('button', { name: /создать/i }))

      await waitFor(() => {
        expect(screen.getByText('Количество запросов в секунду обязательно')).toBeInTheDocument()
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
      await userEvent.type(screen.getByLabelText('Название'), 'Test Policy')

      const requestsInput = screen.getByLabelText('Запросов/сек')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '50')

      const burstInput = screen.getByLabelText('Размер всплеска')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '10')

      await userEvent.click(screen.getByRole('button', { name: /создать/i }))

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

      // Заполняем форму (русские названия согласно локализации Story 16.1)
      await userEvent.type(screen.getByLabelText('Название'), 'New Policy')
      await userEvent.type(screen.getByLabelText('Описание'), 'Test description')

      const requestsInput = screen.getByLabelText('Запросов/сек')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '25')

      const burstInput = screen.getByLabelText('Размер всплеска')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '50')

      await userEvent.click(screen.getByRole('button', { name: /создать/i }))

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

      // Заполняем форму (русские названия согласно локализации Story 16.1)
      await userEvent.type(screen.getByLabelText('Название'), 'New Policy')

      const requestsInput = screen.getByLabelText('Запросов/сек')
      await userEvent.clear(requestsInput)
      await userEvent.type(requestsInput, '25')

      const burstInput = screen.getByLabelText('Размер всплеска')
      await userEvent.clear(burstInput)
      await userEvent.type(burstInput, '50')

      await userEvent.click(screen.getByRole('button', { name: /создать/i }))

      await waitFor(() => {
        expect(onClose).toHaveBeenCalled()
      })
    })
  })

  describe('режим редактирования (AC4)', () => {
    it('отображает заголовок "Редактировать лимит" в режиме редактирования', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Русское название согласно локализации Story 16.1
      expect(screen.getByText('Редактировать лимит')).toBeInTheDocument()
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

    it('имеет кнопку Сохранить', () => {
      renderWithMockAuth(
        <RateLimitFormModal open={true} rateLimit={mockRateLimit} onClose={() => {}} />,
        {
          authValue: {
            user: { userId: '1', username: 'admin', role: 'admin' },
            isAuthenticated: true,
          },
        }
      )

      // Русское название согласно локализации Story 16.1
      expect(screen.getByRole('button', { name: /сохранить/i })).toBeInTheDocument()
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

      // Меняем название (русское название согласно локализации Story 16.1)
      const nameInput = screen.getByLabelText('Название')
      await userEvent.clear(nameInput)
      await userEvent.type(nameInput, 'Updated Limit')

      await userEvent.click(screen.getByRole('button', { name: /сохранить/i }))

      await waitFor(() => {
        expect(mockUpdateRateLimit).toHaveBeenCalledWith(mockRateLimit.id, {
          name: 'Updated Limit',
        })
      })
    })
  })

  it('вызывает onClose при нажатии Отмена', async () => {
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

    // Русское название согласно локализации Story 16.1
    await userEvent.click(screen.getByRole('button', { name: /отмена/i }))

    expect(onClose).toHaveBeenCalled()
  })
})
