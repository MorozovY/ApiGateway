/**
 * Маршрут ожидающий согласования.
 *
 * Подмножество Route с дополнительным полем submittedAt.
 */
export interface PendingRoute {
  id: string
  path: string
  upstreamUrl: string
  methods: string[]
  description: string | null
  submittedAt: string
  createdBy: string
  creatorUsername?: string
}

/**
 * Запрос на отклонение маршрута.
 */
export interface RejectRequest {
  reason: string
}
