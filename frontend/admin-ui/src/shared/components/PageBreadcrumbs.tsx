// Компонент breadcrumbs навигации (Story 16.6)
import { Breadcrumb } from 'antd'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { matchPath } from 'react-router-dom'
import { useThemeContext } from '@shared/providers'
import { BREADCRUMB_ROUTES, type BreadcrumbConfig, type BreadcrumbItem } from '@shared/config/breadcrumbsConfig'
import { ROUTES_QUERY_KEY } from '@features/routes'
import type { Route } from '@features/routes'

/**
 * Находит конфигурацию breadcrumb для текущего pathname.
 * Возвращает null если breadcrumbs не нужны для этого маршрута.
 */
function findBreadcrumbConfig(pathname: string): { config: BreadcrumbConfig; params: Record<string, string> } | null {
  for (const config of BREADCRUMB_ROUTES) {
    const match = matchPath(config.pattern, pathname)
    if (match) {
      return { config, params: match.params as Record<string, string> }
    }
  }
  return null
}

/**
 * Получает путь маршрута из React Query кэша.
 * Используется для динамических breadcrumbs (/routes/:id).
 */
function useRoutePath(id: string | undefined): string | undefined {
  const queryClient = useQueryClient()

  if (!id) return undefined

  // Пробуем получить route из кэша
  const route = queryClient.getQueryData<Route>([ROUTES_QUERY_KEY, id])
  return route?.path
}

/**
 * Резолвит значение breadcrumb item (label или path).
 * Поддерживает как статические строки, так и функции.
 */
function resolveValue<T extends string | undefined>(
  value: T | ((params: Record<string, string>, routePath?: string) => T),
  params: Record<string, string>,
  routePath?: string
): T {
  if (typeof value === 'function') {
    return value(params, routePath)
  }
  return value
}

/**
 * Компонент PageBreadcrumbs.
 * Отображает breadcrumbs навигацию между header и content.
 * Автоматически определяет breadcrumbs по текущему URL.
 *
 * @example
 * // В MainLayout
 * <Header>...</Header>
 * <PageBreadcrumbs />
 * <Content>...</Content>
 */
export function PageBreadcrumbs() {
  const { pathname } = useLocation()
  const { id: routeId } = useParams<{ id: string }>()
  const { isDark } = useThemeContext()

  // Получаем route.path для динамических breadcrumbs
  const routePath = useRoutePath(routeId)

  // Находим конфигурацию для текущего маршрута
  const result = findBreadcrumbConfig(pathname)

  // Если нет конфигурации — не показываем breadcrumbs
  if (!result) {
    return null
  }

  const { config, params: matchedParams } = result

  // Строим items для Ant Design Breadcrumb
  const breadcrumbItems = config.items.map((item: BreadcrumbItem, index: number) => {
    const label = resolveValue(item.label, matchedParams, routePath)
    const path = item.path ? resolveValue(item.path, matchedParams, routePath) : undefined

    // Последний элемент — не ссылка, имеет aria-current="page" для accessibility
    const isLast = index === config.items.length - 1

    return {
      key: index,
      title: path && !isLast
        ? <Link to={path}>{label}</Link>
        : <span aria-current={isLast ? 'page' : undefined}>{label}</span>,
    }
  })

  return (
    <div
      style={{
        padding: '12px 24px',
        background: isDark ? '#1f1f1f' : '#fafafa',
        borderBottom: `1px solid ${isDark ? '#303030' : '#f0f0f0'}`,
      }}
      data-testid="page-breadcrumbs"
    >
      <Breadcrumb items={breadcrumbItems} aria-label="Навигация" />
    </div>
  )
}
