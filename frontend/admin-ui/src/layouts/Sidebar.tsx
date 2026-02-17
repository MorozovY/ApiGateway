// Боковая панель навигации (Story 2.6 — добавлен Users для admin)
import { useMemo } from 'react'
import { Layout, Menu } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  ApiOutlined,
  DashboardOutlined,
  SafetyOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useAuth } from '@features/auth'
import type { ItemType } from 'antd/es/menu/interface'

const { Sider } = Layout

/**
 * Базовые пункты меню для всех пользователей.
 */
const baseMenuItems: ItemType[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Dashboard',
  },
  {
    key: '/routes',
    icon: <ApiOutlined />,
    label: 'Routes',
  },
  {
    key: '/rate-limits',
    icon: <SafetyOutlined />,
    label: 'Rate Limits',
  },
  {
    key: '/approvals',
    icon: <CheckCircleOutlined />,
    label: 'Approvals',
  },
  {
    key: '/audit',
    icon: <AuditOutlined />,
    label: 'Audit Logs',
  },
]

/**
 * Пункт меню Users — только для admin.
 */
const usersMenuItem: ItemType = {
  key: '/users',
  icon: <TeamOutlined />,
  label: 'Users',
}

function Sidebar() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()

  // Формируем меню на основе роли пользователя
  const menuItems = useMemo(() => {
    const items = [...baseMenuItems]

    // Добавляем Users только для admin
    if (user?.role === 'admin') {
      // Вставляем после Dashboard (на второе место)
      items.splice(1, 0, usersMenuItem)
    }

    return items
  }, [user?.role])

  return (
    <Sider theme="light" width={220}>
      <div className="logo" style={{ padding: '16px', textAlign: 'center' }}>
        <SafetyOutlined style={{ fontSize: 24, marginRight: 8 }} />
        <span style={{ fontSize: 18, fontWeight: 'bold' }}>API Gateway</span>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
      />
    </Sider>
  )
}

export default Sidebar
