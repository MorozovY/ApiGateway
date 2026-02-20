// Таблица upstream сервисов для Integrations Report (Story 7.6, AC3, AC4)
import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Table, Input, Button, Space, Empty, Spin, Alert } from 'antd'
import { SearchOutlined, EyeOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useUpstreams } from '../hooks/useUpstreams'
import type { UpstreamSummary } from '../types/audit.types'

/**
 * Склонение слова "маршрут" в зависимости от числа.
 */
function pluralizeRoutes(count: number): string {
  const lastTwo = count % 100
  const lastOne = count % 10

  if (lastTwo >= 11 && lastTwo <= 19) {
    return `${count} маршрутов`
  }

  if (lastOne === 1) {
    return `${count} маршрут`
  }

  if (lastOne >= 2 && lastOne <= 4) {
    return `${count} маршрута`
  }

  return `${count} маршрутов`
}

interface UpstreamsTableProps {
  /** Callback при клике на upstream для навигации (опционально) */
  onUpstreamClick?: (host: string) => void
}

/**
 * Таблица upstream сервисов.
 *
 * Особенности:
 * - Колонки: Upstream Host, Route Count, Actions (View)
 * - Click-through на /routes?upstream={host} (AC4)
 * - Sorting по routeCount (default DESC)
 * - Frontend search по host name
 * - Empty state для пустого списка
 */
export function UpstreamsTable({ onUpstreamClick }: UpstreamsTableProps) {
  const navigate = useNavigate()
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

  // Обработчик клика на upstream — навигация на /routes?upstream={host}
  const handleViewRoutes = (host: string) => {
    if (onUpstreamClick) {
      onUpstreamClick(host)
    } else {
      navigate(`/routes?upstream=${encodeURIComponent(host)}`)
    }
  }

  // Определение колонок таблицы
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
    {
      title: 'Действия',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleViewRoutes(record.host)}
        >
          Маршруты
        </Button>
      ),
    },
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

      {/* Таблица */}
      <Table
        dataSource={filteredUpstreams}
        columns={columns}
        rowKey="host"
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
