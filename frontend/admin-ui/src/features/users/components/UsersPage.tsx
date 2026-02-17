// Страница управления пользователями (Story 2.6, AC4)
import { useState } from 'react'
import { Button, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import UsersTable from './UsersTable'
import UserFormModal from './UserFormModal'
import type { User } from '../types/user.types'

const { Title } = Typography

/**
 * Страница управления пользователями.
 *
 * Включает таблицу пользователей с пагинацией и кнопку добавления.
 * Модальное окно используется для создания и редактирования.
 */
function UsersPage() {
  // Состояние модального окна
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<User | null>(null)

  // Открытие модального окна для создания
  const handleAdd = () => {
    setEditingUser(null)
    setIsModalOpen(true)
  }

  // Открытие модального окна для редактирования
  const handleEdit = (user: User) => {
    setEditingUser(user)
    setIsModalOpen(true)
  }

  // Закрытие модального окна
  const handleModalClose = () => {
    setIsModalOpen(false)
    setEditingUser(null)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          Users
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          Add User
        </Button>
      </div>

      <UsersTable onEdit={handleEdit} />

      <UserFormModal
        open={isModalOpen}
        user={editingUser}
        onClose={handleModalClose}
      />
    </div>
  )
}

export default UsersPage
