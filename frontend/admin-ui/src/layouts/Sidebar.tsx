// Боковая панель навигации (Story 2.6 — Users, Story 4.6 — Badge, Story 6.5 — Metrics, Story 7.6 — Collapsible, Story 9.3 — Role-based filtering)
// Story 14.4: Добавлен prefetch для lazy-загружаемых модулей
// Story 16.7: Разные иконки для Routes и Consumers
import { useMemo, useCallback } from 'react'
import { Layout, Menu, Badge } from 'antd'
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
  ExperimentOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { usePendingRoutesCount } from '@features/approval'
import { usePrefetch } from '@shared/hooks'
import type { MenuItemType } from 'antd/es/menu/interface'
import type { User } from '@features/auth'

const { Sider } = Layout

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
    '/consumers',
    '/routes',
    '/rate-limits',
    '/approvals',
    '/audit',
    '/audit/integrations',
    '/metrics',
    '/test',
  ],
}

interface SidebarProps {
  collapsed: boolean
}

/**
 * Все возможные пункты меню (включая Users).
 * Порядок определяется ROLE_MENU_ACCESS при фильтрации.
 */
const allMenuItems: MenuItemType[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Главная',
  },
  {
    key: '/users',
    icon: <TeamOutlined />,
    label: 'Пользователи',
  },
  {
    key: '/consumers',
    icon: <UserSwitchOutlined />,
    label: 'Потребители',
  },
  {
    key: '/routes',
    icon: <ApiOutlined />,
    label: 'Маршруты',
  },
  {
    key: '/rate-limits',
    icon: <SafetyOutlined />,
    label: 'Лимиты',
  },
  {
    key: '/approvals',
    icon: <CheckCircleOutlined />,
    label: 'Согласования',
  },
  {
    key: '/audit',
    icon: <AuditOutlined />,
    label: 'Аудит',
  },
  {
    key: '/audit/integrations',
    icon: <ClusterOutlined />,
    label: 'Интеграции',
  },
  {
    key: '/metrics',
    icon: <AreaChartOutlined />,
    label: 'Метрики',
  },
  {
    key: '/test',
    icon: <ExperimentOutlined />,
    label: 'Тестирование',
  },
]

function Sidebar({ collapsed }: SidebarProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const { prefetch } = usePrefetch()

  // Счётчик pending маршрутов для Badge (только для security/admin, enabled=false для developer)
  const pendingCount = usePendingRoutesCount()

  // Обработчик hover для prefetch (Story 14.4, AC5)
  const handleMouseEnter = useCallback(
    (key: string) => {
      prefetch(key)
    },
    [prefetch]
  )

  // Формируем меню на основе роли пользователя (Story 9.3 — role-based filtering)
  const menuItems = useMemo(() => {
    // Определяем разрешённые ключи для текущей роли
    const allowedKeys = user?.role ? ROLE_MENU_ACCESS[user.role] : []

    // Фильтруем пункты меню по разрешённым ключам, сохраняя порядок из allowedKeys
    const items: MenuItemType[] = allowedKeys
      .map((key) => {
        // Находим пункт меню по ключу
        const menuItem = allMenuItems.find((item) => item.key === key)
        if (!menuItem) return null

        // Добавляем onMouseEnter для prefetch (Story 14.4)
        const itemWithPrefetch: MenuItemType = {
          ...menuItem,
          onMouseEnter: () => handleMouseEnter(key),
        }

        // Добавляем Badge к пункту /approvals если есть pending маршруты
        if (menuItem.key === '/approvals' && pendingCount > 0) {
          return {
            ...itemWithPrefetch,
            label: (
              <Badge count={pendingCount} offset={[8, 0]} size="small">
                {menuItem.label}
              </Badge>
            ),
          }
        }
        return itemWithPrefetch
      })
      .filter((item): item is MenuItemType => item !== null)

    return items
  }, [user?.role, pendingCount, handleMouseEnter])

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
    </Sider>
  )
}

export default Sidebar
