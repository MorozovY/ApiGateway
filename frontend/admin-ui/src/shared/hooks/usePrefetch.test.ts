// Тесты для usePrefetch hook
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { usePrefetch } from './usePrefetch'

describe('usePrefetch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('возвращает функцию prefetch', () => {
    const { result } = renderHook(() => usePrefetch())

    expect(result.current.prefetch).toBeDefined()
    expect(typeof result.current.prefetch).toBe('function')
  })

  it('prefetch вызывает импорт после debounce 100ms', async () => {
    const { result } = renderHook(() => usePrefetch())

    // Вызываем prefetch
    act(() => {
      result.current.prefetch('/dashboard')
    })

    // До истечения debounce импорт не вызван (проверяем косвенно через таймер)
    expect(vi.getTimerCount()).toBe(1)

    // Ждём debounce
    await act(async () => {
      vi.advanceTimersByTime(100)
    })

    // Таймер должен был сработать
    expect(vi.getTimerCount()).toBe(0)
  })

  it('debounce отменяет предыдущий вызов при быстром наведении', async () => {
    const { result } = renderHook(() => usePrefetch())

    // Быстро вызываем prefetch несколько раз
    act(() => {
      result.current.prefetch('/dashboard')
    })
    await act(async () => {
      vi.advanceTimersByTime(50)
    })
    act(() => {
      result.current.prefetch('/routes')
    })
    await act(async () => {
      vi.advanceTimersByTime(50)
    })
    act(() => {
      result.current.prefetch('/users')
    })

    // Только один активный таймер (последний вызов)
    expect(vi.getTimerCount()).toBe(1)

    // Ждём debounce
    await act(async () => {
      vi.advanceTimersByTime(100)
    })

    // Все таймеры завершены
    expect(vi.getTimerCount()).toBe(0)
  })

  it('не создаёт дополнительный таймер для неизвестных путей', async () => {
    const { result } = renderHook(() => usePrefetch())

    act(() => {
      result.current.prefetch('/unknown-path')
    })

    // Таймер создан, но импорт не найден — это нормально
    expect(vi.getTimerCount()).toBe(1)

    await act(async () => {
      vi.advanceTimersByTime(100)
    })

    // Таймер завершён без ошибок
    expect(vi.getTimerCount()).toBe(0)
  })

  it('очищает timeout при unmount (PA-06 compliance)', async () => {
    const { result, unmount } = renderHook(() => usePrefetch())

    // Создаём pending prefetch
    act(() => {
      result.current.prefetch('/dashboard')
    })

    // Таймер активен
    expect(vi.getTimerCount()).toBe(1)

    // Unmount компонента
    unmount()

    // Таймер должен быть очищен cleanup функцией
    expect(vi.getTimerCount()).toBe(0)
  })
})
