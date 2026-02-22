// Настройка тестовой среды
import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Мок для App.useApp() (требуется для компонентов с message/modal через App.useApp)
// Story 10.9: Глобальный мок для всех тестов, которые не имеют своего мока antd
vi.mock('antd', async () => {
  const actual = await vi.importActual('antd')
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: {
          success: vi.fn(),
          error: vi.fn(),
          warning: vi.fn(),
          info: vi.fn(),
          loading: vi.fn(() => vi.fn()),
        },
        modal: {
          // Мок modal.confirm который сразу вызывает onOk (симуляция подтверждения)
          confirm: vi.fn((config: { onOk?: () => void }) => {
            if (config?.onOk) {
              config.onOk()
            }
          }),
          info: vi.fn(),
          success: vi.fn(),
          error: vi.fn(),
          warning: vi.fn(),
        },
        notification: {
          success: vi.fn(),
          error: vi.fn(),
          warning: vi.fn(),
          info: vi.fn(),
        },
      }),
    },
  }
})

// Мок для window.matchMedia (требуется для Ant Design)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// Мок для ResizeObserver (требуется для Ant Design)
globalThis.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// Подавление предупреждений о CSS в тестах
const originalError = console.error
console.error = (...args) => {
  if (
    typeof args[0] === 'string' &&
    args[0].includes('Warning: An update to')
  ) {
    return
  }
  originalError.call(console, ...args)
}
