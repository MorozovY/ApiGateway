// Публичный API модуля dashboard (Story 16.2)
export { DashboardPage } from './components/DashboardPage'
export { QuickStats } from './components/QuickStats'
export { RecentActivity } from './components/RecentActivity'
export { AdminStats } from './components/AdminStats'
export { PendingApprovals } from './components/PendingApprovals'
export { QuickActions } from './components/QuickActions'

// Hooks
export { useDashboardSummary, useRecentActivity } from './hooks/useDashboard'

// Types
export type {
  DashboardSummary,
  RecentActivityResponse,
  ActivityItem,
  RoutesByStatus,
} from './types/dashboard.types'
