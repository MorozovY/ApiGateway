/**
 * Компонент быстрых действий для Dashboard (Story 16.2)
 *
 * Отображает кнопки быстрых действий в зависимости от роли:
 * - DEVELOPER: "Создать маршрут", "Мои маршруты"
 * - SECURITY: "Согласования", "Журнал аудита"
 * - ADMIN: все основные действия
 */
import { Card, Space, Button } from 'antd'
import {
  PlusOutlined,
  UnorderedListOutlined,
  SafetyCertificateOutlined,
  AuditOutlined,
  UserOutlined,
  ApiOutlined,
  DashboardOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@features/auth'

/**
 * Конфигурация действий для каждой роли
 */
interface QuickAction {
  key: string
  label: string
  icon: React.ReactNode
  path: string
  primary?: boolean
}

const DEVELOPER_ACTIONS: QuickAction[] = [
  {
    key: 'create-route',
    label: 'Создать маршрут',
    icon: <PlusOutlined />,
    path: '/routes/new',
    primary: true,
  },
  {
    key: 'my-routes',
    label: 'Мои маршруты',
    icon: <UnorderedListOutlined />,
    path: '/routes',
  },
]

const SECURITY_ACTIONS: QuickAction[] = [
  {
    key: 'approvals',
    label: 'Согласования',
    icon: <SafetyCertificateOutlined />,
    path: '/approvals',
    primary: true,
  },
  {
    key: 'audit',
    label: 'Журнал аудита',
    icon: <AuditOutlined />,
    path: '/audit',
  },
]

const ADMIN_ACTIONS: QuickAction[] = [
  {
    key: 'create-route',
    label: 'Создать маршрут',
    icon: <PlusOutlined />,
    path: '/routes/new',
    primary: true,
  },
  {
    key: 'approvals',
    label: 'Согласования',
    icon: <SafetyCertificateOutlined />,
    path: '/approvals',
  },
  {
    key: 'users',
    label: 'Пользователи',
    icon: <UserOutlined />,
    path: '/users',
  },
  {
    key: 'consumers',
    label: 'Потребители',
    icon: <ApiOutlined />,
    path: '/consumers',
  },
  {
    key: 'metrics',
    label: 'Метрики',
    icon: <DashboardOutlined />,
    path: '/metrics',
  },
  {
    key: 'rate-limits',
    label: 'Лимиты',
    icon: <SettingOutlined />,
    path: '/rate-limits',
  },
]

/**
 * Получение списка действий для роли
 */
function getActionsForRole(role: string | undefined): QuickAction[] {
  switch (role) {
    case 'developer':
      return DEVELOPER_ACTIONS
    case 'security':
      return SECURITY_ACTIONS
    case 'admin':
      return ADMIN_ACTIONS
    default:
      return DEVELOPER_ACTIONS
  }
}

/**
 * Компонент QuickActions — кнопки быстрых действий.
 *
 * AC1: Developer — "Создать маршрут", "Мои маршруты"
 * AC2: Security — "Согласования", "Журнал аудита"
 * AC3: Admin — все основные действия
 */
export function QuickActions() {
  const navigate = useNavigate()
  const { user } = useAuth()

  const actions = getActionsForRole(user?.role)

  return (
    <Card
      title={
        <Space>
          <PlusOutlined />
          <span>Быстрые действия</span>
        </Space>
      }
      style={{ marginBottom: 24 }}
    >
      <Space wrap size="middle">
        {actions.map((action) => (
          <Button
            key={action.key}
            type={action.primary ? 'primary' : 'default'}
            icon={action.icon}
            onClick={() => navigate(action.path)}
          >
            {action.label}
          </Button>
        ))}
      </Space>
    </Card>
  )
}
