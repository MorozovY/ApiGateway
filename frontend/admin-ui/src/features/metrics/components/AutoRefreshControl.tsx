// Компонент управления auto-refresh для метрик (Story 16.8)
import { useState, useEffect } from 'react'
import { Switch, Select, Space, Typography } from 'antd'
import { SyncOutlined } from '@ant-design/icons'
import { AUTO_REFRESH_INTERVALS } from '../hooks/useAutoRefresh'

const { Text } = Typography

/**
 * Props для компонента AutoRefreshControl.
 */
export interface AutoRefreshControlProps {
  /** Включён ли auto-refresh */
  enabled: boolean
  /** Текущий интервал в миллисекундах */
  interval: number
  /** Время последнего обновления данных */
  lastUpdated: Date | null
  /** Приостановлен ли refresh из-за неактивной вкладки (AC3) */
  isPaused?: boolean
  /** Callback при изменении enabled */
  onEnabledChange: (enabled: boolean) => void
  /** Callback при изменении интервала */
  onIntervalChange: (interval: number) => void
}

/**
 * Склонение числительных для русского языка.
 *
 * @param n - число
 * @param forms - массив форм [для 1, для 2-4, для 5-20]
 * @example pluralize(1, ['секунду', 'секунды', 'секунд']) → 'секунду'
 * @example pluralize(5, ['секунду', 'секунды', 'секунд']) → 'секунд'
 */
function pluralize(n: number, forms: [string, string, string]): string {
  const abs = Math.abs(n) % 100
  const lastDigit = abs % 10
  if (abs > 10 && abs < 20) return forms[2]
  if (lastDigit > 1 && lastDigit < 5) return forms[1]
  if (lastDigit === 1) return forms[0]
  return forms[2]
}

/**
 * Форматирование времени последнего обновления (AC2).
 *
 * Показывает:
 * - "Обновлено только что" — < 10 секунд
 * - "Обновлено X секунд/секунды/секунду назад" — < 60 секунд
 * - "Обновлено X минут/минуты/минуту назад" — >= 60 секунд
 */
export function formatLastUpdated(date: Date | null): string {
  if (!date) return ''
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  // Показываем "только что" до 10 секунд — более естественно
  if (seconds < 10) return 'Обновлено только что'
  if (seconds < 60) {
    const form = pluralize(seconds, ['секунду', 'секунды', 'секунд'])
    return `Обновлено ${seconds} ${form} назад`
  }
  const minutes = Math.floor(seconds / 60)
  const form = pluralize(minutes, ['минуту', 'минуты', 'минут'])
  return `Обновлено ${minutes} ${form} назад`
}

/**
 * Компонент управления auto-refresh метрик.
 *
 * Отображает (AC1):
 * - Toggle "Auto-refresh" (по умолчанию выключен)
 * - Selector интервала: 15s, 30s, 60s
 * - Индикатор "Обновлено X сек назад" (AC2)
 *
 * @example
 * ```tsx
 * <AutoRefreshControl
 *   enabled={autoRefresh.enabled}
 *   interval={autoRefresh.interval}
 *   lastUpdated={dataUpdatedAt ? new Date(dataUpdatedAt) : null}
 *   onEnabledChange={autoRefresh.setEnabled}
 *   onIntervalChange={autoRefresh.setInterval}
 * />
 * ```
 */
export function AutoRefreshControl({
  enabled,
  interval,
  lastUpdated,
  isPaused = false,
  onEnabledChange,
  onIntervalChange,
}: AutoRefreshControlProps) {
  // Story 16.8 AC2: обновляем индикатор каждую секунду когда auto-refresh включён и не приостановлен
  const [, setTick] = useState(0)

  useEffect(() => {
    // Не обновляем если выключен, нет данных или приостановлен
    if (!enabled || !lastUpdated || isPaused) return

    // PA-06: cleanup для setInterval
    const intervalId = setInterval(() => {
      setTick((t) => t + 1)
    }, 1000)

    return () => clearInterval(intervalId)
  }, [enabled, lastUpdated, isPaused])

  // AC3: иконка не крутится когда приостановлен
  const showSpinning = enabled && !isPaused

  return (
    <Space size="middle" align="center" data-testid="auto-refresh-control">
      {/* Toggle Auto-refresh (AC1) */}
      <Space size="small">
        <SyncOutlined spin={showSpinning} style={{ color: enabled ? '#1890ff' : undefined }} />
        <Text>Auto-refresh:</Text>
        <Switch
          checked={enabled}
          onChange={onEnabledChange}
          size="small"
          data-testid="auto-refresh-toggle"
        />
      </Space>

      {/* Interval Selector (AC1) — показываем всегда, но disabled когда выключен (MED-3 fix) */}
      <Select
        value={interval}
        onChange={onIntervalChange}
        size="small"
        style={{ width: 90 }}
        disabled={!enabled}
        options={AUTO_REFRESH_INTERVALS.map((opt) => ({
          label: opt.label,
          value: opt.value,
        }))}
        data-testid="auto-refresh-interval"
      />

      {/* AC3: Индикатор паузы когда вкладка неактивна */}
      {isPaused && (
        <Text type="warning" data-testid="paused-indicator">
          Приостановлено
        </Text>
      )}

      {/* Last Updated Indicator (AC2) — показываем только когда не приостановлен */}
      {lastUpdated && !isPaused && (
        <Text type="secondary" data-testid="last-updated-indicator">
          {formatLastUpdated(lastUpdated)}
        </Text>
      )}
    </Space>
  )
}

export default AutoRefreshControl
