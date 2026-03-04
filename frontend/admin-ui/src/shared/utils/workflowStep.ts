// Утилита для определения текущего шага workflow (Story 16.10)

/**
 * Константы шагов workflow маршрута.
 * Используются для типизации и избежания магических чисел.
 */
export const WORKFLOW_STEP = {
  /** Создание нового маршрута */
  CREATION: 0,
  /** Отправка на согласование */
  SUBMISSION: 1,
  /** Согласование (security review) */
  APPROVAL: 2,
  /** Публикация (активен) */
  PUBLICATION: 3,
} as const

export type WorkflowStep = (typeof WORKFLOW_STEP)[keyof typeof WORKFLOW_STEP]

/**
 * Определяет текущий шаг workflow на основе URL и статуса маршрута.
 *
 * Маппинг URL → шаг:
 * - `/routes/new` → 0 (Создание)
 * - `/routes/:id` с draft/rejected → 1 (Отправка на согласование)
 * - `/routes/:id/edit` → 1 (Отправка на согласование)
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
): WorkflowStep {
  // Шаг 1: Создание нового маршрута
  if (pathname === '/routes/new') {
    return WORKFLOW_STEP.CREATION
  }

  // Шаг 2: Редактирование draft/rejected (отправка на согласование)
  if (
    pathname.match(/^\/routes\/[^/]+$/) &&
    ['draft', 'rejected'].includes(routeStatus || '')
  ) {
    return WORKFLOW_STEP.SUBMISSION
  }

  // Шаг 2 также: редактирование маршрута (/routes/:id/edit)
  if (pathname.match(/^\/routes\/[^/]+\/edit$/)) {
    return WORKFLOW_STEP.SUBMISSION
  }

  // Шаг 3: Согласование
  if (pathname === '/approvals') {
    return WORKFLOW_STEP.APPROVAL
  }

  // Шаг 4: Список маршрутов (публикация/просмотр)
  if (pathname === '/routes') {
    return WORKFLOW_STEP.PUBLICATION
  }

  // По умолчанию: список маршрутов
  return WORKFLOW_STEP.PUBLICATION
}
