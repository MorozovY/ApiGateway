// Главная страница Test с генератором нагрузки (Story 8.9; Story 15.4 — добавлен PageInfoBlock, Story 15.6 — унификация заголовка)
import { Space, Typography, Card } from 'antd'
import { ExperimentOutlined } from '@ant-design/icons'
import { useLoadGenerator } from '../hooks/useLoadGenerator'
import { LoadGeneratorForm } from './LoadGeneratorForm'
import { LoadGeneratorProgress } from './LoadGeneratorProgress'
import { LoadGeneratorSummary } from './LoadGeneratorSummary'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'

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
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 24 }}>
          <Space align="center">
            <ExperimentOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Тестирование
            </Title>
          </Space>
        </div>

        {/* Инфо-блок (Story 15.4) */}
        <PageInfoBlock pageKey="test" {...PAGE_DESCRIPTIONS.test} />
      </Card>

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
