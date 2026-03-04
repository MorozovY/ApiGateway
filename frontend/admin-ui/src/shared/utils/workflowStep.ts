// Утилита для определения текущего шага workflow (Story 16.10)

/**
 * Определяет текущий шаг workflow на основе URL и статуса маршрута.
 *
 * Маппинг URL → шаг:
 * - `/routes/new` → 0 (Создание)
 * - `/routes/:id` с draft/rejected → 1 (Отправка на согласование)
 * - `/approvals` → 2 (Согласование)
 * - `/routes` (список) → 3 (Публикация)
 *
 * @param pathname - текущий URL path
 * @param routeStatus - статус маршрута (опционально)
 * @returns номер шага (0-3)
 */
export function getCurrentWorkflowStep(
  pathname: string,
  routeStatus?: string
): number {
  // Шаг 1: Создание нового маршрута
  if (pathname === '/routes/new') {
    return 0
  }

  // Шаг 2: Редактирование draft/rejected (отправка на согласование)
  if (
    pathname.match(/^\/routes\/[^/]+$/) &&
    ['draft', 'rejected'].includes(routeStatus || '')
  ) {
    return 1
  }

  // Шаг 2 также: редактирование маршрута (/routes/:id/edit)
  if (pathname.match(/^\/routes\/[^/]+\/edit$/)) {
    return 1
  }

  // Шаг 3: Согласование
  if (pathname === '/approvals') {
    return 2
  }

  // Шаг 4: Список маршрутов (публикация/просмотр)
  if (pathname === '/routes') {
    return 3
  }

  // Default: список маршрутов
  return 3
}
