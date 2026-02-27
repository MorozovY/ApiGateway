package com.company.gateway.admin.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetAddress

/**
 * Диагностический тест для проверки передачи env vars и сети в test JVM.
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

        // Проверяем DNS resolution для postgres (hardcoded hostname из application-ci.yml)
        listOf("postgres", "redis", "localhost").forEach { host ->
            try {
                val addr = InetAddress.getByName(host)
                println("DNS OK: $host -> ${addr.hostAddress}")
            } catch (e: Exception) {
                println("DNS FAILED: $host -> ${e.message}")
            }
        }

        // Проверяем TCP connection к postgres:5432
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("postgres", 5432), 5000)
                println("TCP OK: postgres:5432")
            }
        } catch (e: Exception) {
            println("TCP FAILED: postgres:5432 -> ${e.message}")
        }

        // Проверяем что TESTCONTAINERS_DISABLED передан если установлен в shell
        val tcDisabled = System.getenv("TESTCONTAINERS_DISABLED")
        if (tcDisabled != null) {
            assertEquals("true", tcDisabled, "TESTCONTAINERS_DISABLED должен быть 'true'")
        }
    }
}
