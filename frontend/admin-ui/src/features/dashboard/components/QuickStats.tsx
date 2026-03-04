/**
 * Компонент быстрой статистики маршрутов для Dashboard (Story 16.2)
 *
 * Отображает 4 карточки со статусами маршрутов:
 * - Draft (черновики)
 * - Pending (на согласовании)
 * - Published (опубликованы)
 * - Rejected (отклонены)
 *
 * Role-based:
 * - DEVELOPER: показывает только свои маршруты
 * - SECURITY/ADMIN: показывает все маршруты системы
 */
import { Row, Col, Card, Statistic, Alert, Button, Spin } from 'antd'
import {
  FileTextOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDashboardSummary } from '../hooks/useDashboard'

// Цвета для статусов маршрутов (соответствуют RouteStatusTag)
const STATUS_COLORS = {
  draft: '#faad14', // warning/gold
  pending: '#1890ff', // blue
  published: '#52c41a', // success/green
  rejected: '#ff4d4f', // error/red
} as const

/**
 * Компонент QuickStats — карточки со статистикой маршрутов.
 *
 * AC1: Developer видит статистику по своим маршрутам
 * AC2/AC3: Security/Admin видят общую статистику
 * AC4: Loading state с Spin
 */
export function QuickStats() {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refresh } = useDashboardSummary()

  // Обработчик клика на карточку — переход на /routes с фильтром по статусу
  const handleCardClick = (status: string) => {
    navigate(`/routes?status=${status}`)
  }

  // Loading state (AC4)
  if (isLoading) {
    return (
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {[1, 2, 3, 4].map((i) => (
          <Col xs={24} sm={12} md={6} key={i}>
            <Card>
              <div
                style={{
                  height: 80,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Spin />
              </div>
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  // Error state
  if (isError) {
    return (
      <Alert
        type="error"
        message="Ошибка загрузки статистики"
        description={(error as Error)?.message || 'Не удалось загрузить данные'}
        action={
          <Button
            size="small"
            onClick={() => refresh()}
            icon={<ReloadOutlined />}
          >
            Повторить
          </Button>
        }
        style={{ marginBottom: 24 }}
      />
    )
  }

  const stats = data?.routesByStatus

  return (
    <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
      {/* Черновики */}
      <Col xs={24} sm={12} md={6}>
        <Card
          hoverable
          onClick={() => handleCardClick('draft')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Черновики"
            value={stats?.draft ?? 0}
            prefix={<FileTextOutlined style={{ color: STATUS_COLORS.draft }} />}
            valueStyle={{ color: STATUS_COLORS.draft }}
          />
        </Card>
      </Col>

      {/* На согласовании */}
      <Col xs={24} sm={12} md={6}>
        <Card
          hoverable
          onClick={() => handleCardClick('pending')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="На согласовании"
            value={stats?.pending ?? 0}
            prefix={<ClockCircleOutlined style={{ color: STATUS_COLORS.pending }} />}
            valueStyle={{ color: STATUS_COLORS.pending }}
          />
        </Card>
      </Col>

      {/* Опубликованы */}
      <Col xs={24} sm={12} md={6}>
        <Card
          hoverable
          onClick={() => handleCardClick('published')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Опубликованы"
            value={stats?.published ?? 0}
            prefix={<CheckCircleOutlined style={{ color: STATUS_COLORS.published }} />}
            valueStyle={{ color: STATUS_COLORS.published }}
          />
        </Card>
      </Col>

      {/* Отклонены */}
      <Col xs={24} sm={12} md={6}>
        <Card
          hoverable
          onClick={() => handleCardClick('rejected')}
          style={{ cursor: 'pointer' }}
        >
          <Statistic
            title="Отклонены"
            value={stats?.rejected ?? 0}
            prefix={<CloseCircleOutlined style={{ color: STATUS_COLORS.rejected }} />}
            valueStyle={{ color: STATUS_COLORS.rejected }}
          />
        </Card>
      </Col>
    </Row>
  )
}
