// Таблица маршрутов с пагинацией, фильтрацией и поиском (Story 3.4, расширена в Story 5.5; Story 8.8)
import { useMemo, useCallback, useState, useEffect, useRef } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { Table, Button, Space, Popconfirm, Input, Select, Tooltip, Alert } from 'antd'
import { Tag } from 'antd'
import {
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  SearchOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { useRoutes, useDeleteRoute } from '../hooks/useRoutes'
import type { Route, RouteStatus, RouteListParams } from '../types/route.types'
import { useAuth } from '@features/auth'
import { pluralizeRoutes } from '@shared/utils/pluralize'
import { FilterChips, type FilterChip } from '@shared/components/FilterChips'

// Настройка dayjs для относительного времени
dayjs.extend(relativeTime)
dayjs.locale('ru')

/**
 * Размер страницы по умолчанию.
 */
const DEFAULT_PAGE_SIZE = 20

/**
 * Подсветка поискового термина в тексте.
 *
 * Возвращает React элемент с подсвеченным текстом.
 */
function highlightSearchTerm(text: string, searchTerm: string | undefined): React.ReactNode {
  if (!searchTerm || !text) {
    return text
  }

  const lowerText = text.toLowerCase()
  const lowerSearch = searchTerm.toLowerCase()
  const index = lowerText.indexOf(lowerSearch)

  if (index === -1) {
    return text
  }

  const before = text.slice(0, index)
  const match = text.slice(index, index + searchTerm.length)
  const after = text.slice(index + searchTerm.length)

  return (
    <>
      {before}
      <mark style={{ backgroundColor: '#ffc069', padding: 0 }}>{match}</mark>
      {after}
    </>
  )
}

/**
 * Задержка debounce для поиска (мс).
 */
const SEARCH_DEBOUNCE_MS = 300

// Импортируем shared константы для статусов и методов
import { STATUS_COLORS, STATUS_LABELS, METHOD_COLORS } from '@shared/constants'

/**
 * Опции для фильтра по статусу.
 */
const STATUS_OPTIONS = [
  { value: '', label: 'Все статусы' },
  { value: 'draft', label: STATUS_LABELS.draft },
  { value: 'pending', label: STATUS_LABELS.pending },
  { value: 'published', label: STATUS_LABELS.published },
  { value: 'rejected', label: STATUS_LABELS.rejected },
]

/**
 * Props для RoutesTable.
 */
interface RoutesTableProps {
  onEdit?: (route: Route) => void
}

/**
 * Таблица маршрутов с пагинацией, фильтрацией и поиском.
 *
 * Синхронизирует фильтры с URL query params для bookmarking.
 */
export function RoutesTable({ onEdit }: RoutesTableProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user } = useAuth()

  // Локальное состояние для поискового поля (для debounce)
  const [searchInput, setSearchInput] = useState(searchParams.get('search') || '')
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Извлечение параметров из URL (расширено в Story 7.6 для upstream filter)
  const params: RouteListParams = useMemo(() => ({
    offset: Number(searchParams.get('offset')) || 0,
    limit: Number(searchParams.get('limit')) || DEFAULT_PAGE_SIZE,
    status: (searchParams.get('status') as RouteStatus) || undefined,
    search: searchParams.get('search') || undefined,
    upstream: searchParams.get('upstream') || undefined,
  }), [searchParams])

  // Вычисление текущей страницы
  const currentPage = Math.floor(params.offset! / params.limit!) + 1

  // Загрузка данных
  const { data, isLoading, error } = useRoutes(params)

  // Мутация для удаления
  const deleteMutation = useDeleteRoute()

  /**
   * Обновление URL параметров.
   */
  const updateParams = useCallback((updates: Record<string, string | undefined>) => {
    const newParams = new URLSearchParams(searchParams)

    Object.entries(updates).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        newParams.set(key, value)
      } else {
        newParams.delete(key)
      }
    })

    setSearchParams(newParams, { replace: true })
  }, [searchParams, setSearchParams])

  /**
   * Обработчик изменения пагинации.
   */
  const handleTableChange = useCallback((pagination: TablePaginationConfig) => {
    const newPage = pagination.current || 1
    const newPageSize = pagination.pageSize || DEFAULT_PAGE_SIZE
    const newOffset = (newPage - 1) * newPageSize

    updateParams({
      offset: newOffset > 0 ? String(newOffset) : undefined,
      limit: newPageSize !== DEFAULT_PAGE_SIZE ? String(newPageSize) : undefined,
    })
  }, [updateParams])

  /**
   * Обработчик изменения поиска (с debounce).
   * Применяет поиск только после задержки.
   */
  const handleSearchInputChange = useCallback((value: string) => {
    setSearchInput(value)

    // Очистка предыдущего таймера
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current)
    }

    // Установка нового таймера
    debounceTimerRef.current = setTimeout(() => {
      updateParams({
        search: value || undefined,
        offset: undefined,
      })
    }, SEARCH_DEBOUNCE_MS)
  }, [updateParams])

  /**
   * Немедленное применение поиска (по Enter или клику).
   */
  const handleSearchSubmit = useCallback((value: string) => {
    // Очистка таймера debounce
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current)
    }
    updateParams({
      search: value || undefined,
      offset: undefined,
    })
  }, [updateParams])

  // Очистка таймера при размонтировании
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }
    }
  }, [])

  /**
   * Обработчик изменения фильтра статуса.
   */
  const handleStatusChange = useCallback((value: string) => {
    // Сброс на первую страницу при фильтрации
    updateParams({
      status: value || undefined,
      offset: undefined,
    })
  }, [updateParams])

  /**
   * Очистка всех фильтров.
   */
  const handleClearFilters = useCallback(() => {
    setSearchInput('')
    setSearchParams(new URLSearchParams(), { replace: true })
  }, [setSearchParams])

  /**
   * Обработчик удаления маршрута.
   */
  const handleDelete = useCallback((id: string) => {
    deleteMutation.mutate(id)
  }, [deleteMutation])

  /**
   * Проверка, можно ли редактировать/удалять маршрут.
   * Draft маршруты могут редактировать только их создатели.
   * Admin может редактировать/удалять любые draft маршруты (Story 10.4).
   */
  const canModify = useCallback((route: Route): boolean => {
    if (route.status !== 'draft') return false
    // Developer может редактировать/удалять только свои маршруты
    // Admin может редактировать/удалять любые draft маршруты
    return route.createdBy === user?.userId || user?.role === 'admin'
  }, [user?.userId, user?.role])

  // Определение колонок таблицы
  const columns: ColumnsType<Route> = useMemo(() => [
    {
      title: 'Path',
      dataIndex: 'path',
      key: 'path',
      render: (path: string, record: Route) => (
        <Link to={`/routes/${record.id}`}>
          {highlightSearchTerm(path, params.search)}
        </Link>
      ),
      sorter: (a, b) => a.path.localeCompare(b.path),
    },
    {
      title: 'Upstream URL',
      dataIndex: 'upstreamUrl',
      key: 'upstreamUrl',
      ellipsis: true,
      // Story 8.5: подсветка поискового термина в upstream URL
      render: (upstreamUrl: string) => highlightSearchTerm(upstreamUrl, params.search),
    },
    {
      title: 'Methods',
      dataIndex: 'methods',
      key: 'methods',
      render: (methods: string[]) => (
        <Space size={4} wrap>
          {methods.map(method => (
            <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
              {method}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Rate Limit',
      dataIndex: ['rateLimit', 'name'],
      key: 'rateLimit',
      // Story 8.4: отображаем "{name} ({requestsPerSecond}/s)"
      render: (_: unknown, record: Route) => {
        if (!record.rateLimit) {
          return '—'
        }
        return `${record.rateLimit.name} (${record.rateLimit.requestsPerSecond}/s)`
      },
      width: 180,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: RouteStatus) => (
        <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status]}</Tag>
      ),
    },
    {
      title: 'Author',
      dataIndex: 'creatorUsername',
      key: 'creatorUsername',
      render: (username: string | undefined) => username || '—',
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt: string) => (
        <Tooltip title={dayjs(createdAt).format('DD.MM.YYYY HH:mm')}>
          {dayjs(createdAt).fromNow()}
        </Tooltip>
      ),
      sorter: (a, b) => dayjs(a.createdAt).unix() - dayjs(b.createdAt).unix(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          {canModify(record) ? (
            <>
              <Tooltip title="Редактировать">
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => onEdit?.(record) ?? navigate(`/routes/${record.id}/edit`)}
                />
              </Tooltip>
              <Popconfirm
                title="Удалить маршрут?"
                description="Это действие нельзя отменить"
                onConfirm={() => handleDelete(record.id)}
                okText="Да"
                cancelText="Нет"
              >
                <Tooltip title="Удалить">
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    loading={deleteMutation.isPending}
                  />
                </Tooltip>
              </Popconfirm>
            </>
          ) : (
            <Tooltip title="Просмотр">
              <Button
                type="text"
                icon={<EyeOutlined />}
                onClick={() => navigate(`/routes/${record.id}`)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ], [canModify, onEdit, navigate, handleDelete, deleteMutation.isPending])

  // Проверка наличия активных фильтров (расширено в Story 7.6 для upstream)
  const hasActiveFilters = !!(params.search || params.status || params.upstream)

  return (
    <div>
      {/* Панель фильтров */}
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Input.Search
            placeholder="Поиск по path, upstream..."
            allowClear
            value={searchInput}
            onChange={(e) => handleSearchInputChange(e.target.value)}
            onSearch={handleSearchSubmit}
            style={{ width: 280 }}
            prefix={<SearchOutlined />}
            data-testid="routes-search-input"
          />
          <Select
            value={params.status || ''}
            onChange={handleStatusChange}
            options={STATUS_OPTIONS}
            style={{ width: 150 }}
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

      </Space>

      {/* FilterChips для активных фильтров (Story 8.8) */}
      <FilterChips
        chips={[
          ...(params.search
            ? [{
                key: 'search',
                label: `Поиск: ${params.search}`,
                onClose: () => {
                  setSearchInput('')
                  updateParams({ search: undefined })
                },
              } as FilterChip]
            : []),
          ...(params.status
            ? [{
                key: 'status',
                label: STATUS_LABELS[params.status],
                color: STATUS_COLORS[params.status],
                onClose: () => updateParams({ status: undefined }),
              } as FilterChip]
            : []),
          ...(params.upstream
            ? [{
                key: 'upstream',
                label: `Upstream: ${params.upstream}`,
                color: 'purple',
                onClose: () => updateParams({ upstream: undefined }),
              } as FilterChip]
            : []),
        ]}
      />

      {/* Сообщение об ошибке */}
      {error && (
        <Alert
          message="Ошибка загрузки"
          description={error instanceof Error ? error.message : 'Не удалось загрузить список маршрутов'}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Таблица */}
      <Table
        dataSource={data?.items}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: currentPage,
          pageSize: params.limit,
          total: data?.total,
          showSizeChanger: true,
          showTotal: (total) => `Всего ${pluralizeRoutes(total)}`,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        onChange={handleTableChange}
      />
    </div>
  )
}
