// Страница Dashboard (Story 15.4 — добавлен PageInfoBlock, Story 15.6 — унификация заголовка)
import { Button, Card, Typography, Space } from 'antd'
import { LogoutOutlined, DashboardOutlined } from '@ant-design/icons'
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
    <Card>
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <DashboardOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Dashboard
            </Title>
          </Space>
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
        </Space>
      </div>

      {/* Инфо-блок после заголовка (Story 15.4) */}
      <PageInfoBlock pageKey="dashboard" {...PAGE_DESCRIPTIONS.dashboard} />

      {/* Информация о пользователе */}
      <Text>
        Welcome, <strong>{user?.username ?? 'User'}</strong>!
      </Text>
      <br />
      <Text type="secondary">
        Role: {user?.role ? formatRole(user.role) : 'Unknown'}
      </Text>
    </Card>
  )
}
