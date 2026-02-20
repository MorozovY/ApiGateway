// Утилита для экспорта аудит-логов в CSV (Story 7.5, AC4)
import { message } from 'antd'
import dayjs from 'dayjs'
import type { AuditLogEntry } from '../types/audit.types'
import { AUDIT_ACTION_LABELS, MAX_CSV_EXPORT_ROWS } from '../config/auditConfig'

/**
 * Экранирует значение для CSV.
 * Оборачивает в кавычки и экранирует внутренние кавычки.
 */
function escapeCsvValue(value: string | null | undefined): string {
  const str = value ?? ''
  // Экранируем кавычки и оборачиваем в кавычки
  return `"${str.replace(/"/g, '""')}"`
}

/**
 * Экспортирует аудит-логи в CSV файл (AC4).
 *
 * Особенности:
 * - Формат имени файла: audit-log-YYYY-MM-DD-to-YYYY-MM-DD.csv
 * - Максимум 10000 записей (с предупреждением если обрезано)
 * - Все видимые колонки включены
 *
 * @param data Массив записей аудит-лога
 * @param dateFrom Начальная дата фильтра
 * @param dateTo Конечная дата фильтра
 */
export function downloadAuditCsv(
  data: AuditLogEntry[],
  dateFrom?: string,
  dateTo?: string
): void {
  // Проверка на превышение лимита (AC4)
  if (data.length >= MAX_CSV_EXPORT_ROWS) {
    message.warning(`Экспорт ограничен ${MAX_CSV_EXPORT_ROWS} записями`)
  }

  // Заголовки CSV
  const headers = [
    'ID',
    'Timestamp',
    'Action',
    'Entity Type',
    'Entity ID',
    'User',
    'IP Address',
    'Correlation ID',
  ]

  // Форматирование строк данных
  const rows = data.slice(0, MAX_CSV_EXPORT_ROWS).map((entry) => [
    escapeCsvValue(entry.id),
    escapeCsvValue(dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm:ss')),
    escapeCsvValue(AUDIT_ACTION_LABELS[entry.action] || entry.action),
    escapeCsvValue(entry.entityType),
    escapeCsvValue(entry.entityId),
    escapeCsvValue(entry.user.username),
    escapeCsvValue(entry.ipAddress),
    escapeCsvValue(entry.correlationId),
  ])

  // Формирование CSV контента
  const csvContent = [
    headers.map((h) => escapeCsvValue(h)).join(','),
    ...rows.map((row) => row.join(',')),
  ].join('\n')

  // Добавляем BOM для корректного отображения кириллицы в Excel
  const bom = '\uFEFF'
  const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)

  // Формирование имени файла (AC4)
  const fromStr = dateFrom || 'start'
  const toStr = dateTo || dayjs().format('YYYY-MM-DD')
  const filename = `audit-log-${fromStr}-to-${toStr}.csv`

  // Скачивание файла
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  // Освобождение памяти
  URL.revokeObjectURL(url)
}
