/**
 * Типы для consumer management (Story 12.9)
 *
 * Consumer — это Keycloak service account client для API аутентификации
 * через Client Credentials flow.
 */

/**
 * Consumer rate limit настройки.
 */
export interface ConsumerRateLimit {
  id: string;
  consumerId: string;
  requestsPerSecond: number;
  burstSize: number;
  createdAt: string | null;
  updatedAt: string | null;
  createdBy: {
    id: string;
    username: string;
  } | null;
}

/**
 * Consumer данные.
 */
export interface Consumer {
  clientId: string;
  description: string | null;
  enabled: boolean;
  createdTimestamp: number;
  rateLimit: ConsumerRateLimit | null;
}

/**
 * Пагинированный список consumers.
 */
export interface ConsumerListResponse {
  items: Consumer[];
  total: number;
}

/**
 * Запрос на создание consumer.
 */
export interface CreateConsumerRequest {
  clientId: string;
  description?: string;
}

/**
 * Response после создания consumer.
 * ВАЖНО: Secret показывается только один раз!
 */
export interface CreateConsumerResponse {
  clientId: string;
  secret: string;
  message: string;
}

/**
 * Response после ротации secret.
 */
export interface RotateSecretResponse {
  clientId: string;
  secret: string;
  message: string;
}

/**
 * Consumer rate limit request.
 */
export interface ConsumerRateLimitRequest {
  requestsPerSecond: number;
  burstSize: number;
}

/**
 * Параметры для fetchConsumers.
 */
export interface ConsumerListParams {
  offset?: number;
  limit?: number;
  search?: string;
}
