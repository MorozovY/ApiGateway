package com.company.gateway.core.ratelimit

/**
 * Результат проверки rate limit.
 *
 * @property allowed разрешён ли запрос (true = токен доступен, false = лимит превышен)
 * @property remaining количество оставшихся токенов
 * @property resetTime Unix timestamp (миллисекунды) следующего полного восполнения токенов
 */
data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Int,
    val resetTime: Long
)
