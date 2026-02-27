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
    // Передаём конфигурацию через environment с Spring Boot naming convention
    // Spring Boot автоматически конвертирует SPRING_R2DBC_URL -> spring.r2dbc.url
    val pgHost = providers.environmentVariable("POSTGRES_HOST").getOrElse("localhost")
    val pgPort = providers.environmentVariable("POSTGRES_PORT").getOrElse("5432")
    val pgDb = providers.environmentVariable("POSTGRES_DB").getOrElse("gateway_test")
    val pgUser = providers.environmentVariable("POSTGRES_USER").getOrElse("gateway")
    val pgPass = providers.environmentVariable("POSTGRES_PASSWORD").getOrElse("gateway")
    val redisHost = providers.environmentVariable("REDIS_HOST").getOrElse("localhost")
    val redisPort = providers.environmentVariable("REDIS_PORT").getOrElse("6379")

    // Environment для Spring Boot (relaxed binding: SPRING_R2DBC_URL -> spring.r2dbc.url)
    environment("SPRING_R2DBC_URL", "r2dbc:postgresql://$pgHost:$pgPort/$pgDb")
    environment("SPRING_R2DBC_USERNAME", pgUser)
    environment("SPRING_R2DBC_PASSWORD", pgPass)
    environment("SPRING_FLYWAY_URL", "jdbc:postgresql://$pgHost:$pgPort/$pgDb")
    environment("SPRING_FLYWAY_USER", pgUser)
    environment("SPRING_FLYWAY_PASSWORD", pgPass)
    environment("SPRING_DATA_REDIS_HOST", redisHost)
    environment("SPRING_DATA_REDIS_PORT", redisPort)

    // Testcontainers config
    environment("TESTCONTAINERS_DISABLED", providers.environmentVariable("TESTCONTAINERS_DISABLED").getOrElse("false"))
    environment("TESTCONTAINERS_RYUK_DISABLED", providers.environmentVariable("TESTCONTAINERS_RYUK_DISABLED").getOrElse("false"))
}
