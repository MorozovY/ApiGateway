// Индикатор прогресса генерации (Story 8.9, AC3)
import { Card, Row, Col, Statistic } from 'antd'
import {
  SendOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import type { LoadGeneratorState } from '../types/loadGenerator.types'

interface LoadGeneratorProgressProps {
  state: LoadGeneratorState
}

/**
 * Форматирует время в секундах в читаемый формат.
 */
function formatElapsed(startTime: number | null): string {
  if (!startTime) return '0s'
  const elapsed = Math.floor((Date.now() - startTime) / 1000)
  if (elapsed < 60) return `${elapsed}s`
  const minutes = Math.floor(elapsed / 60)
  const seconds = elapsed % 60
  return `${minutes}m ${seconds}s`
}

/**
 * Компонент отображения прогресса генерации нагрузки.
 *
 * Показывает в реальном времени:
 * - Отправленные запросы
 * - Успешные ответы
 * - Ошибки
 * - Время выполнения
 * - Среднее время ответа
 */
export function LoadGeneratorProgress({ state }: LoadGeneratorProgressProps) {
  return (
    <Card title="Progress" data-testid="load-generator-progress">
      <Row gutter={[16, 16]}>
        <Col span={4}>
          <Statistic
            title="Sent"
            value={state.sentCount}
            prefix={<SendOutlined />}
            valueStyle={{ color: '#1890ff' }}
            data-testid="stat-sent"
          />
        </Col>
        <Col span={4}>
          <Statistic
            title="Success"
            value={state.successCount}
            prefix={<CheckCircleOutlined />}
            valueStyle={{ color: '#52c41a' }}
            data-testid="stat-success"
          />
        </Col>
        <Col span={4}>
          <Statistic
            title="Errors"
            value={state.errorCount}
            prefix={<CloseCircleOutlined />}
            valueStyle={{ color: state.errorCount > 0 ? '#ff4d4f' : '#888' }}
            data-testid="stat-errors"
          />
        </Col>
        <Col span={4}>
          <Statistic
            title="Elapsed"
            value={formatElapsed(state.startTime)}
            prefix={<ClockCircleOutlined />}
            data-testid="stat-elapsed"
          />
        </Col>
        <Col span={8}>
          <Statistic
            title="Avg Response Time"
            value={state.averageResponseTime?.toFixed(0) ?? '-'}
            suffix={state.averageResponseTime ? 'ms' : ''}
            data-testid="stat-avg-response"
          />
        </Col>
      </Row>
      {state.lastError && (
        <div style={{ marginTop: 16, color: '#ff4d4f' }} data-testid="last-error">
          Last error: {state.lastError}
        </div>
      )}
    </Card>
  )
}

export default LoadGeneratorProgress
