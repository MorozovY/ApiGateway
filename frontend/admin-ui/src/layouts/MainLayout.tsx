import { Layout } from 'antd'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'

const { Header, Content } = Layout

function MainLayout() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px' }}>
          <span style={{ fontSize: 16 }}>Admin Panel</span>
        </Header>
        <Content style={{ margin: '24px', padding: '24px', background: '#fff' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
