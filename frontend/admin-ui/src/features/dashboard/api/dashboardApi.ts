/**
 * API функции для Dashboard (Story 16.2)
 */
import axios from '@/shared/utils/axios'
import type { DashboardSummary, RecentActivityResponse } from '../types/dashboard.types'

const BASE_URL = '/api/v1/dashboard'

/**
 * Получение сводки Dashboard.
 *
 * GET /api/v1/dashboard/summary
 *
 * Возвращает статистику маршрутов по статусам и дополнительные данные
 * в зависимости от роли пользователя.
 */
export async function getDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await axios.get<DashboardSummary>(`${BASE_URL}/summary`)
  return data
}

/**
 * Получение последней активности.
 *
 * GET /api/v1/dashboard/recent-activity
 *
 * @param limit максимальное количество записей (default: 5, max: 20)
 */
export async function getRecentActivity(limit = 5): Promise<RecentActivityResponse> {
  const { data } = await axios.get<RecentActivityResponse>(`${BASE_URL}/recent-activity`, {
    params: { limit },
  })
  return data
}
