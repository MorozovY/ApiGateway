plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

extra["springCloudVersion"] = "2024.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencies {
    implementation(project(":gateway-common"))

    // Spring Cloud Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // R2DBC PostgreSQL (for route loading from DB)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.4.RELEASE")

    // Redis Reactive
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Caffeine cache (for local cache fallback when Redis unavailable)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security (for actuator endpoint protection)
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.security:spring-security-test")

    // OAuth2 Resource Server (JWT validation, JWKS support) — Story 12.4
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Prometheus Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Structured JSON logging (Story 1.6)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Reactor Context → MDC propagation (Story 1.6)
    implementation("io.micrometer:context-propagation")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.testcontainers:postgresql:1.19.5")
    testImplementation("org.testcontainers:r2dbc:1.19.5")
    testImplementation("org.testcontainers:junit-jupiter:1.19.5")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
    // Flyway for test schema setup (JDBC-based, runs before R2DBC)
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()

    // Выводим stdout/stderr тестов для диагностики в CI
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }

    // Testcontainers config (из -P или env)
    val tcDisabled = findProperty("testcontainersDisabled")?.toString()
        ?: System.getenv("TESTCONTAINERS_DISABLED")
        ?: "false"
    environment("TESTCONTAINERS_DISABLED", tcDisabled)
    environment("TESTCONTAINERS_RYUK_DISABLED", tcDisabled)

    // Passthrough database env vars для BaseIntegrationTest
    val dbVars = listOf("POSTGRES_HOST", "POSTGRES_PORT", "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD",
           "REDIS_HOST", "REDIS_PORT")
    dbVars.forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }

    // Passthrough Spring Boot env vars (SPRING_R2DBC_URL -> spring.r2dbc.url)
    // Эти переменные Spring Boot конвертирует автоматически в application properties
    val springVars = System.getenv().filterKeys { it.startsWith("SPRING_") }
    springVars.forEach { (key, value) ->
        environment(key, value)
    }

    // Диагностика: выводим что передаем в test JVM
    doFirst {
        println("=== ENV VARS PASSED TO TEST JVM ===")
        println("TESTCONTAINERS_DISABLED=$tcDisabled")
        dbVars.forEach { key ->
            println("$key=${System.getenv(key) ?: "NOT SET"}")
        }
        springVars.forEach { (key, value) ->
            println("$key=$value")
        }
        println("===================================")
    }
}
