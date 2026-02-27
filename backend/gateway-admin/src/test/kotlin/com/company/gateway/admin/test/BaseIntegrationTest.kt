package com.company.gateway.admin.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
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
abstract class BaseIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // PostgreSQL контейнер (null в CI)
        private var postgres: PostgreSQLContainer<*>? = null

        // Redis контейнер (null в CI)
        private var redis: GenericContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            if (isTestcontainersDisabled) {
                return
            }
            postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                .withDatabaseName("gateway_test")
                .withUsername("gateway")
                .withPassword("gateway")
            postgres!!.start()

            redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
            redis!!.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
            redis?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI используем application-ci.yml — не переопределяем свойства
                // Свойства уже настроены в application-ci.yml для gateway_admin_test
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
