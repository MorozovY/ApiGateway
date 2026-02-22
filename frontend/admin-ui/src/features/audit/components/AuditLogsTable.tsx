// Таблица аудит-логов с expandable rows (Story 7.5, AC1, AC3, AC5)
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Table, Tag, Typography, Descriptions, Skeleton } from 'antd'
import { ExpandOutlined, CompressOutlined } from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import dayjs from 'dayjs'
import type { AuditLogEntry, AuditFilter, AuditLogsResponse } from '../types/audit.types'
import { ChangesViewer } from './ChangesViewer'
import {
  AUDIT_ACTION_LABELS,
  AUDIT_ACTION_COLORS,
  ENTITY_TYPE_LABELS,
  DEFAULT_PAGE_SIZE,
} from '../config/auditConfig'

// Формат timestamp: "Feb 11, 2026, 14:30" (AC1) — английская локаль по умолчанию

const { Text } = Typography

interface AuditLogsTableProps {
  data: AuditLogsResponse | undefined
  isLoading: boolean
  filter: AuditFilter
  onPaginationChange: (offset: number, limit: number) => void
}

/**
 * Таблица аудит-логов с expandable rows.
 *
 * Колонки (AC1):
 * - Timestamp (formatted: "Feb 11, 2026, 14:30")
 * - Action (badge с цветом)
 * - Entity Type
 * - Entity (ссылка на сущность)
 * - User (username)
 * - Expand icon
 *
 * Сортировка: timestamp DESC (новые первыми).
 * Пагинация: default 20 items per page.
 */
export function AuditLogsTable({
  data,
  isLoading,
  filter,
  onPaginationChange,
}: AuditLogsTableProps) {
  // Вычисление текущей страницы
  const currentPage = Math.floor((filter.offset || 0) / (filter.limit || DEFAULT_PAGE_SIZE)) + 1

  /**
   * Рендер expandable row (AC3).
   * Показывает:
   * - Entity ID
   * - Correlation ID
   * - IP Address
   * - Before/After JSON comparison (для updated)
   */
  const expandedRowRender = (record: AuditLogEntry) => (
    <div style={{ padding: '16px 0' }}>
      <Descriptions column={3} size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Entity ID">
          <Text code copyable>
            {record.entityId}
          </Text>
        </Descriptions.Item>
        <Descriptions.Item label="Correlation ID">
          <Text code copyable>
            {record.correlationId || '—'}
          </Text>
        </Descriptions.Item>
        <Descriptions.Item label="IP Address">
          {record.ipAddress || '—'}
        </Descriptions.Item>
      </Descriptions>

      {/* Story 10.8: Передаём весь changes объект для поддержки generic режима */}
      {record.changes && (
        <ChangesViewer
          changes={record.changes}
          action={record.action}
        />
      )}
    </div>
  )

  /**
   * Генерация ссылки на сущность.
   * Для deleted сущностей ссылка не показывается (они могут не существовать).
   */
  const renderEntityLink = (record: AuditLogEntry) => {
    const entityId = record.entityId
    const truncatedId = `${entityId.slice(0, 8)}...`

    // Для удалённых сущностей не показываем ссылку (они могут не существовать)
    if (record.action === 'deleted') {
      return <Text code>{truncatedId}</Text>
    }

    // Ссылка на маршрут
    if (record.entityType === 'route') {
      return (
        <Link to={`/routes/${entityId}`}>
          <Text code>{truncatedId}</Text>
        </Link>
      )
    }

    // Для других типов просто показываем ID
    return <Text code>{truncatedId}</Text>
  }

  // Определение колонок таблицы
  const columns: ColumnsType<AuditLogEntry> = useMemo(
    () => [
      {
        title: 'Timestamp',
        dataIndex: 'timestamp',
        key: 'timestamp',
        width: 180,
        render: (timestamp: string) =>
          dayjs(timestamp).format('MMM D, YYYY, HH:mm'),
      },
      {
        title: 'Действие',
        dataIndex: 'action',
        key: 'action',
        width: 120,
        render: (action: AuditLogEntry['action']) => (
          <Tag color={AUDIT_ACTION_COLORS[action]}>
            {AUDIT_ACTION_LABELS[action]}
          </Tag>
        ),
      },
      {
        title: 'Тип',
        dataIndex: 'entityType',
        key: 'entityType',
        width: 120,
        render: (entityType: AuditLogEntry['entityType']) =>
          ENTITY_TYPE_LABELS[entityType] || entityType,
      },
      {
        title: 'Сущность',
        key: 'entity',
        width: 150,
        render: (_: unknown, record: AuditLogEntry) => renderEntityLink(record),
      },
      {
        title: 'Пользователь',
        dataIndex: ['user', 'username'],
        key: 'user',
        width: 150,
      },
      Table.EXPAND_COLUMN,
    ],
    []
  )

  /**
   * Обработчик изменения пагинации.
   */
  const handleTableChange = (pagination: TablePaginationConfig) => {
    const newPage = pagination.current || 1
    const newPageSize = pagination.pageSize || DEFAULT_PAGE_SIZE
    const newOffset = (newPage - 1) * newPageSize

    onPaginationChange(newOffset, newPageSize)
  }

  // Skeleton loading (AC7)
  if (isLoading && !data) {
    return (
      <div>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    )
  }

  return (
    <Table
      dataSource={data?.items}
      columns={columns}
      rowKey="id"
      loading={isLoading}
      expandable={{
        expandedRowRender,
        rowExpandable: () => true,
        expandIcon: ({ expanded, onExpand, record }) =>
          expanded ? (
            <CompressOutlined
              style={{ cursor: 'pointer', color: '#1890ff' }}
              onClick={(e) => onExpand(record, e)}
            />
          ) : (
            <ExpandOutlined
              style={{ cursor: 'pointer', color: '#1890ff' }}
              onClick={(e) => onExpand(record, e)}
            />
          ),
      }}
      pagination={{
        current: currentPage,
        pageSize: filter.limit || DEFAULT_PAGE_SIZE,
        total: data?.total,
        showSizeChanger: true,
        showTotal: (total) => `Всего ${total} записей`,
        pageSizeOptions: ['10', '20', '50', '100'],
      }}
      onChange={handleTableChange}
    />
  )
}
