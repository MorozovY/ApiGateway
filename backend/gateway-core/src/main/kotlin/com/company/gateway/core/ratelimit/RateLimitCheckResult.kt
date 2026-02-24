package com.company.gateway.core.ratelimit

/**
 * Результат проверки rate limit с указанием типа лимита.
 *
 * Используется для two-level rate limiting (per-route + per-consumer).
 *
 * @property result базовый результат проверки (allowed, remaining, resetTime)
 * @property limitType тип сработавшего лимита:
 *   - "route" — per-route rate limit
 *   - "consumer" — per-consumer rate limit
 *   - null — нет rate limit (запрос разрешён без ограничений)
 * @property limit применённое значение лимита (requests per second)
 */
data class RateLimitCheckResult(
    val result: RateLimitResult,
    val limitType: String?,
    val limit: Int? = null
) {
    companion object {
        /** Тип лимита: per-route */
        const val TYPE_ROUTE = "route"

        /** Тип лимита: per-consumer */
        const val TYPE_CONSUMER = "consumer"

        /**
         * Создаёт результат "разрешено без лимитов".
         */
        fun allowedNoLimit(): RateLimitCheckResult {
            return RateLimitCheckResult(
                result = RateLimitResult(
                    allowed = true,
                    remaining = Int.MAX_VALUE,
                    resetTime = 0
                ),
                limitType = null,
                limit = null
            )
        }
    }
}
