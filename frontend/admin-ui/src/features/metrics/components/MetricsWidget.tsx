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
import { Tiny } from '@ant-design/charts'
import { useMetricsSummary } from '../hooks/useMetrics'
import { getErrorRateColor, getErrorRateStatus } from '../utils/errorRateUtils'
import { TREND_HISTORY_SIZE, MIN_SPARKLINE_POINTS } from '../config/metricsConfig'

/**
 * Props для MetricsWidget.
 */
interface MetricsWidgetProps {
  /** Обработчик клика на карточку метрики (AC3) */
  onClick?: () => void
}

/**
 * Sparkline компонент для отображения тренда за последние 30 минут (AC2).
 */
interface SparklineProps {
  data: number[]
  color?: string
  testId?: string
}

function Sparkline({ data, color = '#1890ff', testId }: SparklineProps) {
  // Если данных недостаточно, показываем placeholder
  if (data.length < MIN_SPARKLINE_POINTS) {
    return (
      <div
        style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        data-testid={testId}
      >
        <span style={{ color: '#999', fontSize: 12 }}>Collecting data...</span>
      </div>
    )
  }

  return (
    <div data-testid={testId}>
      <Tiny.Area
        data={data}
        height={40}
        autoFit
        smooth
        style={{ fill: color, fillOpacity: 0.3, stroke: color }}
        animate={false}
      />
    </div>
  )
}

/**
 * Виджет метрик для Dashboard.
 *
 * Отображает 4 карточки (AC1):
 * - Current RPS (большое число + sparkline)
 * - Avg Latency (с trend индикатором + sparkline)
 * - Error Rate (с цветовой кодировкой)
 * - Active Routes count
 *
 * Sparkline charts показывают тренд за последние 30 минут (AC2).
 * Auto-refresh каждые 10 секунд (AC2).
 * Error handling с retry кнопкой (AC4).
 */
export function MetricsWidget({ onClick }: MetricsWidgetProps) {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refetch } = useMetricsSummary('5m')

  // История метрик для sparkline графиков (AC2 - 30 минут истории)
  const [rpsHistory, setRpsHistory] = useState<number[]>([])
  const [latencyHistory, setLatencyHistory] = useState<number[]>([])

  // Обновляем историю при получении новых данных
  useEffect(() => {
    if (data?.requestsPerSecond !== undefined) {
      setRpsHistory((prev) => {
        const newHistory = [...prev, data.requestsPerSecond]
        // Храним последние N значений (30 минут при 10s интервале = 180 точек)
        return newHistory.slice(-TREND_HISTORY_SIZE)
      })
    }
    if (data?.avgLatencyMs !== undefined) {
      setLatencyHistory((prev) => {
        const newHistory = [...prev, data.avgLatencyMs]
        return newHistory.slice(-TREND_HISTORY_SIZE)
      })
    }
  }, [data?.requestsPerSecond, data?.avgLatencyMs])

  // Определяем тренд latency (сравниваем с предыдущим значением)
  const latencyTrend = useMemo(() => {
    if (latencyHistory.length < 2) return null
    const current = latencyHistory[latencyHistory.length - 1]
    const previous = latencyHistory[latencyHistory.length - 2]
    // Guard clause для TypeScript (хотя length >= 2 гарантирует существование)
    if (current === undefined || previous === undefined) return null
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
          <Sparkline data={rpsHistory} color="#1890ff" testId="sparkline-rps" />
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
          <Sparkline data={latencyHistory} color="#faad14" testId="sparkline-latency" />
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
