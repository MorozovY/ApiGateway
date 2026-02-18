// Публичный API модуля routes (Story 3.4, 3.5, 3.6)
export { RoutesPage } from './components/RoutesPage'
export { RoutesTable } from './components/RoutesTable'
export { RouteFormPage } from './components/RouteFormPage'
export { RouteForm } from './components/RouteForm'
export { RouteDetailsPage } from './components/RouteDetailsPage'
export { RouteDetailsCard } from './components/RouteDetailsCard'

// Hooks
export {
  useRoutes,
  useRoute,
  useCreateRoute,
  useUpdateRoute,
  useDeleteRoute,
  useCloneRoute,
  ROUTES_QUERY_KEY,
} from './hooks/useRoutes'

// Types
export type {
  Route,
  RouteStatus,
  RouteListParams,
  RouteListResponse,
  CreateRouteRequest,
  UpdateRouteRequest,
} from './types/route.types'
