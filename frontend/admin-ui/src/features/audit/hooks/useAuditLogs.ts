// React Query hooks для аудит-логов (Story 7.5)
import { useQuery } from '@tanstack/react-query'
import { fetchAuditLogs } from '../api/auditApi'
import type { AuditFilter } from '../types/audit.types'

/**
 * Ключ для React Query кэша аудит-логов.
 */
export const AUDIT_LOGS_QUERY_KEY = 'audit-logs'

/**
 * Hook для получения списка аудит-логов.
 *
 * Поддерживает фильтрацию по userId, action, entityType, dateFrom, dateTo.
 * Поддерживает пагинацию через offset/limit.
 */
export function useAuditLogs(filter: AuditFilter = {}) {
  return useQuery({
    queryKey: [AUDIT_LOGS_QUERY_KEY, filter],
    queryFn: () => fetchAuditLogs(filter),
  })
}
