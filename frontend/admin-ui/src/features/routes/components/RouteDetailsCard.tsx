// Карточка с деталями маршрута (Story 3.6)
import { Card, Descriptions, Tag, Button, Space, Typography, Tooltip } from 'antd'
import { EditOutlined, CopyOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { useCloneRoute } from '../hooks/useRoutes'
import type { Route } from '../types/route.types'
import { useAuth } from '@features/auth'
import { STATUS_COLORS, STATUS_LABELS, METHOD_COLORS } from '@shared/constants'

// Настройка dayjs для относительного времени
dayjs.extend(relativeTime)
dayjs.locale('ru')

const { Title } = Typography

interface RouteDetailsCardProps {
  route: Route
}

/**
 * Карточка с полной информацией о маршруте.
 *
 * Отображает:
 * - Header с path и status badge
 * - Configuration: upstream URL, methods
 * - Metadata: автор, даты создания/обновления
 * - Rate Limit: информация если назначен
 * - Actions: Back, Edit (для draft + owner), Clone
 */
export function RouteDetailsCard({ route }: RouteDetailsCardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cloneMutation = useCloneRoute()

  // Проверка: можно ли редактировать (draft + owner)
  const canEdit = route.status === 'draft' && route.createdBy === user?.userId

  /**
   * Переход на страницу редактирования.
   */
  const handleEdit = () => {
    navigate(`/routes/${route.id}/edit`)
  }

  /**
   * Клонирование маршрута и переход на редактирование клона.
   * Ошибки обрабатываются в useCloneRoute hook (показывается message.error).
   */
  const handleClone = async () => {
    try {
      const cloned = await cloneMutation.mutateAsync(route.id)
      navigate(`/routes/${cloned.id}/edit`)
    } catch {
      // Ошибка уже обработана в useCloneRoute hook (message.error)
      // Здесь просто предотвращаем unhandled rejection
    }
  }

  /**
   * Возврат к списку маршрутов.
   */
  const handleBack = () => {
    navigate('/routes')
  }

  return (
    <Card
      title={
        <Space align="center">
          <Title level={4} style={{ margin: 0 }}>{route.path}</Title>
          <Tag color={STATUS_COLORS[route.status]}>{STATUS_LABELS[route.status]}</Tag>
        </Space>
      }
      extra={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>
            Назад
          </Button>
          {canEdit && (
            <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>
              Редактировать
            </Button>
          )}
          <Button
            icon={<CopyOutlined />}
            onClick={handleClone}
            loading={cloneMutation.isPending}
          >
            Клонировать
          </Button>
        </Space>
      }
    >
      <Descriptions column={1} bordered>
        {/* Configuration секция */}
        <Descriptions.Item label="Upstream URL">{route.upstreamUrl}</Descriptions.Item>
        <Descriptions.Item label="HTTP Methods">
          <Space size={4} wrap>
            {route.methods.map(method => (
              <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
                {method}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="Описание">
          {route.description || '—'}
        </Descriptions.Item>

        {/* Metadata секция */}
        <Descriptions.Item label="Автор">{route.creatorUsername || '—'}</Descriptions.Item>
        <Descriptions.Item label="Создан">
          <Tooltip title={dayjs(route.createdAt).format('DD.MM.YYYY HH:mm')}>
            {dayjs(route.createdAt).fromNow()}
          </Tooltip>
        </Descriptions.Item>
        <Descriptions.Item label="Обновлён">
          <Tooltip title={dayjs(route.updatedAt).format('DD.MM.YYYY HH:mm')}>
            {dayjs(route.updatedAt).fromNow()}
          </Tooltip>
        </Descriptions.Item>

        {/* Rate Limit секция — если назначен */}
        {/* TODO: После реализации API rate limits (Epic 4) загружать название и лимиты политики */}
        {route.rateLimitId && (
          <Descriptions.Item label="Rate Limit">
            <Tag color="blue">Политика назначена</Tag>
          </Descriptions.Item>
        )}
      </Descriptions>
    </Card>
  )
}
