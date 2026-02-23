// Тесты для useTheme hook (Story 11.2 — System Theme Default)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTheme } from './useTheme'

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

// Мокаем matchMedia
const createMatchMediaMock = (matches: boolean) => {
  return vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

describe('useTheme', () => {
  beforeEach(() => {
    // Сбрасываем localStorage и mocks перед каждым тестом
    localStorageMock.clear()
    // Сбрасываем spy вызовы
    localStorageMock.getItem.mockClear()
    localStorageMock.setItem.mockClear()
    localStorageMock.removeItem.mockClear()

    vi.stubGlobal('localStorage', localStorageMock)
    vi.stubGlobal('matchMedia', createMatchMediaMock(false)) // По умолчанию light system theme

    // Сбрасываем атрибуты документа
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.style.colorScheme = ''
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('инициализируется с системной темой когда localStorage пуст', () => {
    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('light')
    expect(result.current.isLight).toBe(true)
    expect(result.current.isDark).toBe(false)
  })

  it('инициализируется с dark темой когда система в dark mode', () => {
    vi.stubGlobal('matchMedia', createMatchMediaMock(true))

    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('dark')
    expect(result.current.isDark).toBe(true)
    expect(result.current.isLight).toBe(false)
  })

  it('использует сохранённую тему из localStorage', () => {
    localStorageMock.setItem('app-theme', 'dark')

    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('dark')
    expect(result.current.isDark).toBe(true)
  })

  it('сохранённая тема имеет приоритет над системной', () => {
    // Система в light mode, но сохранена dark
    vi.stubGlobal('matchMedia', createMatchMediaMock(false))
    localStorageMock.setItem('app-theme', 'dark')

    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('dark')
  })

  it('toggle переключает тему с light на dark', () => {
    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('light')

    act(() => {
      result.current.toggle()
    })

    expect(result.current.theme).toBe('dark')
    expect(result.current.isDark).toBe(true)
  })

  it('toggle переключает тему с dark на light', () => {
    localStorageMock.setItem('app-theme', 'dark')
    const { result } = renderHook(() => useTheme())

    expect(result.current.theme).toBe('dark')

    act(() => {
      result.current.toggle()
    })

    expect(result.current.theme).toBe('light')
    expect(result.current.isLight).toBe(true)
  })

  it('НЕ сохраняет системную тему в localStorage при первом визите', () => {
    // Первый визит: localStorage пуст, система в dark mode
    vi.stubGlobal('matchMedia', createMatchMediaMock(true))

    const { result } = renderHook(() => useTheme())

    // Тема должна быть dark (от системы), но НЕ сохранена в localStorage
    expect(result.current.theme).toBe('dark')
    expect(localStorageMock.setItem).not.toHaveBeenCalled()
  })

  it('сохраняет тему в localStorage при явном toggle', () => {
    const { result } = renderHook(() => useTheme())

    // Сбрасываем вызовы setItem (если были при инициализации)
    localStorageMock.setItem.mockClear()

    act(() => {
      result.current.toggle()
    })

    expect(localStorageMock.setItem).toHaveBeenCalledWith('app-theme', 'dark')
    // Проверяем что setItem вызван ровно 1 раз (нет дублирования)
    expect(localStorageMock.setItem).toHaveBeenCalledTimes(1)
  })

  it('сохраняет тему в localStorage при явном setTheme', () => {
    const { result } = renderHook(() => useTheme())

    // Сбрасываем вызовы setItem (если были при инициализации)
    localStorageMock.setItem.mockClear()

    act(() => {
      result.current.setTheme('dark')
    })

    expect(localStorageMock.setItem).toHaveBeenCalledWith('app-theme', 'dark')
    // Проверяем что setItem вызван ровно 1 раз (нет дублирования)
    expect(localStorageMock.setItem).toHaveBeenCalledTimes(1)
  })

  it('setTheme устанавливает конкретную тему', () => {
    const { result } = renderHook(() => useTheme())

    act(() => {
      result.current.setTheme('dark')
    })

    expect(result.current.theme).toBe('dark')

    act(() => {
      result.current.setTheme('light')
    })

    expect(result.current.theme).toBe('light')
  })

  it('устанавливает data-theme атрибут на documentElement', () => {
    const { result } = renderHook(() => useTheme())

    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    act(() => {
      result.current.toggle()
    })

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('устанавливает colorScheme style на documentElement', () => {
    const { result } = renderHook(() => useTheme())

    expect(document.documentElement.style.colorScheme).toBe('light')

    act(() => {
      result.current.toggle()
    })

    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('регистрирует listener для изменения системной темы', () => {
    // Проверяем что addEventListener вызывается для media query
    const addEventListenerMock = vi.fn()
    const mediaQueryMock = {
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: addEventListenerMock,
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mediaQueryMock))

    renderHook(() => useTheme())

    // Проверяем что listener зарегистрирован
    expect(addEventListenerMock).toHaveBeenCalledWith('change', expect.any(Function))
  })

  it('удаляет listener при unmount', () => {
    // Проверяем что removeEventListener вызывается при unmount
    const removeEventListenerMock = vi.fn()
    const mediaQueryMock = {
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: removeEventListenerMock,
      dispatchEvent: vi.fn(),
    }
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mediaQueryMock))

    const { unmount } = renderHook(() => useTheme())

    unmount()

    // Проверяем что listener удалён
    expect(removeEventListenerMock).toHaveBeenCalledWith('change', expect.any(Function))
  })

  it('игнорирует невалидное значение в localStorage', () => {
    // Устанавливаем невалидное значение
    localStorageMock.setItem('app-theme', 'invalid')

    const { result } = renderHook(() => useTheme())

    // Должна использоваться системная тема (light по умолчанию в mock)
    expect(result.current.theme).toBe('light')
  })

  it('обновляет тему при изменении системной когда нет сохранённой', () => {
    // Создаём mock с возможностью вызвать change handler
    let changeHandler: ((e: { matches: boolean }) => void) | null = null
    const mediaQueryMock = {
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((event: string, handler: (e: { matches: boolean }) => void) => {
        if (event === 'change') changeHandler = handler
      }),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mediaQueryMock))

    const { result } = renderHook(() => useTheme())

    // Изначально light (система в light mode)
    expect(result.current.theme).toBe('light')

    // Симулируем изменение системной темы на dark
    act(() => {
      changeHandler?.({ matches: true })
    })

    // Тема должна измениться на dark
    expect(result.current.theme).toBe('dark')
  })

  it('сохранённая тема НЕ обновляется при изменении системной', () => {
    // Есть сохранённая тема dark
    localStorageMock.setItem('app-theme', 'dark')

    // Создаём mock с возможностью вызвать change handler
    let changeHandler: ((e: { matches: boolean }) => void) | null = null
    const mediaQueryMock = {
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((event: string, handler: (e: { matches: boolean }) => void) => {
        if (event === 'change') changeHandler = handler
      }),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mediaQueryMock))

    const { result } = renderHook(() => useTheme())

    // Изначально dark (из localStorage)
    expect(result.current.theme).toBe('dark')

    // Симулируем изменение системной темы на light
    act(() => {
      changeHandler?.({ matches: false })
    })

    // Тема НЕ должна измениться (сохранённая имеет приоритет)
    expect(result.current.theme).toBe('dark')
  })
})
