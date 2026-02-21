// Public exports для audit feature (Story 7.5, расширено в Story 7.6)

// Компоненты
export { AuditPage } from './components/AuditPage'
export { AuditFilterBar } from './components/AuditFilterBar'
export { AuditLogsTable } from './components/AuditLogsTable'
export { ChangesViewer } from './components/ChangesViewer'
// Story 7.6 — новые компоненты
export { RouteHistoryTimeline } from './components/RouteHistoryTimeline'
export { UpstreamsTable } from './components/UpstreamsTable'
export { IntegrationsPage } from './components/IntegrationsPage'

// Hooks
export { useAuditLogs, AUDIT_LOGS_QUERY_KEY } from './hooks/useAuditLogs'
// Story 7.6 — новые hooks
export { useRouteHistory, ROUTE_HISTORY_QUERY_KEY, routeHistoryKeys } from './hooks/useRouteHistory'
export { useUpstreams, UPSTREAMS_QUERY_KEY } from './hooks/useUpstreams'

// API
export { fetchAuditLogs, fetchAllAuditLogsForExport } from './api/auditApi'
// Story 7.6 — новый API
export { fetchUpstreams } from './api/upstreamsApi'

// Utils
export { downloadAuditCsv } from './utils/exportCsv'
// Story 7.6 — новая утилита
export { exportUpstreamReport } from './utils/exportUpstreamReport'

// Типы
export type {
  AuditLogEntry,
  AuditFilter,
  AuditLogsResponse,
  AuditAction,
  AuditEntityType,
  AuditUserInfo,
  AuditChanges,
  // Story 7.6 — Upstream типы
  UpstreamSummary,
  UpstreamsResponse,
  // Story 7.6 — Route History типы (re-export из routes feature)
  RouteHistoryEntry,
  RouteHistoryResponse,
  RouteHistoryAction,
  RouteHistoryUser,
  RouteHistoryChanges,
} from './types/audit.types'

// Конфигурация
export {
  AUDIT_ACTION_LABELS,
  AUDIT_ACTION_COLORS,
  ENTITY_TYPE_LABELS,
  AUDIT_ACTION_OPTIONS,
  ENTITY_TYPE_OPTIONS,
  DEFAULT_PAGE_SIZE,
  FILTER_DEBOUNCE_MS,
  MAX_CSV_EXPORT_ROWS,
} from './config/auditConfig'
