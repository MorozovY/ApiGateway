// Боковая панель навигации (Story 2.6 — Users, Story 4.6 — Badge, Story 6.5 — Metrics, Story 7.6 — Collapsible, Story 9.3 — Role-based filtering)
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
  ExperimentOutlined,
  BookOutlined,
} from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { usePendingRoutesCount } from '@features/approval'
import type { ItemType } from 'antd/es/menu/interface'
import type { User } from '@features/auth'

const { Sider } = Layout

/**
 * Ключ для сохранения состояния collapsed в localStorage (AC8).
 */
const SIDEBAR_COLLAPSED_KEY = 'sidebar-collapsed'

/**
 * Маппинг ролей к разрешённым пунктам меню (Story 9.3, AC1-AC3).
 *
 * Developer: Dashboard, Routes, Metrics
 * Security: Dashboard, Routes, Approvals, Audit, Integrations, Metrics
 * Admin: Все пункты (Dashboard, Users, Routes, Rate Limits, Approvals, Audit, Integrations, Metrics, Test)
 */
const ROLE_MENU_ACCESS: Record<User['role'], string[]> = {
  developer: ['/dashboard', '/routes', '/metrics'],
  security: ['/dashboard', '/routes', '/approvals', '/audit', '/audit/integrations', '/metrics'],
  admin: [
    '/dashboard',
    '/users',
    '/routes',
    '/rate-limits',
    '/approvals',
    '/audit',
    '/audit/integrations',
    '/metrics',
    '/test',
  ],
}

/**
 * Все возможные пункты меню (включая Users).
 * Порядок определяется ROLE_MENU_ACCESS при фильтрации.
 */
const allMenuItems: ItemType[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Dashboard',
  },
  {
    key: '/users',
    icon: <TeamOutlined />,
    label: 'Users',
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
  {
    key: '/audit/integrations',
    icon: <ClusterOutlined />,
    label: 'Integrations',
  },
  {
    key: '/metrics',
    icon: <AreaChartOutlined />,
    label: 'Metrics',
  },
  {
    key: '/test',
    icon: <ExperimentOutlined />,
    label: 'Test',
  },
]

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

  // Формируем меню на основе роли пользователя (Story 9.3 — role-based filtering)
  const menuItems = useMemo(() => {
    // Определяем разрешённые ключи для текущей роли
    const allowedKeys = user?.role ? ROLE_MENU_ACCESS[user.role] : []

    // Фильтруем пункты меню по разрешённым ключам, сохраняя порядок из allowedKeys
    const items: ItemType[] = allowedKeys
      .map((key) => {
        // Находим пункт меню по ключу
        const menuItem = allMenuItems.find(
          (item) => item && 'key' in item && item.key === key
        )
        if (!menuItem) return null

        // Добавляем Badge к пункту /approvals если есть pending маршруты
        if (menuItem && 'key' in menuItem && menuItem.key === '/approvals' && pendingCount > 0) {
          return {
            ...menuItem,
            label: (
              <Badge count={pendingCount} offset={[8, 0]} size="small">
                Approvals
              </Badge>
            ),
          }
        }
        return menuItem
      })
      .filter((item): item is ItemType => item !== null)

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

      {/* Footer: Quick Start Guide + collapse button (Story 10.7, AC4) */}
      <div
        style={{
          position: 'absolute',
          bottom: 16,
          left: 0,
          right: 0,
          padding: '0 12px',
          display: 'flex',
          justifyContent: collapsed ? 'center' : 'space-between',
          alignItems: 'center',
          gap: 8,
        }}
      >
        {/* Ссылка на Quick Start Guide */}
        <Tooltip title="Руководство" placement="right">
          <Button
            type="text"
            icon={<BookOutlined />}
            href="/docs/quick-start-guide.html"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="quick-start-guide-link"
            style={{ width: collapsed ? 48 : 'auto' }}
          >
            {!collapsed && 'Руководство'}
          </Button>
        </Tooltip>

        {/* Кнопка collapse/expand (AC8) */}
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
