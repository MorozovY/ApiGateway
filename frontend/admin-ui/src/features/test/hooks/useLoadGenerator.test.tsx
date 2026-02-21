// Тесты для useLoadGenerator hook (Story 8.9, AC3, AC4; обновлено Story 9.2)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useLoadGenerator } from './useLoadGenerator'

// Мокаем fetch
const mockFetch = vi.fn()
global.fetch = mockFetch

describe('useLoadGenerator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Стандартный успешный HTTP ответ
    mockFetch.mockResolvedValue({ ok: true, status: 200, statusText: 'OK' })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('возвращает начальное состояние idle', () => {
    const { result } = renderHook(() => useLoadGenerator())

    expect(result.current.state.status).toBe('idle')
    expect(result.current.state.sentCount).toBe(0)
    expect(result.current.state.successCount).toBe(0)
    expect(result.current.state.errorCount).toBe(0)
    expect(result.current.summary).toBeNull()
  })

  it('переходит в состояние running после start', () => {
    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test', // L3: исправлено — теперь консистентно с другими тестами
        requestsPerSecond: 10,
        durationSeconds: null,
      })
    })

    expect(result.current.state.status).toBe('running')
    expect(result.current.state.startTime).not.toBeNull()

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  it('увеличивает счётчики при успешных запросах', async () => {
    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100, // Высокий RPS для быстрого теста
        durationSeconds: null,
      })
    })

    // Ждём пока сработает хотя бы один запрос
    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    expect(result.current.state.successCount).toBeGreaterThan(0)
    // Story 9.2: Используем относительный путь /api${routePath} через nginx
    expect(mockFetch).toHaveBeenCalledWith('/api/test-route', {
      method: 'GET',
    })

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  it('увеличивает счётчик ошибок при network errors', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    expect(result.current.state.errorCount).toBeGreaterThan(0)
    expect(result.current.state.lastError).toBe('Network error')

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  // Story 9.2, AC5: HTTP error handling (4xx/5xx)
  it('увеличивает счётчик ошибок при HTTP 4xx ответах', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404, statusText: 'Not Found' })

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/not-found',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    expect(result.current.state.errorCount).toBeGreaterThan(0)
    expect(result.current.state.successCount).toBe(0)
    expect(result.current.state.lastError).toBe('HTTP 404: Not Found')

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  it('увеличивает счётчик ошибок при HTTP 5xx ответах', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503, statusText: 'Service Unavailable' })

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/upstream-down',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    expect(result.current.state.errorCount).toBeGreaterThan(0)
    expect(result.current.state.successCount).toBe(0)
    expect(result.current.state.lastError).toBe('HTTP 503: Service Unavailable')

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  it('переходит в состояние stopped после stop', async () => {
    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    act(() => {
      result.current.stop()
    })

    expect(result.current.state.status).toBe('stopped')
    expect(result.current.summary).not.toBeNull()
  })

  it('автоматически останавливается по durationSeconds', async () => {
    vi.useFakeTimers()

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 10,
        durationSeconds: 1, // 1 секунда
      })
    })

    expect(result.current.state.status).toBe('running')

    // Продвигаем время на 1 секунду (+ запас)
    act(() => {
      vi.advanceTimersByTime(1100)
    })

    expect(result.current.state.status).toBe('stopped')
  })

  it('reset возвращает в начальное состояние', async () => {
    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    act(() => {
      result.current.stop()
    })

    act(() => {
      result.current.reset()
    })

    expect(result.current.state.status).toBe('idle')
    expect(result.current.state.sentCount).toBe(0)
    expect(result.current.state.successCount).toBe(0)
    expect(result.current.state.errorCount).toBe(0)
    expect(result.current.summary).toBeNull()
  })

  it('summary содержит корректные данные после остановки', async () => {
    mockFetch.mockResolvedValue({ ok: true, status: 200, statusText: 'OK' })

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    act(() => {
      result.current.stop()
    })

    const summary = result.current.summary
    expect(summary).not.toBeNull()
    expect(summary!.totalRequests).toBe(result.current.state.sentCount)
    expect(summary!.successCount).toBe(result.current.state.successCount)
    expect(summary!.errorCount).toBe(result.current.state.errorCount)
    expect(summary!.durationMs).toBeGreaterThan(0)
    expect(summary!.successRate).toBeGreaterThanOrEqual(0)
    expect(summary!.successRate).toBeLessThanOrEqual(100)
  })

  it('очищает interval при unmount компонента (cleanup)', async () => {
    const { result, unmount } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/test-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    const fetchCountBeforeUnmount = mockFetch.mock.calls.length

    // Unmount должен остановить interval
    unmount()

    // Небольшая задержка чтобы убедиться что новые запросы не отправляются
    await new Promise((resolve) => setTimeout(resolve, 100))

    // L1: проверяем что после unmount не было новых fetch вызовов
    // Допускаем +1 из-за возможного in-flight запроса в момент unmount
    const fetchCountAfterUnmount = mockFetch.mock.calls.length
    expect(fetchCountAfterUnmount - fetchCountBeforeUnmount).toBeLessThanOrEqual(1)
  })

  // M4: тест на повторный вызов start() — должен очистить предыдущий interval
  it('очищает предыдущий interval при повторном start()', async () => {
    const { result } = renderHook(() => useLoadGenerator())

    // Первый запуск
    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/first-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    // Сбрасываем мок перед вторым запуском
    mockFetch.mockClear()

    // Второй запуск — должен переключиться на новый маршрут
    act(() => {
      result.current.start({
        routeId: 'route-2',
        routePath: '/second-route',
        requestsPerSecond: 100,
        durationSeconds: null,
      })
    })

    await waitFor(
      () => {
        expect(result.current.state.sentCount).toBeGreaterThan(0)
      },
      { timeout: 500 }
    )

    // Проверяем что все новые запросы идут на второй маршрут
    const recentCalls = mockFetch.mock.calls.slice(-3) // последние 3 вызова
    recentCalls.forEach((call) => {
      expect(call[0]).toBe('/api/second-route')
    })

    // Очистка
    act(() => {
      result.current.stop()
    })
  })

  // M1/M5: проверяем что concurrent requests предотвращаются
  it('пропускает запрос если предыдущий ещё выполняется (no concurrent requests)', async () => {
    // Мокаем медленный запрос (200ms)
    mockFetch.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve({ ok: true, status: 200, statusText: 'OK' }), 200))
    )

    const { result } = renderHook(() => useLoadGenerator())

    act(() => {
      result.current.start({
        routeId: 'route-1',
        routePath: '/slow-route',
        requestsPerSecond: 100, // 10ms interval, но запрос занимает 200ms
        durationSeconds: null,
      })
    })

    // Ждём 300ms — за это время должен был бы пройти 30 интервалов
    // Но из-за защиты от concurrent requests, должен быть только 1-2 запроса
    await new Promise((resolve) => setTimeout(resolve, 300))

    act(() => {
      result.current.stop()
    })

    // Проверяем что было меньше 5 запросов (не 30, как было бы без защиты)
    expect(mockFetch.mock.calls.length).toBeLessThan(5)
  })
})
