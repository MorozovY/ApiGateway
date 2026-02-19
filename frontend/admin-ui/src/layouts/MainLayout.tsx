import { Layout, Button, Space, Typography } from 'antd'
import { LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import { useAuth } from '@features/auth'
import { ThemeSwitcher } from '@shared/components'
import { useThemeContext } from '@shared/providers'

const { Header, Content } = Layout
const { Text } = Typography

function MainLayout() {
  const { user, logout, isLoading } = useAuth()
  const { isDark } = useThemeContext()

  // Обработчик выхода
  const handleLogout = async () => {
    await logout()
  }

  // Цвета фона зависят от темы (Ant Design dark theme использует #141414)
  const headerBg = isDark ? '#141414' : '#fff'
  const contentBg = isDark ? '#1f1f1f' : '#fff'

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Layout>
        <Header style={{
          background: headerBg,
          padding: '0 24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span style={{ fontSize: 16 }}>Admin Panel</span>
          <Space>
            <ThemeSwitcher />
            <UserOutlined />
            <Text>{user?.username}</Text>
            <Text type="secondary">({user?.role})</Text>
            <Button
              type="text"
              danger
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              loading={isLoading}
            >
              Logout
            </Button>
          </Space>
        </Header>
        <Content style={{ margin: '24px', padding: '24px', background: contentBg }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
