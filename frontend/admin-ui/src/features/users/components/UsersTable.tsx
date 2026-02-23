// Таблица пользователей с пагинацией и поиском (Story 2.6, AC4; Story 8.3; Story 8.8)
import { useState, useRef, useEffect } from 'react'
import { Table, Tag, Button, Space, Popconfirm, Input } from 'antd'
import { EditOutlined, StopOutlined, SearchOutlined, CloseCircleOutlined } from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useUsers, useDeactivateUser } from '../hooks/useUsers'
import type { User } from '../types/user.types'
import { ROLE_COLORS, ROLE_LABELS, type UserRole } from '@shared/constants'
import { FilterChips, type FilterChip } from '@shared/components/FilterChips'

interface UsersTableProps {
  onEdit: (user: User) => void
}

/**
 * Задержка debounce для поиска (мс).
 */
const SEARCH_DEBOUNCE_MS = 300

/**
 * Размер страницы по умолчанию.
 */
const DEFAULT_PAGE_SIZE = 10

/**
 * Таблица пользователей с пагинацией, поиском и действиями.
 *
 * Колонки: Username, Email, Role, Status, Actions
 * Панель фильтров над таблицей содержит поле поиска.
 *
 * @param onEdit — callback при клике на Edit
 */
function UsersTable({ onEdit }: UsersTableProps) {
  // Состояние пагинации
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: DEFAULT_PAGE_SIZE,
  })

  // Состояние поиска (Story 8.3)
  const [searchInput, setSearchInput] = useState('')
  const [searchValue, setSearchValue] = useState('')
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Обработчик ввода в поле поиска с debounce
  const handleSearchInputChange = (value: string) => {
    setSearchInput(value)

    // Очищаем предыдущий таймер
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current)
    }

    // Устанавливаем новый таймер
    debounceTimerRef.current = setTimeout(() => {
      setSearchValue(value)
      // Сбрасываем на первую страницу при изменении поиска
      setPagination((prev) => ({ ...prev, current: 1 }))
    }, SEARCH_DEBOUNCE_MS)
  }

  // Очистка таймера при размонтировании
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }
    }
  }, [])

  // Сброс фильтров
  const handleClearFilters = () => {
    setSearchInput('')
    setSearchValue('')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  // Вычисляем offset для API
  const offset = (pagination.current - 1) * pagination.pageSize

  // Загрузка данных с поиском
  const { data, isLoading } = useUsers({
    offset,
    limit: pagination.pageSize,
    search: searchValue || undefined,
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
        <Tag color={ROLE_COLORS[role]}>{ROLE_LABELS[role]}</Tag>
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

  // Проверка наличия активных фильтров
  const hasActiveFilters = !!searchValue

  return (
    <div>
      {/* Панель фильтров (Story 8.3) */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Поиск по username или email..."
          allowClear
          value={searchInput}
          onChange={(e) => handleSearchInputChange(e.target.value)}
          style={{ width: 280 }}
          prefix={<SearchOutlined />}
          data-testid="users-search-input"
        />
        {hasActiveFilters && (
          <Button
            type="text"
            icon={<CloseCircleOutlined />}
            onClick={handleClearFilters}
          >
            Сбросить фильтры
          </Button>
        )}
      </Space>

      {/* FilterChips для активных фильтров (Story 8.8) */}
      <FilterChips
        chips={[
          ...(searchValue
            ? [{
                key: 'search',
                label: `Поиск: ${searchValue}`,
                onClose: handleClearFilters,
              } as FilterChip]
            : []),
        ]}
      />

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
    </div>
  )
}

export default UsersTable
