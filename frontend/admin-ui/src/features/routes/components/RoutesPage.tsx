// Страница управления маршрутами (Story 3.4, Story 15.4 — PageInfoBlock, Story 15.6 — унификация, Story 16.9 — OS-specific shortcuts, Story 16.10 — WorkflowIndicator)
import { useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Typography, Button, Space, Tooltip } from 'antd'
import { PlusOutlined, ApiOutlined, EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { RoutesTable } from './RoutesTable'
import { PageInfoBlock, WorkflowIndicator } from '@shared/components'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import { formatShortcut } from '@shared/utils'
import { useWorkflowIndicator } from '@shared/hooks'

const { Title } = Typography

/**
 * Страница управления маршрутами.
 *
 * Отображает таблицу маршрутов с фильтрацией и поиском.
 * Поддерживает keyboard shortcut ⌘+N (Ctrl+N) для создания нового маршрута.
 */
export function RoutesPage() {
  const navigate = useNavigate()
  // Story 16.10: Hook для управления видимостью WorkflowIndicator
  const { visible: workflowVisible, toggle: toggleWorkflow } = useWorkflowIndicator()

  /**
   * Обработчик создания нового маршрута.
   */
  const handleCreateRoute = useCallback(() => {
    navigate('/routes/new')
  }, [navigate])

  /**
   * Обработчик keyboard shortcut ⌘+N (Ctrl+N).
   */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Проверяем Ctrl+N или Cmd+N (для Mac)
      if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
        e.preventDefault()
        handleCreateRoute()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleCreateRoute])

  return (
    <Card>
      {/* Заголовок страницы с кнопкой создания (Story 15.6 — унификация) */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <ApiOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Маршруты
            </Title>
          </Space>
          <Space>
            {/* Story 16.10: Кнопка toggle для WorkflowIndicator (AC3) */}
            <Tooltip title={workflowVisible ? 'Скрыть workflow' : 'Показать workflow'}>
              <Button
                type="text"
                icon={workflowVisible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                onClick={toggleWorkflow}
                data-testid="workflow-toggle"
              />
            </Tooltip>
            {/* Story 16.9: OS-specific shortcut в tooltip (AC3) */}
            <Tooltip title={formatShortcut('N')}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleCreateRoute}
              >
                Новый маршрут
              </Button>
            </Tooltip>
          </Space>
        </Space>
      </div>

      {/* Story 16.10: WorkflowIndicator между header и PageInfoBlock (AC6) */}
      {workflowVisible && <WorkflowIndicator currentStep={3} />}

      {/* Инфо-блок (Story 15.4) */}
      <PageInfoBlock pageKey="routes" {...PAGE_DESCRIPTIONS.routes} />

      {/* Таблица маршрутов */}
      <RoutesTable />
    </Card>
  )
}
