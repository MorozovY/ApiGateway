# Story 2.2: JWT Authentication Service

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **User**,
I want to login with username and password,
So that I can access the Admin UI securely (FR25).

## Acceptance Criteria

1. **AC1: Успешный логин возвращает JWT в HTTP-only cookie**
   **Given** пользователь существует с username "maria" и корректным паролем
   **When** POST `/api/v1/auth/login` с телом `{"username": "maria", "password": "correct"}`
   **Then** ответ возвращает HTTP 200
   **And** JWT токен устанавливается в HTTP-only cookie `auth_token`
   **And** cookie имеет атрибуты: HttpOnly, Secure (в prod), SameSite=Strict, Path=/
   **And** JWT payload содержит: `sub` (user_id), `username`, `role`, `exp` (24h)
   **And** тело ответа содержит: `{"userId": "...", "username": "maria", "role": "developer"}`

2. **AC2: Неверные учётные данные возвращают 401**
   **Given** неверные учётные данные
   **When** POST `/api/v1/auth/login` с неправильным паролем
   **Then** ответ возвращает HTTP 401 Unauthorized
   **And** ответ соответствует формату RFC 7807 с detail "Invalid credentials"
   **And** cookie не устанавливается

3. **AC3: Logout очищает cookie**
   **Given** залогиненный пользователь
   **When** POST `/api/v1/auth/logout`
   **Then** cookie `auth_token` очищается (MaxAge=0)
   **And** ответ возвращает HTTP 200

4. **AC4: Неактивный пользователь не может залогиниться**
   **Given** пользователь с `is_active = false`
   **When** POST `/api/v1/auth/login` с корректными учётными данными
   **Then** ответ возвращает HTTP 401 Unauthorized
   **And** ответ содержит detail "Account is disabled"

5. **AC5: JWT подписывается секретным ключом**
   **Given** JWT токен создан
   **When** токен проверяется
   **Then** используется HMAC-SHA256 алгоритм
   **And** секретный ключ берётся из переменной окружения `JWT_SECRET`

## Tasks / Subtasks

### ✅ ОБЯЗАТЕЛЬНО: Технический долг из ретроспективы Epic 1

