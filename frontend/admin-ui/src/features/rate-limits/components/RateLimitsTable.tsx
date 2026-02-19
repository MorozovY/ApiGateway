// Таблица Rate Limit политик с пагинацией (Story 5.4, AC1, AC8)
import { useState } from 'react'
import { Table, Button, Space, Popconfirm, Tooltip } from 'antd'
import { EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useRateLimits } from '../hooks/useRateLimits'
import type { RateLimit } from '../types/rateLimit.types'

interface RateLimitsTableProps {
  /** Обработчик редактирования — undefined если нет прав (AC8) */
  onEdit?: (rateLimit: RateLimit) => void
  /** Обработчик удаления — undefined если нет прав (AC8) */
  onDelete?: (rateLimit: RateLimit) => void
  /** Обработчик просмотра маршрутов — для AC7 */
  onViewRoutes?: (rateLimit: RateLimit) => void
  /** Флаг загрузки удаления */
  isDeleting?: boolean
}

/**
 * Размер страницы по умолчанию.
 */
const DEFAULT_PAGE_SIZE = 10

/**
 * Склонение слова "политика" в зависимости от числа.
 */
function pluralizePolicies(count: number): string {
  const lastTwo = count % 100
  const lastOne = count % 10

  if (lastTwo >= 11 && lastTwo <= 19) {
    return `${count} политик`
  }

  if (lastOne === 1) {
    return `${count} политика`
  }

  if (lastOne >= 2 && lastOne <= 4) {
    return `${count} политики`
  }

  return `${count} политик`
}

/**
 * Таблица Rate Limit политик.
 *
 * Колонки: Name, Description, Requests/sec, Burst Size, Used By, Actions
 * Actions (Edit/Delete) отображаются только для admin (AC8).
 */
function RateLimitsTable({
  onEdit,
  onDelete,
  onViewRoutes,
  isDeleting = false,
}: RateLimitsTableProps) {
  // Состояние пагинации
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: DEFAULT_PAGE_SIZE,
  })

  // Вычисляем offset для API
  const offset = (pagination.current - 1) * pagination.pageSize

  // Загрузка данных
  const { data, isLoading } = useRateLimits({
    offset,
    limit: pagination.pageSize,
  })

  // Обработчик изменения пагинации
  const handleTableChange = (newPagination: TablePaginationConfig) => {
    setPagination({
      current: newPagination.current || 1,
      pageSize: newPagination.pageSize || DEFAULT_PAGE_SIZE,
    })
  }

  // Определение колонок таблицы
  const columns: ColumnsType<RateLimit> = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string | null) => description || '—',
    },
    {
      title: 'Requests/sec',
      dataIndex: 'requestsPerSecond',
      key: 'requestsPerSecond',
      align: 'right',
      sorter: (a, b) => a.requestsPerSecond - b.requestsPerSecond,
    },
    {
      title: 'Burst Size',
      dataIndex: 'burstSize',
      key: 'burstSize',
      align: 'right',
      sorter: (a, b) => a.burstSize - b.burstSize,
    },
    {
      title: 'Used By',
      dataIndex: 'usageCount',
      key: 'usageCount',
      align: 'right',
      render: (usageCount: number, record: RateLimit) => {
        // Если usageCount > 0, делаем кликабельную ссылку (AC7)
        if (usageCount > 0 && onViewRoutes) {
          return (
            <Button
              type="link"
              size="small"
              onClick={() => onViewRoutes(record)}
              style={{ padding: 0 }}
            >
              {usageCount}
            </Button>
          )
        }
        return usageCount
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => {
        // Если нет прав на редактирование/удаление — не показываем actions (AC8)
        if (!onEdit && !onDelete) {
          return null
        }

        return (
          <Space>
            {onEdit && (
              <Tooltip title="Редактировать">
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => onEdit(record)}
                />
              </Tooltip>
            )}
            {onDelete && (
              <Popconfirm
                title="Удалить политику?"
                description={
                  record.usageCount > 0
                    ? `Политика используется ${record.usageCount} маршрутами`
                    : 'Это действие нельзя отменить'
                }
                onConfirm={() => onDelete(record)}
                okText="Да"
                cancelText="Нет"
              >
                <Tooltip title="Удалить">
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    loading={isDeleting}
                  />
                </Tooltip>
              </Popconfirm>
            )}
          </Space>
        )
      },
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
        showTotal: (total) => `Всего ${pluralizePolicies(total)}`,
      }}
      onChange={handleTableChange}
    />
  )
}

export default RateLimitsTable
