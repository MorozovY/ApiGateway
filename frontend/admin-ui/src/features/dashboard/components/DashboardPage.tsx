// Страница Dashboard (Story 15.4 — добавлен PageInfoBlock)
import { Button, Card, Typography, Space } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'

const { Title, Text } = Typography

/**
 * Страница Dashboard
 *
 * Отображает приветствие с username, role и кнопку Logout.
 * Метрики доступны на странице /metrics (Story 8.2).
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
      {/* Информация о пользователе */}
      <Card>
        <Title level={2}>Dashboard</Title>

        {/* Инфо-блок после заголовка (Story 15.4) */}
        <PageInfoBlock pageKey="dashboard" {...PAGE_DESCRIPTIONS.dashboard} />
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