- [x] **Task 0: Перевод комментариев и тестов на русский язык** (BLOCKING) ✅ ЗАВЕРШЕНО
  - [x] Subtask 0.1: Перевести все существующие комментарии в коде на русский язык
  - [x] Subtask 0.2: Перевести все названия тестов (fun \`...\`) на русский язык
  - [x] Subtask 0.3: Убедиться, что новый код Story 2.2 следует правилам CLAUDE.md

### Основные задачи Story 2.2

- [x] **Task 1: Добавить зависимости для JWT** (AC: #5) ✅ ЗАВЕРШЕНО
  - [x] Subtask 1.1: Добавить `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` в build.gradle.kts
  - [x] Subtask 1.2: Добавить конфигурацию `jwt.secret` и `jwt.expiration` в application.yml
  - [x] Subtask 1.3: Добавить `JWT_SECRET` в .env.example

- [x] **Task 2: Создать JwtService** (AC: #1, #5) ✅ ЗАВЕРШЕНО
  - [x] Subtask 2.1: Создать `JwtService.kt` в `gateway-admin/security/`
  - [x] Subtask 2.2: Реализовать `generateToken(user: User): String`
  - [x] Subtask 2.3: Реализовать `validateToken(token: String): Claims?`
  - [x] Subtask 2.4: Реализовать `extractUserId(token: String): UUID?`
  - [x] Subtask 2.5: Использовать HMAC-SHA256 (HS256) алгоритм

- [x] **Task 3: Создать AuthController** (AC: #1, #2, #3, #4) ✅ ЗАВЕРШЕНО
  - [x] Subtask 3.1: Создать `AuthController.kt` в `gateway-admin/controller/`
  - [x] Subtask 3.2: Реализовать `POST /api/v1/auth/login`
  - [x] Subtask 3.3: Реализовать `POST /api/v1/auth/logout`
  - [x] Subtask 3.4: Возвращать JSON с userId, username, role при успешном логине

- [x] **Task 4: Создать AuthService** (AC: #1, #2, #4) ✅ ЗАВЕРШЕНО
  - [x] Subtask 4.1: Создать `AuthService.kt` в `gateway-admin/service/`
  - [x] Subtask 4.2: Реализовать `authenticate(username: String, password: String): Mono<User>`
  - [x] Subtask 4.3: Проверять `isActive` флаг пользователя
  - [x] Subtask 4.4: Использовать PasswordService для верификации пароля

- [x] **Task 5: Создать DTO для аутентификации** (AC: #1, #2) ✅ ЗАВЕРШЕНО
  - [x] Subtask 5.1: Создать `LoginRequest.kt` (username, password)
  - [x] Subtask 5.2: Создать `LoginResponse.kt` (userId, username, role)
  - [x] Subtask 5.3: AuthExceptionHandler использует ErrorResponse (RFC 7807 формат из gateway-common)

- [x] **Task 6: Настроить HTTP-only cookie** (AC: #1, #3) ✅ ЗАВЕРШЕНО
  - [x] Subtask 6.1: Создать `CookieService.kt` в `gateway-admin/security/`
  - [x] Subtask 6.2: Реализовать `createAuthCookie(token: String): ResponseCookie`
  - [x] Subtask 6.3: Реализовать `createLogoutCookie(): ResponseCookie`
  - [x] Subtask 6.4: Настроить HttpOnly, Secure (для prod), SameSite=Strict, Path=/

- [x] **Task 7: Обновить SecurityConfig** (AC: #1, #2, #3) ✅ ЗАВЕРШЕНО
  - [x] Subtask 7.1: Разрешить `/api/v1/auth/**` без аутентификации
  - [x] Subtask 7.2: Сохранить доступ к actuator endpoints без аутентификации

- [x] **Task 8: Unit тесты** (AC: #1, #2, #5) ✅ ЗАВЕРШЕНО (38 тестов)
  - [x] Subtask 8.1: Тесты для JwtService (генерация, валидация, истечение) - 17 тестов
  - [x] Subtask 8.2: Тесты для AuthService (успешная аутентификация, неверный пароль, неактивный пользователь) - 10 тестов
  - [x] Subtask 8.3: Тесты для CookieService (атрибуты cookie) - 11 тестов

- [x] **Task 9: Integration тесты** (AC: #1, #2, #3, #4) ✅ ЗАВЕРШЕНО (код написан, требует Docker)
  - [x] Subtask 9.1: Тест POST /api/v1/auth/login с корректными данными
  - [x] Subtask 9.2: Тест POST /api/v1/auth/login с неверным паролем
  - [x] Subtask 9.3: Тест POST /api/v1/auth/login с неактивным пользователем
  - [x] Subtask 9.4: Тест POST /api/v1/auth/logout
  - [x] Subtask 9.5: Проверка cookie атрибутов в response

## Dev Notes

### ⚠️ BLOCKING: Технический долг из ретроспективы Epic 1

**Из ретроспективы Epic 1 (Action Items #2, #4):**

Перед началом работы над основным функционалом Story 2.2, ОБЯЗАТЕЛЬНО выполнить перевод:

1. **Комментарии на английском → русский**
   - Все inline комментарии в существующем коде
   - XML/KDoc комментарии если есть

2. **Названия тестов на английском → русский**
   - Формат: `fun \`описание на русском\`()`
   - Пример: `fun \`возвращает 401 при неверном пароле\`()`

**Файлы для проверки и исправления:**
```
backend/gateway-core/src/test/kotlin/**/*Test.kt
backend/gateway-admin/src/test/kotlin/**/*Test.kt
backend/gateway-common/src/test/kotlin/**/*Test.kt
backend/gateway-core/src/main/kotlin/**/*.kt (комментарии)
backend/gateway-admin/src/main/kotlin/**/*.kt (комментарии)
```

**Правила из CLAUDE.md:**
- Комментарии в коде — только на русском языке
- Названия тестов — только на русском языке
- Идентификаторы (переменные, функции) — на английском

---

### Previous Story Intelligence (Story 2.1)

**Созданная инфраструктура:**
- `User.kt` entity в `gateway-common/model/` с Role enum
- `UserRepository.kt` в `gateway-admin/repository/` с findByUsername()
- `PasswordService.kt` в `gateway-admin/service/` с verify() методом
- BCrypt для хеширования паролей

**Существующие паттерны:**
- `@EventListener(ApplicationReadyEvent::class)` для инициализации (НЕ @PostConstruct!)
- Тесты с Testcontainers (PostgreSQL, Redis)
- RFC 7807 формат ошибок
- snake_case для колонок БД, camelCase для Kotlin/JSON

**Файлы для референса:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/PasswordService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/UserRepository.kt`
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/User.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/exception/GlobalExceptionHandler.kt`

---

### Architecture Compliance

**Из architecture.md:**

| Решение | Требование |
|---------|------------|
| **JWT Algorithm** | HMAC-SHA256 (HS256) |
| **Token Storage** | HTTP-only cookies (XSS protection) |
| **Session Type** | Stateless (horizontal scaling) |
| **Password Hashing** | BCrypt (Spring Security default) |
| **Error Format** | RFC 7807 Problem Details |

**JWT Configuration:**
```yaml
jwt:
  secret: ${JWT_SECRET:dev-secret-key-min-32-characters}
  expiration: 86400000  # 24 часа в миллисекундах
```

**Cookie Configuration:**
```kotlin
ResponseCookie.from("auth_token", token)
    .httpOnly(true)
    .secure(isProd)  // true только для production
    .sameSite("Strict")
    .path("/")
    .maxAge(Duration.ofHours(24))
    .build()
```

---

### Technical Requirements

**JwtService (gateway-admin/security/):**
```kotlin
package com.company.gateway.admin.security

import com.company.gateway.common.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}")
    private val secret: String,
    @Value("\${jwt.expiration:86400000}")
    private val expiration: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    // Генерирует JWT токен для пользователя
    fun generateToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("role", user.role.name.lowercase())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    // Валидирует токен и возвращает claims или null
    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            null
        }
    }

    // Извлекает user ID из токена
    fun extractUserId(token: String): UUID? {
        return validateToken(token)?.subject?.let { UUID.fromString(it) }
    }
}
```

**AuthController (gateway-admin/controller/):**
```kotlin
package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.dto.LoginResponse
import com.company.gateway.admin.security.CookieService
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val cookieService: CookieService
) {
    // Аутентификация пользователя
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<LoginResponse>> {
        return authService.authenticate(request.username, request.password)
            .map { user ->
                val token = jwtService.generateToken(user)
                val cookie = cookieService.createAuthCookie(token)

                ResponseEntity.ok()
                    .header("Set-Cookie", cookie.toString())
                    .body(LoginResponse(
                        userId = user.id!!.toString(),
                        username = user.username,
                        role = user.role.name.lowercase()
                    ))
            }
    }

    // Выход из системы
    @PostMapping("/logout")
    fun logout(): Mono<ResponseEntity<Void>> {
        val cookie = cookieService.createLogoutCookie()
        return Mono.just(
            ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .build()
        )
    }
}
```

**AuthService (gateway-admin/service/):**
```kotlin
package com.company.gateway.admin.service

