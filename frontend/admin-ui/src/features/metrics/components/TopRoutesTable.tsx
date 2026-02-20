// Таблица топ-маршрутов для MetricsPage (Story 6.5, AC5)
import { Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { TopRoute } from '../types/metrics.types'

/**
 * Возвращает цвет для Error Rate по порогам.
 */
function getErrorRateColor(rate: number): string {
  if (rate < 0.01) return 'green'
  if (rate < 0.05) return 'orange'
  return 'red'
}

/**
 * Колонки таблицы топ-маршрутов.
 */
const columns: ColumnsType<TopRoute> = [
  {
    title: 'Path',
    dataIndex: 'path',
    key: 'path',
    ellipsis: true,
  },
  {
    title: 'RPS',
    dataIndex: 'requestsPerSecond',
    key: 'requestsPerSecond',
    render: (value: number) => value.toFixed(1),
    sorter: (a, b) => a.requestsPerSecond - b.requestsPerSecond,
  },
  {
    title: 'Avg Latency',
    dataIndex: 'avgLatencyMs',
    key: 'avgLatencyMs',
    render: (value: number) => `${value} ms`,
    sorter: (a, b) => a.avgLatencyMs - b.avgLatencyMs,
  },
  {
    title: 'Error Rate',
    dataIndex: 'errorRate',
    key: 'errorRate',
    render: (value: number) => (
      <Tag color={getErrorRateColor(value)}>{(value * 100).toFixed(2)}%</Tag>
    ),
    sorter: (a, b) => a.errorRate - b.errorRate,
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
