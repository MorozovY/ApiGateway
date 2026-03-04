// Конфигурация breadcrumbs для навигации (Story 16.6)

/**
 * Элемент breadcrumb.
 * Если path не указан — элемент не кликабелен (последний элемент).
 */
export interface BreadcrumbItem {
  /**
   * Текст breadcrumb — строка или функция для динамических значений.
   */
  label: string | ((params: Record<string, string>, routePath?: string) => string)
  /**
   * Путь для навигации — строка или функция. Если undefined — не ссылка.
   */
  path?: string | ((params: Record<string, string>) => string)
}

/**
 * Конфигурация breadcrumb для конкретного маршрута.
 */
export interface BreadcrumbConfig {
  /**
   * Паттерн маршрута для matchPath (например '/routes/:id/edit').
   */
  pattern: string
  /**
   * Элементы breadcrumb в порядке отображения.
   */
  items: BreadcrumbItem[]
}

/**
 * Конфигурация breadcrumbs для всех маршрутов приложения.
 * Порядок важен — более специфичные паттерны должны идти первыми.
 */
export const BREADCRUMB_ROUTES: BreadcrumbConfig[] = [
  // Routes — специфичные паттерны первыми
  {
    pattern: '/routes/new',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: 'Новый маршрут' },
    ],
  },
  {
    pattern: '/routes/:id/edit',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: (_params, routePath) => routePath || 'Маршрут', path: (params) => `/routes/${params.id}` },
      { label: 'Редактирование' },
    ],
  },
  {
    pattern: '/routes/:id',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: (_params, routePath) => routePath || 'Маршрут' },
    ],
  },
  // Audit
  {
    pattern: '/audit/integrations',
    items: [
      { label: 'Журнал аудита', path: '/audit' },
      { label: 'Интеграции' },
    ],
  },
]
