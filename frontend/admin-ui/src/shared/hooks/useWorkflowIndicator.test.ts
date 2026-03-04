// Unit тесты для useWorkflowIndicator (Story 16.10)
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useWorkflowIndicator } from './useWorkflowIndicator'

// Мокаем localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: () => {
      store = {}
    },
  }
})()

describe('useWorkflowIndicator', () => {
  beforeEach(() => {
    // Сбрасываем localStorage и mocks перед каждым тестом
    localStorageMock.clear()
    localStorageMock.getItem.mockClear()
    localStorageMock.setItem.mockClear()

    vi.stubGlobal('localStorage', localStorageMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('по умолчанию скрыт (visible=false)', () => {
    const { result } = renderHook(() => useWorkflowIndicator())
    expect(result.current.visible).toBe(false)
  })

  it('toggle переключает видимость', () => {
    const { result } = renderHook(() => useWorkflowIndicator())

    expect(result.current.visible).toBe(false)

    act(() => {
      result.current.toggle()
    })
    expect(result.current.visible).toBe(true)

    act(() => {
      result.current.toggle()
    })
    expect(result.current.visible).toBe(false)
  })

  it('show устанавливает visible=true', () => {
    const { result } = renderHook(() => useWorkflowIndicator())

    expect(result.current.visible).toBe(false)

    act(() => {
      result.current.show()
    })
    expect(result.current.visible).toBe(true)
  })

  it('hide устанавливает visible=false', () => {
    localStorageMock.setItem('workflow-indicator-visible', 'true')

    const { result } = renderHook(() => useWorkflowIndicator())
    expect(result.current.visible).toBe(true)

    act(() => {
      result.current.hide()
    })
    expect(result.current.visible).toBe(false)
  })

  it('сохраняет состояние в localStorage', () => {
    const { result } = renderHook(() => useWorkflowIndicator())

    // Сбрасываем начальные вызовы
    localStorageMock.setItem.mockClear()

    act(() => {
      result.current.show()
    })
    expect(localStorageMock.setItem).toHaveBeenCalledWith(
      'workflow-indicator-visible',
      'true'
    )

    localStorageMock.setItem.mockClear()

    act(() => {
      result.current.hide()
    })
    expect(localStorageMock.setItem).toHaveBeenCalledWith(
      'workflow-indicator-visible',
      'false'
    )
  })

  it('восстанавливает состояние из localStorage', () => {
    localStorageMock.setItem('workflow-indicator-visible', 'true')

    const { result } = renderHook(() => useWorkflowIndicator())
    expect(result.current.visible).toBe(true)
  })

  it('возвращает false если localStorage пуст', () => {
    // localStorage.clear() уже вызван в beforeEach
    const { result } = renderHook(() => useWorkflowIndicator())
    expect(result.current.visible).toBe(false)
  })

  it('обрабатывает ошибки localStorage gracefully', () => {
    // Мокаем ошибку localStorage
    const errorLocalStorage = {
      getItem: vi.fn(() => {
        throw new Error('localStorage not available')
      }),
      setItem: vi.fn(() => {
        throw new Error('localStorage not available')
      }),
      removeItem: vi.fn(),
      clear: vi.fn(),
    }
    vi.stubGlobal('localStorage', errorLocalStorage)

    // Не должен бросать исключение
    const { result } = renderHook(() => useWorkflowIndicator())
    expect(result.current.visible).toBe(false)

    // toggle тоже не должен бросать исключение
    act(() => {
      result.current.toggle()
    })
    expect(result.current.visible).toBe(true)
  })
})
