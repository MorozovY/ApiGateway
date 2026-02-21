// Hook для управления генерацией нагрузки (Story 8.9, AC3, AC4; обновлено Story 9.2)
import { useState, useCallback, useRef, useEffect } from 'react'
import type {
  LoadGeneratorConfig,
  LoadGeneratorState,
  LoadGeneratorSummary,
} from '../types/loadGenerator.types'

/**
 * Префикс для API запросов к gateway-core через nginx.
 * nginx routing: /api/* → gateway-core:8080 (см. docker/nginx/nginx.conf)
 * Используем относительный путь для работы через same-origin (без CORS).
 */
const GATEWAY_API_PREFIX = '/api'

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
  const stoppedDurationRef = useRef<number | null>(null) // M2: захватываем duration при остановке
  const requestInFlightRef = useRef<boolean>(false) // M1: предотвращаем concurrent requests

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
    // M2: захватываем duration при остановке, чтобы не пересчитывать на каждом рендере
    stoppedDurationRef.current = startTimeRef.current ? Date.now() - startTimeRef.current : 0
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
      stoppedDurationRef.current = null // M2: сбрасываем захваченную duration
      requestInFlightRef.current = false // M1: сбрасываем флаг concurrent request

      // Вычисляем интервал между запросами в миллисекундах
      const intervalMs = 1000 / config.requestsPerSecond

      setState({
        ...initialState,
        status: 'running',
        startTime: Date.now(),
      })

      // Отправляем запросы с заданным интервалом
      intervalRef.current = window.setInterval(async () => {
        // M1: предотвращаем concurrent requests — пропускаем если предыдущий ещё выполняется
        if (requestInFlightRef.current) {
          return
        }
        requestInFlightRef.current = true

        const requestStartTime = performance.now()
        try {
          // Отправляем запрос через nginx → gateway-core
          // Используем относительный путь /api${routePath} для same-origin запросов
          const response = await fetch(`${GATEWAY_API_PREFIX}${config.routePath}`, {
            method: 'GET',
          })
          const elapsed = performance.now() - requestStartTime

          if (response.ok) {
            // HTTP 2xx — успех
            responseTimes.current.push(elapsed)
            setState((prev) => ({
              ...prev,
              sentCount: prev.sentCount + 1,
              successCount: prev.successCount + 1,
              averageResponseTime: calculateAverage(responseTimes.current),
            }))
          } else {
            // HTTP 4xx/5xx — ошибка от upstream или gateway
            setState((prev) => ({
              ...prev,
              sentCount: prev.sentCount + 1,
              errorCount: prev.errorCount + 1,
              lastError: `HTTP ${response.status}: ${response.statusText}`,
            }))
          }
        } catch (error) {
          // Network error (CORS, timeout, connection refused)
          // L2: сохраняем информацию об ошибке корректно
          const errorMessage = error instanceof Error ? error.message : String(error) || 'Unknown error'
          setState((prev) => ({
            ...prev,
            sentCount: prev.sentCount + 1,
            errorCount: prev.errorCount + 1,
            lastError: errorMessage,
          }))
        } finally {
          requestInFlightRef.current = false
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
    stoppedDurationRef.current = null
    requestInFlightRef.current = false
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
   * M2: используем захваченную duration вместо пересчёта на каждом рендере.
   */
  const summary: LoadGeneratorSummary | null =
    state.status === 'stopped'
      ? {
          totalRequests: state.sentCount,
          successCount: state.successCount,
          errorCount: state.errorCount,
          durationMs: stoppedDurationRef.current ?? 0,
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
