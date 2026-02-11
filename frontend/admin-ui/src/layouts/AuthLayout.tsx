import { Layout } from 'antd'
import { Outlet } from 'react-router-dom'
import { SafetyOutlined } from '@ant-design/icons'

const { Content } = Layout

function AuthLayout() {
  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5' }}>
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
            background: '#fff',
            padding: '40px',
            borderRadius: '8px',
            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
            width: '100%',
            maxWidth: '400px',
          }}
        >
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <SafetyOutlined style={{ fontSize: 48, color: '#1890ff' }} />
            <h1 style={{ marginTop: '16px', marginBottom: '8px' }}>API Gateway</h1>
            <p style={{ color: '#666' }}>Admin Panel</p>
          </div>
          <Outlet />
        </div>
      </Content>
    </Layout>
  )
}

export default AuthLayout
