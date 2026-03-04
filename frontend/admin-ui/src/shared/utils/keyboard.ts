// Утилиты для работы с keyboard shortcuts (Story 16.9)

// Расширение типов Navigator для userAgentData (Navigator API High Entropy)
declare global {
  interface Navigator {
    userAgentData?: {
      platform: string
      brands: Array<{ brand: string; version: string }>
      mobile: boolean
    }
  }
}

/**
 * Определяет, является ли текущая ОС macOS (включая iOS).
 *
 * Использует современный Navigator.userAgentData API если доступен,
 * с fallback на navigator.platform для старых браузеров.
 *
 * @returns true если macOS/iOS, false для Windows/Linux/ChromeOS
 */
export function isMacOS(): boolean {
  // Проверка на SSR — navigator недоступен на сервере
  if (typeof navigator === 'undefined') {
    return false
  }

  // Современный способ (Chromium 93+, Safari 15+)
  // Примечание: пустая строка в platform — falsy, поэтому используем fallback
  if ('userAgentData' in navigator && navigator.userAgentData?.platform) {
    return navigator.userAgentData.platform === 'macOS'
  }

  // Fallback для старых браузеров и iOS
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform)
}

/**
 * Возвращает символ модификатора для текущей ОС.
 *
 * - macOS: '⌘' (Command key)
 * - Windows/Linux: 'Ctrl'
 *
 * @returns символ модификатора
 */
export function getModifierKey(): '⌘' | 'Ctrl' {
  return isMacOS() ? '⌘' : 'Ctrl'
}

/**
 * Форматирует keyboard shortcut для текущей ОС.
 *
 * @param key - основная клавиша (e.g., 'N', 'S', 'Enter')
 * @param includeModifier - включить модификатор (default: true)
 * @returns форматированный shortcut (e.g., '⌘+N' или 'Ctrl+N')
 *
 * @example
 * // На macOS:
 * formatShortcut('N') // '⌘+N'
 *
 * // На Windows/Linux:
 * formatShortcut('N') // 'Ctrl+N'
 *
 * // Без модификатора:
 * formatShortcut('N', false) // 'N'
 */
export function formatShortcut(key: string, includeModifier = true): string {
  if (!includeModifier) return key
  return `${getModifierKey()}+${key}`
}
