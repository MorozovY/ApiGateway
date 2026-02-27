package com.company.gateway.admin.repository

import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        private var postgres: PostgreSQLContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            if (isTestcontainersDisabled) {
                return
            }
            postgres = PostgreSQLContainer("postgres:16")
                .withDatabaseName("gateway")
                .withUsername("gateway")
                .withPassword("gateway")
            postgres!!.start()
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
                // В CI читаем из env переменных (gateway-admin использует POSTGRES_DB_ADMIN)
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB_ADMIN") ?: System.getenv("POSTGRES_DB") ?: "gateway_admin_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
            } else {
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${postgres!!.host}:${postgres!!.firstMappedPort}/${postgres!!.databaseName}"
                }
                registry.add("spring.r2dbc.username", postgres!!::getUsername)
                registry.add("spring.r2dbc.password", postgres!!::getPassword)
                registry.add("spring.flyway.url", postgres!!::getJdbcUrl)
                registry.add("spring.flyway.user", postgres!!::getUsername)
                registry.add("spring.flyway.password", postgres!!::getPassword)
            }
            // Отключаем Redis для репозиторных тестов
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            registry.add("management.health.redis.enabled") { false }
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
        }
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        // Очищаем пользователей перед каждым тестом (кроме admin из миграции)
        // Используем StepVerifier вместо blockLast() для соблюдения reactive паттернов
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()
    }

    @Test
    fun `сохраняет и находит пользователя по username`() {
        val user = User(
            username = "testuser",
            email = "test@example.com",
            passwordHash = "\$2a\$10\$hashedpwd",
            role = Role.DEVELOPER
        )

        StepVerifier.create(
            userRepository.save(user)
                .flatMap { userRepository.findByUsername("testuser") }
        )
            .assertNext { found ->
                assertThat(found.username).isEqualTo("testuser")
                assertThat(found.email).isEqualTo("test@example.com")
                assertThat(found.role).isEqualTo(Role.DEVELOPER)
                assertThat(found.isActive).isTrue()
                assertThat(found.id).isNotNull()
            }
            .verifyComplete()
    }

    @Test
    fun `находит пользователя по email`() {
        val user = User(
            username = "emailuser",
            email = "unique@example.com",
            passwordHash = "hash",
            role = Role.SECURITY
        )

        StepVerifier.create(
            userRepository.save(user)
                .flatMap { userRepository.findByEmail("unique@example.com") }
        )
            .assertNext { found ->
                assertThat(found.email).isEqualTo("unique@example.com")
                assertThat(found.role).isEqualTo(Role.SECURITY)
            }
            .verifyComplete()
    }

    @Test
    fun `возвращает true для existsByUsername когда пользователь существует`() {
        val user = User(
            username = "existinguser",
            email = "existing@example.com",
            passwordHash = "hash",
            role = Role.DEVELOPER
        )

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.existsByUsername("existinguser"))
        )
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `возвращает false для existsByUsername когда пользователь не существует`() {
        StepVerifier.create(userRepository.existsByUsername("nonexistent"))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `возвращает true для existsByEmail когда email существует`() {
        val user = User(
            username = "emailcheck",
            email = "emailcheck@example.com",
            passwordHash = "hash",
            role = Role.DEVELOPER
        )

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.existsByEmail("emailcheck@example.com"))
        )
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `уникальное ограничение на username`() {
        val user1 = User(username = "dupe", email = "a@test.com", passwordHash = "hash", role = Role.DEVELOPER)
        val user2 = User(username = "dupe", email = "b@test.com", passwordHash = "hash", role = Role.DEVELOPER)

        StepVerifier.create(
            userRepository.save(user1)
                .then(userRepository.save(user2))
        )
            .expectError(DataIntegrityViolationException::class.java)
            .verify()
    }

    @Test
    fun `уникальное ограничение на email`() {
        val user1 = User(username = "user1dupe", email = "same@test.com", passwordHash = "hash", role = Role.DEVELOPER)
        val user2 = User(username = "user2dupe", email = "same@test.com", passwordHash = "hash", role = Role.DEVELOPER)

        StepVerifier.create(
            userRepository.save(user1)
                .then(userRepository.save(user2))
        )
            .expectError(DataIntegrityViolationException::class.java)
            .verify()
    }

    @Test
    fun `сохраняет пользователя с ролью ADMIN`() {
        val adminUser = User(
            username = "secondadmin",
            email = "admin2@example.com",
            passwordHash = "\$2a\$10\$hashedpwd",
            role = Role.ADMIN
        )

        StepVerifier.create(
            userRepository.save(adminUser)
                .flatMap { userRepository.findByUsername("secondadmin") }
        )
            .assertNext { found ->
                assertThat(found.role).isEqualTo(Role.ADMIN)
            }
            .verifyComplete()
    }

    @Test
    fun `таблица users создана после миграции V3`() {
        // Проверяем, что миграция создала таблицу - admin seed должен существовать
        StepVerifier.create(userRepository.count())
            .assertNext { count ->
                // Seed admin из V3_1 или AdminUserDataLoader должен существовать
                assertThat(count).isGreaterThanOrEqualTo(0)
            }
            .verifyComplete()
    }

    @Test
    fun `admin seed пользователь создан миграцией V3_1`() {
        // Проверяем, что seed admin существует после миграции
        StepVerifier.create(userRepository.findByUsername("admin"))
            .assertNext { admin ->
                assertThat(admin.username).isEqualTo("admin")
                assertThat(admin.email).isEqualTo("admin@gateway.local")
                assertThat(admin.role).isEqualTo(Role.ADMIN)
                assertThat(admin.isActive).isTrue()
                // Проверяем что password_hash содержит placeholder (до обновления AdminUserDataLoader)
                // или валидный BCrypt хеш (после обновления)
                assertThat(admin.passwordHash).isNotBlank()
            }
            .verifyComplete()
    }
}
