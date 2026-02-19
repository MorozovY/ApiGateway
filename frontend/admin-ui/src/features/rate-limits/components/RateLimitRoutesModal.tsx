// Модальное окно со списком маршрутов, использующих Rate Limit политику (Story 5.4, AC7)
import { Modal, List, Typography, Spin, Tag, Empty } from 'antd'
import { Link } from 'react-router-dom'
import { useRoutesByRateLimitId } from '../hooks/useRateLimits'
import type { RateLimit } from '../types/rateLimit.types'
import { STATUS_COLORS, STATUS_LABELS } from '@shared/constants'
import type { RouteStatus } from '@/features/routes/types/route.types'

const { Text } = Typography

interface RateLimitRoutesModalProps {
  open: boolean
  rateLimit: RateLimit | null
  onClose: () => void
}

/**
 * Модальное окно, отображающее список маршрутов, использующих Rate Limit политику.
 *
 * Каждый маршрут — ссылка на его детальную страницу.
 */
function RateLimitRoutesModal({ open, rateLimit, onClose }: RateLimitRoutesModalProps) {
  // Загружаем маршруты только когда модальное окно открыто
  const { data: routes, isLoading } = useRoutesByRateLimitId(
    open && rateLimit ? rateLimit.id : undefined
  )

  return (
    <Modal
      title={`Routes using "${rateLimit?.name || ''}"`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={600}
    >
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : routes && routes.length > 0 ? (
        <List
          dataSource={routes}
          renderItem={(route) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Link to={`/routes/${route.id}`} onClick={onClose}>
                    {route.path}
                  </Link>
                }
                description={
                  <Text type="secondary" ellipsis>
                    {route.upstreamUrl}
                  </Text>
                }
              />
              <Tag color={STATUS_COLORS[route.status as RouteStatus]}>
                {STATUS_LABELS[route.status as RouteStatus]}
              </Tag>
            </List.Item>
          )}
        />
      ) : (
        <Empty description="Нет маршрутов, использующих эту политику" />
      )}
    </Modal>
  )
}

export default RateLimitRoutesModal
