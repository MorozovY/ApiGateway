// Модальное окно для создания и редактирования пользователя (Story 2.6, AC4)
import { useEffect } from 'react'
import { Modal, Form, Input, Select, Alert } from 'antd'
import { useCreateUser, useUpdateUser } from '../hooks/useUsers'
import type { User, UserRole, CreateUserRequest, UpdateUserRequest } from '../types/user.types'

interface UserFormModalProps {
  open: boolean
  user: User | null  // null — режим создания, User — режим редактирования
  onClose: () => void
}

/**
 * Опции выбора роли.
 */
const roleOptions = [
  { value: 'developer', label: 'Developer' },
  { value: 'security', label: 'Security' },
  { value: 'admin', label: 'Admin' },
]

/**
 * Модальное окно для создания и редактирования пользователя.
 *
 * Режим создания: все поля включая пароль.
 * Режим редактирования: email и role (пароль не отображается).
 */
function UserFormModal({ open, user, onClose }: UserFormModalProps) {
  const [form] = Form.useForm()
  const createMutation = useCreateUser()
  const updateMutation = useUpdateUser()

  const isEditMode = user !== null
  const isLoading = createMutation.isPending || updateMutation.isPending

  // Сброс формы при открытии/смене пользователя
  useEffect(() => {
    if (open) {
      if (user) {
        form.setFieldsValue({
          username: user.username,
          email: user.email,
          role: user.role,
        })
      } else {
        form.resetFields()
      }
    }
  }, [open, user, form])

  // Обработчик отправки формы
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()

      if (isEditMode && user) {
        // Режим редактирования — отправляем только изменённые поля
        const updateData: UpdateUserRequest = {}
        if (values.email !== user.email) updateData.email = values.email
        if (values.role !== user.role) updateData.role = values.role

        await updateMutation.mutateAsync({ id: user.id, data: updateData })
      } else {
        // Режим создания — отправляем все поля
        const createData: CreateUserRequest = {
          username: values.username,
          email: values.email,
          password: values.password,
          role: values.role,
        }
        await createMutation.mutateAsync(createData)
      }

      onClose()
    } catch {
      // Ошибки валидации обрабатываются автоматически Form
    }
  }

  // Закрытие с очисткой
  const handleCancel = () => {
    form.resetFields()
    createMutation.reset()
    updateMutation.reset()
    onClose()
  }

  return (
    <Modal
      title={isEditMode ? 'Edit User' : 'Add User'}
      open={open}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={isLoading}
      okText={isEditMode ? 'Save' : 'Create'}
      cancelText="Cancel"
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ role: 'developer' as UserRole }}
      >
        {/* Username — только при создании */}
        {!isEditMode && (
          <Form.Item
            name="username"
            label="Username"
            rules={[
              { required: true, message: 'Username обязателен' },
              { min: 3, message: 'Минимум 3 символа' },
              { max: 50, message: 'Максимум 50 символов' },
            ]}
          >
            <Input placeholder="Введите username" />
          </Form.Item>
        )}

        {/* Email */}
        <Form.Item
          name="email"
          label="Email"
          validateTrigger={['onChange', 'onBlur']}
          rules={[
            { required: true, message: 'Email обязателен' },
            { type: 'email', message: 'Некорректный формат email' },
          ]}
        >
          <Input placeholder="Введите email" />
        </Form.Item>

        {/* Password — только при создании */}
        {!isEditMode && (
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Пароль обязателен' },
              { min: 8, message: 'Минимум 8 символов' },
            ]}
          >
            <Input.Password placeholder="Введите пароль" />
          </Form.Item>
        )}

        {/* Role */}
        <Form.Item
          name="role"
          label="Role"
          rules={[{ required: true, message: 'Роль обязательна' }]}
        >
          <Select options={roleOptions} placeholder="Выберите роль" />
        </Form.Item>

        {/* Информация о пароле в режиме редактирования */}
        {isEditMode && (
          <Alert
            message="Пароль нельзя изменить через эту форму"
            type="info"
            showIcon
            style={{ marginTop: 8 }}
          />
        )}
      </Form>
    </Modal>
  )
}

export default UserFormModal
