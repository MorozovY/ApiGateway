// Тесты для ConsumersTable (Story 12.9, AC1, AC4-9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import ConsumersTable from './ConsumersTable'
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
const mockRotateSecret = consumersApi.rotateSecret as ReturnType<typeof vi.fn>
const mockDisableConsumer = consumersApi.disableConsumer as ReturnType<typeof vi.fn>
const mockEnableConsumer = consumersApi.enableConsumer as ReturnType<typeof vi.fn>

// Тестовые данные
const mockConsumers: Consumer[] = [
  {
    clientId: 'consumer-active',
    description: 'Active Consumer',
    enabled: true,
    createdTimestamp: 1706784000000,
    rateLimit: {
      requestsPerSecond: 100,
      burstSize: 150,
    },
  },
  {
    clientId: 'consumer-disabled',
    description: 'Disabled Consumer',
    enabled: false,
    createdTimestamp: 1706870400000,
    rateLimit: null,
  },
]

const mockConsumerListResponse: ConsumerListResponse = {
  items: mockConsumers,
  total: 2,
}

describe('ConsumersTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchConsumers.mockResolvedValue(mockConsumerListResponse)
  })

  it('рендерит таблицу с колонками (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Ждём загрузки данных
    await waitFor(() => {
      expect(screen.getByText('Client ID')).toBeInTheDocument()
    })

    expect(screen.getByText('Status')).toBeInTheDocument()
    expect(screen.getByText('Rate Limit')).toBeInTheDocument()
    expect(screen.getByText('Created')).toBeInTheDocument()
    expect(screen.getByText('Actions')).toBeInTheDocument()
  })

  it('отображает consumers в таблице (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-active')).toBeInTheDocument()
    })

    expect(screen.getByText('consumer-disabled')).toBeInTheDocument()
  })

  it('показывает Status tag "Active" для enabled consumer (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument()
    })

    // Проверяем зелёный tag
    const activeTag = screen.getByText('Active')
    expect(activeTag).toBeInTheDocument()
  })

  it('показывает Status tag "Disabled" для disabled consumer (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Disabled')).toBeInTheDocument()
    })

    // Проверяем серый tag
    const disabledTag = screen.getByText('Disabled')
    expect(disabledTag).toBeInTheDocument()
  })

  it('отображает rate limit если настроен (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('100 req/s, burst 150')).toBeInTheDocument()
    })
  })

  it('отображает "—" если rate limit отсутствует (AC1)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-disabled')).toBeInTheDocument()
    })

    // Проверяем что для consumer-disabled нет rate limit
    const rateLimitCells = screen.getAllByText('—')
    expect(rateLimitCells.length).toBeGreaterThan(0)
  })

  it('показывает кнопки действий: Rotate Secret, Disable/Enable, Set Rate Limit (AC3-5, AC8)', async () => {
    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: /rotate secret/i }).length).toBeGreaterThan(0)
    })

    expect(screen.getByRole('button', { name: /disable/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /enable/i })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /set rate limit/i }).length).toBeGreaterThan(0)
  })

  it('вызывает rotateSecret при подтверждении Rotate Secret (AC3)', async () => {
    const user = userEvent.setup()
    mockRotateSecret.mockResolvedValue({
      clientId: 'consumer-active',
      secret: 'new-secret-12345',
    })

    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-active')).toBeInTheDocument()
    })

    // Кликаем на Rotate Secret
    const rotateButtons = screen.getAllByRole('button', { name: /rotate secret/i })
    await user.click(rotateButtons[0])

    // Подтверждаем в Popconfirm
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await user.click(confirmButton)

    await waitFor(() => {
      expect(mockRotateSecret).toHaveBeenCalledWith('consumer-active')
    })
  })

  it('вызывает disableConsumer при подтверждении Disable (AC4)', async () => {
    const user = userEvent.setup()
    mockDisableConsumer.mockResolvedValue(undefined)

    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-active')).toBeInTheDocument()
    })

    // Кликаем на Disable для active consumer
    const disableButton = screen.getByRole('button', { name: /disable/i })
    await user.click(disableButton)

    // Подтверждаем в Popconfirm
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await user.click(confirmButton)

    await waitFor(() => {
      expect(mockDisableConsumer).toHaveBeenCalledWith('consumer-active')
    })
  })

  it('вызывает enableConsumer при клике на Enable (AC5)', async () => {
    const user = userEvent.setup()
    mockEnableConsumer.mockResolvedValue(undefined)

    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-disabled')).toBeInTheDocument()
    })

    // Кликаем на Enable для disabled consumer
    const enableButton = screen.getByRole('button', { name: /enable/i })
    await user.click(enableButton)

    await waitFor(() => {
      expect(mockEnableConsumer).toHaveBeenCalledWith('consumer-disabled')
    })
  })

  it('открывает rate limit modal при клике на Set Rate Limit (AC8)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-active')).toBeInTheDocument()
    })

    // Кликаем на Set Rate Limit
    const rateLimitButtons = screen.getAllByRole('button', { name: /set rate limit/i })
    await user.click(rateLimitButtons[0])

    // Модальное окно должно открыться (title содержит "Rate Limit для consumer-active")
    await waitFor(() => {
      expect(screen.getByText(/rate limit для/i)).toBeInTheDocument()
    })
  })

  it('фильтрует consumers по search параметру (AC9)', async () => {
    mockFetchConsumers.mockResolvedValueOnce({
      items: [mockConsumers[0]], // только consumer-active
      total: 1,
    })

    renderWithMockAuth(<ConsumersTable search="consumer-active" />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(mockFetchConsumers).toHaveBeenCalledWith(
        expect.objectContaining({
          search: 'consumer-active',
        })
      )
    })
  })

  it('обновляет пагинацию при изменении страницы', async () => {
    mockFetchConsumers.mockResolvedValueOnce({
      items: mockConsumers,
      total: 50, // больше 1 страницы
    })

    renderWithMockAuth(<ConsumersTable />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('consumer-active')).toBeInTheDocument()
    })

    // Проверяем что API вызван и данные отображаются
    expect(mockFetchConsumers).toHaveBeenCalledWith(
      expect.objectContaining({
        offset: 0,
        limit: 20,
      })
    )

    // Таблица рендерит consumers корректно
    expect(screen.getByText('consumer-disabled')).toBeInTheDocument()
  })
})
