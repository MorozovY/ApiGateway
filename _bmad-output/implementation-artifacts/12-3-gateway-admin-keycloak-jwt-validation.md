# Story 12.3: Gateway Admin — Keycloak JWT Validation

Status: ready-for-dev

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

- [ ] Task 0: Pre-flight Checklist (PA-09)
  - [ ] 0.1 Проверить что текущий login работает (admin/admin123)
  - [ ] 0.2 Проверить что Keycloak запущен (`docker-compose ps keycloak`)
  - [ ] 0.3 Feature flag `KEYCLOAK_ENABLED=false` по умолчанию — добавить в application.yml

- [ ] Task 1: Add Spring Security OAuth2 Resource Server Dependency (AC: #1)
  - [ ] 1.1 Добавить `spring-boot-starter-oauth2-resource-server` в build.gradle.kts
  - [ ] 1.2 Оставить существующие JJWT зависимости для совместимости (fallback)

- [ ] Task 2: Keycloak Configuration Properties (AC: #1)
  - [ ] 2.1 Создать `KeycloakProperties.kt` с @ConfigurationProperties
  - [ ] 2.2 Добавить свойства: issuerUri, jwksUri, clientId
  - [ ] 2.3 Обновить application.yml с keycloak.oauth2 секцией

- [ ] Task 3: Keycloak JWT Decoder Bean (AC: #1, #2)
  - [ ] 3.1 Создать `KeycloakJwtConfig.kt` с ReactiveJwtDecoder bean
  - [ ] 3.2 Настроить NimbusReactiveJwtDecoder с jwkSetUri
  - [ ] 3.3 Добавить JWKS cache configuration (5 min TTL)
  - [ ] 3.4 Добавить @ConditionalOnProperty для feature flag

- [ ] Task 4: Keycloak Role Converter (AC: #5, #6)
  - [ ] 4.1 Создать `KeycloakGrantedAuthoritiesConverter.kt`
  - [ ] 4.2 Реализовать маппинг `realm_access.roles` → Spring authorities
  - [ ] 4.3 admin-ui:developer → ROLE_DEVELOPER
  - [ ] 4.4 admin-ui:security → ROLE_SECURITY
  - [ ] 4.5 admin-ui:admin → ROLE_ADMIN

- [ ] Task 5: Keycloak Authentication Filter (AC: #2, #3, #4)
  - [ ] 5.1 Создать `KeycloakJwtAuthenticationFilter.kt`
  - [ ] 5.2 Валидация JWT через ReactiveJwtDecoder
  - [ ] 5.3 Создание Authentication с ролями из converter
  - [ ] 5.4 RFC 7807 error response для 401/403
  - [ ] 5.5 Добавить @ConditionalOnProperty для feature flag

- [ ] Task 6: Dual-Mode Security Config (AC: all)
  - [ ] 6.1 Рефакторинг SecurityConfig для поддержки двух режимов
  - [ ] 6.2 keycloak.enabled=true → Keycloak JWT validation
  - [ ] 6.3 keycloak.enabled=false → Legacy cookie/HMAC validation (без изменений)
  - [ ] 6.4 Role-based authorization: `/api/v1/users/**` требует ROLE_ADMIN

- [ ] Task 7: AuthenticatedUser Keycloak Adapter (AC: #2)
  - [ ] 7.1 Расширить AuthenticatedUser для работы с Keycloak claims
  - [ ] 7.2 Добавить factory method `fromKeycloakJwt(Jwt jwt)`
  - [ ] 7.3 Маппинг: sub → userId, preferred_username → username, email → email

- [ ] Task 8: Unit Tests (AC: all)
  - [ ] 8.1 KeycloakGrantedAuthoritiesConverterTest — маппинг ролей
  - [ ] 8.2 KeycloakJwtAuthenticationFilterTest — валидация токенов
  - [ ] 8.3 SecurityConfig test — оба режима работают
  - [ ] 8.4 Integration test с MockWebServer для JWKS endpoint

- [ ] Task 9: Manual Verification
  - [ ] 9.1 Smoke test с feature flag OFF — legacy login работает
  - [ ] 9.2 Smoke test с feature flag ON — Keycloak JWT принимается
  - [ ] 9.3 Проверить role-based access (admin видит users, developer — нет)
  - [ ] 9.4 Проверить 401 для invalid/expired token

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

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

