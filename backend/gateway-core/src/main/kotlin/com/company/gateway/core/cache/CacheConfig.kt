package com.company.gateway.core.cache

import com.company.gateway.common.model.Route
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {

    companion object {
        const val ROUTE_CACHE = "routes"
    }

    @Value("\${gateway.cache.ttl-seconds:60}")
    private var ttlSeconds: Long = 60

    @Value("\${gateway.cache.max-routes:1000}")
    private var maxRoutes: Long = 1000

    @Bean
    fun caffeineRouteCache(): Cache<String, List<Route>> =
        Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .maximumSize(maxRoutes)
            .build()

    @Bean("stringRedisTemplate")
    @ConditionalOnBean(ReactiveRedisConnectionFactory::class)
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveRedisTemplate<String, String> {
        val serializer = StringRedisSerializer()
        val context = RedisSerializationContext
            .newSerializationContext<String, String>(serializer)
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build()
        return ReactiveRedisTemplate(connectionFactory, context)
    }
}
