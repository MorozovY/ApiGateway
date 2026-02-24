package com.company.gateway.admin.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Тесты для KeycloakGrantedAuthoritiesConverter.
 *
 * Проверяет маппинг Keycloak ролей (realm_access.roles) в Spring Security authorities.
 */
class KeycloakGrantedAuthoritiesConverterTest {

    private lateinit var converter: KeycloakGrantedAuthoritiesConverter

    @BeforeEach
    fun setUp() {
        converter = KeycloakGrantedAuthoritiesConverter()
    }

    /**
     * Создаёт mock Keycloak JWT с заданными realm_access.roles.
     */
    private fun createJwtWithRoles(roles: List<String>?): Jwt {
        val builder = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject("test-user-id")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))

        if (roles != null) {
            builder.claim("realm_access", mapOf("roles" to roles))
        }

        return builder.build()
    }

    @Test
    fun `extractAuthorities возвращает ROLE_DEVELOPER для admin-ui_developer`() {
        // Given: JWT с ролью admin-ui:developer
        val jwt = createJwtWithRoles(listOf("admin-ui:developer"))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities содержат ROLE_DEVELOPER
        assertThat(authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_DEVELOPER")
    }

    @Test
    fun `extractAuthorities возвращает ROLE_SECURITY для admin-ui_security`() {
        // Given: JWT с ролью admin-ui:security
        val jwt = createJwtWithRoles(listOf("admin-ui:security"))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities содержат ROLE_SECURITY
        assertThat(authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_SECURITY")
    }

    @Test
    fun `extractAuthorities возвращает ROLE_ADMIN для admin-ui_admin`() {
        // Given: JWT с ролью admin-ui:admin
        val jwt = createJwtWithRoles(listOf("admin-ui:admin"))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities содержат ROLE_ADMIN
        assertThat(authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `extractAuthorities возвращает все mapped roles при нескольких ролях`() {
        // Given: JWT с несколькими admin-ui ролями
        val jwt = createJwtWithRoles(listOf("admin-ui:developer", "admin-ui:security", "admin-ui:admin"))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities содержат все три роли
        assertThat(authorities)
            .hasSize(3)
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_DEVELOPER", "ROLE_SECURITY", "ROLE_ADMIN")
    }

    @Test
    fun `extractAuthorities игнорирует роли без префикса admin-ui`() {
        // Given: JWT со смешанными ролями
        val jwt = createJwtWithRoles(listOf(
            "admin-ui:developer",
            "default-roles-api-gateway",
            "other-app:user",
            "api:consumer"
        ))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: только admin-ui:developer преобразована
        assertThat(authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_DEVELOPER")
    }

    @Test
    fun `extractAuthorities возвращает пустой список при отсутствии realm_access`() {
        // Given: JWT без realm_access claim
        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject("test-user-id")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities пустой
        assertThat(authorities).isEmpty()
    }

    @Test
    fun `extractAuthorities возвращает пустой список при отсутствии roles в realm_access`() {
        // Given: JWT с realm_access но без roles
        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject("test-user-id")
            .claim("realm_access", mapOf("other_key" to "value"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities пустой
        assertThat(authorities).isEmpty()
    }

    @Test
    fun `extractAuthorities возвращает пустой список при пустом списке ролей`() {
        // Given: JWT с пустым списком ролей
        val jwt = createJwtWithRoles(emptyList())

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities пустой
        assertThat(authorities).isEmpty()
    }

    @Test
    fun `extractAuthorities возвращает пустой список когда нет matching admin-ui ролей`() {
        // Given: JWT с ролями без admin-ui префикса
        val jwt = createJwtWithRoles(listOf(
            "default-roles-api-gateway",
            "api:consumer",
            "other-role"
        ))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: authorities пустой
        assertThat(authorities).isEmpty()
    }

    @Test
    fun `extractAuthorities не дедуплицирует роли`() {
        // Given: JWT с дублирующимися ролями
        val jwt = createJwtWithRoles(listOf(
            "admin-ui:developer",
            "admin-ui:developer"  // дубликат
        ))

        // When: извлекаем authorities
        val authorities = converter.extractAuthorities(jwt)

        // Then: роль присутствует дважды (converter не дедуплицирует)
        assertThat(authorities)
            .hasSize(2)
            .extracting("authority")
            .containsExactly("ROLE_DEVELOPER", "ROLE_DEVELOPER")
    }

    @Test
    fun `convert возвращает Flux с authorities`() {
        // Given: JWT с ролями
        val jwt = createJwtWithRoles(listOf("admin-ui:admin", "admin-ui:developer"))

        // When: конвертируем
        val authoritiesFlux = converter.convert(jwt)

        // Then: Flux содержит правильные authorities
        val authorities = authoritiesFlux.collectList().block()!!
        assertThat(authorities)
            .hasSize(2)
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DEVELOPER")
    }
}
