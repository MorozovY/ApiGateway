// Hook для управления auto-refresh метрик (Story 16.8)
import { useState, useEffect, useCallback, useMemo, useSyncExternalStore } from 'react'

/**
 * Доступные интервалы auto-refresh (AC1).
 */
export const AUTO_REFRESH_INTERVALS = [
  { label: '15 сек', value: 15000 },
  { label: '30 сек', value: 30000 },
  { label: '60 сек', value: 60000 },
] as const

/**
 * Ключ для хранения настроек в localStorage (AC5).
 */
export const STORAGE_KEY = 'metrics-auto-refresh'

/**
 * Hook для отслеживания видимости страницы (AC3).
 *
 * Использует Page Visibility API для определения активности вкладки.
 * Возвращает true когда вкладка активна, false когда в фоне.
 */
export function usePageVisibility(): boolean {
  const subscribe = useCallback((callback: () => void) => {
    document.addEventListener('visibilitychange', callback)
    return () => document.removeEventListener('visibilitychange', callback)
  }, [])

  const getSnapshot = useCallback(() => document.visibilityState === 'visible', [])

  // SSR fallback — считаем страницу видимой
  const getServerSnapshot = useCallback(() => true, [])

  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}

/**
 * Настройки auto-refresh, сохраняемые в localStorage.
 */
interface AutoRefreshSettings {
  enabled: boolean
  interval: number
}

/**
 * Интерфейс возвращаемого значения хука.
 */
export interface UseAutoRefreshReturn {
  /** Включён ли auto-refresh */
  enabled: boolean
  /** Текущий интервал в миллисекундах */
  interval: number
  /** Время последнего обновления данных */
  lastUpdated: Date | null
  /** Видима ли страница (AC3 — Page Visibility API) */
  isPageVisible: boolean
  /** Приостановлен ли refresh из-за неактивной вкладки */
  isPaused: boolean
  /** Включить/выключить auto-refresh */
  setEnabled: (enabled: boolean) => void
  /** Установить интервал обновления */
  setInterval: (interval: number) => void
  /** Установить время последнего обновления */
  setLastUpdated: (date: Date | null) => void
  /** Сбросить таймер (при смене time range) */
  resetTimer: () => void
  /** refetchInterval для React Query (false если выключен) */
  refetchInterval: number | false
}

/**
 * Загрузка настроек из localStorage.
 */
function loadSettings(): AutoRefreshSettings {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      return {
        // Auto-refresh ВЫКЛЮЧЕН по умолчанию (AC1)
        enabled: typeof parsed.enabled === 'boolean' ? parsed.enabled : false,
        interval: typeof parsed.interval === 'number' ? parsed.interval : 30000,
      }
    }
  } catch {
    // Игнорируем ошибки парсинга
  }
  return { enabled: false, interval: 30000 }
}

/**
 * Сохранение настроек в localStorage (AC5).
 */
function saveSettings(settings: AutoRefreshSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  } catch {
    // Игнорируем ошибки записи (квота, приватный режим)
  }
}

/**
 * Hook для управления auto-refresh метрик.
 *
 * Функциональность:
 * - Toggle для включения/выключения (AC1)
 * - Выбор интервала: 15s, 30s, 60s (AC1)
 * - Сохранение настроек в localStorage (AC5)
 * - Сброс таймера при смене Time Range (AC4)
 *
 * @example
 * ```tsx
 * const autoRefresh = useAutoRefresh()
 *
 * // В React Query
 * const { data } = useQuery({
 *   queryKey: ['metrics'],
 *   queryFn: fetchMetrics,
 *   refetchInterval: autoRefresh.refetchInterval,
 * })
 * ```
 */
export function useAutoRefresh(): UseAutoRefreshReturn {
  // Загружаем начальное состояние из localStorage
  const [settings, setSettings] = useState<AutoRefreshSettings>(() => loadSettings())
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  // AC3: отслеживание видимости страницы
  const isPageVisible = usePageVisibility()

  // Сохранение настроек в localStorage при изменении (AC5)
  useEffect(() => {
    saveSettings(settings)
  }, [settings])

  const setEnabled = useCallback((enabled: boolean) => {
    setSettings((prev) => ({ ...prev, enabled }))
  }, [])

  const setIntervalValue = useCallback((interval: number) => {
    setSettings((prev) => ({ ...prev, interval }))
  }, [])

  const resetTimer = useCallback(() => {
    // Сброс lastUpdated вызывает перерисовку индикатора
    setLastUpdated(null)
  }, [])

  // Вычисляем refetchInterval для React Query
  // React Query автоматически обрабатывает Page Visibility через refetchIntervalInBackground: false (по умолчанию)
  const refetchInterval = useMemo(
    () => (settings.enabled ? settings.interval : false),
    [settings.enabled, settings.interval]
  )

  // AC3: приостановлен ли refresh из-за неактивной вкладки
  const isPaused = settings.enabled && !isPageVisible

  return {
    enabled: settings.enabled,
    interval: settings.interval,
    lastUpdated,
    isPageVisible,
    isPaused,
    setEnabled,
    setInterval: setIntervalValue,
    setLastUpdated,
    resetTimer,
    refetchInterval,
  }
}

export default useAutoRefresh
