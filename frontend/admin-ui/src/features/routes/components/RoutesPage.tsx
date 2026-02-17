// Страница управления маршрутами (Story 3.4)
import { useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Typography, Button, Space, Tooltip } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { RoutesTable } from './RoutesTable'

const { Title } = Typography

/**
 * Страница управления маршрутами.
 *
 * Отображает таблицу маршрутов с фильтрацией и поиском.
 * Поддерживает keyboard shortcut ⌘+N (Ctrl+N) для создания нового маршрута.
 */
export function RoutesPage() {
  const navigate = useNavigate()

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
      {/* Заголовок страницы с кнопкой создания */}
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Routes</Title>
        <Tooltip title="Ctrl+N">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleCreateRoute}
          >
            New Route
          </Button>
        </Tooltip>
      </Space>

      {/* Таблица маршрутов */}
      <RoutesTable />
    </Card>
  )
}
