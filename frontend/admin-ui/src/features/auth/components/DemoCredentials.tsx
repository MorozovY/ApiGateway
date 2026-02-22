// Компактная сноска с демо-учётными данными для страницы входа (Story 9.5)
import { Button, Typography, Tooltip, Divider, Space, App } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useState } from 'react'
import axios from '@shared/utils/axios'

const { Text, Link } = Typography

/**
 * Данные демо-пользователей.
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
    features: 'Все разделы',
  },
]

interface DemoCredentialsProps {
  /**
   * Callback при выборе учётных данных.
   * Вызывается при клике на credentials.
   */
  onSelect?: (username: string, password: string) => void
}

/**
 * Компактная сноска с демо-учётными данными для страницы входа.
 *
 * AC1: Отображает credentials с ролями и возможностями (в tooltip).
 * AC2: Клик по credentials заполняет форму входа.
 * AC4: Кнопка сброса паролей вызывает API.
 * AC5: Подсказка о сбросе паролей.
 */
export function DemoCredentials({ onSelect }: DemoCredentialsProps) {
  const [isResetting, setIsResetting] = useState(false)
  const { message } = App.useApp()

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

  // AC2: Клик по credentials заполняет форму
  const handleCredentialClick = (username: string, password: string) => {
    onSelect?.(username, password)
  }

  return (
    <div style={{ marginTop: 32 }} data-testid="demo-credentials-card">
      <Divider style={{ margin: '16px 0' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>🔐 Демо-доступ</Text>
      </Divider>

      {/* AC1: Компактный список credentials */}
      <Space
        direction="vertical"
        size={4}
        style={{ width: '100%' }}
        data-testid="demo-credentials-table"
      >
        {DEMO_CREDENTIALS.map((cred) => (
          <Tooltip key={cred.username} title={cred.features} placement="right">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Link
                onClick={() => handleCredentialClick(cred.username, cred.password)}
                data-testid={`demo-login-${cred.username}`}
                style={{ fontFamily: 'monospace' }}
              >
                {cred.username} / {cred.password}
              </Link>
              <Text type="secondary" style={{ fontSize: 12 }}>{cred.role}</Text>
            </div>
          </Tooltip>
        ))}
      </Space>

      {/* AC5: Подсказка и кнопка сброса */}
      <div style={{ marginTop: 12, textAlign: 'center' }}>
        <Text type="secondary" style={{ fontSize: 11 }} data-testid="demo-hint">
          Не работает?{' '}
          <Button
            type="link"
            size="small"
            icon={<ReloadOutlined />}
            onClick={handleResetPasswords}
            loading={isResetting}
            data-testid="reset-passwords-button"
            style={{ padding: 0, height: 'auto', fontSize: 11 }}
          >
            Сбросить пароли
          </Button>
        </Text>
      </div>
    </div>
  )
}
