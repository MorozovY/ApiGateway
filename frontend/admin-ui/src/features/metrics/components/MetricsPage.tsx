// Страница детальных метрик (Story 6.5, AC5)
import { useState } from 'react'
import { Card, Row, Col, Statistic, Segmented, Button, Space, Alert, Spin, Typography } from 'antd'
import { LinkOutlined, ReloadOutlined, InfoCircleOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { useMetricsSummary, useTopRoutes } from '../hooks/useMetrics'
import TopRoutesTable from './TopRoutesTable'
import { GRAFANA_DASHBOARD_URL } from '../config/metricsConfig'
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

  // Developer видит только свои маршруты (AC6 — фильтрация на backend)
  const isDeveloper = user?.role === 'developer'

  const {
    data: summary,
    isLoading: summaryLoading,
    isError: summaryError,
    error: summaryErrorData,
    refetch: refetchSummary,
  } = useMetricsSummary(period)

  const {
    data: topRoutes,
    isLoading: topRoutesLoading,
    isError: topRoutesError,
  } = useTopRoutes('requests', 10)

  const isLoading = summaryLoading
  const isError = summaryError || topRoutesError

  // Error state
  if (isError) {
    return (
      <Card>
        <Alert
          message="Metrics unavailable"
          description={summaryErrorData?.message || 'Could not load metrics data'}
          type="warning"
          showIcon
          action={
            <Button
              size="small"
              onClick={() => refetchSummary()}
              icon={<ReloadOutlined />}
              data-testid="metrics-page-retry-button"
            >
              Retry
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
          <div style={{ marginTop: 16 }}>Loading metrics...</div>
        </div>
      </Card>
    )
  }

  return (
    <div data-testid="metrics-page">
      {/* Header с Time Range Selector и Grafana кнопкой */}
      <Card style={{ marginBottom: 16 }}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <div>
            <span style={{ marginRight: 12, fontWeight: 500 }}>Time Range:</span>
            <Segmented
              options={periodOptions}
              value={period}
              onChange={(value) => setPeriod(value as MetricsPeriod)}
              data-testid="time-range-selector"
            />
          </div>
          <Button
            type="primary"
            href={GRAFANA_DASHBOARD_URL}
            target="_blank"
            icon={<LinkOutlined />}
            data-testid="open-grafana-button"
          >
            Open in Grafana
          </Button>
        </Space>
      </Card>

      {/* Summary Metrics Cards */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <Card data-testid="summary-card-total-requests">
            <Statistic
              title="Total Requests"
              value={summary?.totalRequests ?? 0}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card data-testid="summary-card-rps">
            <Statistic
              title="RPS"
              value={summary?.requestsPerSecond ?? 0}
              precision={1}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card data-testid="summary-card-avg-latency">
            <Statistic
              title="Avg Latency"
              value={summary?.avgLatencyMs ?? 0}
              suffix="ms"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card data-testid="summary-card-p95">
            <Statistic
              title="P95 Latency"
              value={summary?.p95LatencyMs ?? 0}
              suffix="ms"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card data-testid="summary-card-error-rate">
            <Statistic
              title="Error Rate"
              value={(summary?.errorRate ?? 0) * 100}
              precision={2}
              suffix="%"
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card data-testid="summary-card-active-routes">
            <Statistic
              title="Active Routes"
              value={summary?.activeRoutes ?? 0}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
      </Row>

      {/* Top Routes Table */}
      <Card title="Top Routes by Requests">
        {/* AC6: Индикатор для developer роли — видит только свои маршруты */}
        {isDeveloper && (
          <Alert
            message={
              <Space>
                <InfoCircleOutlined />
                <Text>Showing only routes you created</Text>
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
