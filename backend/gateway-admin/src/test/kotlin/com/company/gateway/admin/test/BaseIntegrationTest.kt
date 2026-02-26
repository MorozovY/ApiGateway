package com.company.gateway.admin.test

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Базовый класс для интеграционных тестов.
 *
 * В локальной среде использует Testcontainers для PostgreSQL и Redis.
 * В CI (TESTCONTAINERS_DISABLED=true) использует GitLab Services через application-ci.yml.
 *
 * Использование:
 * ```
 * @SpringBootTest
 * class MyIntegrationTest : BaseIntegrationTest() {
 *     // тесты...
 * }
 * ```
 */
@Testcontainers
abstract class BaseIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // PostgreSQL контейнер (null в CI)
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*>? = if (!isTestcontainersDisabled) {
            PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                .withDatabaseName("gateway_test")
                .withUsername("gateway")
                .withPassword("gateway")
        } else null

        // Redis контейнер (null в CI)
        @Container
        @JvmStatic
        val redis: GenericContainer<*>? = if (!isTestcontainersDisabled) {
            GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
        } else null

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // В CI используем properties из application-ci.yml
            if (isTestcontainersDisabled) {
                return
            }

            // Локально настраиваем Testcontainers
            postgres?.let { pg ->
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${pg.host}:${pg.firstMappedPort}/${pg.databaseName}"
                }
                registry.add("spring.r2dbc.username", pg::getUsername)
                registry.add("spring.r2dbc.password", pg::getPassword)
                registry.add("spring.flyway.url", pg::getJdbcUrl)
                registry.add("spring.flyway.user", pg::getUsername)
                registry.add("spring.flyway.password", pg::getPassword)
            }

            redis?.let { rd ->
                registry.add("spring.data.redis.host", rd::getHost)
                registry.add("spring.data.redis.port") { rd.getMappedPort(6379) }
            }
        }
    }
}
