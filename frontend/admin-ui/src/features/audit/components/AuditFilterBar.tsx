// Панель фильтров для аудит-логов (Story 7.5, AC2)
import { useCallback, useState, useEffect, useRef } from 'react'
import { Space, DatePicker, Select, Button } from 'antd'
import { CloseCircleOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { useQuery } from '@tanstack/react-query'
import { fetchUserOptions } from '@features/users/api/usersApi'
import type { AuditFilter, AuditAction, AuditEntityType } from '../types/audit.types'
import {
  AUDIT_ACTION_OPTIONS,
  ENTITY_TYPE_OPTIONS,
  FILTER_DEBOUNCE_MS,
} from '../config/auditConfig'

const { RangePicker } = DatePicker

/**
 * Presets для DatePicker (AC2).
 */
const DATE_PRESETS: { label: string; value: [Dayjs, Dayjs] }[] = [
  { label: 'Последние 7 дней', value: [dayjs().subtract(7, 'd'), dayjs()] },
  { label: 'Последние 30 дней', value: [dayjs().subtract(30, 'd'), dayjs()] },
  { label: 'Этот месяц', value: [dayjs().startOf('month'), dayjs()] },
]

interface AuditFilterBarProps {
  filter: AuditFilter
  onFilterChange: (updates: Partial<AuditFilter>) => void
  onClearFilters: () => void
}

/**
 * Панель фильтров для аудит-логов.
 *
 * Включает:
 * - Date range picker с presets
 * - Select для пользователя (async load)
 * - Select для типа сущности
 * - Select mode="multiple" для действия (AC2)
 * - Clear Filters button
 *
 * Все фильтры применяются с debounce 300ms (AC2).
 */
export function AuditFilterBar({
  filter,
  onFilterChange,
  onClearFilters,
}: AuditFilterBarProps) {
  // Локальное состояние для debounce (все фильтры применяются с debounce — AC2)
  const [localAction, setLocalAction] = useState<AuditAction[] | undefined>(filter.action)
  const [localUserId, setLocalUserId] = useState<string | undefined>(filter.userId)
  const [localEntityType, setLocalEntityType] = useState<AuditEntityType | undefined>(filter.entityType)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Загрузка списка пользователей для dropdown (Story 8.6)
  // Используем /api/v1/users/options — доступен для security и admin ролей
  const { data: userOptionsData, isError: isUserOptionsError } = useQuery({
    queryKey: ['users-options'],
    queryFn: fetchUserOptions,
    staleTime: 5 * 60 * 1000, // 5 минут
  })

  // Опции для select пользователей (отсортированы по алфавиту на backend)
  // При ошибке загрузки показываем пустой список (dropdown будет работать, но без опций)
  const userOptions = isUserOptionsError
    ? []
    : userOptionsData?.items.map((user) => ({
        value: user.id,
        label: user.username,
      })) || []

  // Синхронизация локального состояния с props
  useEffect(() => {
    setLocalAction(filter.action)
  }, [filter.action])

  useEffect(() => {
    setLocalUserId(filter.userId)
  }, [filter.userId])

  useEffect(() => {
    setLocalEntityType(filter.entityType)
  }, [filter.entityType])

  // Очистка debounce при размонтировании
  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [])

  /**
   * Вспомогательная функция для применения изменений с debounce (AC2).
   */
  const applyFilterWithDebounce = useCallback(
    (updates: Partial<AuditFilter>) => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }

      debounceRef.current = setTimeout(() => {
        onFilterChange({
          ...updates,
          offset: 0, // Сброс на первую страницу при изменении фильтров
        })
      }, FILTER_DEBOUNCE_MS)
    },
    [onFilterChange]
  )

  /**
   * Обработчик изменения date range с debounce (AC2).
   */
  const handleDateRangeChange = useCallback(
    (dates: [Dayjs | null, Dayjs | null] | null) => {
      applyFilterWithDebounce({
        dateFrom: dates?.[0]?.format('YYYY-MM-DD'),
        dateTo: dates?.[1]?.format('YYYY-MM-DD'),
      })
    },
    [applyFilterWithDebounce]
  )

  /**
   * Обработчик изменения пользователя с debounce (AC2).
   */
  const handleUserChange = useCallback(
    (value: string | undefined) => {
      setLocalUserId(value)
      applyFilterWithDebounce({ userId: value })
    },
    [applyFilterWithDebounce]
  )

  /**
   * Обработчик изменения типа сущности с debounce (AC2).
   */
  const handleEntityTypeChange = useCallback(
    (value: AuditEntityType | undefined) => {
      setLocalEntityType(value)
      applyFilterWithDebounce({ entityType: value })
    },
    [applyFilterWithDebounce]
  )

  /**
   * Обработчик изменения действия (multi-select) с debounce (AC2).
   */
  const handleActionChange = useCallback(
    (value: AuditAction[]) => {
      const actionValue = value.length > 0 ? value : undefined
      setLocalAction(actionValue)
      applyFilterWithDebounce({ action: actionValue })
    },
    [applyFilterWithDebounce]
  )

  // Проверка наличия активных фильтров
  const hasActiveFilters = !!(
    filter.userId ||
    filter.action ||
    filter.entityType ||
    filter.dateFrom ||
    filter.dateTo
  )

  // Значение date range для RangePicker
  const dateRangeValue: [Dayjs, Dayjs] | null =
    filter.dateFrom && filter.dateTo
      ? [dayjs(filter.dateFrom), dayjs(filter.dateTo)]
      : null

  return (
    <Space wrap style={{ marginBottom: 16, width: '100%' }}>
      <RangePicker
        presets={DATE_PRESETS}
        format="YYYY-MM-DD"
        value={dateRangeValue}
        onChange={handleDateRangeChange}
        placeholder={['Дата от', 'Дата до']}
        allowClear
      />

      <Select
        placeholder="Пользователь"
        allowClear
        showSearch
        optionFilterProp="label"
        value={localUserId}
        onChange={handleUserChange}
        options={userOptions}
        style={{ width: 180 }}
      />

      <Select
        placeholder="Тип сущности"
        allowClear
        value={localEntityType}
        onChange={handleEntityTypeChange}
        options={ENTITY_TYPE_OPTIONS}
        style={{ width: 150 }}
      />

      <Select
        mode="multiple"
        placeholder="Действие"
        allowClear
        value={localAction}
        onChange={handleActionChange}
        options={AUDIT_ACTION_OPTIONS}
        style={{ minWidth: 200 }}
        maxTagCount="responsive"
      />

      {hasActiveFilters && (
        <Button
          type="text"
          icon={<CloseCircleOutlined />}
          onClick={onClearFilters}
        >
          Сбросить фильтры
        </Button>
      )}
    </Space>
  )
}
