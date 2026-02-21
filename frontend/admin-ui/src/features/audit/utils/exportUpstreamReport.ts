// Утилита для экспорта Upstream Report в CSV (Story 7.6, AC5)
import { message } from 'antd'
import dayjs from 'dayjs'
import { fetchRoutes } from '@features/routes/api/routesApi'
import type { UpstreamSummary } from '../types/audit.types'
import type { Route } from '@features/routes/types/route.types'
import { STATUS_LABELS } from '@shared/constants'

/**
 * Максимальное количество маршрутов на один upstream в отчёте.
 * При превышении показывается warning.
 */
const EXPORT_LIMIT_PER_UPSTREAM = 1000

/**
 * Максимальное количество параллельных запросов к API.
 * Ограничиваем чтобы не перегружать сервер.
 */
const MAX_CONCURRENT_REQUESTS = 5

/**
 * Экранирует значение для CSV.
 * Оборачивает в кавычки и экранирует внутренние кавычки.
 */
function escapeCsvValue(value: string | null | undefined): string {
  const str = value ?? ''
  return `"${str.replace(/"/g, '""')}"`
}

/**
 * Результат загрузки маршрутов для upstream.
 */
interface UpstreamFetchResult {
  host: string
  routes: Route[]
  error?: boolean
  truncated?: boolean
  total?: number
}

/**
 * Загружает маршруты для одного upstream.
 */
async function fetchRoutesForUpstream(upstream: UpstreamSummary): Promise<UpstreamFetchResult> {
  try {
    const response = await fetchRoutes({
      upstream: upstream.host,
      limit: EXPORT_LIMIT_PER_UPSTREAM,
    })

    return {
      host: upstream.host,
      routes: response.items,
      truncated: response.total > EXPORT_LIMIT_PER_UPSTREAM,
      total: response.total,
    }
  } catch {
    return {
      host: upstream.host,
      routes: [],
      error: true,
    }
  }
}

/**
 * Выполняет запросы параллельно с ограничением concurrency.
 */
async function fetchAllUpstreamsParallel(
  upstreams: UpstreamSummary[]
): Promise<UpstreamFetchResult[]> {
  const results: UpstreamFetchResult[] = []

  // Разбиваем на батчи для ограничения параллельных запросов
  for (let i = 0; i < upstreams.length; i += MAX_CONCURRENT_REQUESTS) {
    const batch = upstreams.slice(i, i + MAX_CONCURRENT_REQUESTS)
    const batchResults = await Promise.all(batch.map(fetchRoutesForUpstream))
    results.push(...batchResults)
  }

  return results
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

    // Загружаем все маршруты параллельно (с ограничением concurrency)
    const fetchResults = await fetchAllUpstreamsParallel(upstreams)

    // Проверяем на truncated результаты
    const truncatedUpstreams = fetchResults.filter((r) => r.truncated)
    if (truncatedUpstreams.length > 0) {
      const names = truncatedUpstreams.map((r) => `${r.host} (${r.total} маршрутов)`).join(', ')
      message.warning(
        `Некоторые upstream имеют более ${EXPORT_LIMIT_PER_UPSTREAM} маршрутов и были обрезаны: ${names}`,
        5
      )
    }

    // Собираем все строки данных
    const rows: string[][] = []

    for (const result of fetchResults) {
      if (result.error) {
        // Ошибка загрузки
        rows.push([
          escapeCsvValue(result.host),
          escapeCsvValue('Ошибка загрузки'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
        ])
      } else if (result.routes.length === 0) {
        // Нет маршрутов
        rows.push([
          escapeCsvValue(result.host),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
          escapeCsvValue('—'),
        ])
      } else {
        // Добавляем строку для каждого маршрута
        for (const route of result.routes) {
          rows.push([
            escapeCsvValue(result.host),
            escapeCsvValue(route.path),
            escapeCsvValue(route.creatorUsername || route.createdBy),
            escapeCsvValue(STATUS_LABELS[route.status] || route.status),
            escapeCsvValue(dayjs(route.updatedAt).format('YYYY-MM-DD HH:mm')),
          ])
        }
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
