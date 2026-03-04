// Страница детальных метрик (Story 6.5, AC5, Story 8.1, Story 15.4 — добавлен PageInfoBlock, Story 15.6 — унификация заголовка, Story 16.4 — responsive cards, Story 16.8 — auto-refresh)
import { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Segmented, Button, Space, Alert, Spin, Typography } from 'antd'
import { LinkOutlined, ReloadOutlined, InfoCircleOutlined, AreaChartOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { isDeveloper as isDeveloperFn } from '@shared/utils'
import { useMetricsSummary, useTopRoutes } from '../hooks/useMetrics'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import TopRoutesTable from './TopRoutesTable'
import HealthCheckSection from './HealthCheckSection'
import AutoRefreshControl from './AutoRefreshControl'
import { GRAFANA_DASHBOARD_URL } from '../config/metricsConfig'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import type { MetricsPeriod } from '../types/metrics.types'

const { Text } = Typography

/**
 * Опции выбора периода времени (AC5).
 */
const periodOptions = [
  { label: '5m', value: '5m' },
  { label: '15m', value: '15m' },
  { label: '1h', value: '1h' },
  { label: '6h', value: '6h' },
  { label: '24h', value: '24h' },
]

/**
 * Страница детальных метрик.
 *
 * Отображает (AC5):
 * - Summary metrics cards наверху
 * - Top routes table с per-route метриками
 * - Time range selector (5m, 15m, 1h, 6h, 24h)
 * - Кнопка "Open in Grafana"
 */
export function MetricsPage() {
  const [period, setPeriod] = useState<MetricsPeriod>('5m')
  const { user } = useAuth()

  // Story 16.8: управление auto-refresh
  const autoRefresh = useAutoRefresh()

  // Developer видит только свои маршруты (AC6 — фильтрация на backend)
  // Story 11.6: используем централизованный helper
  const isDeveloper = isDeveloperFn(user ?? undefined)

  const {
    data: summary,
    isLoading: summaryLoading,
    isError: summaryError,
    error: summaryErrorData,
    refetch: refetchSummary,
    dataUpdatedAt: summaryUpdatedAt,
  } = useMetricsSummary(period, { refetchInterval: autoRefresh.refetchInterval })

  // AC1: Top Routes реагирует на выбранный time range
  // Story 16.8: dynamic refetchInterval
  const {
    data: topRoutes,
    isLoading: topRoutesLoading,
    isError: topRoutesError,
  } = useTopRoutes('requests', 10, period, { refetchInterval: autoRefresh.refetchInterval })

  // Story 16.8 AC2: обновляем lastUpdated когда данные обновились
  // setLastUpdated стабилен (useCallback), поэтому безопасно добавлять в deps
  useEffect(() => {
    // summaryUpdatedAt — timestamp в ms, 0 означает что данных нет
    if (summaryUpdatedAt && summaryUpdatedAt > 0) {
      autoRefresh.setLastUpdated(new Date(summaryUpdatedAt))
    }
  }, [summaryUpdatedAt, autoRefresh.setLastUpdated])

  // Story 16.8 AC4: сброс таймера при смене Time Range
  const handlePeriodChange = (value: string | number) => {
    setPeriod(value as MetricsPeriod)
    autoRefresh.resetTimer()
    // React Query автоматически делает refetch при смене queryKey
  }

  const isLoading = summaryLoading
  const isError = summaryError || topRoutesError

  // Error state
  if (isError) {
    return (
      <Card>
        <Alert
          message="Метрики недоступны"
          description={summaryErrorData?.message || 'Не удалось загрузить данные метрик'}
          type="warning"
          showIcon
          action={
            <Button
              size="small"
              onClick={() => refetchSummary()}
              icon={<ReloadOutlined />}
              data-testid="metrics-page-retry-button"
            >
              Повторить
            </Button>
          }
          data-testid="metrics-page-error"
        />
      </Card>
    )
  }

  // Loading state
  if (isLoading) {
    return (
      <Card data-testid="metrics-page-loading">
        <div style={{ textAlign: 'center', padding: '48px' }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>Загрузка метрик...</div>
        </div>
      </Card>
    )
  }

  return (
    <div data-testid="metrics-page">
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 24 }}>
          <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              <AreaChartOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              <Typography.Title level={3} style={{ margin: 0 }}>
                Метрики
              </Typography.Title>
            </Space>
            <Button
              type="primary"
              href={GRAFANA_DASHBOARD_URL}
              target="_blank"
              icon={<LinkOutlined />}
              data-testid="open-grafana-button"
            >
              Открыть в Grafana
            </Button>
          </Space>
        </div>

        {/* Инфо-блок (Story 15.4) */}
        <PageInfoBlock pageKey="metrics" {...PAGE_DESCRIPTIONS.metrics} />

        {/* Time Range Selector и Auto-Refresh Control (Story 16.8) */}
        <div style={{ marginTop: 16 }}>
          <Space size="large" wrap>
            <Space>
              <span style={{ fontWeight: 500 }}>Период:</span>
              <Segmented
                options={periodOptions}
                value={period}
                onChange={handlePeriodChange}
                data-testid="time-range-selector"
              />
            </Space>
            {/* Story 16.8 AC1, AC3: Auto-refresh control с индикатором паузы */}
            <AutoRefreshControl
              enabled={autoRefresh.enabled}
              interval={autoRefresh.interval}
              lastUpdated={autoRefresh.lastUpdated}
              isPaused={autoRefresh.isPaused}
              onEnabledChange={autoRefresh.setEnabled}
              onIntervalChange={autoRefresh.setInterval}
            />
          </Space>
        </div>
      </Card>

      {/* Story 8.1: Health Check секция перед Summary Cards */}
      <HealthCheckSection />

      {/* Summary Metrics Cards */}
      {/* Story 16.4 AC2: responsive spans — xs: 24, sm: 12, md: 8, lg: 4 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-total-requests">
            <Statistic
              title="Всего запросов"
              value={summary?.totalRequests ?? 0}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-rps">
            <Statistic
              title="RPS"
              value={summary?.requestsPerSecond ?? 0}
              precision={1}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-avg-latency">
            <Statistic
              title="Средняя задержка"
              value={summary?.avgLatencyMs ?? 0}
              suffix="мс"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-p95">
            <Statistic
              title="P95 задержка"
              value={summary?.p95LatencyMs ?? 0}
              suffix="мс"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-error-rate">
            <Statistic
              title="Ошибки"
              value={(summary?.errorRate ?? 0) * 100}
              precision={2}
              suffix="%"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card data-testid="summary-card-active-routes">
            <Statistic
              title="Активных маршрутов"
              value={summary?.activeRoutes ?? 0}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
      </Row>

      {/* Top Routes Table */}
      <Card title="Топ маршрутов по запросам">
        {/* AC6: Индикатор для developer роли — видит только свои маршруты */}
        {isDeveloper && (
          <Alert
            message={
              <Space>
                <InfoCircleOutlined />
                <Text>Показаны только созданные вами маршруты</Text>
              </Space>
            }
            type="info"
            showIcon={false}
            style={{ marginBottom: 16 }}
            data-testid="developer-routes-notice"
          />
        )}
        <TopRoutesTable data={topRoutes ?? []} loading={topRoutesLoading} />
      </Card>
    </div>
  )
}

export default MetricsPage
