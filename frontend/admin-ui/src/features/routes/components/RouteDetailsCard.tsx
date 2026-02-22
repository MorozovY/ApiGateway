// Карточка с деталями маршрута (Story 3.6, расширена в Story 4.5, Story 5.5 и Story 10.3)
import { Card, Descriptions, Tag, Button, Space, Typography, Tooltip, Modal, Alert } from 'antd'
import { EditOutlined, CopyOutlined, ArrowLeftOutlined, SendOutlined, ExclamationCircleOutlined, RollbackOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { useCloneRoute, useSubmitRoute, useRollbackRoute } from '../hooks/useRoutes'
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
 * - Статус rejected: причина отклонения + кнопка "Редактировать и повторно отправить"
 * - Статус pending: сообщение "Ожидает одобрения Security"
 * - Actions: Back, Submit (для draft + owner), Edit (для draft + owner), Clone
 */
export function RouteDetailsCard({ route }: RouteDetailsCardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cloneMutation = useCloneRoute()
  const submitMutation = useSubmitRoute()
  const rollbackMutation = useRollbackRoute()

  // Проверка прав для разных действий (canEdit и canSubmit — одно условие: draft + owner)
  const canSubmit = route.status === 'draft' && route.createdBy === user?.userId
  const canResubmit = route.status === 'rejected' && route.createdBy === user?.userId
  const isPendingOwner = route.status === 'pending' && route.createdBy === user?.userId

  // Story 10.3: Rollback доступен только для Security/Admin на published маршрутах
  const canRollback = route.status === 'published' &&
    (user?.role === 'security' || user?.role === 'admin')

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

  /**
   * Показывает модальное окно подтверждения submit.
   */
  const handleSubmitClick = () => {
    Modal.confirm({
      title: 'Отправить на согласование',
      icon: <ExclamationCircleOutlined />,
      content: 'Маршрут будет отправлен в Security на проверку. Вы не сможете редактировать его до одобрения или отклонения.',
      okText: 'Отправить',
      cancelText: 'Отмена',
      onOk: async () => {
        try {
          await submitMutation.mutateAsync(route.id)
        } catch {
          // Ошибка уже обработана в useSubmitRoute hook (message.error)
        }
      },
    })
  }

  /**
   * Показывает модальное окно подтверждения rollback.
   * Story 10.3
   */
  const handleRollbackClick = () => {
    Modal.confirm({
      title: 'Откатить маршрут в Draft?',
      icon: <ExclamationCircleOutlined />,
      content: 'Маршрут будет удалён из gateway и вернётся в статус Draft.',
      okText: 'Откатить',
      okType: 'danger',
      cancelText: 'Отмена',
      onOk: async () => {
        try {
          await rollbackMutation.mutateAsync(route.id)
        } catch {
          // Ошибка уже обработана в useRollbackRoute hook (message.error)
        }
      },
    })
  }

  return (
    <>
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
            {/* Кнопка Rollback — только для published + Security/Admin (Story 10.3) */}
            {canRollback && (
              <Button
                type="primary"
                danger
                icon={<RollbackOutlined />}
                onClick={handleRollbackClick}
                loading={rollbackMutation.isPending}
              >
                Откатить в Draft
              </Button>
            )}
            {/* Кнопка Submit — только для draft + owner */}
            {canSubmit && (
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={handleSubmitClick}
                loading={submitMutation.isPending}
              >
                Отправить на согласование
              </Button>
            )}
            {/* Кнопка Edit — только для draft + owner */}
            {canSubmit && (
              <Button type="default" icon={<EditOutlined />} onClick={handleEdit}>
                Редактировать
              </Button>
            )}
            {/* Кнопка "Редактировать и повторно отправить" — только для rejected + owner */}
            {canResubmit && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={handleEdit}
              >
                Редактировать и повторно отправить
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
        {/* Блок pending — ожидает одобрения, только для owner */}
        {isPendingOwner && (
          <Alert
            type="info"
            message="Ожидает одобрения Security"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        {/* Блок rejection reason — только для rejected + owner */}
        {canResubmit && route.rejectionReason && (
          <Alert
            type="error"
            message="Маршрут отклонён"
            description={
              <>
                <div><strong>Причина:</strong> {route.rejectionReason}</div>
                {route.rejectorUsername && (
                  <div><strong>Отклонил:</strong> {route.rejectorUsername}</div>
                )}
              </>
            }
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

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

          {/* Rate Limit секция (Story 5.5) */}
          {route.rateLimit ? (
            <>
              <Descriptions.Item label="Rate Limit Policy">
                <strong>{route.rateLimit.name}</strong>
              </Descriptions.Item>
              <Descriptions.Item label="Requests per Second">
                {route.rateLimit.requestsPerSecond}
              </Descriptions.Item>
              <Descriptions.Item label="Burst Size">
                {route.rateLimit.burstSize}
              </Descriptions.Item>
            </>
          ) : (
            <Descriptions.Item label="Rate Limit">
              <Space direction="vertical" size={4}>
                <span style={{ color: '#8c8c8c' }}>No rate limiting configured</span>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Consider adding rate limiting for production routes
                </Typography.Text>
              </Space>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>
    </>
  )
}
