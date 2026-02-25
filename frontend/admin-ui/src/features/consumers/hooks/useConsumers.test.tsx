// Тесты для useConsumers hooks (Story 12.9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import * as consumersApi from '../api/consumersApi'
import {
  useConsumers,
  useConsumer,
  useCreateConsumer,
  useRotateSecret,
  useDisableConsumer,
  useEnableConsumer,
  useConsumerRateLimit,
  useSetConsumerRateLimit,
  useDeleteConsumerRateLimit,
} from './useConsumers'
import type { ConsumerListResponse, Consumer, ConsumerRateLimitResponse } from '../types/consumer.types'

// Мокаем antd App.useApp() (необходимо для message API в hooks)
const mockMessageSuccess = vi.fn()
const mockMessageError = vi.fn()
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd')
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: {
          success: (text: string) => mockMessageSuccess(text),
          error: (text: string) => mockMessageError(text),
          warning: vi.fn(),
          info: vi.fn(),
          loading: vi.fn(() => vi.fn()),
        },
        modal: { confirm: vi.fn() },
        notification: { success: vi.fn(), error: vi.fn() },
      }),
    },
  }
})

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

const mockFetchConsumers = consumersApi.fetchConsumers as ReturnType<typeof vi.fn>
const mockFetchConsumer = consumersApi.fetchConsumer as ReturnType<typeof vi.fn>
const mockCreateConsumer = consumersApi.createConsumer as ReturnType<typeof vi.fn>
const mockRotateSecret = consumersApi.rotateSecret as ReturnType<typeof vi.fn>
const mockDisableConsumer = consumersApi.disableConsumer as ReturnType<typeof vi.fn>
const mockEnableConsumer = consumersApi.enableConsumer as ReturnType<typeof vi.fn>
const mockGetConsumerRateLimit = consumersApi.getConsumerRateLimit as ReturnType<typeof vi.fn>
const mockSetConsumerRateLimit = consumersApi.setConsumerRateLimit as ReturnType<typeof vi.fn>
const mockDeleteConsumerRateLimit = consumersApi.deleteConsumerRateLimit as ReturnType<typeof vi.fn>

// Wrapper для React Query
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

// Тестовые данные
const mockConsumer: Consumer = {
  clientId: 'test-consumer',
  description: 'Test Consumer',
  enabled: true,
  createdTimestamp: 1706784000000,
  rateLimit: null,
}

const mockConsumerListResponse: ConsumerListResponse = {
  items: [mockConsumer],
  total: 1,
}

describe('useConsumers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('загружает список consumers', async () => {
    mockFetchConsumers.mockResolvedValue(mockConsumerListResponse)

    const { result } = renderHook(() => useConsumers(), { wrapper: createWrapper() })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockConsumerListResponse)
    expect(mockFetchConsumers).toHaveBeenCalledWith({})
  })

  it('передаёт параметры пагинации и поиска', async () => {
    mockFetchConsumers.mockResolvedValue(mockConsumerListResponse)

    const params = { offset: 10, limit: 20, search: 'test' }
    const { result } = renderHook(() => useConsumers(params), { wrapper: createWrapper() })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockFetchConsumers).toHaveBeenCalledWith(params)
  })
})

describe('useConsumer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('загружает одного consumer по clientId', async () => {
    mockFetchConsumer.mockResolvedValue(mockConsumer)

    const { result } = renderHook(() => useConsumer('test-consumer'), { wrapper: createWrapper() })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockConsumer)
    expect(mockFetchConsumer).toHaveBeenCalledWith('test-consumer')
  })
})

describe('useCreateConsumer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('создаёт нового consumer', async () => {
    mockCreateConsumer.mockResolvedValue({
      clientId: 'new-consumer',
      secret: 'new-secret',
      message: 'Secret created',
    })

    const { result } = renderHook(() => useCreateConsumer(), { wrapper: createWrapper() })

    result.current.mutate({ clientId: 'new-consumer', description: 'New Consumer' })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockCreateConsumer).toHaveBeenCalledWith({
      clientId: 'new-consumer',
      description: 'New Consumer',
    })
  })
})

describe('useRotateSecret', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('ротирует secret consumer', async () => {
    mockRotateSecret.mockResolvedValue({
      clientId: 'test-consumer',
      secret: 'new-secret-12345',
    })

    const { result } = renderHook(() => useRotateSecret(), { wrapper: createWrapper() })

    result.current.mutate('test-consumer')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockRotateSecret).toHaveBeenCalledWith('test-consumer')
  })
})

describe('useDisableConsumer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('деактивирует consumer', async () => {
    mockDisableConsumer.mockResolvedValue(undefined)

    const { result } = renderHook(() => useDisableConsumer(), { wrapper: createWrapper() })

    result.current.mutate('test-consumer')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockDisableConsumer).toHaveBeenCalledWith('test-consumer')
  })
})

describe('useEnableConsumer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('активирует consumer', async () => {
    mockEnableConsumer.mockResolvedValue(undefined)

    const { result } = renderHook(() => useEnableConsumer(), { wrapper: createWrapper() })

    result.current.mutate('test-consumer')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockEnableConsumer).toHaveBeenCalledWith('test-consumer')
  })
})

describe('useConsumerRateLimit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('загружает rate limit consumer', async () => {
    const mockRateLimit: ConsumerRateLimitResponse = {
      requestsPerSecond: 100,
      burstSize: 150,
    }
    mockGetConsumerRateLimit.mockResolvedValue(mockRateLimit)

    const { result } = renderHook(() => useConsumerRateLimit('test-consumer'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockRateLimit)
    expect(mockGetConsumerRateLimit).toHaveBeenCalledWith('test-consumer')
  })

  it('возвращает null если rate limit отсутствует', async () => {
    mockGetConsumerRateLimit.mockResolvedValue(null)

    const { result } = renderHook(() => useConsumerRateLimit('test-consumer'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toBeNull()
  })
})

describe('useSetConsumerRateLimit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('устанавливает rate limit consumer', async () => {
    const rateLimitData = {
      requestsPerSecond: 200,
      burstSize: 300,
    }
    mockSetConsumerRateLimit.mockResolvedValue(rateLimitData)

    const { result } = renderHook(() => useSetConsumerRateLimit(), { wrapper: createWrapper() })

    result.current.mutate({ consumerId: 'test-consumer', data: rateLimitData })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockSetConsumerRateLimit).toHaveBeenCalledWith('test-consumer', rateLimitData)
  })
})

describe('useDeleteConsumerRateLimit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('удаляет rate limit consumer', async () => {
    mockDeleteConsumerRateLimit.mockResolvedValue(undefined)

    const { result } = renderHook(() => useDeleteConsumerRateLimit(), { wrapper: createWrapper() })

    result.current.mutate('test-consumer')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockDeleteConsumerRateLimit).toHaveBeenCalledWith('test-consumer')
  })
})