import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.User
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) {
    // Аутентифицирует пользователя по username и password
    fun authenticate(username: String, password: String): Mono<User> {
        return userRepository.findByUsername(username)
            .filter { user -> user.isActive }
            .switchIfEmpty(Mono.error(AuthenticationException("Account is disabled")))
            .filter { user -> passwordService.verify(password, user.passwordHash) }
            .switchIfEmpty(Mono.error(AuthenticationException("Invalid credentials")))
    }
}

class AuthenticationException(message: String) : RuntimeException(message)
```

**CookieService (gateway-admin/security/):**
```kotlin
package com.company.gateway.admin.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class CookieService(
    @Value("\${spring.profiles.active:dev}")
    private val activeProfile: String
) {
    private val cookieName = "auth_token"
    private val isProduction: Boolean
        get() = activeProfile == "prod"

    // Создаёт cookie с JWT токеном
    fun createAuthCookie(token: String): ResponseCookie {
        return ResponseCookie.from(cookieName, token)
            .httpOnly(true)
            .secure(isProduction)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofHours(24))
            .build()
    }

    // Создаёт cookie для logout (очистка)
    fun createLogoutCookie(): ResponseCookie {
        return ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(isProduction)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build()
    }
}
```

**DTO:**
```kotlin
// LoginRequest.kt
package com.company.gateway.admin.dto

