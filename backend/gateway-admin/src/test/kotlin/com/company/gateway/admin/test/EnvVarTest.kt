package com.company.gateway.admin.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Диагностический тест для проверки передачи env vars в test JVM.
 * Используется для отладки CI pipeline.
 */
class EnvVarTest {

    @Test
    fun `проверка передачи env в test JVM`() {
        // Выводим все релевантные env vars
        println("=== ENV VARS IN TEST JVM ===")
        println("TESTCONTAINERS_DISABLED = ${System.getenv("TESTCONTAINERS_DISABLED")}")
        println("POSTGRES_HOST = ${System.getenv("POSTGRES_HOST")}")
        println("POSTGRES_PORT = ${System.getenv("POSTGRES_PORT")}")
        println("REDIS_HOST = ${System.getenv("REDIS_HOST")}")
        println("REDIS_PORT = ${System.getenv("REDIS_PORT")}")
        println("SPRING_R2DBC_URL = ${System.getenv("SPRING_R2DBC_URL")}")
        println("SPRING_R2DBC_USERNAME = ${System.getenv("SPRING_R2DBC_USERNAME")}")
        println("SPRING_FLYWAY_URL = ${System.getenv("SPRING_FLYWAY_URL")}")
        println("SPRING_DATA_REDIS_HOST = ${System.getenv("SPRING_DATA_REDIS_HOST")}")
        println("============================")

        // Проверяем что TESTCONTAINERS_DISABLED передан если установлен в shell
        val tcDisabled = System.getenv("TESTCONTAINERS_DISABLED")
        if (tcDisabled != null) {
            assertEquals("true", tcDisabled, "TESTCONTAINERS_DISABLED должен быть 'true'")
        }

        // Если в CI, проверяем что DB vars тоже переданы
        if (tcDisabled == "true") {
            assertNotNull(System.getenv("POSTGRES_HOST"), "POSTGRES_HOST должен быть установлен в CI")
            assertNotNull(System.getenv("REDIS_HOST"), "REDIS_HOST должен быть установлен в CI")
        }
    }
}
