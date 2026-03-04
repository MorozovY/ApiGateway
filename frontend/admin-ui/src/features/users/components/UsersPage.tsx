// Страница управления пользователями (Story 2.6, AC4; Story 8.3 — поиск; Story 15.4 — добавлен PageInfoBlock, Story 15.6 — унификация заголовка)
import { useState } from 'react'
import { Button, Typography, Space, Card } from 'antd'
import { PlusOutlined, TeamOutlined } from '@ant-design/icons'
import UsersTable from './UsersTable'
import UserFormModal from './UserFormModal'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import type { User } from '../types/user.types'

const { Title } = Typography

/**
 * Страница управления пользователями.
 *
 * Включает таблицу пользователей с пагинацией, поиском и кнопку добавления.
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
    <Card>
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <TeamOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Users
            </Title>
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            Add User
          </Button>
        </Space>
      </div>

      {/* Инфо-блок (Story 15.4) */}
      <PageInfoBlock pageKey="users" {...PAGE_DESCRIPTIONS.users} />

      <UsersTable onEdit={handleEdit} />

      <UserFormModal
        open={isModalOpen}
        user={editingUser}
        onClose={handleModalClose}
      />
    </Card>
  )
}

export default UsersPage