data class LoginRequest(
    val username: String,
    val password: String
)

// LoginResponse.kt
package com.company.gateway.admin.dto

data class LoginResponse(
    val userId: String,
    val username: String,
    val role: String
)
```

---

### Dependencies to Add

**build.gradle.kts (gateway-admin):**
```kotlin
dependencies {
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
}
```

---

### Files to Create

```
backend/gateway-admin/
├── src/main/kotlin/com/company/gateway/admin/
│   ├── controller/
│   │   └── AuthController.kt              # CREATE
│   ├── service/
│   │   └── AuthService.kt                 # CREATE
│   ├── security/
│   │   ├── JwtService.kt                  # CREATE
│   │   └── CookieService.kt               # CREATE
│   └── dto/
│       ├── LoginRequest.kt                # CREATE
│       └── LoginResponse.kt               # CREATE
└── src/test/kotlin/com/company/gateway/admin/
    ├── security/
    │   ├── JwtServiceTest.kt              # CREATE
    │   └── CookieServiceTest.kt           # CREATE
    ├── service/
    │   └── AuthServiceTest.kt             # CREATE
    └── integration/
        └── AuthControllerIntegrationTest.kt  # CREATE
```

### Files to Modify

```
backend/gateway-admin/
├── build.gradle.kts                       # MODIFY - add JWT dependencies
├── src/main/resources/
│   └── application.yml                    # MODIFY - add jwt.secret, jwt.expiration
└── src/main/kotlin/com/company/gateway/admin/
    └── config/
        └── SecurityConfig.kt              # MODIFY - permit /api/v1/auth/**

.env.example                               # MODIFY - add JWT_SECRET
```

### Files to Fix (Tech Debt)

```
# Перевести комментарии и названия тестов на русский:
backend/gateway-core/src/test/kotlin/**/*Test.kt
backend/gateway-admin/src/test/kotlin/**/*Test.kt
backend/gateway-common/src/test/kotlin/**/*Test.kt
backend/gateway-core/src/main/kotlin/**/*.kt
backend/gateway-admin/src/main/kotlin/**/*.kt
```

---

### Environment Variables

```bash
# .env.example - добавить:
JWT_SECRET=your-secret-key-minimum-32-characters-long

# application.yml:
jwt:
  secret: ${JWT_SECRET:dev-secret-key-minimum-32-chars}
  expiration: 86400000  # 24 часа
