// Страница аудит-логов (Story 7.5, AC1-AC7; Story 15.4 — добавлен PageInfoBlock; Story 16.5 — empty state)
import { useMemo, useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams, Navigate } from 'react-router-dom'
import { Card, Typography, Button, Space, Alert, App } from 'antd'
import { DownloadOutlined, AuditOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { isDeveloper } from '@shared/utils'
import { useAuditLogs } from '../hooks/useAuditLogs'
import { fetchAllAuditLogsForExport } from '../api/auditApi'
import { AuditFilterBar } from './AuditFilterBar'
import { AuditLogsTable } from './AuditLogsTable'
import { downloadAuditCsv } from '../utils/exportCsv'
import { DEFAULT_PAGE_SIZE, AUDIT_ACTION_LABELS } from '../config/auditConfig'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import { EmptyState } from '@shared/components/EmptyState'
import type { AuditFilter, AuditAction, AuditEntityType } from '../types/audit.types'

/**
 * Валидирует и фильтрует action values из URL.
 * Защищает от невалидных значений при манипуляции URL.
 */
function parseActionParam(actionParam: string | null): AuditAction[] | undefined {
  if (!actionParam) return undefined

  const validActions = Object.keys(AUDIT_ACTION_LABELS) as AuditAction[]
  const parsed = actionParam
    .split(',')
    .filter(Boolean)
    .filter((action): action is AuditAction => validActions.includes(action as AuditAction))

  return parsed.length > 0 ? parsed : undefined
}

const { Title } = Typography

/**
 * Страница аудит-логов.
 *
 * Доступна только для security и admin ролей (AC6).
 * Developer role редиректится на главную с сообщением об ошибке.
 *
 * Включает:
 * - Панель фильтров (AC2)
 * - Таблицу с expandable rows (AC1, AC3)
 * - Кнопку экспорта в CSV (AC4)
 * - Синхронизацию фильтров с URL (AC2)
 * - Empty state (AC7)
 */
export function AuditPage() {
  const { user } = useAuth()
  const { message } = App.useApp()
  const [searchParams, setSearchParams] = useSearchParams()
  const [isExporting, setIsExporting] = useState(false)
  const accessDeniedShown = useRef(false)

  // Проверка доступа (AC6) — показываем сообщение только один раз
  // Story 11.6: используем централизованный helper
  const isAccessDenied = isDeveloper(user ?? undefined)

  // Извлечение фильтров из URL (AC2)
  // action поддерживает multi-select: хранится в URL как "created,updated,deleted"
  // Используем parseActionParam для валидации значений из URL
  const filter: AuditFilter = useMemo(() => {
    return {
      userId: searchParams.get('userId') || undefined,
      action: parseActionParam(searchParams.get('action')),
      entityType: (searchParams.get('entityType') as AuditEntityType) || undefined,
      dateFrom: searchParams.get('dateFrom') || undefined,
      dateTo: searchParams.get('dateTo') || undefined,
      offset: Number(searchParams.get('offset')) || 0,
      limit: Number(searchParams.get('limit')) || DEFAULT_PAGE_SIZE,
    }
  }, [searchParams])

  // Загрузка данных (disabled для developer)
  const { data, isLoading, error, refetch } = useAuditLogs(
    isAccessDenied ? { limit: 0 } : filter
  )

  /**
   * Обновление URL параметров.
   * action массив конвертируется в строку через запятую для URL.
   */
  const updateParams = useCallback(
    (updates: Partial<AuditFilter>) => {
      const newParams = new URLSearchParams(searchParams)

      Object.entries(updates).forEach(([key, value]) => {
        if (value !== undefined && value !== '' && value !== null) {
          // Массив action конвертируем в строку через запятую
          if (key === 'action' && Array.isArray(value)) {
            if (value.length > 0) {
              newParams.set(key, value.join(','))
            } else {
              newParams.delete(key)
            }
          } else {
            newParams.set(key, String(value))
          }
        } else {
          newParams.delete(key)
        }
      })

      setSearchParams(newParams, { replace: true })
    },
    [searchParams, setSearchParams]
  )

  /**
   * Обработчик изменения фильтров.
   */
  const handleFilterChange = useCallback(
    (updates: Partial<AuditFilter>) => {
      updateParams(updates)
    },
    [updateParams]
  )

  /**
   * Очистка всех фильтров.
   */
  const handleClearFilters = useCallback(() => {
    setSearchParams(new URLSearchParams(), { replace: true })
  }, [setSearchParams])

  /**
   * Обработчик изменения пагинации.
   */
  const handlePaginationChange = useCallback(
    (offset: number, limit: number) => {
      updateParams({
        offset: offset > 0 ? offset : undefined,
        limit: limit !== DEFAULT_PAGE_SIZE ? limit : undefined,
      })
    },
    [updateParams]
  )

  /**
   * Экспорт в CSV (AC4).
   *
   * Загружает ВСЕ записи с текущими фильтрами (до 10000),
   * а не только текущую страницу.
   */
  const handleExport = useCallback(async () => {
    if (!data?.total) {
      message.warning('Нет данных для экспорта')
      return
    }

    setIsExporting(true)
    try {
      // Загружаем все записи с текущими фильтрами (AC4)
      const allData = await fetchAllAuditLogsForExport(filter)
      downloadAuditCsv(allData.items, message, filter.dateFrom, filter.dateTo)
      message.success('Экспорт завершён')
    } catch {
      message.error('Ошибка при экспорте данных')
    } finally {
      setIsExporting(false)
    }
  }, [data?.total, filter, message])

  // Показываем сообщение об отказе доступа
  useEffect(() => {
    if (isAccessDenied && !accessDeniedShown.current) {
      accessDeniedShown.current = true
      message.error('Недостаточно прав для просмотра аудит-логов')
    }
  }, [isAccessDenied, message])

  // Developer role редиректится на главную
  if (isAccessDenied) {
    return <Navigate to="/" replace />
  }

  // Empty state (AC7)
  const showEmptyState =
    !isLoading && !error && (!data?.items || data.items.length === 0)

  return (
    <Card>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        {/* Заголовок страницы (Story 15.6 — унификация) */}
        <div style={{ marginBottom: 24 }}>
          <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              <AuditOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              <Title level={3} style={{ margin: 0 }}>
                Журнал аудита
              </Title>
            </Space>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExport}
              disabled={!data?.total || isExporting}
              loading={isExporting}
            >
              Экспорт CSV
            </Button>
          </Space>
        </div>

        {/* Инфо-блок (Story 15.4) */}
        <PageInfoBlock pageKey="audit" {...PAGE_DESCRIPTIONS.audit} />

        {/* Панель фильтров (AC2) */}
        <AuditFilterBar
          filter={filter}
          onFilterChange={handleFilterChange}
          onClearFilters={handleClearFilters}
        />

        {/* Сообщение об ошибке */}
        {error && (
          <Alert
            message="Ошибка загрузки"
            description={
              error instanceof Error
                ? error.message
                : 'Не удалось загрузить аудит-логи'
            }
            type="error"
            showIcon
            action={
              <Button size="small" onClick={() => refetch()}>
                Повторить
              </Button>
            }
          />
        )}

        {/* Empty state (AC7; Story 16.5 AC3 — кнопка сброса фильтров) */}
        {showEmptyState && (
          <EmptyState
            title="Записи не найдены"
            description="Попробуйте изменить параметры фильтра"
            useSimpleImage
            action={{
              label: 'Сбросить фильтры',
              onClick: handleClearFilters,
              type: 'default',
            }}
          />
        )}

        {/* Таблица (AC1, AC3, AC5) */}
        {!showEmptyState && (
          <AuditLogsTable
            data={data}
            isLoading={isLoading}
            filter={filter}
            onPaginationChange={handlePaginationChange}
          />
        )}
      </Space>
    </Card>
  )
}
