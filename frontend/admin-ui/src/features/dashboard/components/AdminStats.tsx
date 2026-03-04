/**
 * Компонент дополнительной статистики для Admin (Story 16.2, AC3)
 *
 * Отображает:
 * - Общее количество пользователей
 * - Количество consumers (API клиентов)
 * - Статус системы (здоровье)
 *
 * Доступен только для роли ADMIN.
 */
import { Row, Col, Card, Statistic, Badge, Space } from 'antd'
import {
  UserOutlined,
  ApiOutlined,
  HeartOutlined,
  ExclamationCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDashboardSummary } from '../hooks/useDashboard'
import { useAuth } from '@features/auth'

// Конфигурация статусов здоровья системы
const HEALTH_CONFIG = {
  healthy: {
    status: 'success' as const,
    text: 'Все сервисы работают',
    icon: <HeartOutlined style={{ color: '#52c41a' }} />,
    color: '#52c41a',
  },
  degraded: {
    status: 'warning' as const,
    text: 'Частичные проблемы',
    icon: <ExclamationCircleOutlined style={{ color: '#faad14' }} />,
    color: '#faad14',
  },
  down: {
    status: 'error' as const,
    text: 'Система недоступна',
    icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    color: '#ff4d4f',
  },
  unknown: {
    status: 'default' as const,
    text: 'Статус неизвестен',
    icon: <ExclamationCircleOutlined style={{ color: '#999' }} />,
    color: '#999',
  },
}

/**
 * Компонент AdminStats — дополнительная статистика для администраторов.
 *
 * Отображается только для пользователей с ролью ADMIN.
 */
export function AdminStats() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data, isLoading } = useDashboardSummary()

  // Показывать только для Admin
  if (user?.role !== 'admin') {
    return null
  }

  // Если данные ещё не загружены или нет специфичных для админа полей
  if (isLoading || data?.totalUsers === undefined) {
    return null
  }

  const healthStatus = data.systemHealth || 'unknown'
  const healthConfig = HEALTH_CONFIG[healthStatus] || HEALTH_CONFIG.unknown

  return (
    <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
      {/* Количество пользователей */}
      <Col xs={24} sm={12} md={8}>
        <Card
          hoverable
          onClick={() => navigate('/users')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Пользователей"
            value={data.totalUsers}
            prefix={<UserOutlined style={{ color: '#722ed1' }} />}
            valueStyle={{ color: '#722ed1' }}
          />
        </Card>
      </Col>

      {/* Количество consumers */}
      <Col xs={24} sm={12} md={8}>
        <Card
          hoverable
          onClick={() => navigate('/consumers')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="API Consumers"
            value={data.totalConsumers ?? '—'}
            prefix={<ApiOutlined style={{ color: '#13c2c2' }} />}
            valueStyle={{ color: '#13c2c2' }}
          />
        </Card>
      </Col>

      {/* Здоровье системы */}
      <Col xs={24} sm={24} md={8}>
        <Card
          hoverable
          onClick={() => navigate('/metrics')}
          style={{ cursor: 'pointer' }}
        >
          <Space direction="vertical" size={0}>
            <Space>
              {healthConfig.icon}
              <span style={{ color: '#999', fontSize: 14 }}>Статус системы</span>
            </Space>
            <Badge
              status={healthConfig.status}
              text={
                <span style={{ fontSize: 20, fontWeight: 500, color: healthConfig.color }}>
                  {healthConfig.text}
                </span>
              }
            />
          </Space>
        </Card>
      </Col>
    </Row>
  )
}
