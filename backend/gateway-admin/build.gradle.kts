plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":gateway-common"))

    // Spring Boot WebFlux
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // AOP для @RequireRole аспекта
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // R2DBC PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.4.RELEASE")

    // Flyway for migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Redis Reactive
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    // OAuth2 Resource Server для Keycloak JWT валидации (Story 12.3)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Prometheus Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI (2.7.0 для совместимости с Spring Boot 3.4.x)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.7.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.19.5")
    testImplementation("org.testcontainers:r2dbc:1.19.5")
    testImplementation("org.testcontainers:junit-jupiter:1.19.5")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    // MockWebServer для PrometheusClient тестов (Story 7.0)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Nimbus JOSE+JWT для тестов Keycloak JWT validation (Story 12.3)
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
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
