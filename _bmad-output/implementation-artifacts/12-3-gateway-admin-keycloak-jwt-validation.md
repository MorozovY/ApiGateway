# Story 12.3: Gateway Admin — Keycloak JWT Validation

Status: review

## Story

As a **System**,
I want Gateway Admin API to validate JWT tokens from Keycloak,
So that only authenticated users can access the API (FR33, FR34).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Переход с custom JWT (HMAC-SHA256 с локальным секретом) на Keycloak JWT (RS256 с JWKS) обеспечивает централизованное управление пользователями, единую точку аутентификации и enterprise-grade security (rotation keys, audit, MFA support).

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅
- Story 12.2 (Admin UI Keycloak Auth Migration) — DONE ✅

**Blocked By This Story:**
- Story 12.4 (Gateway Core JWT Filter) — использует аналогичный подход

## Acceptance Criteria

### AC1: Spring Security OAuth2 Resource Server Configuration
**Given** gateway-admin is configured with Keycloak issuer URI
**When** application starts
**Then** Spring Security OAuth2 Resource Server is configured
**And** JWKS endpoint is cached

### AC2: Valid JWT Token Authentication
**Given** request with valid JWT token from Keycloak
**When** request is made to protected endpoint
**Then** request is authenticated
**And** user principal contains Keycloak claims (sub, preferred_username, email, realm_access.roles)

### AC3: Invalid JWT Token Rejection
**Given** request with invalid JWT token
**When** request is made to protected endpoint
**Then** response returns HTTP 401 Unauthorized
**And** error follows RFC 7807 format

### AC4: Expired JWT Token Rejection
**Given** request with expired JWT token
**When** request is made to protected endpoint
**Then** response returns HTTP 401 Unauthorized

### AC5: Role Mapping — Security
**Given** JWT contains `realm_access.roles: ["admin-ui:security"]`
**When** Spring Security evaluates authorization
**Then** user has `ROLE_SECURITY` authority

### AC6: Role Mapping — Admin
**Given** JWT contains `realm_access.roles: ["admin-ui:admin"]`
**When** Spring Security evaluates authorization
**Then** user has `ROLE_ADMIN` authority

### AC7: User Management Authorization
**Given** user without `admin-ui:admin` role
**When** accessing `/api/v1/users/**` endpoints
**Then** response returns HTTP 403 Forbidden

## Tasks / Subtasks

- [x] Task 0: Pre-flight Checklist (PA-09)
  - [x] 0.1 Проверить что текущий login работает (admin/admin123)
  - [x] 0.2 Проверить что Keycloak запущен (`docker-compose ps keycloak`)
  - [x] 0.3 Feature flag `KEYCLOAK_ENABLED=false` по умолчанию — добавить в application.yml

