# Story 2.4: Role-Based Access Control

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **System**,
I want to restrict actions based on user role,
So that users can only perform authorized operations (FR27).

## Acceptance Criteria

1. **AC1: Developer не может получить доступ к ADMIN endpoints**
   **Given** аутентифицированный пользователь с ролью "developer"
   **When** запрос к endpoint с аннотацией `@RequireRole(ADMIN)`
   **Then** ответ возвращает HTTP 403 Forbidden
   **And** response body содержит detail "Insufficient permissions"
   **And** response следует формату RFC 7807

2. **AC2: Admin может получить доступ к ADMIN endpoints**
   **Given** аутентифицированный пользователь с ролью "admin"
   **When** запрос к endpoint с аннотацией `@RequireRole(ADMIN)`
   **Then** запрос успешно обрабатывается

3. **AC3: Role hierarchy — Admin > Security > Developer**
   **Given** endpoint требует роль "security" (`@RequireRole(SECURITY)`)
   **When** user с ролью "admin" делает запрос
   **Then** запрос успешно обрабатывается (admin имеет все права security)
   **When** user с ролью "security" делает запрос
   **Then** запрос успешно обрабатывается
   **When** user с ролью "developer" делает запрос
   **Then** ответ возвращает HTTP 403 Forbidden

4. **AC4: Developer может только read/update/delete свои маршруты**
   **Given** маршрут создан другим пользователем
   **When** developer пытается обновить или удалить этот маршрут
   **Then** ответ возвращает HTTP 403 Forbidden
   **And** detail содержит "You can only modify your own routes"

5. **AC5: Developer может удалять только draft маршруты**
   **Given** developer владеет маршрутом в статусе "published" или "pending"
   **When** developer пытается удалить маршрут
   **Then** ответ возвращает HTTP 409 Conflict
   **And** detail содержит "Only draft routes can be deleted"

6. **AC6: Security может approve/reject маршруты**
   **Given** аутентифицированный пользователь с ролью "security" или "admin"
   **When** POST `/api/v1/routes/{id}/approve` или `/api/v1/routes/{id}/reject`
   **Then** операция выполняется успешно
   **When** user с ролью "developer" пытается выполнить эти операции
   **Then** ответ возвращает HTTP 403 Forbidden

7. **AC7: Security может читать audit logs**
   **Given** аутентифицированный пользователь с ролью "security" или "admin"
   **When** GET `/api/v1/audit`
   **Then** ответ возвращает audit log entries
   **When** user с ролью "developer" делает запрос
   **Then** ответ возвращает HTTP 403 Forbidden

8. **AC8: Admin может управлять пользователями**
   **Given** аутентифицированный пользователь с ролью "admin"
   **When** запрос к `/api/v1/users` endpoints (GET, POST, PUT)
   **Then** операция выполняется успешно
   **When** user с ролью "security" или "developer" делает запрос
   **Then** ответ возвращает HTTP 403 Forbidden

9. **AC9: Admin может управлять rate limit policies**
   **Given** аутентифицированный пользователь с ролью "admin"
   **When** запрос к `/api/v1/rate-limits` endpoints (POST, PUT, DELETE)
   **Then** операция выполняется успешно
   **When** user с ролью "security" или "developer" делает запрос
   **Then** ответ возвращает HTTP 403 Forbidden

10. **AC10: Correlation ID включён в 403 responses**
    **Given** любая ошибка авторизации (403)
    **When** формируется ответ об ошибке
    **Then** X-Correlation-ID header присутствует в ответе
    **And** correlationId включён в тело RFC 7807 ответа

## Tasks / Subtasks

