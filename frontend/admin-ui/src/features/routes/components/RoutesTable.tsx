// Таблица маршрутов с пагинацией, фильтрацией и поиском (Story 3.4, расширена в Story 5.5; Story 8.8; Story 16.4 — responsive; Story 16.5 — empty state)
import { useMemo, useCallback, useState, useEffect, useRef } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { Table, Button, Space, Popconfirm, Input, Select, Tooltip, Alert, Descriptions } from 'antd'
import { Tag } from 'antd'
import {
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  SearchOutlined,
  CloseCircleOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons'
import { CollapsibleMethods } from './CollapsibleMethods'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { useRoutes, useDeleteRoute } from '../hooks/useRoutes'
import type { Route, RouteStatus, RouteListParams } from '../types/route.types'
import { useAuth } from '@features/auth'
import { pluralizeRoutes, canModify as canModifyFn } from '@shared/utils'
import { FilterChips, type FilterChip } from '@shared/components/FilterChips'
import { EmptyState } from '@shared/components/EmptyState'

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

// Импортируем shared константы для статусов (METHOD_COLORS используется в CollapsibleMethods)
import { STATUS_COLORS, STATUS_LABELS } from '@shared/constants'

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
   * Story 11.6: используем централизованный helper.
   */
  const canModify = useCallback((route: Route): boolean => {
    return canModifyFn(route, user ?? undefined)
  }, [user])

  // Определение колонок таблицы
  const columns: ColumnsType<Route> = useMemo(() => [
    {
      title: 'Путь',
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
      title: 'URL сервиса',
      dataIndex: 'upstreamUrl',
      key: 'upstreamUrl',
      ellipsis: true,
      // Story 8.5: подсветка поискового термина в upstream URL
      render: (upstreamUrl: string) => highlightSearchTerm(upstreamUrl, params.search),
    },
    {
      title: 'Методы',
      dataIndex: 'methods',
      key: 'methods',
      // Story 16.4 AC3: сворачивание методов если >3
      render: (methods: string[]) => <CollapsibleMethods methods={methods} />,
    },
    {
      title: 'Лимит',
      dataIndex: ['rateLimit', 'name'],
      key: 'rateLimit',
      // Story 8.4: отображаем "{name} ({requestsPerSecond}/s)"
      // Story 16.4 AC1: скрываем на экранах < xl (1200px)
      responsive: ['xl'],
      render: (_: unknown, record: Route) => {
        if (!record.rateLimit) {
          return '—'
        }
        return `${record.rateLimit.name} (${record.rateLimit.requestsPerSecond}/s)`
      },
      width: 180,
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      render: (status: RouteStatus) => (
        <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status]}</Tag>
      ),
    },
    {
      // Story 12.7: Auth badge (Защищён/Публичный)
      // Story 16.4 AC1: скрываем на экранах < xl (1200px)
      title: 'Авторизация',
      dataIndex: 'authRequired',
      key: 'authRequired',
      width: 120,
      responsive: ['xl'],
      render: (authRequired: boolean) => (
        <Tag color={authRequired ? 'green' : 'default'}>
          {authRequired ? (
            <><LockOutlined /> Защищён</>
          ) : (
            <><UnlockOutlined /> Публичный</>
          )}
        </Tag>
      ),
    },
    {
      title: 'Автор',
      dataIndex: 'creatorUsername',
      key: 'creatorUsername',
      // Story 16.4 AC1: скрываем на экранах < xl (1200px)
      responsive: ['xl'],
      render: (username: string | undefined) => username || '—',
    },
    {
      title: 'Создано',
      dataIndex: 'createdAt',
      key: 'createdAt',
      // Story 16.4 AC1: скрываем на экранах < lg (992px)
      responsive: ['lg'],
      render: (createdAt: string) => (
        <Tooltip title={dayjs(createdAt).format('DD.MM.YYYY HH:mm')}>
          {dayjs(createdAt).fromNow()}
        </Tooltip>
      ),
      sorter: (a, b) => dayjs(a.createdAt).unix() - dayjs(b.createdAt).unix(),
    },
    {
      title: 'Действия',
      key: 'actions',
      // Story 16.4 AC4: touch-friendly размеры кнопок (min 44x44px)
      render: (_, record) => (
        <Space>
          {canModify(record) ? (
            <>
              <Tooltip title="Редактировать">
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => onEdit?.(record) ?? navigate(`/routes/${record.id}/edit`)}
                  style={{ minWidth: 44, minHeight: 44 }}
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
                    style={{ minWidth: 44, minHeight: 44 }}
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
                style={{ minWidth: 44, minHeight: 44 }}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ], [canModify, onEdit, navigate, handleDelete, deleteMutation.isPending])

  // Проверка наличия активных фильтров (расширено в Story 7.6 для upstream)
  const hasActiveFilters = !!(params.search || params.status || params.upstream)

  /**
   * Story 16.4 AC1: Expandable row для просмотра скрытых данных на маленьких экранах.
   *
   * Показывает Rate Limit, Author, Created, Auth status.
   */
  const expandedRowRender = useCallback((record: Route) => {
    return (
      <Descriptions size="small" column={1} style={{ paddingLeft: 48 }}>
        <Descriptions.Item label="Лимит">
          {record.rateLimit
            ? `${record.rateLimit.name} (${record.rateLimit.requestsPerSecond}/s)`
            : '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Авторизация">
          <Tag color={record.authRequired ? 'green' : 'default'}>
            {record.authRequired ? (
              <><LockOutlined /> Защищён</>
            ) : (
              <><UnlockOutlined /> Публичный</>
            )}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Автор">
          {record.creatorUsername || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Создано">
          <Tooltip title={dayjs(record.createdAt).format('DD.MM.YYYY HH:mm')}>
            <span>{dayjs(record.createdAt).fromNow()}</span>
          </Tooltip>
        </Descriptions.Item>
      </Descriptions>
    )
  }, [])

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
      {/* Story 16.4 AC1: expandable row для просмотра скрытых данных */}
      {/* Story 16.5 AC1: кастомный empty state */}
      <Table
        dataSource={data?.items}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        locale={{
          emptyText: (
            <EmptyState
              title="Маршруты ещё не созданы"
              description="Создайте первый маршрут для начала работы"
              action={{
                label: 'Создать маршрут',
                onClick: () => navigate('/routes/new'),
              }}
            />
          ),
        }}
        pagination={{
          current: currentPage,
          pageSize: params.limit,
          total: data?.total,
          showSizeChanger: true,
          showTotal: (total) => `Всего ${pluralizeRoutes(total)}`,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        onChange={handleTableChange}
        expandable={{
          expandedRowRender,
          rowExpandable: () => true,
        }}
      />
    </div>
  )
}
