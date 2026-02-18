// API для управления маршрутами (Story 3.4)
import axios from '@shared/utils/axios'
import type {
  Route,
  RouteListResponse,
  RouteListParams,
  CreateRouteRequest,
  UpdateRouteRequest,
} from '../types/route.types'

const BASE_URL = '/api/v1/routes'

/**
 * Получение списка маршрутов с пагинацией и фильтрацией.
 *
 * GET /api/v1/routes?offset=0&limit=20&status=draft&search=api
 */
export async function fetchRoutes(params: RouteListParams = {}): Promise<RouteListResponse> {
  const { data } = await axios.get<RouteListResponse>(BASE_URL, { params })
  return data
}

/**
 * Получение маршрута по ID.
 *
 * GET /api/v1/routes/{id}
 */
export async function fetchRouteById(id: string): Promise<Route> {
  const { data } = await axios.get<Route>(`${BASE_URL}/${id}`)
  return data
}

/**
 * Создание нового маршрута.
 *
 * POST /api/v1/routes
 */
export async function createRoute(request: CreateRouteRequest): Promise<Route> {
  const { data } = await axios.post<Route>(BASE_URL, request)
  return data
}

/**
 * Обновление маршрута.
 *
 * PUT /api/v1/routes/{id}
 */
export async function updateRoute(id: string, request: UpdateRouteRequest): Promise<Route> {
  const { data } = await axios.put<Route>(`${BASE_URL}/${id}`, request)
  return data
}

/**
 * Удаление маршрута.
 *
 * DELETE /api/v1/routes/{id}
 */
export async function deleteRoute(id: string): Promise<void> {
  await axios.delete(`${BASE_URL}/${id}`)
}

/**
 * Клонирование маршрута.
 *
 * POST /api/v1/routes/{id}/clone
 */
export async function cloneRoute(id: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/clone`)
  return data
}

/**
 * Отправка маршрута на согласование.
 *
 * POST /api/v1/routes/{id}/submit
 *
 * Работает для статусов draft и rejected.
 */
export async function submitForApproval(id: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/submit`)
  return data
}

/**
 * Проверка существования маршрута с указанным path.
 *
 * GET /api/v1/routes/check-path?path=...
 *
 * Используется для inline валидации уникальности path в форме.
 */
export async function checkPathExists(path: string): Promise<boolean> {
  const { data } = await axios.get<{ exists: boolean }>(`${BASE_URL}/check-path`, {
    params: { path },
  })
  return data.exists
}
