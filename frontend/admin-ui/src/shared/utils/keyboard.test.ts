// Unit тесты для keyboard utils (Story 16.9)
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { isMacOS, getModifierKey, formatShortcut } from './keyboard'

describe('keyboard utils', () => {
  // Сохраняем оригинальные значения
  const originalUserAgentData = navigator.userAgentData
  const originalPlatform = navigator.platform

  afterEach(() => {
    // Восстанавливаем оригинальные значения после каждого теста
    Object.defineProperty(navigator, 'userAgentData', {
      value: originalUserAgentData,
      configurable: true,
    })
    Object.defineProperty(navigator, 'platform', {
      value: originalPlatform,
      configurable: true,
    })
  })

  describe('isMacOS', () => {
    it('возвращает true для macOS через userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'macOS' },
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })

    it('возвращает false для Windows через userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Windows' },
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('возвращает false для Linux через userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Linux' },
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('возвращает false для Chrome OS через userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Chrome OS' },
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('использует fallback navigator.platform когда userAgentData отсутствует', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'MacIntel',
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })

    it('возвращает true для iPhone через fallback', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'iPhone',
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })

    it('возвращает true для iPad через fallback', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'iPad',
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })

    it('возвращает false для Win32 через fallback', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'Win32',
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('возвращает false для Linux через fallback', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'Linux x86_64',
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('обрабатывает пустой platform в userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: '' },
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'MacIntel',
        configurable: true,
      })
      // Пустой platform (falsy) означает fallback на navigator.platform
      expect(isMacOS()).toBe(true)
    })

    // Примечание: SSR тест (navigator undefined) не реализован —
    // требует специальной конфигурации Vitest с environment: 'node'.
    // Код обрабатывает SSR случай в keyboard.ts:23-26.
  })

  describe('getModifierKey', () => {
    it('возвращает ⌘ для macOS', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'macOS' },
        configurable: true,
      })
      expect(getModifierKey()).toBe('⌘')
    })

    it('возвращает Ctrl для Windows', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Windows' },
        configurable: true,
      })
      expect(getModifierKey()).toBe('Ctrl')
    })

    it('возвращает Ctrl для Linux', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Linux' },
        configurable: true,
      })
      expect(getModifierKey()).toBe('Ctrl')
    })
  })

  describe('formatShortcut', () => {
    beforeEach(() => {
      // Устанавливаем Windows по умолчанию для предсказуемых результатов
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Windows' },
        configurable: true,
      })
    })

    it('форматирует shortcut с модификатором для Windows', () => {
      expect(formatShortcut('N')).toBe('Ctrl+N')
    })

    it('форматирует shortcut с модификатором для macOS', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'macOS' },
        configurable: true,
      })
      expect(formatShortcut('N')).toBe('⌘+N')
    })

    it('возвращает только клавишу когда includeModifier=false', () => {
      expect(formatShortcut('N', false)).toBe('N')
    })

    it('работает с разными клавишами', () => {
      expect(formatShortcut('S')).toBe('Ctrl+S')
      expect(formatShortcut('Enter')).toBe('Ctrl+Enter')
      expect(formatShortcut('Backspace')).toBe('Ctrl+Backspace')
    })

    it('сохраняет регистр клавиши', () => {
      expect(formatShortcut('n')).toBe('Ctrl+n')
      expect(formatShortcut('N')).toBe('Ctrl+N')
    })
  })
})
