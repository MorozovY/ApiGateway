// Форма логина
import { useEffect, useRef } from 'react'
import { Form, Input, Button, Alert } from 'antd'
import type { InputRef } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useAuth } from '../hooks/useAuth'
import { DemoCredentials } from './DemoCredentials'

interface LoginFormValues {
  username: string
  password: string
}

export function LoginForm() {
  const { login, isLoading, error, clearError } = useAuth()
  const [form] = Form.useForm<LoginFormValues>()
  const passwordInputRef = useRef<InputRef>(null)

  // Фокус на password при ошибке
  useEffect(() => {
    if (error && passwordInputRef.current) {
      passwordInputRef.current.focus()
    }
  }, [error])

  // Обработка отправки формы
  const handleSubmit = async (values: LoginFormValues) => {
    clearError()
    await login(values.username, values.password)
  }

  // AC2: Заполнение формы при выборе демо-учётных данных
  const handleDemoSelect = (username: string, password: string) => {
    form.setFieldsValue({ username, password })
  }

  return (
    <Form
      form={form}
      onFinish={handleSubmit}
      layout="vertical"
      requiredMark={false}
    >
      {/* Область для ошибок */}
      {error && (
        <Alert
          message={error}
          type="error"
          showIcon
          style={{ marginBottom: 24 }}
          data-testid="login-error"
        />
      )}

      {/* Поле Username */}
      <Form.Item
        name="username"
        rules={[{ required: true, message: 'Введите имя пользователя' }]}
      >
        <Input
          prefix={<UserOutlined />}
          placeholder="Имя пользователя"
          size="large"
          data-testid="username-input"
        />
      </Form.Item>

      {/* Поле Password */}
      <Form.Item
        name="password"
        rules={[{ required: true, message: 'Введите пароль' }]}
      >
        <Input.Password
          ref={passwordInputRef}
          prefix={<LockOutlined />}
          placeholder="Пароль"
          size="large"
          data-testid="password-input"
        />
      </Form.Item>

      {/* Кнопка Войти */}
      <Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          loading={isLoading}
          block
          size="large"
          data-testid="login-button"
        >
          Войти
        </Button>
      </Form.Item>

      {/* AC1: Таблица демо-учётных данных */}
      <DemoCredentials onSelect={handleDemoSelect} />
    </Form>
  )
}
