// Health Check секция для страницы Metrics (Story 8.1)
import { Card, Row, Col, Tag, Button, Tooltip, Spin, Typography, Space, Alert } from 'antd'
import { ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import { useThemeContext } from '@/shared/providers/ThemeProvider'
import { useHealth } from '../hooks/useHealth'
import type { ServiceHealth } from '../types/metrics.types'

const { Text } = Typography

/**
 * Цвета для карточек в зависимости от статуса и темы.
 */
const STATUS_COLORS = {
  light: {
    up: { border: '#b7eb8f', background: '#f6ffed' },
    down: { border: '#ffa39e', background: '#fff2f0' },
  },
  dark: {
    up: { border: '#49aa19', background: 'rgba(73, 170, 25, 0.15)' },
    down: { border: '#a61d24', background: 'rgba(166, 29, 36, 0.15)' },
  },
}

/**
 * Конфигурация отображения сервисов.
 * Определяет порядок и отображаемые имена.
 * Nginx первый (order: 0), так как это entry point системы (reverse proxy).
 * Всего 7 сервисов: nginx, gateway-core, gateway-admin, postgresql, redis, prometheus, grafana.
 */
const SERVICE_CONFIG: Record<string, { displayName: string; order: number }> = {
  'nginx': { displayName: 'Nginx', order: 0 },
  'gateway-core': { displayName: 'Gateway Core', order: 1 },
  'gateway-admin': { displayName: 'Gateway Admin', order: 2 },
  'postgresql': { displayName: 'PostgreSQL', order: 3 },
  'redis': { displayName: 'Redis', order: 4 },
  'prometheus': { displayName: 'Prometheus', order: 5 },
  'grafana': { displayName: 'Grafana', order: 6 },
}

/**
 * Форматирует timestamp в локализованное время.
 */
function formatTimestamp(isoString: string): string {
  try {
    const date = new Date(isoString)
    return date.toLocaleTimeString('ru-RU', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return isoString
  }
}

/**
 * Карточка статуса отдельного сервиса.
 */
interface ServiceCardProps {
  service: ServiceHealth
  isDark: boolean
}

function ServiceCard({ service, isDark }: ServiceCardProps) {
  const isUp = service.status === 'UP'
  const config = SERVICE_CONFIG[service.name] || { displayName: service.name, order: 999 }

  // Выбираем цвета в зависимости от темы
  const themeColors = isDark ? STATUS_COLORS.dark : STATUS_COLORS.light
  const colors = isUp ? themeColors.up : themeColors.down

  const statusIcon = isUp ? (
    <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
  ) : (
    <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
  )

  const cardContent = (
    <Card
      size="small"
      data-testid={`health-card-${service.name}`}
      styles={{ body: { padding: '8px 12px' } }}
      style={{
        borderColor: colors.border,
        backgroundColor: colors.background,
      }}
    >
      <Space size="small" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space size={4}>
          {statusIcon}
          <Text strong style={{ fontSize: 13 }}>{config.displayName}</Text>
        </Space>
        <Tag
          color={isUp ? 'success' : 'error'}
          style={{ margin: 0, fontSize: 11 }}
          data-testid={`service-status-${service.name}`}
        >
          {isUp ? 'UP' : 'DOWN'}
        </Tag>
      </Space>
    </Card>
  )

  // AC2: При DOWN показываем детали ошибки в Tooltip
  if (!isUp && service.details) {
    return (
      <Tooltip
        title={service.details}
        placement="bottom"
        data-testid={`health-tooltip-${service.name}`}
      >
        {cardContent}
      </Tooltip>
    )
  }

  return cardContent
}

/**
 * Секция Health Check для страницы Metrics.
 *
 * AC1: Отображает статус всех сервисов (UP/DOWN)
 * AC2: Показывает детали ошибки при hover/expand для DOWN сервисов
 * AC3: Auto-refresh каждые 30 секунд + кнопка ручного обновления
 * AC4: Responsive layout (4 → 2 → 1 колонка)
 */
export function HealthCheckSection() {
  const { isDark } = useThemeContext()
  const { data, isLoading, isError, error, refresh, isFetching } = useHealth()

  // Сортируем сервисы по заданному порядку
  const sortedServices = data?.services?.slice().sort((a, b) => {
    const orderA = SERVICE_CONFIG[a.name]?.order ?? 999
    const orderB = SERVICE_CONFIG[b.name]?.order ?? 999
    return orderA - orderB
  })

  // Loading state
  if (isLoading) {
    return (
      <Card
        title="Health Check"
        style={{ marginBottom: 16 }}
        data-testid="health-section-loading"
      >
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
          <div style={{ marginTop: 8 }}>Проверка сервисов...</div>
        </div>
      </Card>
    )
  }

  // Error state
  if (isError) {
    return (
      <Card
        title="Health Check"
        style={{ marginBottom: 16 }}
        data-testid="health-section-error"
      >
        <Alert
          message="Не удалось получить статус сервисов"
          description={error?.message || 'Попробуйте обновить позже'}
          type="warning"
          showIcon
          action={
            <Button
              size="small"
              onClick={() => refresh()}
              icon={<ReloadOutlined />}
            >
              Повторить
            </Button>
          }
        />
      </Card>
    )
  }

  return (
    <Card
      title={
        <Space>
          <span>Health Check</span>
          {data?.timestamp && (
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              (обновлено: {formatTimestamp(data.timestamp)})
            </Text>
          )}
        </Space>
      }
      extra={
        // AC3: Кнопка ручного обновления
        <Button
          type="text"
          icon={<ReloadOutlined spin={isFetching} />}
          onClick={() => refresh()}
          data-testid="health-refresh-button"
          title="Обновить статус"
        >
          Обновить
        </Button>
      }
      style={{ marginBottom: 16 }}
      data-testid="health-section"
    >
      {/* AC4: Responsive layout — 6 колонок на desktop, 3 на tablet, 2 на mobile */}
      <Row gutter={[12, 12]}>
        {sortedServices?.map((service) => (
          <Col
            key={service.name}
            xs={12}
            sm={8}
            lg={4}
          >
            <ServiceCard service={service} isDark={isDark} />
          </Col>
        ))}
      </Row>
    </Card>
  )
}

export default HealthCheckSection
