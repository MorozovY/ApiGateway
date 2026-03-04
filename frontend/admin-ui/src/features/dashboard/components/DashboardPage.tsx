// Страница Dashboard (Story 15.4 — добавлен PageInfoBlock, Story 15.6 — унификация заголовка, Story 16.2 — наполнение контентом)
import { Card, Typography, Space } from 'antd'
import { DashboardOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import { ROLE_LABELS, type UserRole } from '@shared/constants'
import { QuickStats } from './QuickStats'
import { RecentActivity } from './RecentActivity'
import { AdminStats } from './AdminStats'
import { PendingApprovals } from './PendingApprovals'

const { Title, Text } = Typography

/**
 * Страница Dashboard (Story 16.2)
 *
 * Отображает:
 * - Приветствие с username и role
 * - PendingApprovals (для Security/Admin)
 * - QuickStats — статистика маршрутов по статусам
 * - AdminStats — дополнительная статистика (только Admin)
 * - RecentActivity — последние действия
 *
 * Logout доступен через dropdown в header (Story 16.3).
 * Метрики доступны на странице /metrics (Story 8.2).
 */
export function DashboardPage() {
  const { user } = useAuth()

  // Форматирование роли для отображения на русском (Story 16.1 — использует централизованные ROLE_LABELS)
  const formatRole = (role: string) => {
    return ROLE_LABELS[role as UserRole] || role.charAt(0).toUpperCase() + role.slice(1)
  }

  return (
    <div>
      <Card style={{ marginBottom: 24 }}>
        {/* Заголовок страницы (Story 15.6 — унификация) */}
        <div style={{ marginBottom: 24 }}>
          <Space>
            <DashboardOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Главная
            </Title>
          </Space>
        </div>

        {/* Инфо-блок после заголовка (Story 15.4) */}
        <PageInfoBlock pageKey="dashboard" {...PAGE_DESCRIPTIONS.dashboard} />

        {/* Информация о пользователе */}
        <Text>
          Добро пожаловать, <strong>{user?.username ?? 'Пользователь'}</strong>!
        </Text>
        <br />
        <Text type="secondary">
          Роль: {user?.role ? formatRole(user.role) : 'Неизвестно'}
        </Text>
      </Card>

      {/* Уведомление о pending approvals для Security/Admin (AC2) */}
      <PendingApprovals />

      {/* Быстрая статистика маршрутов (AC1) */}
      <QuickStats />

      {/* Дополнительная статистика для Admin (AC3) */}
      <AdminStats />

      {/* Последние действия (AC1, AC2) */}
      <RecentActivity limit={5} />
    </div>
  )
}
