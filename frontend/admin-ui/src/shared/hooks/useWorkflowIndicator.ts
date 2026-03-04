// Hook для управления видимостью WorkflowIndicator (Story 16.10)
import { useState, useEffect, useCallback } from 'react'

/**
 * Возвращаемое значение hook useWorkflowIndicator.
 */
export interface UseWorkflowIndicatorReturn {
  /** Видимость индикатора */
  visible: boolean
  /** Переключить видимость */
  toggle: () => void
  /** Показать индикатор */
  show: () => void
  /** Скрыть индикатор */
  hide: () => void
}

/** Ключ для хранения состояния в localStorage */
const STORAGE_KEY = 'workflow-indicator-visible'

/**
 * Hook для управления видимостью WorkflowIndicator.
 *
 * По умолчанию индикатор скрыт (AC5).
 * Состояние сохраняется в localStorage (AC4).
 *
 * @returns объект с состоянием видимости и функциями управления
 */
export function useWorkflowIndicator(): UseWorkflowIndicatorReturn {
  const [visible, setVisible] = useState(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      // По умолчанию скрыт (false) — AC5
      return stored === 'true'
    } catch {
      // localStorage может быть недоступен (SSR, privacy mode)
      return false
    }
  })

  // Сохраняем состояние в localStorage при изменении
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(visible))
    } catch {
      // Игнорируем ошибки localStorage
    }
  }, [visible])

  const toggle = useCallback(() => setVisible((v) => !v), [])
  const show = useCallback(() => setVisible(true), [])
  const hide = useCallback(() => setVisible(false), [])

  return { visible, toggle, show, hide }
}
