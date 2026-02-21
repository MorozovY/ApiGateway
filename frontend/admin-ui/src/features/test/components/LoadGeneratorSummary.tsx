// Summary после остановки генерации (Story 8.9, AC4)
import { Card, Row, Col, Statistic, Button, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import type { LoadGeneratorSummary as SummaryType } from '../types/loadGenerator.types'

interface LoadGeneratorSummaryProps {
  summary: SummaryType
  onReset: () => void
}

/**
 * Форматирует миллисекунды в читаемый формат.
 */
function formatDuration(ms: number): string {
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(1)}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = (seconds % 60).toFixed(0)
  return `${minutes}m ${remainingSeconds}s`
}

/**
 * Компонент отображения итогов генерации нагрузки.
 *
 * Показывает после остановки:
 * - Total requests
 * - Duration
 * - Success rate
 * - Average response time
 */
export function LoadGeneratorSummary({ summary, onReset }: LoadGeneratorSummaryProps) {
  return (
    <Card title="Summary" data-testid="load-generator-summary">
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Statistic
            title="Total Requests"
            value={summary.totalRequests}
            data-testid="summary-total"
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="Duration"
            value={formatDuration(summary.durationMs)}
            data-testid="summary-duration"
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="Success Rate"
            value={summary.successRate.toFixed(1)}
            suffix="%"
            valueStyle={{
              color: summary.successRate >= 95 ? '#52c41a' : summary.successRate >= 80 ? '#faad14' : '#ff4d4f',
            }}
            data-testid="summary-success-rate"
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="Avg Response"
            value={summary.averageResponseTime?.toFixed(0) ?? '-'}
            suffix={summary.averageResponseTime ? 'ms' : ''}
            data-testid="summary-avg-response"
          />
        </Col>
      </Row>
      <div style={{ marginTop: 24 }}>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={onReset} data-testid="reset-button">
            Reset
          </Button>
        </Space>
      </div>
    </Card>
  )
}

export default LoadGeneratorSummary
