// Утилита для экспорта Upstream Report в CSV (Story 7.6, AC5)
import { message } from 'antd'
import dayjs from 'dayjs'
import { fetchRoutes } from '@features/routes/api/routesApi'
import type { UpstreamSummary } from '../types/audit.types'
import { STATUS_LABELS } from '@shared/constants'

/**
 * Экранирует значение для CSV.
 * Оборачивает в кавычки и экранирует внутренние кавычки.
 */
function escapeCsvValue(value: string | null | undefined): string {
  const str = value ?? ''
  return `"${str.replace(/"/g, '""')}"`
}

/**
 * Экспортирует Upstream Report в CSV файл (AC5).
 *
 * Формат CSV:
 * - Upstream service URL
 * - All routes accessing it (path)
 * - Route owners (createdBy username)
 * - Current status
 * - Last modified date
 *
 * Filename: upstream-report-YYYY-MM-DD.csv
 *
 * @param upstreams Список upstream сервисов
 */
export async function exportUpstreamReport(upstreams: UpstreamSummary[]): Promise<void> {
  const hideLoading = message.loading('Формирование отчёта...', 0)

  try {
    // Заголовки CSV (AC5)
    const headers = [
      'Upstream Service',
      'Route Path',
      'Owner',
      'Status',
      'Last Modified',
    ]

    // Собираем все строки данных
    const rows: string[][] = []

    // Для каждого upstream загружаем маршруты
    for (const upstream of upstreams) {
      try {
        // Загружаем маршруты с фильтром по upstream
        const response = await fetchRoutes({
          upstream: upstream.host,
          limit: 1000, // Максимум для отчёта
        })

        if (response.items.length === 0) {
          // Если нет маршрутов, добавляем строку с upstream без маршрутов
          rows.push([
            escapeCsvValue(upstream.host),
            escapeCsvValue('—'),
            escapeCsvValue('—'),
            escapeCsvValue('—'),
            escapeCsvValue('—'),
          ])
        } else {
          // Добавляем строку для каждого маршрута
          for (const route of response.items) {
            rows.push([
              escapeCsvValue(upstream.host),
              escapeCsvValue(route.path),
              escapeCsvValue(route.creatorUsername || route.createdBy),
              escapeCsvValue(STATUS_LABELS[route.status] || route.status),
              escapeCsvValue(dayjs(route.updatedAt).format('YYYY-MM-DD HH:mm')),
            ])
          }
        }
      } catch {
        // Если не удалось загрузить маршруты для upstream, добавляем с ошибкой
        rows.push([
          escapeCsvValue(upstream.host),
          escapeCsvValue('Ошибка загрузки'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
        ])
      }
    }

    // Формирование CSV контента
    const csvContent = [
      headers.map((h) => escapeCsvValue(h)).join(','),
      ...rows.map((row) => row.join(',')),
    ].join('\n')

    // Добавляем BOM для корректного отображения кириллицы в Excel
    const bom = '\uFEFF'
    const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)

    // Формирование имени файла (AC5)
    const filename = `upstream-report-${dayjs().format('YYYY-MM-DD')}.csv`

    // Скачивание файла
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    // Освобождение памяти
    URL.revokeObjectURL(url)

    hideLoading()
    message.success(`Отчёт экспортирован: ${filename}`)
  } catch (err) {
    hideLoading()
    message.error('Ошибка экспорта отчёта')
    throw err
  }
}
