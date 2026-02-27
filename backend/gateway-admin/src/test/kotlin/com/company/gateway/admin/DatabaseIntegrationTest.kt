package com.company.gateway.admin

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier

@SpringBootTest
class DatabaseIntegrationTest {

    companion object {
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"
        private var postgres: PostgreSQLContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            if (!isTestcontainersDisabled) {
                postgres = PostgreSQLContainer("postgres:16")
                    .withDatabaseName("gateway")
                    .withUsername("gateway")
                    .withPassword("gateway")
                postgres?.start()
            }
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB") ?: "gateway_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
            } else {
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
            }
            // Disable Redis auto-configuration for database tests
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            // Disable Redis health check when Redis is not available
            registry.add("management.health.redis.enabled") { false }
            // Exclude Redis from readiness group
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
        }
    }

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Test
    fun `flyway должен выполнить V1 миграцию и создать таблицу routes`() {
        val result = databaseClient.sql("SELECT COUNT(*)::int as cnt FROM information_schema.tables WHERE table_name = 'routes'")
            .map { row -> row.get("cnt", Integer::class.java)!!.toInt() }
            .one()

        StepVerifier.create(result)
            .expectNext(1)
            .verifyComplete()
    }

    @Test
    fun `таблица routes должна иметь корректные колонки`() {
        val expectedColumns = listOf("id", "path", "upstream_url", "methods", "status", "created_by", "created_at", "updated_at")

        val result = databaseClient.sql(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_name = 'routes'
            ORDER BY ordinal_position
            """.trimIndent()
        )
            .map { row -> row.get("column_name", String::class.java) }
            .all()
            .collectList()

        StepVerifier.create(result)
            .expectNextMatches { columns ->
                expectedColumns.all { expected -> columns?.contains(expected) == true }
            }
            .verifyComplete()
    }

    @Test
    fun `таблица routes должна иметь check constraint на status`() {
        val invalidInsert = databaseClient.sql(
            """
            INSERT INTO routes (path, upstream_url, status)
            VALUES ('/test', 'http://localhost', 'invalid_status')
            """.trimIndent()
        ).fetch().rowsUpdated()

        StepVerifier.create(invalidInsert)
            .expectError()
            .verify()
    }

    @Test
    fun `таблица routes должна разрешать валидные значения status`() {
        val validStatuses = listOf("draft", "pending", "published", "rejected")

        for (status in validStatuses) {
            val insert = databaseClient.sql(
                """
                INSERT INTO routes (path, upstream_url, status)
                VALUES ('/test-${status}', 'http://localhost', '${status}')
                """.trimIndent()
            ).fetch().rowsUpdated()

            StepVerifier.create(insert)
                .expectNext(1L)
                .verifyComplete()
        }
    }

    @Test
    fun `таблица routes должна иметь индексы на path и status`() {
        val result = databaseClient.sql(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'routes' AND indexname IN ('idx_routes_path', 'idx_routes_status')
            """.trimIndent()
        )
            .map { row -> row.get("indexname", String::class.java) }
            .all()
            .collectList()

        StepVerifier.create(result)
            .expectNextMatches { indexes ->
                indexes?.contains("idx_routes_path") == true && indexes.contains("idx_routes_status")
            }
            .verifyComplete()
    }

    @Test
    fun `колонка path должна иметь уникальное ограничение`() {
        // Первая вставка
        val firstInsert = databaseClient.sql(
            """
            INSERT INTO routes (path, upstream_url)
            VALUES ('/unique-test', 'http://localhost')
            """.trimIndent()
        ).fetch().rowsUpdated()

        StepVerifier.create(firstInsert)
            .expectNext(1L)
            .verifyComplete()

        // Дубликат вставки должен завершиться ошибкой
        val duplicateInsert = databaseClient.sql(
            """
            INSERT INTO routes (path, upstream_url)
            VALUES ('/unique-test', 'http://other-host')
            """.trimIndent()
        ).fetch().rowsUpdated()

        StepVerifier.create(duplicateInsert)
            .expectError()
            .verify()
    }
}
