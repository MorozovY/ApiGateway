// Таблица пользователей с пагинацией (Story 2.6, AC4)
import { useState } from 'react'
import { Table, Tag, Button, Space, Popconfirm } from 'antd'
import { EditOutlined, StopOutlined } from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useUsers, useDeactivateUser } from '../hooks/useUsers'
import type { User, UserRole } from '../types/user.types'

interface UsersTableProps {
  onEdit: (user: User) => void
}

/**
 * Цвета для badges ролей.
 */
const roleColors: Record<UserRole, string> = {
  developer: 'blue',
  security: 'orange',
  admin: 'purple',
}

/**
 * Человекочитаемые названия ролей.
 */
const roleLabels: Record<UserRole, string> = {
  developer: 'Developer',
  security: 'Security',
  admin: 'Admin',
}

/**
 * Размер страницы по умолчанию.
 */
const DEFAULT_PAGE_SIZE = 10

/**
 * Таблица пользователей с пагинацией и действиями.
 *
 * Колонки: Username, Email, Role, Status, Actions
 */
function UsersTable({ onEdit }: UsersTableProps) {
  // Состояние пагинации
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: DEFAULT_PAGE_SIZE,
  })

  // Вычисляем offset для API
  const offset = (pagination.current - 1) * pagination.pageSize

  // Загрузка данных
  const { data, isLoading } = useUsers({
    offset,
    limit: pagination.pageSize,
  })

  // Мутация для деактивации
  const deactivateMutation = useDeactivateUser()

  // Обработчик деактивации
  const handleDeactivate = (id: string) => {
    deactivateMutation.mutate(id)
  }

  // Обработчик изменения пагинации
  const handleTableChange = (newPagination: TablePaginationConfig) => {
    setPagination({
      current: newPagination.current || 1,
      pageSize: newPagination.pageSize || DEFAULT_PAGE_SIZE,
    })
  }

  // Определение колонок таблицы
  const columns: ColumnsType<User> = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      sorter: (a, b) => a.username.localeCompare(b.username),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: UserRole) => (
        <Tag color={roleColors[role]}>{roleLabels[role]}</Tag>
      ),
      filters: [
        { text: 'Developer', value: 'developer' },
        { text: 'Security', value: 'security' },
        { text: 'Admin', value: 'admin' },
      ],
      onFilter: (value, record) => record.role === value,
    },
    {
      title: 'Status',
      dataIndex: 'isActive',
      key: 'isActive',
      render: (isActive: boolean) => (
        <Tag color={isActive ? 'green' : 'default'}>
          {isActive ? 'Active' : 'Inactive'}
        </Tag>
      ),
      filters: [
        { text: 'Active', value: true },
        { text: 'Inactive', value: false },
      ],
      onFilter: (value, record) => record.isActive === value,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          {/* Edit доступен только для активных пользователей.
              Деактивированных можно только просматривать. */}
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => onEdit(record)}
            disabled={!record.isActive}
          >
            Edit
          </Button>
          {record.isActive && (
            <Popconfirm
              title="Деактивировать пользователя?"
              description="Пользователь потеряет доступ к системе"
              onConfirm={() => handleDeactivate(record.id)}
              okText="Да"
              cancelText="Нет"
            >
              <Button
                type="text"
                danger
                icon={<StopOutlined />}
                loading={deactivateMutation.isPending && deactivateMutation.variables === record.id}
              >
                Deactivate
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Table
      dataSource={data?.items}
      columns={columns}
      rowKey="id"
      loading={isLoading}
      pagination={{
        current: pagination.current,
        pageSize: pagination.pageSize,
        total: data?.total,
        showSizeChanger: true,
        showTotal: (total) => `Всего ${total} пользователей`,
      }}
      onChange={handleTableChange}
    />
  )
}

export default UsersTable
