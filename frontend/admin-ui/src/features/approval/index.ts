// Публичный API feature approval
// Примечание: ApprovalsPage НЕ экспортируется здесь — используется lazy loading
// через LazyComponents.tsx (Story 14.4). Статический экспорт блокирует code splitting.
export {
  usePendingRoutes,
  useApproveRoute,
  useRejectRoute,
  usePendingRoutesCount,
  PENDING_ROUTES_QUERY_KEY,
  APPROVALS_REFRESH_INTERVAL,
  APPROVALS_STALE_TIME,
} from './hooks/useApprovals'
export type { PendingRoute, RejectRequest } from './types/approval.types'
