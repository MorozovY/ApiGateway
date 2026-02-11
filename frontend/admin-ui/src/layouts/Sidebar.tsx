import { Layout, Menu } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  ApiOutlined,
  DashboardOutlined,
  SafetyOutlined,
  AuditOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'

const { Sider } = Layout

const menuItems = [
  {
    key: '/routes',
    icon: <ApiOutlined />,
    label: 'Routes',
  },
  {
    key: '/rate-limits',
    icon: <DashboardOutlined />,
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

function Sidebar() {
  const navigate = useNavigate()
  const location = useLocation()

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