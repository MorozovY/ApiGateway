import { Layout } from 'antd'
import { Outlet } from 'react-router-dom'
import { SafetyOutlined } from '@ant-design/icons'
import { useThemeContext } from '@shared/providers'

const { Content } = Layout

function AuthLayout() {
  const { isDark } = useThemeContext()

  // Цвета зависят от темы
  const layoutBg = isDark ? '#141414' : '#f0f2f5'
  const cardBg = isDark ? '#1f1f1f' : '#fff'
  const shadowColor = isDark ? 'rgba(0, 0, 0, 0.45)' : 'rgba(0, 0, 0, 0.1)'
  const subtitleColor = isDark ? '#a0a0a0' : '#666'

  return (
    <Layout style={{ minHeight: '100vh', background: layoutBg }}>
      <Content
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          padding: '24px',
        }}
      >
        <div
          style={{
            background: cardBg,
            padding: '40px',
            borderRadius: '8px',
            boxShadow: `0 2px 8px ${shadowColor}`,
            width: '100%',
            maxWidth: '400px',
          }}
        >
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <SafetyOutlined style={{ fontSize: 48, color: '#1890ff' }} />
            <h1 style={{ marginTop: '16px', marginBottom: '8px' }}>API Gateway</h1>
            <p style={{ color: subtitleColor }}>Admin Panel</p>
          </div>
          <Outlet />
        </div>
      </Content>
    </Layout>
  )
}

export default AuthLayout
