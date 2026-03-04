/**
 * Компонент списка последних действий для Dashboard (Story 16.2)
 *
 * Отображает последние действия пользователя с маршрутами.
 *
 * Role-based фильтрация (на backend):
 * - DEVELOPER: только свои действия
 * - SECURITY: только approve/reject действия
 * - ADMIN: все действия
 */
import { Card, List, Typography, Tag, Alert, Button, Empty, Spin, Space } from 'antd'
import {
  HistoryOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  RocketOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useRecentActivity } from '../hooks/useDashboard'
import type { ActivityItem } from '../types/dashboard.types'

const { Text, Link } = Typography

// Конфигурация действий для отображения
const ACTION_CONFIG: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  created: { label: 'Создан', color: 'green', icon: <PlusOutlined /> },
  updated: { label: 'Изменён', color: 'blue', icon: <EditOutlined /> },
  approved: { label: 'Одобрен', color: 'success', icon: <CheckCircleOutlined /> },
  rejected: { label: 'Отклонён', color: 'error', icon: <CloseCircleOutlined /> },
  published: { label: 'Опубликован', color: 'purple', icon: <RocketOutlined /> },
  deleted: { label: 'Удалён', color: 'default', icon: <DeleteOutlined /> },
  submitted: { label: 'На согласование', color: 'processing', icon: <HistoryOutlined /> },
}

/**
 * Форматирование времени в относительном формате (X минут назад)
 */
function formatRelativeTime(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return 'только что'
  if (minutes < 60) return `${minutes} мин. назад`
  if (hours < 24) return `${hours} ч. назад`
  if (days < 7) return `${days} дн. назад`

  // Для дат старше недели показываем полную дату
  return date.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

interface RecentActivityProps {
  /** Количество отображаемых записей (default: 5) */
  limit?: number
}

/**
 * Компонент RecentActivity — список последних действий.
 */
export function RecentActivity({ limit = 5 }: RecentActivityProps) {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refresh } = useRecentActivity(limit)

  // Обработчик клика на элемент — переход к маршруту
  const handleItemClick = (item: ActivityItem) => {
    if (item.entityType === 'route') {
      navigate(`/routes/${item.entityId}`)
    }
  }

  // Отображение одного элемента активности
  const renderActivityItem = (item: ActivityItem) => {
    const actionConfig = ACTION_CONFIG[item.action] || {
      label: item.action,
      color: 'default',
      icon: <HistoryOutlined />,
    }

    return (
      <List.Item>
        <List.Item.Meta
          avatar={
            <Tag color={actionConfig.color} icon={actionConfig.icon}>
              {actionConfig.label}
            </Tag>
          }
          title={
            <Space>
              <Link onClick={() => handleItemClick(item)}>
                {item.entityName || item.entityId}
              </Link>
            </Space>
          }
          description={
            <Space size="small">
              <Text type="secondary">{item.performedBy}</Text>
              <Text type="secondary">·</Text>
              <Text type="secondary">{formatRelativeTime(item.performedAt)}</Text>
            </Space>
          }
        />
      </List.Item>
    )
  }

  return (
    <Card
      title={
        <Space>
          <HistoryOutlined />
          <span>Последние действия</span>
        </Space>
      }
      extra={
        <Button
          type="text"
          icon={<ReloadOutlined />}
          onClick={() => refresh()}
          loading={isLoading}
        />
      }
      style={{ marginBottom: 24 }}
    >
      {isLoading && !data && (
        <div
          style={{
            padding: 40,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Spin />
        </div>
      )}

      {isError && (
        <Alert
          type="error"
          message="Ошибка загрузки"
          description={(error as Error)?.message}
          action={
            <Button size="small" onClick={() => refresh()} icon={<ReloadOutlined />}>
              Повторить
            </Button>
          }
        />
      )}

      {!isLoading && !isError && data && (
        <>
          {data.items.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="Нет действий для отображения"
            />
          ) : (
            <List
              itemLayout="horizontal"
              dataSource={data.items}
              renderItem={renderActivityItem}
            />
          )}
        </>
      )}
    </Card>
  )
}
