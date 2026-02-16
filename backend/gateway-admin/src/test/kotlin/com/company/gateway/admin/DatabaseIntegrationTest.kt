package com.company.gateway.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier

@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("gateway")
            .withUsername("gateway")
            .withPassword("gateway")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
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
