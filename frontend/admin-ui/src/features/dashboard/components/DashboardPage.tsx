// Страница Dashboard (placeholder)
import { Button, Card, Typography } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'

const { Title, Text } = Typography

/**
 * Страница Dashboard
 * Отображает приветствие с username и role
 * Содержит кнопку Logout
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
  )
}
