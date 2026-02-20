// Таблица топ-маршрутов для MetricsPage (Story 6.5, AC5)
// Story 7.0: Обновлено для нового формата API (value + metric)
import { Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { TopRoute } from '../types/metrics.types'

/**
 * Колонки таблицы топ-маршрутов.
 *
 * Story 7.0: API теперь возвращает value (total requests) вместо детальных метрик.
 * Показываем только path и количество запросов.
 */
const columns: ColumnsType<TopRoute> = [
  {
    title: 'Path',
    dataIndex: 'path',
    key: 'path',
    ellipsis: true,
  },
  {
    title: 'Total Requests',
    dataIndex: 'value',
    key: 'value',
    render: (value: number) => value?.toFixed(0) ?? '0',
    sorter: (a, b) => (a.value ?? 0) - (b.value ?? 0),
    defaultSortOrder: 'descend',
  },
]

interface TopRoutesTableProps {
  /** Данные топ-маршрутов */
  data: TopRoute[]
  /** Состояние загрузки */
  loading?: boolean
}

/**
 * Таблица топ-маршрутов с метриками.
 */
export function TopRoutesTable({ data, loading }: TopRoutesTableProps) {
  return (
    <Table
      columns={columns}
      dataSource={data}
      rowKey="routeId"
      loading={loading}
      pagination={false}
      size="small"
      data-testid="top-routes-table"
    />
  )
}

export default TopRoutesTable
