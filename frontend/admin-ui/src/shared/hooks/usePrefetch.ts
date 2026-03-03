// Hook для prefetch lazy-загружаемых route модулей
// Загружает модули заранее при наведении на navigation items
import { useCallback, useRef, useEffect } from 'react'

// Кэш загруженных модулей — не повторяем загрузку
const loadedModules = new Set<string>()

// Маппинг путей на import функции
// Должен соответствовать LazyComponents.tsx
const routeImports: Record<string, () => Promise<unknown>> = {
  '/dashboard': () =>
    import('@features/dashboard/components/DashboardPage'),
  '/routes': () => import('@features/routes/components/RoutesPage'),
  '/users': () => import('@features/users/components/UsersPage'),
  '/consumers': () =>
    import('@features/consumers/components/ConsumersPage'),
  '/rate-limits': () =>
    import('@features/rate-limits/components/RateLimitsPage'),
  '/approvals': () =>
    import('@features/approval/components/ApprovalsPage'),
  '/audit': () => import('@features/audit/components/AuditPage'),
  '/audit/integrations': () =>
    import('@features/audit/components/IntegrationsPage'),
  '/metrics': () => import('@features/metrics/components/MetricsPage'),
  '/test': () => import('@features/test/components/TestPage'),
}

/**
 * Hook для prefetch route модулей при наведении.
 *
 * Использование:
 * ```tsx
 * const { prefetch } = usePrefetch()
 * <Menu.Item onMouseEnter={() => prefetch('/dashboard')} />
 * ```
 */
export function usePrefetch() {
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Cleanup timeout при unmount (PA-06: обязателен cleanup для setTimeout)
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
    }
  }, [])

  const prefetch = useCallback((path: string) => {
    // Уже загружен — не повторяем
    if (loadedModules.has(path)) return

    // Debounce 100ms чтобы не загружать при быстром скролле
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
    }

    timeoutRef.current = setTimeout(() => {
      const importFn = routeImports[path]
      if (importFn) {
        importFn()
          .then(() => loadedModules.add(path))
          .catch(() => {
            /* Игнорируем ошибки prefetch — это оптимизация, не критичный функционал */
          })
      }
    }, 100)
  }, [])

  return { prefetch }
}
