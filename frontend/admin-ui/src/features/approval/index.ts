// Публичный API feature approval
export { ApprovalsPage } from './components/ApprovalsPage'
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
