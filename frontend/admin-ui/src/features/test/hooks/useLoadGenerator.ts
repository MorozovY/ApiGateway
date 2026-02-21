// Hook для управления генерацией нагрузки (Story 8.9, AC3, AC4)
import { useState, useCallback, useRef, useEffect } from 'react'
import type {
  LoadGeneratorConfig,
  LoadGeneratorState,
  LoadGeneratorSummary,
} from '../types/loadGenerator.types'

/**
 * URL gateway-core для отправки запросов.
 * Используем environment variable для гибкости конфигурации.
 */
const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'

/**
 * Начальное состояние генератора.
 */
const initialState: LoadGeneratorState = {
  status: 'idle',
  startTime: null,
  sentCount: 0,
  successCount: 0,
  errorCount: 0,
  lastError: null,
  averageResponseTime: null,
}

/**
 * Вычисляет среднее значение массива чисел.
 */
function calculateAverage(values: number[]): number | null {
  if (values.length === 0) return null
  return values.reduce((sum, val) => sum + val, 0) / values.length
}

/**
 * Возвращаемое значение hook useLoadGenerator.
 */
export interface UseLoadGeneratorReturn {
  state: LoadGeneratorState
  start: (config: LoadGeneratorConfig) => void
  stop: () => void
  reset: () => void
  summary: LoadGeneratorSummary | null
}

/**
 * Hook для управления генерацией нагрузки.
 *
 * Генерирует HTTP запросы к выбранному маршруту через gateway-core
 * с заданной частотой (RPS) и отслеживает результаты.
 */
export function useLoadGenerator(): UseLoadGeneratorReturn {
  const [state, setState] = useState<LoadGeneratorState>(initialState)
  const intervalRef = useRef<number | null>(null)
  const timeoutRef = useRef<number | null>(null)
  const responseTimes = useRef<number[]>([])
  const configRef = useRef<LoadGeneratorConfig | null>(null)
  const startTimeRef = useRef<number | null>(null)

  /**
   * Останавливает генерацию запросов.
   */
  const stop = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
    setState((prev) => ({ ...prev, status: 'stopped' }))
  }, [])

  /**
   * Запускает генерацию запросов с заданной конфигурацией.
   */
  const start = useCallback(
    (config: LoadGeneratorConfig) => {
      // Очищаем предыдущее состояние
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
      }
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
      responseTimes.current = []
      configRef.current = config
      startTimeRef.current = Date.now()

      // Вычисляем интервал между запросами в миллисекундах
      const intervalMs = 1000 / config.requestsPerSecond

      setState({
        ...initialState,
        status: 'running',
        startTime: Date.now(),
      })

      // Отправляем запросы с заданным интервалом
      intervalRef.current = window.setInterval(async () => {
        const requestStartTime = performance.now()
        try {
          // Отправляем запрос через gateway-core
          await fetch(`${GATEWAY_URL}${config.routePath}`, {
            method: 'GET',
            mode: 'cors',
          })
          const elapsed = performance.now() - requestStartTime
          responseTimes.current.push(elapsed)
          setState((prev) => ({
            ...prev,
            sentCount: prev.sentCount + 1,
            successCount: prev.successCount + 1,
            averageResponseTime: calculateAverage(responseTimes.current),
          }))
        } catch (error) {
          setState((prev) => ({
            ...prev,
            sentCount: prev.sentCount + 1,
            errorCount: prev.errorCount + 1,
            lastError: error instanceof Error ? error.message : 'Unknown error',
          }))
        }
      }, intervalMs)

      // Автоостановка по duration (если задан)
      if (config.durationSeconds) {
        timeoutRef.current = window.setTimeout(() => {
          stop()
        }, config.durationSeconds * 1000)
      }
    },
    [stop]
  )

  /**
   * Сбрасывает генератор в начальное состояние.
   */
  const reset = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
    responseTimes.current = []
    configRef.current = null
    startTimeRef.current = null
    setState(initialState)
  }, [])

  /**
   * Cleanup при unmount компонента.
   * Останавливает все активные таймеры чтобы избежать memory leak
   * и попыток setState на unmounted компоненте.
   */
  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
        timeoutRef.current = null
      }
    }
  }, [])

  /**
   * Summary доступен только после остановки генерации.
   */
  const summary: LoadGeneratorSummary | null =
    state.status === 'stopped'
      ? {
          totalRequests: state.sentCount,
          successCount: state.successCount,
          errorCount: state.errorCount,
          durationMs: startTimeRef.current ? Date.now() - startTimeRef.current : 0,
          successRate: state.sentCount > 0 ? (state.successCount / state.sentCount) * 100 : 0,
          averageResponseTime: state.averageResponseTime,
        }
      : null

  return {
    state,
    start,
    stop,
    reset,
    summary,
  }
}
