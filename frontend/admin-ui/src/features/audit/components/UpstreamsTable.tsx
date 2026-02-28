// Таблица upstream сервисов для Integrations Report (Story 7.6, AC3, AC4)
// Story 11.1: Добавлены expandable rows для просмотра маршрутов inline
import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Table, Input, Space, Empty, Spin, Alert, Tag, Skeleton } from 'antd'
import { SearchOutlined, ExpandOutlined, CompressOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useUpstreams } from '../hooks/useUpstreams'
import { useUpstreamRoutes } from '../hooks/useUpstreamRoutes'
import type { UpstreamSummary } from '../types/audit.types'
import type { Route, RouteStatus } from '@features/routes/types/route.types'
import { pluralizeRoutes } from '@shared/utils/pluralize'
import { STATUS_COLORS, STATUS_LABELS } from '@shared/constants'

/**
 * Компонент для отображения маршрутов в expandable row (Story 11.1, AC1, AC3).
 *
 * Показывает nested table с колонками: path, status, methods, rate limit.
 * Состояния: loading (Skeleton), empty, error, данные.
 */
function ExpandedRoutes({ host }: { host: string }) {
  const { data, isLoading, error } = useUpstreamRoutes(host)

  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 3 }} data-testid="routes-skeleton" />
  }

  if (error) {
    return <Alert type="error" message="Ошибка загрузки маршрутов" showIcon />
  }

  if (!data?.items?.length) {
    return <Empty description="Нет маршрутов для этого upstream" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  const routeColumns: ColumnsType<Route> = [
    {
      title: 'Path',
      dataIndex: 'path',
      key: 'path',
      width: 250,
      render: (path: string, record: Route) => (
        <Link to={`/routes/${record.id}`}>{path}</Link>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status: RouteStatus) => (
        <Tag color={STATUS_COLORS[status]}>
          {STATUS_LABELS[status]}
        </Tag>
      ),
    },
    {
      title: 'Методы',
      dataIndex: 'methods',
      key: 'methods',
      width: 150,
      render: (methods: string[]) => methods?.join(', ') || '—',
    },
    {
      title: 'Rate Limit',
      key: 'rateLimit',
      width: 150,
      render: (_: unknown, record: Route) => record.rateLimit?.name || '—',
    },
  ]

  return (
    <div style={{ padding: '16px 0' }}>
      <Table
        dataSource={data.items}
        columns={routeColumns}
        rowKey="id"
        pagination={false}
        size="small"
        data-testid="nested-routes-table"
      />
    </div>
  )
}

/**
 * Таблица upstream сервисов.
 *
 * Особенности:
 * - Колонки: Upstream Host, Route Count
 * - Expandable rows с маршрутами (Story 11.1)
 * - Sorting по routeCount (default DESC)
 * - Frontend search по host name
 * - Empty state для пустого списка
 */
export function UpstreamsTable() {
  const { data, isLoading, error } = useUpstreams()
  const [searchText, setSearchText] = useState('')

  // Фильтрация по поисковому запросу (frontend filter)
  const filteredUpstreams = useMemo(() => {
    if (!data?.upstreams) return []
    if (!searchText) return data.upstreams

    const lowerSearch = searchText.toLowerCase()
    return data.upstreams.filter((upstream) =>
      upstream.host.toLowerCase().includes(lowerSearch)
    )
  }, [data?.upstreams, searchText])

  // Определение колонок таблицы (без колонки "Действия")
  // Table.EXPAND_COLUMN используется для позиционирования expand icon справа
  const columns: ColumnsType<UpstreamSummary> = [
    {
      title: 'Upstream Host',
      dataIndex: 'host',
      key: 'host',
      sorter: (a, b) => a.host.localeCompare(b.host),
    },
    {
      title: 'Маршрутов',
      dataIndex: 'routeCount',
      key: 'routeCount',
      sorter: (a, b) => a.routeCount - b.routeCount,
      defaultSortOrder: 'descend',
      render: (count: number) => pluralizeRoutes(count),
      width: 150,
    },
    Table.EXPAND_COLUMN, // Expand icon справа
  ]

  // Состояние загрузки
  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 0' }}>
        <Spin size="large" />
      </div>
    )
  }

  // Ошибка загрузки
  if (error) {
    return (
      <Alert
        type="error"
        message="Ошибка загрузки"
        description={error instanceof Error ? error.message : 'Не удалось загрузить список upstream сервисов'}
        showIcon
      />
    )
  }

  // Пустое состояние (AC9)
  if (!data?.upstreams?.length) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="Нет данных о внешних сервисах"
      >
        <p style={{ color: '#8c8c8c' }}>
          Создайте маршруты с upstream URL для отображения интеграций
        </p>
      </Empty>
    )
  }

  return (
    <div>
      {/* Панель поиска */}
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="Поиск по host..."
          allowClear
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          style={{ width: 300 }}
        />
      </Space>

      {/* Таблица с expandable rows (Story 11.1, AC1, AC2) */}
      <Table
        dataSource={filteredUpstreams}
        columns={columns}
        rowKey="host"
        expandable={{
          expandedRowRender: (record) => <ExpandedRoutes host={record.host} />,
          rowExpandable: () => true,
          expandIcon: ({ expanded, onExpand, record }) =>
            expanded ? (
              <CompressOutlined
                style={{ cursor: 'pointer', color: '#1890ff' }}
                onClick={(e) => onExpand(record, e)}
                data-testid="collapse-icon"
              />
            ) : (
              <ExpandOutlined
                style={{ cursor: 'pointer', color: '#1890ff' }}
                onClick={(e) => onExpand(record, e)}
                data-testid="expand-icon"
              />
            ),
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `Всего ${total} upstream сервисов`,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
      />
    </div>
  )
}
