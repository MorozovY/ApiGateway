// MainLayout с header и sidebar (Story 9.4 — добавлен dropdown с Change Password, Story 10.7 — Quick Start Guide)
import { Layout, Button, Space, Typography, Dropdown, Tooltip } from 'antd'
import {
  LogoutOutlined,
  UserOutlined,
  LockOutlined,
  DownOutlined,
  BookOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { Outlet } from 'react-router-dom'
import { useState } from 'react'
import type { MenuProps } from 'antd'
import Sidebar from './Sidebar'
import { useAuth, ChangePasswordModal } from '@features/auth'
import { ThemeSwitcher } from '@shared/components'
import { useThemeContext } from '@shared/providers'

/**
 * Ключ для сохранения состояния collapsed в localStorage.
 */
const SIDEBAR_COLLAPSED_KEY = 'sidebar-collapsed'

const { Header, Content } = Layout
const { Text } = Typography

function MainLayout() {
  const { user, logout, isLoading } = useAuth()
  const { isDark } = useThemeContext()

  // Состояние collapsed sidebar с сохранением в localStorage
  const [collapsed, setCollapsed] = useState(() => {
    const saved = localStorage.getItem(SIDEBAR_COLLAPSED_KEY)
    return saved === 'true'
  })

  // Обработчик collapse с сохранением в localStorage
  const handleCollapse = () => {
    const newValue = !collapsed
    setCollapsed(newValue)
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(newValue))
  }

  // Story 9.4: state для modal смены пароля (Subtask 4.1)
  const [isChangePasswordModalOpen, setIsChangePasswordModalOpen] = useState(false)

  // Обработчик выхода
  const handleLogout = async () => {
    await logout()
  }

  // AC1: Dropdown items (Subtask 4.2)
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'change-password',
      label: 'Сменить пароль',
      icon: <LockOutlined />,
      onClick: () => setIsChangePasswordModalOpen(true),
    },
    { type: 'divider' },
    {
      key: 'logout',
      label: 'Выйти',
      icon: <LogoutOutlined />,
      danger: true,
      onClick: handleLogout,
    },
  ]

  // Цвета фона зависят от темы (Ant Design dark theme использует #141414)
  const headerBg = isDark ? '#141414' : '#fff'
  const contentBg = isDark ? '#1f1f1f' : '#fff'

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar collapsed={collapsed} />
      <Layout>
        <Header style={{
          background: headerBg,
          padding: '0 24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          {/* Левая часть: кнопка collapse + заголовок */}
          <Space>
            <Tooltip title={collapsed ? 'Развернуть меню' : 'Свернуть меню'}>
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={handleCollapse}
                data-testid="sidebar-collapse-button"
              />
            </Tooltip>
            <span style={{ fontSize: 16 }}>Admin Panel</span>
          </Space>
          {/* Правая часть: руководство, тема, пользователь */}
          <Space>
            {/* Ссылка на Quick Start Guide (Story 10.7) */}
            <Tooltip title="Руководство">
              <Button
                type="text"
                icon={<BookOutlined />}
                href="/docs/quick-start-guide.html"
                target="_blank"
                rel="noopener noreferrer"
                data-testid="quick-start-guide-link"
                aria-label="Открыть руководство пользователя"
              />
            </Tooltip>
            <ThemeSwitcher />
            {/* AC1: Dropdown при клике на username (Subtask 4.3) */}
            <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
              <Button type="text" loading={isLoading}>
                <Space>
                  <UserOutlined />
                  <Text>{user?.username}</Text>
                  <Text type="secondary">({user?.role})</Text>
                  <DownOutlined />
                </Space>
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: '24px', padding: '24px', background: contentBg }}>
          <Outlet />
        </Content>
      </Layout>

      {/* Story 9.4: Modal смены пароля */}
      <ChangePasswordModal
        open={isChangePasswordModalOpen}
        onClose={() => setIsChangePasswordModalOpen(false)}
      />
    </Layout>
  )
}

export default MainLayout
