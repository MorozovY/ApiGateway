// Боковая панель навигации (Story 2.6 — Users для admin, Story 4.6 — Badge для pending, Story 6.5 — Metrics, Story 7.6 — Collapsible + Integrations)
import { useMemo, useState } from 'react'
import { Layout, Menu, Badge, Button, Tooltip } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  ApiOutlined,
  DashboardOutlined,
  SafetyOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  TeamOutlined,
  AreaChartOutlined,
  ClusterOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { usePendingRoutesCount } from '@features/approval'
import type { ItemType } from 'antd/es/menu/interface'

const { Sider } = Layout

/**
 * Ключ для сохранения состояния collapsed в localStorage (AC8).
 */
const SIDEBAR_COLLAPSED_KEY = 'sidebar-collapsed'

/**
 * Базовые пункты меню для всех пользователей (расширено в Story 7.6 для Integrations).
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
    key: '/metrics',
    icon: <AreaChartOutlined />,
    label: 'Metrics',
  },
  {
    key: '/audit',
    icon: <AuditOutlined />,
    label: 'Audit Logs',
  },
  // Integrations пункт меню (Story 7.6, AC7) — flat menu, без submenu
  {
    key: '/audit/integrations',
    icon: <ClusterOutlined />,
    label: 'Integrations',
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

  // Состояние collapsed с сохранением в localStorage (AC8)
  const [collapsed, setCollapsed] = useState(() => {
    const saved = localStorage.getItem(SIDEBAR_COLLAPSED_KEY)
    return saved === 'true'
  })

  // Обработчик collapse с сохранением в localStorage
  const handleCollapse = (value: boolean) => {
    setCollapsed(value)
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(value))
  }

  // Счётчик pending маршрутов для Badge (только для security/admin, enabled=false для developer)
  const pendingCount = usePendingRoutesCount()

  // Формируем меню на основе роли пользователя (расширено в Story 7.6)
  const menuItems = useMemo(() => {
    let items: ItemType[] = baseMenuItems.map((item) => {
      // Добавляем Badge к пункту /approvals если есть pending маршруты
      if (item && 'key' in item && item.key === '/approvals' && pendingCount > 0) {
        return {
          ...item,
          label: (
            <Badge count={pendingCount} offset={[8, 0]} size="small">
              Approvals
            </Badge>
          ),
        }
      }
      return item
    })

    // Фильтруем Integrations для developer (AC6 — только security/admin)
    if (user?.role === 'developer') {
      items = items.filter((item) => {
        if (item && 'key' in item) {
          return item.key !== '/audit/integrations'
        }
        return true
      })
    }

    // Добавляем Users только для admin
    if (user?.role === 'admin') {
      // Вставляем после Dashboard (на второе место)
      items.splice(1, 0, usersMenuItem)
    }

    return items
  }, [user?.role, pendingCount])

  // Определяем selectedKeys для поддержки nested paths (AC7)
  const selectedKeys = useMemo(() => {
    // Для /audit/integrations выбираем именно этот пункт, не /audit
    if (location.pathname === '/audit/integrations') {
      return ['/audit/integrations']
    }
    // Для /audit выбираем /audit
    if (location.pathname === '/audit') {
      return ['/audit']
    }
    return [location.pathname]
  }, [location.pathname])

  return (
    <Sider
      theme="light"
      width={220}
      collapsedWidth={80}
      collapsible
      collapsed={collapsed}
      onCollapse={handleCollapse}
      trigger={null}
    >
      {/* Логотип */}
      <div
        className="logo"
        style={{
          padding: '16px',
          textAlign: 'center',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <SafetyOutlined style={{ fontSize: 24 }} />
        {!collapsed && (
          <span style={{ fontSize: 18, fontWeight: 'bold' }}>API Gateway</span>
        )}
      </div>

      {/* Меню */}
      <Menu
        mode="inline"
        inlineCollapsed={collapsed}
        selectedKeys={selectedKeys}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
      />

      {/* Кнопка collapse/expand (AC8) */}
      <div
        style={{
          position: 'absolute',
          bottom: 16,
          left: 0,
          right: 0,
          textAlign: 'center',
        }}
      >
        <Tooltip title={collapsed ? 'Развернуть' : 'Свернуть'} placement="right">
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => handleCollapse(!collapsed)}
            style={{ width: collapsed ? 48 : 'auto' }}
          />
        </Tooltip>
      </div>
    </Sider>
  )
}

export default Sidebar
