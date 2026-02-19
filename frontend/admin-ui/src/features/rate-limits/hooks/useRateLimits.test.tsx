// Тесты для useRateLimits hook (Story 5.9 — refetch при mount)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRateLimits } from './useRateLimits'
import * as rateLimitsApi from '../api/rateLimitsApi'
import type { RateLimitListResponse } from '../types/rateLimit.types'

// Мокаем API
vi.mock('../api/rateLimitsApi', () => ({
  getRateLimits: vi.fn(),
}))

const mockGetRateLimits = rateLimitsApi.getRateLimits as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRateLimitListResponse: RateLimitListResponse = {
  items: [
    {
      id: '1',
      name: 'Test Policy',
      description: 'Тестовая политика',
      requestsPerSecond: 10,
      burstSize: 20,
      usageCount: 0,
      createdBy: 'admin',
      createdAt: '2026-02-01T10:00:00Z',
      updatedAt: '2026-02-01T10:00:00Z',
    },
  ],
  total: 1,
  offset: 0,
  limit: 10,
}

// Фабрика для создания тестового QueryClient
// Используем минимальные настройки — useRateLimits переопределяет staleTime и refetchOnMount
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
}

// Wrapper для renderHook
function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }
}

describe('useRateLimits', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetRateLimits.mockResolvedValue(mockRateLimitListResponse)
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('загружает данные при первом рендере', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useRateLimits(), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockRateLimitListResponse)
    expect(mockGetRateLimits).toHaveBeenCalledTimes(1)
  })

  it('использует staleTime: 0 для немедленного устаревания данных', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    // Первый рендер
    const { result, unmount } = renderHook(() => useRateLimits(), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    // Записываем количество вызовов после первого рендера
    const callsAfterFirstRender = mockGetRateLimits.mock.calls.length
    expect(callsAfterFirstRender).toBe(1)

    // Размонтируем
    unmount()

    // Повторный рендер того же хука (симулирует re-mount компонента)
    const { result: result2 } = renderHook(() => useRateLimits(), { wrapper })

    await waitFor(() => {
      expect(result2.current.isSuccess).toBe(true)
    })

    // Story 5.9: staleTime: 0 + refetchOnMount: 'always' гарантирует refetch при re-mount
    // Ожидаем что API был вызван снова
    expect(mockGetRateLimits).toHaveBeenCalledTimes(2)
  })

  it('передаёт параметры пагинации в API', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useRateLimits({ offset: 10, limit: 20 }), {
      wrapper,
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetRateLimits).toHaveBeenCalledWith({ offset: 10, limit: 20 })
  })

  it('refetchOnMount: always перезагружает данные даже при наличии в кэше', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    // Предзаполняем кэш "свежими" данными (имитация предыдущего запроса)
    const cachedData = { ...mockRateLimitListResponse, items: [] }
    queryClient.setQueryData(['rateLimits', undefined], cachedData)

    // Проверяем что данные в кэше
    expect(queryClient.getQueryData(['rateLimits', undefined])).toEqual(cachedData)

    // Рендерим хук — должен сделать refetch несмотря на наличие данных в кэше
    const { result } = renderHook(() => useRateLimits(), { wrapper })

    // Сначала показываются данные из кэша (пустой массив)
    expect(result.current.data).toEqual(cachedData)

    await waitFor(() => {
      // После refetch данные обновляются на актуальные
      expect(result.current.data).toEqual(mockRateLimitListResponse)
    })

    // Хук использует refetchOnMount: 'always', поэтому вызвал API
    // несмотря на наличие данных в кэше
    expect(mockGetRateLimits).toHaveBeenCalledTimes(1)
  })

  it('показывает loading state во время загрузки', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    // Создаём промис который не резолвится сразу
    let resolvePromise: (value: RateLimitListResponse) => void
    mockGetRateLimits.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    const { result } = renderHook(() => useRateLimits(), { wrapper })

    // Проверяем loading state
    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeUndefined()

    // Резолвим промис
    resolvePromise!(mockRateLimitListResponse)

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toEqual(mockRateLimitListResponse)
  })

  it('обрабатывает ошибки API', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const apiError = new Error('Network error')
    mockGetRateLimits.mockRejectedValue(apiError)

    const { result } = renderHook(() => useRateLimits(), { wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toEqual(apiError)
  })
})
