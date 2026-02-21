// API функции для Health Check (Story 8.1)
import axios from '@/shared/utils/axios'
import type { HealthResponse } from '../types/metrics.types'

const BASE_URL = '/api/v1/health'

/**
 * Получение статуса здоровья всех сервисов.
 *
 * GET /api/v1/health/services
 *
 * Возвращает статус каждого сервиса (UP/DOWN) с timestamp последней проверки.
 */
export async function getServicesHealth(): Promise<HealthResponse> {
  const { data } = await axios.get<HealthResponse>(`${BASE_URL}/services`)
  return data
}
