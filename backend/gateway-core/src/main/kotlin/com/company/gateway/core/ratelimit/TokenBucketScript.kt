package com.company.gateway.core.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * Обёртка над Redis Lua скриптом для атомарного token bucket rate limiting.
 *
 * Скрипт выполняется атомарно на стороне Redis, что гарантирует
 * корректную работу distributed rate limiting при нескольких инстансах gateway.
 */
@Component
class TokenBucketScript(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(TokenBucketScript::class.java)

    // Ленивая загрузка Lua скрипта при первом использовании
    private val script: RedisScript<List<*>> by lazy {
        val scriptResource = ClassPathResource("scripts/token-bucket.lua")
        val scriptText = scriptResource.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        logger.info("Token bucket Lua скрипт загружен")
        RedisScript.of(scriptText, List::class.java)
    }

    /**
     * Выполняет проверку rate limit с использованием token bucket алгоритма.
     *
     * @param key ключ Redis в формате "ratelimit:{routeId}:{clientKey}"
     * @param requestsPerSecond скорость восполнения токенов (токенов в секунду)
     * @param burstSize максимальное количество токенов (burst capacity)
     * @param ttlSeconds TTL для ключа Redis (для автоочистки неактивных bucket)
     * @return RateLimitResult с информацией о разрешении запроса
     */
    fun checkRateLimit(
        key: String,
        requestsPerSecond: Int,
        burstSize: Int,
        ttlSeconds: Int = DEFAULT_TTL_SECONDS
    ): Mono<RateLimitResult> {
        val now = System.currentTimeMillis()

        return redisTemplate.execute(
            script,
            listOf(key),
            listOf(
                requestsPerSecond.toString(),
                burstSize.toString(),
                now.toString(),
                ttlSeconds.toString()
            )
        )
            .next()
            .map { result ->
                @Suppress("UNCHECKED_CAST")
                val list = result as List<Long>
                RateLimitResult(
                    allowed = list[0] == 1L,
                    remaining = list[1].toInt(),
                    resetTime = list[2]
                )
            }
            .doOnError { e ->
                logger.warn("Ошибка выполнения token bucket скрипта для ключа {}: {}", key, e.message)
            }
    }

    companion object {
        /**
         * TTL по умолчанию для Redis ключей (2 минуты).
         * Достаточно для хранения bucket между запросами, но не слишком долго для неактивных клиентов.
         */
        const val DEFAULT_TTL_SECONDS = 120
    }
}
