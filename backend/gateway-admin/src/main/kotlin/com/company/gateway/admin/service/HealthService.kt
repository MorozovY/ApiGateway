package com.company.gateway.admin.service

import com.company.gateway.admin.dto.HealthResponse
import com.company.gateway.admin.dto.ServiceHealthDto
import com.company.gateway.admin.dto.ServiceStatus
import io.r2dbc.spi.ConnectionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

/**
 * Сервис проверки здоровья всех компонентов системы.
 *
 * Проверяет:
 * - nginx: HTTP GET на /nginx-health (entry point, reverse proxy)
 * - gateway-core: HTTP GET на /actuator/health
 * - gateway-admin: всегда UP (если сервис отвечает)
 * - PostgreSQL: R2DBC connection test (SELECT 1)
 * - Redis: команда PING
 * - prometheus: HTTP GET на /-/healthy
 * - grafana: HTTP GET на /api/health
 *
 * Каждая проверка имеет таймаут 5 секунд и возвращает DOWN при ошибке.
 */
@Service
class HealthService(
    webClientBuilder: WebClient.Builder,
    private val connectionFactory: ConnectionFactory,
    @Autowired(required = false)
    private val redisTemplate: ReactiveStringRedisTemplate?,
    @Value("\${gateway.core.url:http://localhost:8080}")
    private val gatewayCoreUrl: String,
    @Value("\${prometheus.url:http://localhost:9090}")
    private val prometheusUrl: String,
    @Value("\${grafana.url:http://localhost:3001}")
    private val grafanaUrl: String,
    @Value("\${nginx.url:http://localhost:80}")
    private val nginxUrl: String,
    @Value("\${health.check.timeout:5s}")
    private val checkTimeout: Duration
) {
    private val logger = LoggerFactory.getLogger(HealthService::class.java)

    private val webClient: WebClient = webClientBuilder.build()

    companion object {
        const val SERVICE_NGINX = "nginx"
        const val SERVICE_GATEWAY_CORE = "gateway-core"
        const val SERVICE_GATEWAY_ADMIN = "gateway-admin"
        const val SERVICE_POSTGRESQL = "postgresql"
        const val SERVICE_REDIS = "redis"
        const val SERVICE_PROMETHEUS = "prometheus"
        const val SERVICE_GRAFANA = "grafana"
    }

    /**
     * Получает статус здоровья всех сервисов.
     *
     * Запросы выполняются параллельно для минимизации времени ответа.
     *
     * @return Mono с HealthResponse, содержащим статусы всех сервисов
     */
    fun getServicesHealth(): Mono<HealthResponse> {
        logger.debug("Запрос статуса здоровья всех сервисов")

        val healthChecks = listOf(
            checkNginx(),
            checkGatewayCore(),
            checkGatewayAdmin(),
            checkPostgresql(),
            checkRedis(),
            checkPrometheus(),
            checkGrafana()
        )

        return Flux.merge(healthChecks)
            .collectList()
            .map { services -> HealthResponse.from(services) }
            .doOnSuccess { response ->
                val upCount = response.services.count { it.status == ServiceStatus.UP }
                val downCount = response.services.count { it.status == ServiceStatus.DOWN }
                logger.info("Health check завершён: UP={}, DOWN={}", upCount, downCount)
            }
    }

    /**
     * Проверяет доступность Nginx через /nginx-health endpoint.
     *
     * Nginx является entry point системы (reverse proxy), поэтому его
     * доступность критична для работы всего сервиса.
     */
    fun checkNginx(): Mono<ServiceHealthDto> {
        logger.debug("Проверка Nginx: {}/nginx-health", nginxUrl)

        return webClient.get()
            .uri("$nginxUrl/nginx-health")
            .retrieve()
            .bodyToMono(String::class.java)
            .map {
                ServiceHealthDto(SERVICE_NGINX, ServiceStatus.UP, Instant.now())
            }
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("Nginx недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_NGINX, error))
            }
    }

    /**
     * Проверяет доступность gateway-core через /actuator/health.
     */
    fun checkGatewayCore(): Mono<ServiceHealthDto> {
        logger.debug("Проверка gateway-core: {}/actuator/health", gatewayCoreUrl)

        return webClient.get()
            .uri("$gatewayCoreUrl/actuator/health")
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val status = response["status"] as? String
                if (status == "UP") {
                    ServiceHealthDto(SERVICE_GATEWAY_CORE, ServiceStatus.UP, Instant.now())
                } else {
                    ServiceHealthDto(SERVICE_GATEWAY_CORE, ServiceStatus.DOWN, Instant.now(), "Status: $status")
                }
            }
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("gateway-core недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_GATEWAY_CORE, error))
            }
    }

    /**
     * Возвращает статус gateway-admin.
     *
     * Если этот метод вызывается — значит сервис работает, возвращаем UP.
     */
    fun checkGatewayAdmin(): Mono<ServiceHealthDto> {
        return Mono.just(
            ServiceHealthDto(SERVICE_GATEWAY_ADMIN, ServiceStatus.UP, Instant.now())
        )
    }

    /**
     * Проверяет доступность PostgreSQL через R2DBC.
     *
     * Использует Mono.usingWhen для корректного управления соединением:
     * - Открывает соединение
     * - Выполняет SELECT 1
     * - Закрывает соединение (гарантированно, даже при ошибках)
     */
    fun checkPostgresql(): Mono<ServiceHealthDto> {
        logger.debug("Проверка PostgreSQL через R2DBC")

        return Mono.usingWhen(
            // Открытие ресурса (соединения)
            Mono.from(connectionFactory.create()),
            // Использование ресурса
            { connection ->
                Mono.from(connection.createStatement("SELECT 1").execute())
                    .map { ServiceHealthDto(SERVICE_POSTGRESQL, ServiceStatus.UP, Instant.now()) }
            },
            // Закрытие ресурса (всегда выполняется)
            { connection -> Mono.from(connection.close()) }
        )
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("PostgreSQL недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_POSTGRESQL, error))
            }
    }

    /**
     * Проверяет доступность Redis через PING.
     */
    fun checkRedis(): Mono<ServiceHealthDto> {
        logger.debug("Проверка Redis через PING")

        // Если Redis не настроен — возвращаем DOWN
        if (redisTemplate == null) {
            logger.warn("Redis не настроен")
            return Mono.just(
                ServiceHealthDto(SERVICE_REDIS, ServiceStatus.DOWN, Instant.now(), "Redis not configured")
            )
        }

        return redisTemplate.connectionFactory.reactiveConnection
            .ping()
            .map {
                ServiceHealthDto(SERVICE_REDIS, ServiceStatus.UP, Instant.now())
            }
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("Redis недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_REDIS, error))
            }
    }

    /**
     * Проверяет доступность Prometheus через /-/healthy endpoint.
     */
    fun checkPrometheus(): Mono<ServiceHealthDto> {
        logger.debug("Проверка Prometheus: {}/-/healthy", prometheusUrl)

        return webClient.get()
            .uri("$prometheusUrl/-/healthy")
            .retrieve()
            .bodyToMono(String::class.java)
            .map {
                ServiceHealthDto(SERVICE_PROMETHEUS, ServiceStatus.UP, Instant.now())
            }
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("Prometheus недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_PROMETHEUS, error))
            }
    }

    /**
     * Проверяет доступность Grafana через /api/health endpoint.
     */
    fun checkGrafana(): Mono<ServiceHealthDto> {
        logger.debug("Проверка Grafana: {}/api/health", grafanaUrl)

        return webClient.get()
            .uri("$grafanaUrl/api/health")
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val database = response["database"] as? String
                if (database == "ok") {
                    ServiceHealthDto(SERVICE_GRAFANA, ServiceStatus.UP, Instant.now())
                } else {
                    ServiceHealthDto(SERVICE_GRAFANA, ServiceStatus.DOWN, Instant.now(), "Database: $database")
                }
            }
            .timeout(checkTimeout)
            .onErrorResume { error ->
                logger.warn("Grafana недоступен: {}", error.message)
                Mono.just(createDownStatus(SERVICE_GRAFANA, error))
            }
    }

    /**
     * Создаёт ServiceHealthDto со статусом DOWN и сообщением об ошибке.
     */
    private fun createDownStatus(serviceName: String, error: Throwable): ServiceHealthDto {
        val errorMessage = when (error) {
            is TimeoutException -> "Connection timeout"
            is ConnectException -> "Connection refused"
            is WebClientRequestException -> "Connection failed: ${error.message}"
            else -> error.message ?: "Unknown error"
        }
        return ServiceHealthDto(serviceName, ServiceStatus.DOWN, Instant.now(), errorMessage)
    }
}
