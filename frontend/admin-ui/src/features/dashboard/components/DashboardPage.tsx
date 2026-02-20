// Страница Dashboard с виджетом метрик (Story 6.5)
import { Button, Card, Typography, Space } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { MetricsWidget } from '@features/metrics'

const { Title, Text } = Typography

/**
 * Страница Dashboard
 *
 * Отображает:
 * - Виджет метрик сверху (AC1, Story 6.5)
 * - Приветствие с username и role
 * - Кнопку Logout
 */
export function DashboardPage() {
  const { user, logout, isLoading } = useAuth()

  // Обработчик выхода
  const handleLogout = async () => {
    await logout()
  }

  // Форматирование роли для отображения
  const formatRole = (role: string) => {
    return role.charAt(0).toUpperCase() + role.slice(1)
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Виджет метрик — видим для всех пользователей (AC6: developer видит read-only) */}
      <MetricsWidget />

      {/* Информация о пользователе */}
      <Card>
        <Title level={2}>Dashboard</Title>
        <Text>
          Welcome, <strong>{user?.username ?? 'User'}</strong>!
        </Text>
        <br />
        <Text type="secondary">
          Role: {user?.role ? formatRole(user.role) : 'Unknown'}
        </Text>
        <br />
        <br />
        <Button
          type="primary"
          danger
          icon={<LogoutOutlined />}
          onClick={handleLogout}
          loading={isLoading}
          data-testid="logout-button"
        >
          Logout
        </Button>
      </Card>
    </Space>
  )
}
