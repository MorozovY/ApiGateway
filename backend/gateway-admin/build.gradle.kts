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
    // Передача project properties (-P) из командной строки в test JVM как system properties
    // Gradle: -PdbUrl=... -> test JVM: -Dspring.r2dbc.url=...
    findProperty("dbUrl")?.let { systemProperty("spring.r2dbc.url", it) }
    findProperty("dbUser")?.let { systemProperty("spring.r2dbc.username", it) }
    findProperty("dbPass")?.let { systemProperty("spring.r2dbc.password", it) }
    findProperty("flywayUrl")?.let { systemProperty("spring.flyway.url", it) }
    findProperty("flywayUser")?.let { systemProperty("spring.flyway.user", it) }
    findProperty("flywayPass")?.let { systemProperty("spring.flyway.password", it) }
    findProperty("redisHost")?.let { systemProperty("spring.data.redis.host", it) }
    findProperty("redisPort")?.let { systemProperty("spring.data.redis.port", it) }

    // Testcontainers env
    environment("TESTCONTAINERS_DISABLED", System.getenv("TESTCONTAINERS_DISABLED") ?: "false")
    environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "false")
}
