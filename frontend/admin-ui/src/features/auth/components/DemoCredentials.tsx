// Таблица демо-учётных данных для страницы входа (Story 9.5)
import { Card, Table, Button, Typography, message, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useState } from 'react'
import axios from '@shared/utils/axios'

const { Text } = Typography

/**
 * Данные демо-пользователей для таблицы.
 */
const DEMO_CREDENTIALS = [
  {
    username: 'developer',
    password: 'developer123',
    role: 'Developer',
    features: 'Dashboard, Routes, Metrics, Test',
  },
  {
    username: 'security',
    password: 'security123',
    role: 'Security',
    features: 'Dashboard, Routes, Approvals, Audit, Integrations, Metrics',
  },
  {
    username: 'admin',
    password: 'admin123',
    role: 'Admin',
    features: 'Все: Dashboard, Users, Routes, Rate Limits, Approvals, Audit, Integrations, Metrics, Test',
  },
]

interface DemoCredentialsProps {
  /**
   * Callback при выборе учётных данных.
   * Вызывается при клике на логин в таблице.
   */
  onSelect?: (username: string, password: string) => void
}

/**
 * Таблица демо-учётных данных для страницы входа.
 *
 * AC1: Отображает таблицу с логинами, паролями, ролями и возможностями.
 * AC2: Клик по логину заполняет форму входа.
 * AC4: Кнопка сброса паролей вызывает API.
 * AC5: Подсказка о сбросе паролей.
 */
export function DemoCredentials({ onSelect }: DemoCredentialsProps) {
  const [isResetting, setIsResetting] = useState(false)

  // AC4: Сброс паролей демо-пользователей
  const handleResetPasswords = async () => {
    setIsResetting(true)
    try {
      await axios.post('/api/v1/auth/reset-demo-passwords')
      message.success('Пароли сброшены')
    } catch {
      message.error('Ошибка при сбросе паролей')
    } finally {
      setIsResetting(false)
    }
  }

  // AC2: Клик по логину заполняет форму
  const handleUsernameClick = (username: string, password: string) => {
    onSelect?.(username, password)
  }

  const columns = [
    {
      title: 'Логин',
      dataIndex: 'username',
      key: 'username',
      render: (text: string, record: (typeof DEMO_CREDENTIALS)[0]) => (
        <a
          onClick={() => handleUsernameClick(record.username, record.password)}
          data-testid={`demo-login-${text}`}
        >
          <code>{text}</code>
        </a>
      ),
    },
    {
      title: 'Пароль',
      dataIndex: 'password',
      key: 'password',
      render: (text: string) => <code>{text}</code>,
    },
    {
      title: 'Роль',
      dataIndex: 'role',
      key: 'role',
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: 'Возможности',
      dataIndex: 'features',
      key: 'features',
    },
  ]

  return (
    <Card
      title="🔐 Демо-доступ"
      size="small"
      style={{ marginTop: 24 }}
      data-testid="demo-credentials-card"
      extra={
        // AC4: Кнопка сброса паролей
        <Button
          type="link"
          icon={<ReloadOutlined />}
          onClick={handleResetPasswords}
          loading={isResetting}
          data-testid="reset-passwords-button"
        >
          Сбросить пароли
        </Button>
      }
    >
      <Table
        dataSource={DEMO_CREDENTIALS}
        columns={columns}
        pagination={false}
        size="small"
        rowKey="username"
        scroll={{ x: 'max-content' }}
        data-testid="demo-credentials-table"
      />

      {/* AC5: Подсказка о сбросе паролей */}
      <Space style={{ marginTop: 12 }}>
        <Text type="secondary" data-testid="demo-hint">
          Если учётные данные не работают, нажмите «Сбросить пароли»
        </Text>
      </Space>
    </Card>
  )
}
