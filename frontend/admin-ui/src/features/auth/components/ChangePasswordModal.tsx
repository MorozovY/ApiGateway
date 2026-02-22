// Модальное окно смены пароля (Story 9.4)
import { Modal, Form, Input, Button, App } from 'antd'
import { useState } from 'react'
import { isAxiosError } from 'axios'
import { changePasswordApi } from '../api/authApi'

interface ChangePasswordFormValues {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

interface ChangePasswordModalProps {
  open: boolean
  onClose: () => void
}

/**
 * Модальное окно для смены пароля текущего пользователя.
 *
 * Реализует AC1-AC6 Story 9.4:
 * - AC2: Форма с Current Password, New Password, Confirm Password
 * - AC3: Успешная смена закрывает modal и показывает toast
 * - AC4: Неверный текущий пароль — inline error
 * - AC5: Валидация на frontend (required, min 8, passwords match)
 * - AC6: Cancel сбрасывает форму
 */
export function ChangePasswordModal({ open, onClose }: ChangePasswordModalProps) {
  const [form] = Form.useForm<ChangePasswordFormValues>()
  const [loading, setLoading] = useState(false)
  const { message } = App.useApp()

  // AC5: отслеживаем значения формы для проверки валидности
  const formValues = Form.useWatch([], form)

  // AC5: кнопка disabled пока форма не заполнена корректно
  const isFormValid =
    formValues?.currentPassword &&
    formValues?.newPassword?.length >= 8 &&
    formValues?.confirmPassword === formValues?.newPassword

  const handleSubmit = async (values: ChangePasswordFormValues) => {
    setLoading(true)
    try {
      await changePasswordApi({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      // AC3: toast notification и закрытие modal
      message.success('Пароль успешно изменён')
      form.resetFields()
      onClose()
    } catch (error: unknown) {
      // AC4: inline error при неверном текущем пароле (401)
      if (isAxiosError(error) && error.response?.status === 401) {
        form.setFields([
          { name: 'currentPassword', errors: ['Неверный текущий пароль'] },
        ])
      } else {
        message.error('Ошибка при смене пароля')
      }
    } finally {
      setLoading(false)
    }
  }

  // AC6: Cancel сбрасывает форму
  const handleCancel = () => {
    form.resetFields()
    onClose()
  }

  return (
    <Modal
      title="Сменить пароль"
      open={open}
      onCancel={handleCancel}
      footer={null}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
      >
        {/* AC5: Current Password — required */}
        <Form.Item
          name="currentPassword"
          label="Текущий пароль"
          rules={[{ required: true, message: 'Введите текущий пароль' }]}
        >
          <Input.Password data-testid="current-password" />
        </Form.Item>

        {/* AC5: New Password — required, минимум 8 символов */}
        <Form.Item
          name="newPassword"
          label="Новый пароль"
          rules={[
            { required: true, message: 'Введите новый пароль' },
            { min: 8, message: 'Минимум 8 символов' },
          ]}
        >
          <Input.Password data-testid="new-password" />
        </Form.Item>

        {/* AC5: Confirm Password — должен совпадать с New Password */}
        <Form.Item
          name="confirmPassword"
          label="Подтвердите пароль"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Подтвердите новый пароль' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('Пароли не совпадают'))
              },
            }),
          ]}
        >
          <Input.Password data-testid="confirm-password" />
        </Form.Item>

        {/* AC5: кнопка disabled пока форма невалидна */}
        <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
          <Button onClick={handleCancel} style={{ marginRight: 8 }} data-testid="cancel-button">
            Отмена
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            disabled={!isFormValid}
            data-testid="submit-button"
          >
            Сменить пароль
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  )
}
