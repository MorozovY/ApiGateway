// API для работы с upstream сервисами (Story 7.6, AC3, AC4)
import axios from '@shared/utils/axios'
import type { UpstreamsResponse } from '../types/audit.types'

const ROUTES_BASE_URL = '/api/v1/routes'

/**
 * Получение списка upstream сервисов с подсчётом маршрутов.
 *
 * GET /api/v1/routes/upstreams
 *
 * Возвращает список уникальных upstream хостов с количеством маршрутов.
 * Доступно для security и admin ролей.
 */
export async function fetchUpstreams(): Promise<UpstreamsResponse> {
  const { data } = await axios.get<UpstreamsResponse>(`${ROUTES_BASE_URL}/upstreams`)
  return data
}
