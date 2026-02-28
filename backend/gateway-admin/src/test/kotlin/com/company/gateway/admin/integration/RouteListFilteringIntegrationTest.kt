package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Интеграционные тесты для фильтрации и поиска маршрутов (Story 3.2).
 *
 * Тестирование AC1-AC6:
 * - AC1: Базовый список маршрутов с пагинацией
 * - AC2: Фильтрация по статусу
 * - AC3: Фильтрация по автору (мои маршруты)
 * - AC4: Текстовый поиск
 * - AC5: Пагинация с offset и limit
 * - AC6: Комбинация фильтров (AND логика)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RouteListFilteringIntegrationTest {

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
                // В CI читаем из env переменных
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
            // Отключаем Redis для тестов
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            registry.add("management.health.redis.enabled") { false }
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
            // JWT конфигурация
            registry.add("jwt.secret") { "test-secret-key-minimum-32-characters-long" }
            registry.add("jwt.expiration") { 86400000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerToken: String
    private lateinit var otherDeveloperToken: String
    private lateinit var developerUser: User
    private lateinit var otherDeveloperUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
        // Очищаем audit_logs перед пользователями (FK RESTRICT, V5_1 миграция)
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        otherDeveloperUser = createTestUser("otherdev", "password", Role.DEVELOPER)

        developerToken = jwtService.generateToken(developerUser)
        otherDeveloperToken = jwtService.generateToken(otherDeveloperUser)
    }

    // ============================================
    // AC1: Базовый список маршрутов с пагинацией
    // ============================================

    @Nested
    inner class AC1_BasicListWithPagination {

        @Test
        fun `возвращает пагинированный список маршрутов с default offset и limit`() {
            // Создаём тестовые маршруты
            repeat(5) { i ->
                createTestRoute("/api/service$i", developerUser.id!!)
            }

            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(5)
                .jsonPath("$.total").isEqualTo(5)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(20)
        }

        @Test
        fun `сортировка по умолчанию createdAt descending`() {
            // Создаём маршруты с разным временем создания через явное указание createdAt
            val baseTime = Instant.now()
            createTestRouteWithTimestamp("/api/first", developerUser.id!!, createdAt = baseTime.minusSeconds(20))
            createTestRouteWithTimestamp("/api/second", developerUser.id!!, createdAt = baseTime.minusSeconds(10))
            createTestRouteWithTimestamp("/api/third", developerUser.id!!, createdAt = baseTime)

            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].path").isEqualTo("/api/third")
                .jsonPath("$.items[1].path").isEqualTo("/api/second")
                .jsonPath("$.items[2].path").isEqualTo("/api/first")
        }
    }

    // ============================================
    // AC2: Фильтрация по статусу
    // ============================================

    @Nested
    inner class AC2_FilterByStatus {

        @Test
        fun `фильтрует маршруты по статусу draft`() {
            createTestRoute("/api/draft1", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/draft2", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/published", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/pending", developerUser.id!!, RouteStatus.PENDING)

            webTestClient.get()
                .uri("/api/v1/routes?status=draft")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[*].status").value<List<String>> { statuses ->
                    assertThat(statuses).allMatch { it == "draft" }
                }
        }

        @Test
        fun `фильтрует маршруты по статусу published`() {
            createTestRoute("/api/draft", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/published1", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/published2", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/pending", developerUser.id!!, RouteStatus.PENDING)

            webTestClient.get()
                .uri("/api/v1/routes?status=published")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[*].status").value<List<String>> { statuses ->
                    assertThat(statuses).allMatch { it == "published" }
                }
        }
    }

    // ============================================
    // AC3: Фильтрация по автору (мои маршруты)
    // ============================================

    @Nested
    inner class AC3_FilterByCreatedBy {

        @Test
        fun `createdBy=me возвращает только свои маршруты`() {
            // Маршруты текущего пользователя
            createTestRoute("/api/my-route1", developerUser.id!!)
            createTestRoute("/api/my-route2", developerUser.id!!)
            // Маршруты другого пользователя
            createTestRoute("/api/other-route1", otherDeveloperUser.id!!)
            createTestRoute("/api/other-route2", otherDeveloperUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[*].createdBy").value<List<String>> { createdByList ->
                    assertThat(createdByList).allMatch { it == developerUser.id.toString() }
                }
        }

        @Test
        fun `другой пользователь видит только свои маршруты с createdBy=me`() {
            // Маршруты первого пользователя
            createTestRoute("/api/dev-route1", developerUser.id!!)
            createTestRoute("/api/dev-route2", developerUser.id!!)
            // Маршруты второго пользователя
            createTestRoute("/api/other-route1", otherDeveloperUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me")
                .cookie("auth_token", otherDeveloperToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].createdBy").isEqualTo(otherDeveloperUser.id.toString())
        }

        @Test
        fun `createdBy с невалидным UUID возвращает пустой результат`() {
            // Создаём маршруты
            createTestRoute("/api/my-route1", developerUser.id!!)
            createTestRoute("/api/my-route2", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=invalid-uuid")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `createdBy с валидным UUID другого пользователя возвращает его маршруты`() {
            // Маршруты текущего пользователя
            createTestRoute("/api/my-route", developerUser.id!!)
            // Маршруты другого пользователя
            createTestRoute("/api/other-route1", otherDeveloperUser.id!!)
            createTestRoute("/api/other-route2", otherDeveloperUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=${otherDeveloperUser.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[*].createdBy").value<List<String>> { createdByList ->
                    assertThat(createdByList).allMatch { it == otherDeveloperUser.id.toString() }
                }
        }
    }

    // ============================================
    // AC4: Текстовый поиск
    // ============================================

    @Nested
    inner class AC4_TextSearch {

        @Test
        fun `search по path находит маршруты (case-insensitive)`() {
            createTestRoute("/api/orders", developerUser.id!!)
            createTestRoute("/api/ORDERS-v2", developerUser.id!!)
            createTestRoute("/api/products", developerUser.id!!)
            createTestRoute("/api/users", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?search=order")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `search по description находит маршруты (case-insensitive)`() {
            createTestRoute("/api/svc1", developerUser.id!!, description = "Order management service")
            createTestRoute("/api/svc2", developerUser.id!!, description = "ORDERS processing")
            createTestRoute("/api/svc3", developerUser.id!!, description = "Product catalog")
            createTestRoute("/api/svc4", developerUser.id!!, description = "User management")

            webTestClient.get()
                .uri("/api/v1/routes?search=order")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `search находит по path ИЛИ description`() {
            createTestRoute("/api/orders", developerUser.id!!, description = "Service A")
            createTestRoute("/api/products", developerUser.id!!, description = "Order related products")
            createTestRoute("/api/users", developerUser.id!!, description = "User management")

            webTestClient.get()
                .uri("/api/v1/routes?search=order")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `search корректно экранирует спецсимвол процент`() {
            // Маршрут содержащий % в path
            createTestRoute("/api/discount-100%", developerUser.id!!)
            createTestRoute("/api/discount-50", developerUser.id!!)
            createTestRoute("/api/orders", developerUser.id!!)

            // Поиск "100%" должен найти только маршрут с буквальным %
            webTestClient.get()
                .uri { it.path("/api/v1/routes").queryParam("search", "100%").build() }
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/discount-100%")
        }

        @Test
        fun `search корректно экранирует спецсимвол подчёркивание`() {
            // _ в ILIKE означает "любой один символ", должен быть экранирован
            createTestRoute("/api/user_settings", developerUser.id!!)
            createTestRoute("/api/user-settings", developerUser.id!!)
            createTestRoute("/api/usersettings", developerUser.id!!)

            // Поиск "user_" должен найти только маршрут с буквальным _
            webTestClient.get()
                .uri { it.path("/api/v1/routes").queryParam("search", "user_").build() }
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/user_settings")
        }
    }

    // ============================================
    // AC5: Пагинация с offset и limit
    // ============================================

    @Nested
    inner class AC5_Pagination {

        @Test
        fun `пагинация offset=20, limit=10 возвращает маршруты 21-30`() {
            // Создаём 30 маршрутов
            repeat(30) { i ->
                createTestRoute("/api/route-${String.format("%02d", i)}", developerUser.id!!)
            }

            webTestClient.get()
                .uri("/api/v1/routes?offset=20&limit=10")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(10)
                .jsonPath("$.total").isEqualTo(30)
                .jsonPath("$.offset").isEqualTo(20)
                .jsonPath("$.limit").isEqualTo(10)
        }

        @Test
        fun `total отражает полное количество с учётом фильтров`() {
            // Создаём 10 draft и 5 published маршрутов
            repeat(10) { i ->
                createTestRoute("/api/draft-$i", developerUser.id!!, RouteStatus.DRAFT)
            }
            repeat(5) { i ->
                createTestRoute("/api/published-$i", developerUser.id!!, RouteStatus.PUBLISHED)
            }

            webTestClient.get()
                .uri("/api/v1/routes?status=draft&limit=5")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(5)
                .jsonPath("$.total").isEqualTo(10) // total = все draft маршруты
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(5)
        }
    }

    // ============================================
    // AC6: Комбинация фильтров (AND логика)
    // ============================================

    @Nested
    inner class AC6_CombinedFilters {

        @Test
        fun `комбинация status и search применяется с AND логикой`() {
            // Создаём маршруты с разными комбинациями
            createTestRoute("/api/orders-v1", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/orders-v2", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/products", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/users", developerUser.id!!, RouteStatus.DRAFT)

            webTestClient.get()
                .uri("/api/v1/routes?status=published&search=order")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/orders-v1")
                .jsonPath("$.items[0].status").isEqualTo("published")
        }

        @Test
        fun `комбинация createdBy=me и status`() {
            // Маршруты текущего пользователя
            createTestRoute("/api/my-draft", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/my-published", developerUser.id!!, RouteStatus.PUBLISHED)
            // Маршруты другого пользователя
            createTestRoute("/api/other-draft", otherDeveloperUser.id!!, RouteStatus.DRAFT)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me&status=draft")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/my-draft")
        }

        @Test
        fun `комбинация всех фильтров createdBy=me, status и search`() {
            // Маршруты текущего пользователя
            createTestRoute("/api/my-orders", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/my-products", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/my-orders-published", developerUser.id!!, RouteStatus.PUBLISHED)
            // Маршруты другого пользователя
            createTestRoute("/api/other-orders", otherDeveloperUser.id!!, RouteStatus.DRAFT)

            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me&status=draft&search=order")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/my-orders")
        }
    }

    // ============================================
    // Валидация входных параметров
    // ============================================

    @Nested
    inner class InputValidation {

        @Test
        fun `возвращает 400 когда offset отрицательный`() {
            webTestClient.get()
                .uri("/api/v1/routes?offset=-1")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда limit больше 100`() {
            webTestClient.get()
                .uri("/api/v1/routes?limit=101")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда limit меньше 1`() {
            webTestClient.get()
                .uri("/api/v1/routes?limit=0")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда search пустой`() {
            webTestClient.get()
                .uri("/api/v1/routes?search=")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда search превышает 100 символов`() {
            val longSearch = "a".repeat(101)
            webTestClient.get()
                .uri("/api/v1/routes?search=$longSearch")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `ошибка валидации возвращается в формате RFC 7807`() {
            webTestClient.get()
                .uri("/api/v1/routes?offset=-1")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.title").isEqualTo("Validation Error")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").isEqualTo("Offset must be greater than or equal to 0")
                .jsonPath("$.instance").isEqualTo("/api/v1/routes")
                .jsonPath("$.correlationId").exists()
        }

        @Test
        fun `ошибка limit возвращается в формате RFC 7807`() {
            webTestClient.get()
                .uri("/api/v1/routes?limit=101")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.title").isEqualTo("Validation Error")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").isEqualTo("Limit must be between 1 and 100")
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(
        username: String,
        password: String,
        role: Role,
        isActive: Boolean = true
    ): User {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = isActive
        )
        var savedUser: User? = null
        StepVerifier.create(userRepository.save(user))
            .consumeNextWith { savedUser = it }
            .verifyComplete()
        return savedUser!!
    }

    private fun createTestRoute(
        path: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT,
        description: String? = "Test route"
    ): Route {
        return createTestRouteWithTimestamp(path, createdBy, status, description, Instant.now())
    }

    /**
     * Создаёт тестовый маршрут с явным указанием времени создания.
     * Используется для тестирования сортировки без Thread.sleep().
     */
    private fun createTestRouteWithTimestamp(
        path: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT,
        description: String? = "Test route",
        createdAt: Instant
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = description,
            status = status,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