```

---

### Anti-Patterns to Avoid

- ❌ **НЕ хранить JWT в localStorage** — только HTTP-only cookies
- ❌ **НЕ использовать слабый секрет** — минимум 32 символа для HS256
- ❌ **НЕ использовать @PostConstruct** в reactive контексте
- ❌ **НЕ использовать .block()** — reactive chains only
- ❌ **НЕ логировать пароли** — только username в логах
- ❌ **НЕ возвращать passwordHash** в response
- ❌ **НЕ писать комментарии на английском** — только русский (CLAUDE.md)
- ❌ **НЕ писать названия тестов на английском** — только русский (CLAUDE.md)

---

### Testing Strategy

**Unit Tests (JwtServiceTest.kt):**
```kotlin
@Test
fun `генерирует валидный JWT токен`() {
    val user = User(
        id = UUID.randomUUID(),
        username = "testuser",
        email = "test@example.com",
        passwordHash = "hash",
        role = Role.DEVELOPER
    )

    val token = jwtService.generateToken(user)

    assertThat(token).isNotBlank()
    assertThat(token.split(".")).hasSize(3)  // header.payload.signature
}

@Test
fun `извлекает userId из валидного токена`() {
    val userId = UUID.randomUUID()
    val user = User(id = userId, username = "test", email = "test@test.com", passwordHash = "h", role = Role.DEVELOPER)

    val token = jwtService.generateToken(user)
    val extractedId = jwtService.extractUserId(token)

    assertThat(extractedId).isEqualTo(userId)
}

@Test
fun `возвращает null для невалидного токена`() {
    val claims = jwtService.validateToken("invalid.token.here")

    assertThat(claims).isNull()
}
```

**Integration Tests (AuthControllerIntegrationTest.kt):**
```kotlin
@Test
fun `возвращает 200 и cookie при успешном логине`() {
    // Given: пользователь существует в БД
    createTestUser("maria", "password123", Role.DEVELOPER)

    // When
    webTestClient.post()
        .uri("/api/v1/auth/login")
        .bodyValue(LoginRequest("maria", "password123"))
        .exchange()
        // Then
        .expectStatus().isOk
        .expectHeader().exists("Set-Cookie")
        .expectBody()
        .jsonPath("$.username").isEqualTo("maria")
        .jsonPath("$.role").isEqualTo("developer")
}

@Test
fun `возвращает 401 при неверном пароле`() {
    createTestUser("maria", "password123", Role.DEVELOPER)

    webTestClient.post()
        .uri("/api/v1/auth/login")
        .bodyValue(LoginRequest("maria", "wrongpassword"))
        .exchange()
        .expectStatus().isUnauthorized
        .expectBody()
        .jsonPath("$.detail").isEqualTo("Invalid credentials")
}

