// Главная страница Test с генератором нагрузки (Story 8.9)
import { Space, Typography } from 'antd'
import { ExperimentOutlined } from '@ant-design/icons'
import { useLoadGenerator } from '../hooks/useLoadGenerator'
import { LoadGeneratorForm } from './LoadGeneratorForm'
import { LoadGeneratorProgress } from './LoadGeneratorProgress'
import { LoadGeneratorSummary } from './LoadGeneratorSummary'

const { Title } = Typography

/**
 * Страница Test с генератором нагрузки.
 *
 * Позволяет:
 * - Выбрать опубликованный маршрут
 * - Настроить RPS и duration
 * - Запустить генерацию HTTP запросов через gateway-core
 * - Отслеживать прогресс в реальном времени
 * - Просмотреть итоги после остановки
 */
export function TestPage() {
  const { state, start, stop, reset, summary } = useLoadGenerator()

  const isRunning = state.status === 'running'
  const showProgress = state.status === 'running' || (state.status === 'stopped' && !summary)
  const showSummary = state.status === 'stopped' && summary !== null

  return (
    <div data-testid="test-page">
      <Space align="center" style={{ marginBottom: 24 }}>
        <ExperimentOutlined style={{ fontSize: 24 }} />
        <Title level={2} style={{ margin: 0 }}>
          Test Load Generator
        </Title>
      </Space>

      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <LoadGeneratorForm
          isRunning={isRunning}
          onStart={start}
          onStop={stop}
        />

        {showProgress && <LoadGeneratorProgress state={state} />}

        {showSummary && <LoadGeneratorSummary summary={summary} onReset={reset} />}
      </Space>
    </div>
  )
}

export default TestPage
