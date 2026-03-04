// Unit тесты для useAutoRefresh hook (Story 16.8)
import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAutoRefresh, usePageVisibility, AUTO_REFRESH_INTERVALS, STORAGE_KEY } from './useAutoRefresh'

// Мок для localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
  }
})()

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

describe('useAutoRefresh', () => {
  // STORAGE_KEY импортирован из hook (LOW-2 fix)

  beforeEach(() => {
    // Очищаем localStorage перед каждым тестом
    localStorageMock.clear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  it('возвращает default values если localStorage пустой', () => {
    const { result } = renderHook(() => useAutoRefresh())

    expect(result.current.enabled).toBe(false) // Выключен по умолчанию
    expect(result.current.interval).toBe(30000) // 30 сек по умолчанию
    expect(result.current.lastUpdated).toBe(null)
    expect(result.current.refetchInterval).toBe(false) // false когда выключен
  })

  it('загружает состояние из localStorage', () => {
    // Сохраняем настройки в localStorage
    localStorageMock.setItem(STORAGE_KEY, JSON.stringify({ enabled: true, interval: 15000 }))

    const { result } = renderHook(() => useAutoRefresh())

    expect(result.current.enabled).toBe(true)
    expect(result.current.interval).toBe(15000)
  })

  it('сохраняет состояние в localStorage при изменении enabled', () => {
    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setEnabled(true)
    })

    const saved = JSON.parse(localStorageMock.getItem(STORAGE_KEY)!)
    expect(saved.enabled).toBe(true)
  })

  it('сохраняет состояние в localStorage при изменении interval', () => {
    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setInterval(60000)
    })

    const saved = JSON.parse(localStorageMock.getItem(STORAGE_KEY)!)
    expect(saved.interval).toBe(60000)
  })

  it('возвращает refetchInterval равный interval когда enabled', () => {
    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setEnabled(true)
      result.current.setInterval(15000)
    })

    expect(result.current.refetchInterval).toBe(15000)
  })

  it('возвращает refetchInterval = false когда disabled', () => {
    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setEnabled(false)
    })

    expect(result.current.refetchInterval).toBe(false)
  })

  it('сбрасывает lastUpdated при вызове resetTimer', () => {
    const { result } = renderHook(() => useAutoRefresh())

    // Устанавливаем время
    act(() => {
      result.current.setLastUpdated(new Date())
    })

    expect(result.current.lastUpdated).not.toBe(null)

    // Сбрасываем
    act(() => {
      result.current.resetTimer()
    })

    expect(result.current.lastUpdated).toBe(null)
  })

  it('обрабатывает некорректный JSON в localStorage gracefully', () => {
    localStorageMock.setItem(STORAGE_KEY, 'invalid json')

    const { result } = renderHook(() => useAutoRefresh())

    // Должен вернуть default values
    expect(result.current.enabled).toBe(false)
    expect(result.current.interval).toBe(30000)
  })

  it('обрабатывает частично заполненный объект в localStorage', () => {
    localStorageMock.setItem(STORAGE_KEY, JSON.stringify({ enabled: true })) // без interval

    const { result } = renderHook(() => useAutoRefresh())

    expect(result.current.enabled).toBe(true)
    expect(result.current.interval).toBe(30000) // default
  })

  it('setLastUpdated обновляет время последнего обновления', () => {
    const { result } = renderHook(() => useAutoRefresh())
    const testDate = new Date('2026-03-04T12:00:00Z')

    act(() => {
      result.current.setLastUpdated(testDate)
    })

    expect(result.current.lastUpdated).toEqual(testDate)
  })
})

describe('AUTO_REFRESH_INTERVALS', () => {
  it('содержит три опции: 15s, 30s, 60s', () => {
    expect(AUTO_REFRESH_INTERVALS).toHaveLength(3)

    expect(AUTO_REFRESH_INTERVALS[0]).toEqual({ label: '15 сек', value: 15000 })
    expect(AUTO_REFRESH_INTERVALS[1]).toEqual({ label: '30 сек', value: 30000 })
    expect(AUTO_REFRESH_INTERVALS[2]).toEqual({ label: '60 сек', value: 60000 })
  })
})

// AC3: Page Visibility API
describe('usePageVisibility', () => {
  const originalVisibilityState = document.visibilityState

  afterEach(() => {
    // Восстанавливаем оригинальное состояние
    Object.defineProperty(document, 'visibilityState', {
      value: originalVisibilityState,
      writable: true,
    })
  })

  it('возвращает true когда страница видима', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
    })

    const { result } = renderHook(() => usePageVisibility())
    expect(result.current).toBe(true)
  })

  it('возвращает false когда страница скрыта', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      writable: true,
    })

    const { result } = renderHook(() => usePageVisibility())
    expect(result.current).toBe(false)
  })

  it('обновляется при событии visibilitychange', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
    })

    const { result } = renderHook(() => usePageVisibility())
    expect(result.current).toBe(true)

    // Симулируем скрытие вкладки
    act(() => {
      Object.defineProperty(document, 'visibilityState', {
        value: 'hidden',
        writable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))
    })

    expect(result.current).toBe(false)
  })
})

describe('useAutoRefresh isPaused', () => {
  beforeEach(() => {
    localStorageMock.clear()
    // Устанавливаем страницу как видимую по умолчанию
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
    })
  })

  it('isPaused = false когда enabled и страница видима', () => {
    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setEnabled(true)
    })

    expect(result.current.isPaused).toBe(false)
  })

  it('isPaused = false когда disabled (независимо от видимости)', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      writable: true,
    })

    const { result } = renderHook(() => useAutoRefresh())
    // По умолчанию выключен
    expect(result.current.isPaused).toBe(false)
  })

  it('isPaused = true когда enabled и страница скрыта', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      writable: true,
    })

    const { result } = renderHook(() => useAutoRefresh())

    act(() => {
      result.current.setEnabled(true)
    })

    expect(result.current.isPaused).toBe(true)
  })

  it('isPageVisible отражает состояние видимости страницы', () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
    })

    const { result } = renderHook(() => useAutoRefresh())
    expect(result.current.isPageVisible).toBe(true)

    act(() => {
      Object.defineProperty(document, 'visibilityState', {
        value: 'hidden',
        writable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))
    })

    expect(result.current.isPageVisible).toBe(false)
  })
})