- [x] **Task 1: Создать аннотацию @RequireRole** (AC: #1, #2, #3)
  - [x] Subtask 1.1: Создать `RequireRole.kt` annotation в `gateway-admin/security/`
  - [x] Subtask 1.2: Аннотация принимает vararg ролей (например `@RequireRole(ADMIN)` или `@RequireRole(SECURITY, ADMIN)`)
  - [x] Subtask 1.3: Добавить target METHOD и CLASS для гибкости

- [x] **Task 2: Создать RoleAuthorizationAspect** (AC: #1, #2, #3)
  - [x] Subtask 2.1: Создать `RoleAuthorizationAspect.kt` в `gateway-admin/security/`
  - [x] Subtask 2.2: Реализовать @Around advice для методов с @RequireRole
  - [x] Subtask 2.3: Извлекать текущего пользователя из SecurityContext
  - [x] Subtask 2.4: Проверять role hierarchy (Admin > Security > Developer)
  - [x] Subtask 2.5: Выбрасывать `AccessDeniedException` при недостаточных правах

- [x] **Task 3: Определить Role Hierarchy** (AC: #3)
  - [x] Subtask 3.1: Создать `RoleHierarchy.kt` в `gateway-admin/config/`
  - [x] Subtask 3.2: Определить иерархию: ADMIN включает SECURITY, SECURITY включает DEVELOPER
  - [x] Subtask 3.3: Создать utility метод `hasRequiredRole(userRole, requiredRole): Boolean`

- [x] **Task 4: Создать исключение AccessDeniedException** (AC: #1, #10)
  - [x] Subtask 4.1: Создать `AccessDeniedException.kt` в `gateway-admin/exception/`
  - [x] Subtask 4.2: Включить detail message для RFC 7807 response
  - [x] Subtask 4.3: Обновить GlobalExceptionHandler для обработки AccessDeniedException → 403

- [x] **Task 5: Создать OwnershipChecker сервис** (AC: #4, #5)
  - [x] Subtask 5.1: Создать `OwnershipService.kt` в `gateway-admin/service/`
  - [x] Subtask 5.2: Метод `isOwner(routeId, userId): Mono<Boolean>`
  - [x] Subtask 5.3: Метод `canDeleteRoute(routeId, userId): Mono<DeleteCheckResult>` — проверяет ownership + draft status

- [x] **Task 6: Добавить @RequireRole аннотации к endpoints** (AC: #6, #7, #8, #9)
  - [x] Subtask 6.1: AuthController — login/logout остаются публичными (без изменений)
  - [x] Subtask 6.2: Создать placeholder RouteController с @RequireRole аннотациями
  - [x] Subtask 6.3: Создать placeholder UserController с @RequireRole(ADMIN)
  - [x] Subtask 6.4: Создать placeholder AuditController с @RequireRole(SECURITY)
  - [x] Subtask 6.5: Создать placeholder RateLimitController с @RequireRole(ADMIN) для write operations

- [x] **Task 7: Включить AspectJ в проект** (AC: #1, #2, #3)
  - [x] Subtask 7.1: Добавить `spring-boot-starter-aop` dependency в build.gradle.kts
  - [x] Subtask 7.2: @EnableAspectJAutoProxy включён автоматически Spring Boot AOP starter

- [x] **Task 8: Unit тесты** (AC: #1, #2, #3, #4, #5)
  - [x] Subtask 8.1: Тесты для RoleHierarchy (13 тестов — все сценарии ролей и иерархии)
  - [x] Subtask 8.2: Тесты для OwnershipService (7 тестов — ownership и delete checks)
  - [x] Subtask 8.3: AccessDeniedException handling покрыт integration тестами

- [x] **Task 9: Integration тесты** (AC: #1, #2, #3, #6, #7, #8, #9, #10)
  - [x] Subtask 9.1: Тест developer → ADMIN endpoint → 403
  - [x] Subtask 9.2: Тест admin → ADMIN endpoint → success
  - [x] Subtask 9.3: Тест role hierarchy (admin → SECURITY endpoint → success)
  - [x] Subtask 9.4: Тест developer → security-only endpoint → 403
  - [x] Subtask 9.5: Тест 403 response содержит X-Correlation-ID и RFC 7807 формат
  - [x] Subtask 9.6: Тест approve/reject endpoints с разными ролями
  - [x] Subtask 9.7: Тест audit endpoints с разными ролями
  - [x] Subtask 9.8: Тест users endpoints с разными ролями

### Review Follow-ups (AI)
- [ ] [AI-Review][LOW] Добавить unit тесты для wrapFluxWithRoleCheck() в RoleAuthorizationAspect [RoleAuthorizationAspect.kt:77-92]

## Dev Notes

### Previous Story Intelligence (Story 2.3 - Authentication Middleware)

**Созданная инфраструктура из Story 2.3:**
- `JwtAuthenticationFilter.kt` — WebFilter для JWT валидации из cookie
- `AuthenticatedUser.kt` — Principal с userId, username, role
- `SecurityContextUtils.kt` — утилита `currentUser(): Mono<AuthenticatedUser>`
- `GlobalExceptionHandler.kt` — ErrorWebExceptionHandler для обработки исключений из WebFilter chain
- `JwtAuthenticationException` — sealed class для JWT ошибок

**Ключевые паттерны из Story 2.3:**
- AuthenticatedUser доступен через `ReactiveSecurityContextHolder`
- Role хранится в `AuthenticatedUser.role` как enum `Role.DEVELOPER | SECURITY | ADMIN`
- Все error responses следуют RFC 7807 формату через GlobalExceptionHandler
- X-Correlation-ID включается во все ответы (паттерн уже реализован)

**Файлы для референса:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/AuthenticatedUser.kt
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/SecurityContextUtils.kt
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/GlobalExceptionHandler.kt
backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Role.kt
```

### Previous Story Intelligence (Story 2.2 - JWT Authentication Service)

**Существующие компоненты:**
- `JwtService.kt` — generateToken(), validateToken(), extractUserId()
- `AuthService.kt` — authenticate(username, password)
- `AuthController.kt` — /api/v1/auth/login, /api/v1/auth/logout (публичные endpoints)

### Previous Story Intelligence (Story 2.1 - User Entity)

**Существующие компоненты:**
- `User.kt` entity в `gateway-common/model/`
- `Role.kt` enum: DEVELOPER, SECURITY, ADMIN
- `UserRepository.kt` в `gateway-admin/repository/`

---

### Architecture Compliance

**Из architecture.md:**

| Решение | Требование |
|---------|------------|
| **Authorization** | RBAC с 3 ролями (Developer, Security, Admin) |
| **Error Format** | RFC 7807 Problem Details |
| **Correlation** | X-Correlation-ID header |
| **Reactive Stack** | WebFlux, Mono/Flux, @Around с reactive return types |

**Role Permissions Matrix (из PRD/Epics):**

| Role | Permissions |
|------|-------------|
| DEVELOPER | routes:create, routes:read, routes:update (own), routes:delete (own draft) |
| SECURITY | all DEVELOPER + routes:approve, routes:reject, audit:read |
| ADMIN | all SECURITY + users:manage, ratelimits:manage |

---

### Technical Requirements

**@RequireRole Annotation (gateway-admin/security/):**
```kotlin
package com.company.gateway.admin.security

import com.company.gateway.common.model.Role

// Аннотация для проверки роли на уровне метода или класса
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireRole(vararg val roles: Role)
```

**RoleHierarchy (gateway-admin/config/):**
```kotlin
package com.company.gateway.admin.config

import com.company.gateway.common.model.Role

// Иерархия ролей: Admin > Security > Developer
object RoleHierarchy {

    // Возвращает все роли, которые включены в указанную роль
    fun getIncludedRoles(role: Role): Set<Role> = when (role) {
        Role.ADMIN -> setOf(Role.ADMIN, Role.SECURITY, Role.DEVELOPER)
        Role.SECURITY -> setOf(Role.SECURITY, Role.DEVELOPER)
        Role.DEVELOPER -> setOf(Role.DEVELOPER)
    }

    // Проверяет, имеет ли пользователь достаточные права для требуемой роли
    fun hasRequiredRole(userRole: Role, requiredRole: Role): Boolean {
        return getIncludedRoles(userRole).contains(requiredRole)
    }
}
```

**RoleAuthorizationAspect (gateway-admin/security/):**
```kotlin
package com.company.gateway.admin.security

import com.company.gateway.admin.config.RoleHierarchy
import com.company.gateway.admin.exception.AccessDeniedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Aspect
@Component
class RoleAuthorizationAspect {

    @Around("@annotation(requireRole) || @within(requireRole)")
    fun checkRole(joinPoint: ProceedingJoinPoint, requireRole: RequireRole?): Any? {
        // Получаем аннотацию с метода или класса
        val annotation = requireRole ?: getAnnotationFromMethod(joinPoint)
            ?: return joinPoint.proceed()

        val requiredRoles = annotation.roles.toSet()

        // Для reactive методов оборачиваем в Mono
        val result = joinPoint.proceed()

        return when (result) {
            is Mono<*> -> SecurityContextUtils.currentUser()
                .flatMap { user ->
                    val hasAccess = requiredRoles.any { requiredRole ->
                        RoleHierarchy.hasRequiredRole(user.role, requiredRole)
                    }
                    if (hasAccess) {
                        @Suppress("UNCHECKED_CAST")
                        result as Mono<Any>
                    } else {
                        Mono.error(AccessDeniedException("Insufficient permissions"))
                    }
                }
                .switchIfEmpty(Mono.error(AccessDeniedException("Authentication required")))
            else -> {
                // Для non-reactive методов — синхронная проверка
                // (не рекомендуется в WebFlux, но поддерживаем для совместимости)
                throw UnsupportedOperationException("Non-reactive methods not supported")
            }
        }
    }

    private fun getAnnotationFromMethod(joinPoint: ProceedingJoinPoint): RequireRole? {
        val signature = joinPoint.signature as? MethodSignature ?: return null
        return signature.method.getAnnotation(RequireRole::class.java)
    }
}
```

**AccessDeniedException (gateway-admin/exception/):**
```kotlin
package com.company.gateway.admin.exception

// Исключение для 403 Forbidden ответов
class AccessDeniedException(
    override val message: String,
    val detail: String = message
) : RuntimeException(message)
```

**GlobalExceptionHandler обновление:**
```kotlin
// Добавить в существующий GlobalExceptionHandler
@ExceptionHandler(AccessDeniedException::class)
fun handleAccessDenied(ex: AccessDeniedException, exchange: ServerWebExchange): Mono<Void> {
    val correlationId = exchange.request.headers
        .getFirst(CORRELATION_ID_HEADER)
        ?: UUID.randomUUID().toString()

    val errorResponse = ErrorResponse(
        type = "https://api.gateway/errors/forbidden",
        title = "Forbidden",
        status = HttpStatus.FORBIDDEN.value(),
        detail = ex.detail,
        instance = exchange.request.path.value(),
        correlationId = correlationId
    )

    exchange.response.statusCode = HttpStatus.FORBIDDEN
    exchange.response.headers.contentType = MediaType.APPLICATION_JSON
    exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

    val body = objectMapper.writeValueAsBytes(errorResponse)
    val buffer = exchange.response.bufferFactory().wrap(body)

    return exchange.response.writeWith(Mono.just(buffer))
}
```

---

### Placeholder Controllers Pattern

Поскольку Story 2.4 фокусируется на RBAC, а не на полной реализации CRUD, создаём placeholder controllers:

**RouteController (placeholder):**
```kotlin
@RestController
@RequestMapping("/api/v1/routes")
class RouteController {

    @GetMapping
    @RequireRole(Role.DEVELOPER)  // Все роли могут читать
    fun listRoutes(): Mono<ResponseEntity<Map<String, Any>>> {
        // Placeholder — вернуть пустой список
        return Mono.just(ResponseEntity.ok(mapOf("items" to emptyList<Any>(), "total" to 0)))
    }

    @PostMapping("/{id}/approve")
    @RequireRole(Role.SECURITY)  // Только Security и Admin
    fun approveRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.ok().build())
    }

    @PostMapping("/{id}/reject")
    @RequireRole(Role.SECURITY)  // Только Security и Admin
    fun rejectRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return Mono.just(ResponseEntity.ok().build())
    }
}
```

**UserController (placeholder):**
```kotlin
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(Role.ADMIN)  // Весь контроллер только для Admin
class UserController {

    @GetMapping
    fun listUsers(): Mono<ResponseEntity<Map<String, Any>>> {
        return Mono.just(ResponseEntity.ok(mapOf("items" to emptyList<Any>(), "total" to 0)))
    }

    @PostMapping
    fun createUser(): Mono<ResponseEntity<Void>> {
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).build())
    }
}
```

**AuditController (placeholder):**
```kotlin
@RestController
@RequestMapping("/api/v1/audit")
@RequireRole(Role.SECURITY)  // Security и Admin
class AuditController {

    @GetMapping
    fun listAuditLogs(): Mono<ResponseEntity<Map<String, Any>>> {
        return Mono.just(ResponseEntity.ok(mapOf("items" to emptyList<Any>(), "total" to 0)))
    }
}
```

**RateLimitController (placeholder):**
```kotlin
@RestController
@RequestMapping("/api/v1/rate-limits")
class RateLimitController {

    @GetMapping
    @RequireRole(Role.DEVELOPER)  // Все могут читать
    fun listPolicies(): Mono<ResponseEntity<Map<String, Any>>> {
        return Mono.just(ResponseEntity.ok(mapOf("items" to emptyList<Any>(), "total" to 0)))
    }

    @PostMapping
    @RequireRole(Role.ADMIN)  // Только Admin может создавать
    fun createPolicy(): Mono<ResponseEntity<Void>> {
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).build())
    }

    @PutMapping("/{id}")
    @RequireRole(Role.ADMIN)
    fun updatePolicy(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return Mono.just(ResponseEntity.ok().build())
    }

    @DeleteMapping("/{id}")
    @RequireRole(Role.ADMIN)
    fun deletePolicy(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return Mono.just(ResponseEntity.noContent().build())
    }
}
```

---

### Files to Create

```
backend/gateway-admin/
└── src/main/kotlin/com/company/gateway/admin/
    ├── security/
    │   ├── RequireRole.kt                      # CREATE - Аннотация для проверки ролей
    │   └── RoleAuthorizationAspect.kt          # CREATE - Aspect для проверки @RequireRole
    ├── config/
    │   └── RoleHierarchy.kt                    # CREATE - Определение иерархии ролей
    ├── exception/
    │   └── AccessDeniedException.kt            # CREATE - Исключение для 403
    ├── service/
    │   └── OwnershipService.kt                 # CREATE - Проверка владения ресурсами
    └── controller/
        ├── RouteController.kt                  # CREATE - Placeholder с RBAC
        ├── UserController.kt                   # CREATE - Placeholder с RBAC
        ├── AuditController.kt                  # CREATE - Placeholder с RBAC
        └── RateLimitController.kt              # CREATE - Placeholder с RBAC

backend/gateway-admin/
└── src/test/kotlin/com/company/gateway/admin/
    ├── security/
    │   ├── RoleAuthorizationAspectTest.kt      # CREATE - Unit тесты aspect
    │   └── RoleHierarchyTest.kt                # CREATE - Unit тесты hierarchy
    ├── service/
    │   └── OwnershipServiceTest.kt             # CREATE - Unit тесты ownership
    └── integration/
        └── RbacIntegrationTest.kt              # CREATE - Integration тесты RBAC
```

### Files to Modify

```
backend/gateway-admin/
└── src/main/kotlin/com/company/gateway/admin/
    ├── exception/
    │   └── GlobalExceptionHandler.kt           # MODIFY - Добавить обработку AccessDeniedException
    └── build.gradle.kts                        # MODIFY - Добавить spring-boot-starter-aop
```

---

### Environment Variables

Нет новых переменных окружения для этой истории.

---

### Anti-Patterns to Avoid

- ❌ **НЕ использовать .block()** — только reactive chains
- ❌ **НЕ хранить роли в ThreadLocal** — использовать ReactiveSecurityContextHolder
- ❌ **НЕ пропускать correlation ID** — всегда включать в 403 error responses
- ❌ **НЕ возвращать stack traces** — только RFC 7807 format
- ❌ **НЕ писать комментарии на английском** — только русский (CLAUDE.md)
- ❌ **НЕ писать названия тестов на английском** — только русский (CLAUDE.md)
- ❌ **НЕ дублировать проверки ролей** — использовать единый RoleAuthorizationAspect
- ❌ **НЕ hardcode иерархию в нескольких местах** — использовать RoleHierarchy object

---

### Testing Strategy

**Unit Tests (RoleAuthorizationAspectTest.kt):**
```kotlin
@Test
fun `developer не имеет доступа к ADMIN endpoint`() {
    // Given: метод с @RequireRole(ADMIN)
    // When: вызов от пользователя с ролью DEVELOPER
    // Then: выбрасывается AccessDeniedException
}

@Test
fun `admin имеет доступ к ADMIN endpoint`() {
    // Given: метод с @RequireRole(ADMIN)
    // When: вызов от пользователя с ролью ADMIN
    // Then: метод выполняется успешно
}

@Test
fun `admin имеет доступ к SECURITY endpoint через иерархию`() {
    // Given: метод с @RequireRole(SECURITY)
    // When: вызов от пользователя с ролью ADMIN
    // Then: метод выполняется успешно (Admin > Security)
}

@Test
fun `developer не имеет доступа к SECURITY endpoint`() {
    // Given: метод с @RequireRole(SECURITY)
    // When: вызов от пользователя с ролью DEVELOPER
    // Then: выбрасывается AccessDeniedException
}
```

**Unit Tests (RoleHierarchyTest.kt):**
```kotlin
@Test
fun `admin включает все роли`() {
    val included = RoleHierarchy.getIncludedRoles(Role.ADMIN)
    assertThat(included).containsExactlyInAnyOrder(Role.ADMIN, Role.SECURITY, Role.DEVELOPER)
}

@Test
fun `security включает developer`() {
    val included = RoleHierarchy.getIncludedRoles(Role.SECURITY)
    assertThat(included).containsExactlyInAnyOrder(Role.SECURITY, Role.DEVELOPER)
}

@Test
fun `developer включает только себя`() {
    val included = RoleHierarchy.getIncludedRoles(Role.DEVELOPER)
    assertThat(included).containsExactly(Role.DEVELOPER)
}
```

**Integration Tests (RbacIntegrationTest.kt):**
```kotlin
@Test
fun `developer получает 403 при доступе к users endpoint`() {
    val token = generateValidToken("testuser", Role.DEVELOPER)

    webTestClient.get()
        .uri("/api/v1/users")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isForbidden
        .expectBody()
        .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
        .jsonPath("$.status").isEqualTo(403)
        .jsonPath("$.detail").isEqualTo("Insufficient permissions")
        .jsonPath("$.correlationId").isNotEmpty
}

@Test
fun `admin успешно получает доступ к users endpoint`() {
    val token = generateValidToken("admin", Role.ADMIN)

    webTestClient.get()
        .uri("/api/v1/users")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isOk
}

@Test
fun `security успешно получает доступ к audit endpoint`() {
    val token = generateValidToken("security", Role.SECURITY)

    webTestClient.get()
        .uri("/api/v1/audit")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isOk
}

@Test
fun `developer получает 403 при доступе к audit endpoint`() {
    val token = generateValidToken("developer", Role.DEVELOPER)

    webTestClient.get()
        .uri("/api/v1/audit")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isForbidden
}

@Test
fun `admin успешно вызывает approve через иерархию`() {
    val token = generateValidToken("admin", Role.ADMIN)

    webTestClient.post()
        .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isOk
}

@Test
fun `developer получает 403 при попытке approve`() {
    val token = generateValidToken("developer", Role.DEVELOPER)

    webTestClient.post()
        .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isForbidden
}

@Test
fun `403 response содержит X-Correlation-ID header`() {
    val token = generateValidToken("developer", Role.DEVELOPER)

    webTestClient.get()
        .uri("/api/v1/users")
        .cookie("auth_token", token)
        .exchange()
        .expectStatus().isForbidden
        .expectHeader().exists("X-Correlation-ID")
}
```

---

### Project Structure Notes

**Alignment with Architecture:**
- Security классы в `gateway-admin/security/`
- Config классы в `gateway-admin/config/`
- Exception классы в `gateway-admin/exception/`
- Все error responses в RFC 7807 формате
- X-Correlation-ID во всех ответах

**Existing Files to Reference:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/AuthenticatedUser.kt` — Principal с role
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/SecurityContextUtils.kt` — получение текущего пользователя
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/GlobalExceptionHandler.kt` — паттерн обработки ошибок
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Role.kt` — enum ролей

### Git Intelligence

**Последние коммиты:**
- `ca3aa22` feat: Authentication middleware with JWT filter (Story 2.3)
- `773cd8a` feat: JWT Authentication Service with login/logout (Story 2.2)
- `1d11992` feat: User entity and database schema (Story 2.1)

**Паттерн коммитов:** `feat: <description> (Story X.Y)`

---

### References

- [Source: epics.md#Story 2.4: Role-Based Access Control] — Original AC и role-permission mapping
- [Source: architecture.md#Authentication & Security] — RBAC, 3 roles
- [Source: architecture.md#API & Communication Patterns] — RFC 7807, X-Correlation-ID
- [Source: 2-3-authentication-middleware.md] — AuthenticatedUser, SecurityContextUtils, GlobalExceptionHandler
- [Source: 2-1-user-entity-database-schema.md] — Role enum
- [Source: CLAUDE.md] — Комментарии на русском, названия тестов на русском

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все 213 тестов gateway-admin прошли успешно
- Integration тесты RBAC покрывают все AC (AC1-AC10)
- Обновлены тесты AuthMiddlewareIntegrationTest для совместимости с новым RouteController

### Completion Notes List

- Реализована система RBAC с иерархией ролей Admin > Security > Developer
- @RequireRole аннотация работает на уровне метода и класса
- RoleAuthorizationAspect проверяет роли в reactive chain без блокирующих вызовов
- GlobalExceptionHandler обрабатывает AccessDeniedException → 403 и ConflictException → 409 в RFC 7807 формате
- X-Correlation-ID включается во все error responses (401, 403, 409)
- OwnershipService интегрирован в RouteController для проверки владения маршрутами (AC4, AC5)
- RouteController реализует update/delete с ownership checking для developer
- Placeholder контроллеры созданы для тестирования RBAC (полная реализация в следующих эпиках)
- Code Review Fix: RoleAuthorizationAspect теперь возвращает 401 (не 403) для неаутентифицированных запросов
- Code Review Fix #2: Удалены дублирующие тесты RoleHierarchy из RoleAuthorizationAspectTest.kt
- Code Review Fix #2: Добавлены тесты edge case для OwnershipService (createdBy == null)
- Code Review Fix #2: Исправлена дубликация metadata в sprint-status.yaml

### Change Log

- 2026-02-16: Story 2.4 - Role-Based Access Control implemented
- 2026-02-16: Code Review #2 - Fixed test duplication, added edge case tests, cleaned sprint-status.yaml

### File List

**Созданные файлы:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/RequireRole.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/RoleAuthorizationAspect.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/RoleHierarchy.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/AccessDeniedException.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/ConflictException.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/OwnershipService.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/UserController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuditController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RateLimitController.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/config/RoleHierarchyTest.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/OwnershipServiceTest.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/RoleAuthorizationAspectTest.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RbacIntegrationTest.kt

**Изменённые файлы:**
- backend/gateway-admin/build.gradle.kts (добавлена зависимость spring-boot-starter-aop)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/GlobalExceptionHandler.kt (добавлена обработка AccessDeniedException и ConflictException)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuthMiddlewareIntegrationTest.kt (обновлены тесты для совместимости с RouteController)
- _bmad-output/implementation-artifacts/sprint-status.yaml (обновлён статус 2-4 → review)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/RoleAuthorizationAspectTest.kt (Code Review #2: удалены дублирующие тесты)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/OwnershipServiceTest.kt (Code Review #2: добавлены edge case тесты)
