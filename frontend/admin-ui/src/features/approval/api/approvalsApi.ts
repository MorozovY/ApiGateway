// API функции для работы с согласованиями маршрутов
import axios from '@shared/utils/axios'
import type { Route } from '@features/routes'
import type { PendingRoute } from '../types/approval.types'

const BASE_URL = '/api/v1/routes'

/**
 * Получение списка маршрутов ожидающих согласования.
 *
 * GET /api/v1/routes/pending
 * Бэкенд возвращает PagedResponse — извлекаем items.
 * Требует роль security или admin.
 */
export async function fetchPendingRoutes(): Promise<PendingRoute[]> {
  // limit=100 — загружаем все pending маршруты (максимум бэкенда — 100)
  const { data } = await axios.get<{ items: PendingRoute[] }>(`${BASE_URL}/pending?limit=100`)
  return data.items
}

/**
 * Одобрение маршрута.
 *
 * POST /api/v1/routes/{id}/approve
 * Возвращает обновлённый маршрут со статусом active.
 */
export async function approveRoute(id: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/approve`)
  return data
}

/**
 * Отклонение маршрута с причиной.
 *
 * POST /api/v1/routes/{id}/reject
 * Возвращает обновлённый маршрут со статусом rejected.
 */
export async function rejectRoute(id: string, reason: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/reject`, { reason })
  return data
}
