// API для работы с аудит-логами (Story 7.5)
import axios from '@shared/utils/axios'
import type { AuditFilter, AuditLogsResponse } from '../types/audit.types'
import { MAX_CSV_EXPORT_ROWS } from '../config/auditConfig'

const BASE_URL = '/api/v1/audit'

/**
 * Получение списка аудит-логов с фильтрацией и пагинацией.
 *
 * GET /api/v1/audit?userId=UUID&action=string&entityType=string&dateFrom=2026-01-01&dateTo=2026-02-01&offset=0&limit=50
 *
 * Параметр action поддерживает multi-select: передаётся как строка через запятую (AC2).
 * Обрабатывает ошибки RFC 7807 через axios interceptor.
 */
export async function fetchAuditLogs(filter: AuditFilter = {}): Promise<AuditLogsResponse> {
  // Очищаем undefined параметры для чистого URL
  const params: Record<string, string | number> = {}

  if (filter.userId) params.userId = filter.userId
  // Multi-select action: конвертируем массив в строку через запятую
  if (filter.action && filter.action.length > 0) {
    params.action = filter.action.join(',')
  }
  if (filter.entityType) params.entityType = filter.entityType
  if (filter.dateFrom) params.dateFrom = filter.dateFrom
  if (filter.dateTo) params.dateTo = filter.dateTo
  if (filter.offset !== undefined) params.offset = filter.offset
  if (filter.limit !== undefined) params.limit = filter.limit

  const { data } = await axios.get<AuditLogsResponse>(BASE_URL, { params })
  return data
}

/**
 * Получение всех аудит-логов для экспорта в CSV (AC4).
 *
 * Загружает до MAX_CSV_EXPORT_ROWS записей с текущими фильтрами.
 * Используется для полного экспорта, а не только текущей страницы.
 */
export async function fetchAllAuditLogsForExport(filter: AuditFilter = {}): Promise<AuditLogsResponse> {
  return fetchAuditLogs({
    ...filter,
    offset: 0,
    limit: MAX_CSV_EXPORT_ROWS,
  })
}
