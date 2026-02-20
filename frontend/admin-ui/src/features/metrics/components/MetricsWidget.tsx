// Виджет метрик для Dashboard (Story 6.5, AC1, AC2, AC4)
import { Row, Col, Card, Statistic, Alert, Button, Spin } from 'antd'
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  ReloadOutlined,
  ApiOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useEffect, useState, useMemo } from 'react'
import { useMetricsSummary } from '../hooks/useMetrics'

/**
 * Возвращает цвет для Error Rate по порогам (AC1).
 * - зелёный: < 1%
 * - жёлтый: 1-5%
 * - красный: > 5%
 */
function getErrorRateColor(rate: number): string {
  if (rate < 0.01) return '#52c41a' // зелёный
  if (rate < 0.05) return '#faad14' // жёлтый
  return '#f5222d' // красный
}

/**
 * Возвращает текстовое описание статуса error rate.
 */
function getErrorRateStatus(rate: number): string {
  if (rate < 0.01) return 'healthy'
  if (rate < 0.05) return 'warning'
  return 'critical'
}

/**
 * Props для MetricsWidget.
 */
interface MetricsWidgetProps {
  /** Обработчик клика на карточку метрики (AC3) */
  onClick?: () => void
}

/**
 * Виджет метрик для Dashboard.
 *
 * Отображает 4 карточки (AC1):
 * - Current RPS (большое число)
 * - Avg Latency (с trend индикатором)
 * - Error Rate (с цветовой кодировкой)
 * - Active Routes count
 *
 * Auto-refresh каждые 10 секунд (AC2).
 * Error handling с retry кнопкой (AC4).
 */
export function MetricsWidget({ onClick }: MetricsWidgetProps) {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refetch } = useMetricsSummary('5m')

  // История latency для определения тренда (AC2)
  const [latencyHistory, setLatencyHistory] = useState<number[]>([])

  // Обновляем историю latency при получении новых данных
  useEffect(() => {
    if (data?.avgLatencyMs !== undefined) {
      setLatencyHistory((prev) => {
        const newHistory = [...prev, data.avgLatencyMs]
        // Храним последние 5 значений для определения тренда
        return newHistory.slice(-5)
      })
    }
  }, [data?.avgLatencyMs])

  // Определяем тренд latency (сравниваем с предыдущим значением)
  const latencyTrend = useMemo(() => {
    if (latencyHistory.length < 2) return null
    const current = latencyHistory[latencyHistory.length - 1] as number
    const previous = latencyHistory[latencyHistory.length - 2] as number
    if (current > previous) return 'up'
    if (current < previous) return 'down'
    return null
  }, [latencyHistory])

  // Обработчик клика — переход на /metrics (AC3)
  const handleClick = () => {
    if (onClick) {
      onClick()
    } else {
      navigate('/metrics')
    }
  }

  // Error state (AC4)
  if (isError) {
    return (
      <Alert
        message="Metrics unavailable"
        description={error?.message || 'Could not load metrics data'}
        type="warning"
        showIcon
        action={
          <Button
            size="small"
            onClick={() => refetch()}
            icon={<ReloadOutlined />}
            data-testid="metrics-retry-button"
          >
            Retry
          </Button>
        }
        data-testid="metrics-error-alert"
      />
    )
  }

  // Loading state
  if (isLoading) {
    return (
      <Card data-testid="metrics-loading">
        <div style={{ textAlign: 'center', padding: '24px' }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>Loading metrics...</div>
        </div>
      </Card>
    )
  }

  const errorRateColor = getErrorRateColor(data?.errorRate ?? 0)
  const errorRateStatus = getErrorRateStatus(data?.errorRate ?? 0)

  return (
    <Row gutter={16} data-testid="metrics-widget">
      {/* RPS Card */}
      <Col span={6}>
        <Card
          hoverable
          onClick={handleClick}
          data-testid="metrics-card-rps"
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Requests per Second"
            value={data?.requestsPerSecond ?? 0}
            precision={1}
            prefix={<ApiOutlined />}
            valueStyle={{ fontSize: 28 }}
          />
        </Card>
      </Col>

      {/* Latency Card */}
      <Col span={6}>
        <Card
          hoverable
          onClick={handleClick}
          data-testid="metrics-card-latency"
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Avg Latency"
            value={data?.avgLatencyMs ?? 0}
            suffix="ms"
            valueStyle={{ fontSize: 28 }}
            prefix={
              latencyTrend === 'up' ? (
                <ArrowUpOutlined style={{ color: '#f5222d' }} data-testid="latency-trend-up" />
              ) : latencyTrend === 'down' ? (
                <ArrowDownOutlined style={{ color: '#52c41a' }} data-testid="latency-trend-down" />
              ) : null
            }
          />
        </Card>
      </Col>

      {/* Error Rate Card */}
      <Col span={6}>
        <Card
          hoverable
          onClick={handleClick}
          data-testid="metrics-card-error-rate"
          data-error-status={errorRateStatus}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Error Rate"
            value={(data?.errorRate ?? 0) * 100}
            precision={2}
            suffix="%"
            valueStyle={{ fontSize: 28, color: errorRateColor }}
          />
        </Card>
      </Col>

      {/* Active Routes Card */}
      <Col span={6}>
        <Card
          hoverable
          onClick={handleClick}
          data-testid="metrics-card-active-routes"
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Active Routes"
            value={data?.activeRoutes ?? 0}
            valueStyle={{ fontSize: 28 }}
          />
        </Card>
      </Col>
    </Row>
  )
}

export default MetricsWidget