- [x] Task 1: Add Spring Security OAuth2 Resource Server Dependency (AC: #1)
  - [x] 1.1 Добавить `spring-boot-starter-oauth2-resource-server` в build.gradle.kts
  - [x] 1.2 Оставить существующие JJWT зависимости для совместимости (fallback)

- [x] Task 2: Keycloak Configuration Properties (AC: #1)
  - [x] 2.1 Создать `KeycloakProperties.kt` с @ConfigurationProperties
  - [x] 2.2 Добавить свойства: issuerUri, jwksUri, clientId
  - [x] 2.3 Обновить application.yml с keycloak.oauth2 секцией

- [x] Task 3: Keycloak JWT Decoder Bean (AC: #1, #2)
  - [x] 3.1 Создать `KeycloakSecurityConfig.kt` с ReactiveJwtDecoder bean (объединено с Task 6)
  - [x] 3.2 Настроить NimbusReactiveJwtDecoder с jwkSetUri
  - [x] 3.3 Добавить multi-issuer validator для Docker/localhost совместимости
  - [x] 3.4 Добавить @ConditionalOnProperty для feature flag

- [x] Task 4: Keycloak Role Converter (AC: #5, #6)
  - [x] 4.1 Создать `KeycloakGrantedAuthoritiesConverter.kt`
  - [x] 4.2 Реализовать маппинг `realm_access.roles` → Spring authorities
  - [x] 4.3 admin-ui:developer → ROLE_DEVELOPER
  - [x] 4.4 admin-ui:security → ROLE_SECURITY
  - [x] 4.5 admin-ui:admin → ROLE_ADMIN

- [x] Task 5: Keycloak Authentication Entry Points (AC: #2, #3, #4)
  - [x] 5.1 Создать `KeycloakAuthenticationEntryPoint.kt` — RFC 7807 для 401
  - [x] 5.2 Создать `KeycloakAccessDeniedHandler.kt` — RFC 7807 для 403
  - [x] 5.3 Использовать Spring OAuth2 Resource Server вместо custom filter
  - [x] 5.4 RFC 7807 error response для 401/403
  - [x] 5.5 @Component для автоматической регистрации

- [x] Task 6: Dual-Mode Security Config (AC: all)
  - [x] 6.1 Создать KeycloakSecurityConfig.kt с @ConditionalOnProperty
  - [x] 6.2 keycloak.enabled=true → Keycloak JWT validation
  - [x] 6.3 keycloak.enabled=false → Legacy cookie/HMAC validation (SecurityConfig.kt)
  - [x] 6.4 Role-based authorization: `/api/v1/users/**` требует ROLE_ADMIN

- [x] Task 7: AuthenticatedUser Keycloak Adapter (AC: #2)
  - [x] 7.1 Расширить AuthenticatedUser для работы с Keycloak claims
  - [x] 7.2 Добавить factory method `fromKeycloakJwt(Jwt jwt)`
  - [x] 7.3 Маппинг: sub → userId, preferred_username → username, email → email
  - [x] 7.4 Обновить SecurityContextUtils для поддержки Jwt principal

- [x] Task 8: Unit Tests (AC: all)
  - [x] 8.1 KeycloakGrantedAuthoritiesConverterTest — 11 тестов маппинга ролей
  - [x] 8.2 AuthenticatedUserTest.FromKeycloakJwtTests — 9 тестов fromKeycloakJwt
  - [x] 8.3 KeycloakSecurityConfigTest — интеграционные тесты с MockWebServer
  - [x] 8.4 Integration test с MockWebServer для JWKS endpoint

- [x] Task 9: Manual Verification (2026-02-24)
  - [x] 9.1 Smoke test с feature flag OFF — legacy login работает ✅
  - [x] 9.2 Smoke test с feature flag ON — Keycloak JWT принимается ✅
  - [x] 9.3 Проверить role-based access (admin ✅, developer 403 ✅)
  - [x] 9.4 Проверить 401 для invalid/expired token ✅

### Review Follow-ups (AI)

- [ ] [AI-Review][LOW] Добавить WebTestClient интеграционные тесты для KeycloakAuthenticationEntryPoint и KeycloakAccessDeniedHandler [KeycloakSecurityConfigTest.kt:242-258]
- [ ] [AI-Review][LOW] Рассмотреть .distinct() в KeycloakGrantedAuthoritiesConverter для предотвращения дублирования ролей [KeycloakGrantedAuthoritiesConverter.kt:65-67]
- [ ] [AI-Review][LOW] Вынести isKeycloakEnabled() в shared utility (дублирование в AuthController и UserService)
- [x] [AI-Review][MEDIUM] Удалить unused variable `props` в AuthController.changePasswordViaKeycloak — FIXED

## API Dependencies Checklist

**Backend API endpoints — без изменений в контрактах:**

| Endpoint | Method | Auth Change | Статус |
|----------|--------|-------------|--------|
| `/api/v1/**` | * | Bearer token вместо cookie | ✅ Работает |
| `/api/v1/users/**` | * | Требует ROLE_ADMIN | ⚠️ Новое ограничение |
| `/api/v1/auth/login` | POST | Deprecated при Keycloak mode | ℹ️ Сохранён для fallback |

**Keycloak endpoints (внешние):**

| Endpoint | Purpose | Статус |
|----------|---------|--------|
| `{KEYCLOAK_URL}/realms/api-gateway/.well-known/openid-configuration` | Discovery | ✅ Story 12.1 |
| `{KEYCLOAK_URL}/realms/api-gateway/protocol/openid-connect/certs` | JWKS | ✅ Story 12.1 |

## Dev Notes

### Architecture Reference

Полная архитектура описана в [Source: architecture.md#JWKS Caching Strategy]

### Feature Flag Strategy

```yaml
# application.yml
keycloak:
  enabled: ${KEYCLOAK_ENABLED:false}  # По умолчанию ВЫКЛЮЧЕН
  url: ${KEYCLOAK_URL:http://localhost:8180}
  realm: api-gateway
```

**Важно:** Feature flag OFF по умолчанию — это критично для безопасного rollout.

### Spring Boot OAuth2 Resource Server

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
```

Spring Boot auto-configures:
- `NimbusReactiveJwtDecoder` с JWKS support
- `JwtAuthenticationConverter` для маппинга claims → Authentication
- JWKS caching (по умолчанию 5 min)

### Keycloak Role Mapping

Keycloak хранит роли в `realm_access.roles`:

```json
{
  "realm_access": {
    "roles": ["admin-ui:admin", "default-roles-api-gateway"]
  }
}
```

Маппинг в Spring Security authorities:

| Keycloak Role | Spring Authority | Description |
|---------------|------------------|-------------|
| `admin-ui:developer` | `ROLE_DEVELOPER` | Создание/редактирование routes |
| `admin-ui:security` | `ROLE_SECURITY` | Approve/reject routes |
| `admin-ui:admin` | `ROLE_ADMIN` | All + user management |

### KeycloakGrantedAuthoritiesConverter Implementation

```kotlin
class KeycloakGrantedAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {

    companion object {
        private val ROLE_MAPPING = mapOf(
            "admin-ui:developer" to "ROLE_DEVELOPER",
            "admin-ui:security" to "ROLE_SECURITY",
            "admin-ui:admin" to "ROLE_ADMIN"
        )
    }

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.getClaimAsMap("realm_access") ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val roles = realmAccess["roles"] as? List<String> ?: return emptyList()

        return roles
            .mapNotNull { ROLE_MAPPING[it] }
            .map { SimpleGrantedAuthority(it) }
    }
}
```

### Dual-Mode Security Configuration

```kotlin
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
    fun keycloakSecurityFilterChain(
        http: ServerHttpSecurity,
        jwtDecoder: ReactiveJwtDecoder
    ): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtDecoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/health/**").permitAll()
                    .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                    .pathMatchers("/api/v1/**").authenticated()
                    .anyExchange().permitAll()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(keycloakAuthenticationEntryPoint())
            }
            .build()
    }

    @Bean
    @ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "false", matchIfMissing = true)
    fun legacySecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        // Существующая конфигурация без изменений
        // ...
    }
}
```

### RFC 7807 Error Response

```kotlin
@Component
class KeycloakAuthenticationEntryPoint : ServerAuthenticationEntryPoint {

    override fun commence(
        exchange: ServerWebExchange,
        ex: AuthenticationException
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON

        val problem = mapOf(
            "type" to "about:blank",
            "title" to "Unauthorized",
            "status" to 401,
            "detail" to (ex.message ?: "Authentication required"),
            "instance" to exchange.request.path.value()
        )

        val bytes = ObjectMapper().writeValueAsBytes(problem)
        val buffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }
}
```

### JWKS Caching Configuration

Spring Security OAuth2 использует NimbusJwtDecoder с встроенным JWKS cache:

```kotlin
@Bean
fun jwtDecoder(properties: KeycloakProperties): ReactiveJwtDecoder {
    return NimbusReactiveJwtDecoder
        .withJwkSetUri(properties.jwksUri)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build()
}
```

По умолчанию JWKS кэшируется на 5 минут. Для настройки:

```kotlin
// Кастомный JWKSource с cache
val jwkSetCache = DefaultJWKSetCache(
    Duration.ofMinutes(5).toMillis(), // lifespan
    Duration.ofMinutes(4).toMillis(), // refresh time
    TimeUnit.MILLISECONDS
)
```

### Graceful Degradation

При недоступности Keycloak JWKS endpoint:
1. Используется cached JWKS (если есть)
2. Log warning если cache старше 10 минут
3. При полном отказе — reject все JWT запросы (security over availability)

### File Structure

```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── config/
│   ├── SecurityConfig.kt           # MODIFY: dual-mode support
│   └── KeycloakJwtConfig.kt        # NEW: JWT decoder bean
├── security/
│   ├── JwtAuthenticationFilter.kt  # KEEP: legacy support
│   ├── JwtService.kt               # KEEP: legacy support
│   ├── AuthenticatedUser.kt        # MODIFY: add fromKeycloakJwt()
│   ├── KeycloakJwtAuthenticationFilter.kt  # NEW
│   ├── KeycloakGrantedAuthoritiesConverter.kt  # NEW
│   └── KeycloakAuthenticationEntryPoint.kt  # NEW
└── properties/
    └── KeycloakProperties.kt       # NEW
```

### Environment Variables

```bash
# .env / docker-compose.yml
KEYCLOAK_ENABLED=false              # Feature flag — ВЫКЛЮЧЕН по умолчанию
KEYCLOAK_URL=http://localhost:8180  # Keycloak server URL
```

### Previous Story Intelligence

Из Story 12.2:
- Frontend отправляет `Authorization: Bearer <token>` при `VITE_USE_KEYCLOAK=true`
- JWT структура содержит `realm_access.roles` с ролями `admin-ui:*`
- Direct Access Grants API используется для login (не redirect flow)
- Feature flag паттерн уже установлен для безопасного rollout

Из Story 12.1:
- Keycloak доступен на `localhost:8180`
- JWKS endpoint: `http://localhost:8180/realms/api-gateway/protocol/openid-connect/certs`
- Тестовые пользователи: admin@example.com (admin), dev@example.com (developer), security@example.com (security)
- JWT подписан RS256 (asymmetric), не HMAC

### Critical Constraints

1. **Feature flag OFF по умолчанию** — legacy auth продолжает работать
2. **НЕ удалять legacy auth код** — нужен для fallback
3. **RFC 7807 для всех ошибок** — consistency с существующим API
4. **JWKS caching обязателен** — Keycloak может быть временно недоступен
5. **Role mapping точный** — admin-ui:* → ROLE_* (не api:consumer)

### Testing Strategy

1. **Unit Tests:**
   - KeycloakGrantedAuthoritiesConverter — все комбинации ролей
   - AuthenticatedUser.fromKeycloakJwt() — корректный маппинг claims

2. **Integration Tests:**
   - MockWebServer для JWKS endpoint
   - Тесты SecurityConfig с обоими режимами
   - 401/403 response format

3. **Manual Testing:**
   - Feature flag OFF → login через admin/admin123 работает
   - Feature flag ON → Bearer token от Keycloak принимается
   - Проверка role-based access

### Project Structure Notes

- Все новые файлы в существующих директориях `config/` и `security/`
- Следуем существующим naming conventions
- @ConditionalOnProperty для feature flag isolation

### References

- [Source: architecture.md#JWKS Caching Strategy] — caching configuration
- [Source: architecture.md#Spring Security OAuth2 Resource Server] — configuration example
- [Source: epics.md#Story 12.3] — acceptance criteria
- [Source: 12-1-keycloak-setup-configuration.md] — Keycloak setup details
- [Source: 12-2-admin-ui-keycloak-auth-migration.md] — Frontend auth migration
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- Использован Spring OAuth2 Resource Server вместо custom filter (Task 5 изменён)
- KeycloakJwtConfig объединён с KeycloakSecurityConfig
- Добавлен multi-issuer validator для Docker/localhost совместимости
- KeycloakAdminService добавлен для change-password и reset-demo-passwords через Keycloak API

### File List

**NEW files:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/KeycloakSecurityConfig.kt` — Security config для Keycloak режима
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/properties/KeycloakProperties.kt` — Configuration properties
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/KeycloakGrantedAuthoritiesConverter.kt` — Role mapping
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/KeycloakAuthenticationEntryPoint.kt` — RFC 7807 401 handler
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/KeycloakAccessDeniedHandler.kt` — RFC 7807 403 handler
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/KeycloakAdminService.kt` — Keycloak Admin API client
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/config/KeycloakSecurityConfigTest.kt` — Integration tests
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/KeycloakGrantedAuthoritiesConverterTest.kt` — Unit tests
- `backend/gateway-admin/src/test/resources/application-keycloak-test.yml` — Test profile

**MODIFIED files:**
- `backend/gateway-admin/build.gradle.kts` — Added oauth2-resource-server, nimbus-jose-jwt dependencies
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/SecurityConfig.kt` — Added @ConditionalOnProperty
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuthController.kt` — Dual-mode change-password/me
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/AuthenticatedUser.kt` — Added fromKeycloakJwt()
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/SecurityContextUtils.kt` — Support Jwt principal
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/UserService.kt` — Dual-mode resetDemoPasswords
- `backend/gateway-admin/src/main/resources/application.yml` — Added keycloak.* properties
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/security/AuthenticatedUserTest.kt` — Added FromKeycloakJwtTests