@Test
fun `возвращает 401 для неактивного пользователя`() {
    createTestUser("inactive", "password123", Role.DEVELOPER, isActive = false)

    webTestClient.post()
        .uri("/api/v1/auth/login")
        .bodyValue(LoginRequest("inactive", "password123"))
        .exchange()
        .expectStatus().isUnauthorized
        .expectBody()
        .jsonPath("$.detail").isEqualTo("Account is disabled")
}
```

---

### Project Structure Notes

**Alignment with Architecture:**
- Security классы в `gateway-admin/security/` (JwtService, CookieService)
- DTOs в `gateway-admin/dto/`
- Controllers в `gateway-admin/controller/`
- Services в `gateway-admin/service/`
- Package naming: `com.company.gateway.admin.security`, `com.company.gateway.admin.dto`

---

### References

- [Source: epics.md#Story 2.2: JWT Authentication Service] - Original AC
- [Source: architecture.md#Authentication & Security] - JWT self-issued, HTTP-only cookies
- [Source: architecture.md#API & Communication Patterns] - RFC 7807 error format
- [Source: 2-1-user-entity-database-schema.md] - User entity, PasswordService, UserRepository
- [Source: CLAUDE.md] - Комментарии на русском, названия тестов на русском
- [Source: retrospective-epic-1.md#Action Items] - Обязательный перевод комментариев и тестов

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

1. **Task 0 (Tech Debt)**: Переведено ~100 названий тестов и ~50 комментариев с английского на русский язык
2. **Task 1**: Добавлены JWT зависимости (jjwt-api, jjwt-impl, jjwt-jackson v0.12.5)
3. **Task 2**: JwtService реализован с HMAC-SHA256 (HS256) алгоритмом
4. **Task 3**: AuthController с endpoints /login и /logout
5. **Task 4**: AuthService с проверкой isActive и верификацией пароля
6. **Task 5**: DTO (LoginRequest, LoginResponse), RFC 7807 через AuthExceptionHandler
7. **Task 6**: CookieService с HttpOnly, Secure (prod), SameSite=Strict, Path=/
8. **Task 7**: SecurityConfig обновлён для /api/v1/auth/** без аутентификации
9. **Task 8**: 38 unit тестов (все проходят)
10. **Task 9**: 15 integration тестов написаны (требуют Docker для запуска)

### Senior Developer Review (AI)

**Review Date:** 2026-02-16
**Reviewer:** Claude Opus 4.5 (Adversarial Code Review)
**Outcome:** ✅ APPROVED (after fixes)

**Issues Found:** 2 HIGH, 4 MEDIUM, 4 LOW

**Fixes Applied:**

1. ✅ **HIGH: Добавлена валидация входных данных в LoginRequest**
   - Добавлены аннотации `@NotBlank` и `@Size` для username/password
   - Добавлена зависимость `spring-boot-starter-validation`
   - AuthController теперь использует `@Valid`
   - AuthExceptionHandler обрабатывает `WebExchangeBindException`

2. ✅ **HIGH: Удалён Thread.sleep() из JwtServiceTest**
   - Заменён на тест `токены для одного пользователя содержат одинаковые данные`
   - Добавлен тест `validateToken возвращает null для истёкшего токена`

3. ✅ **MEDIUM: Добавлен тест на истёкший JWT токен**
   - Тест создаёт JwtService с 1 мс expiration для проверки expired token

4. ✅ **MEDIUM: AuthExceptionHandler включает Correlation-ID**
   - Добавлен `extractOrGenerateCorrelationId()` для всех error responses
   - X-Correlation-ID header добавляется в ответы

5. ✅ **MEDIUM: CookieService корректно обрабатывает несколько профилей**
   - Исправлена проверка `isProduction` для поддержки "prod,kubernetes"
   - Добавлены тесты для нескольких профилей

6. ✅ **MEDIUM: Добавлены тесты на граничные случаи**
   - Тест на пустой username/password
   - Тест на отсутствующие поля
   - Тест на слишком длинный username

**Low severity issues (not fixed, documented for future):**
- Отсутствует логирование попыток входа
- AuthController использует !! оператор на user.id
- JwtService.toByteArray() без указания charset
- Integration tests используют .block() в setup

### File List

**Созданные файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/JwtService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/CookieService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/AuthService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuthController.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/LoginRequest.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/LoginResponse.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/AuthExceptionHandler.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/JwtServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/CookieServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/AuthServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuthControllerIntegrationTest.kt`

**Изменённые файлы:**
- `backend/gateway-admin/build.gradle.kts` - добавлены JWT, mockito-kotlin и validation зависимости
- `backend/gateway-admin/src/main/resources/application.yml` - добавлена JWT конфигурация
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/SecurityConfig.kt` - разрешены auth endpoints
- `.env.example` - добавлен JWT_SECRET
- Множество тестовых файлов с переведёнными названиями и комментариями (Task 0)

**Файлы изменённые при Code Review (2026-02-16):**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/LoginRequest.kt` - добавлена валидация @NotBlank, @Size
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuthController.kt` - добавлен @Valid
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/AuthExceptionHandler.kt` - добавлен Correlation-ID и обработка ValidationException
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/CookieService.kt` - исправлена обработка нескольких профилей
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/JwtServiceTest.kt` - удалён Thread.sleep(), добавлен тест на expired token
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/CookieServiceTest.kt` - добавлены тесты на несколько профилей
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuthControllerIntegrationTest.kt` - добавлены тесты валидации
