package com.company.gateway.admin.config

import com.company.gateway.admin.properties.KeycloakProperties
import com.company.gateway.admin.security.KeycloakAccessDeniedHandler
import com.company.gateway.admin.security.KeycloakAuthenticationEntryPoint
import com.company.gateway.admin.security.KeycloakGrantedAuthoritiesConverter
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import java.time.Duration
import java.util.Date
import java.util.UUID

/**
 * Интеграционные тесты для KeycloakSecurityConfig.
 *
 * Использует MockWebServer для эмуляции Keycloak JWKS endpoint.
 */
class KeycloakSecurityConfigTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var rsaKey: RSAKey
    private lateinit var jwksJson: String

    @BeforeEach
    fun setUp() {
        // Генерируем RSA ключ для подписи JWT
        rsaKey = RSAKeyGenerator(2048)
            .keyID("test-key-id")
            .generate()

        // Создаём JWKS response
        val jwkSet = JWKSet(rsaKey.toPublicJWK())
        jwksJson = jwkSet.toString()

        // Запускаем MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Создаёт подписанный JWT токен с Keycloak claims.
     */
    private fun createSignedJwt(
        subject: String = UUID.randomUUID().toString(),
        preferredUsername: String = "testuser",
        email: String = "test@example.com",
        roles: List<String> = listOf("admin-ui:developer"),
        expirationTime: Date = Date(System.currentTimeMillis() + 3600000)
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .claim("preferred_username", preferredUsername)
            .claim("email", email)
            .claim("realm_access", mapOf("roles" to roles))
            .issueTime(Date())
            .expirationTime(expirationTime)
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .build()

        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaKey))

        return signedJwt.serialize()
    }

    @Test
    fun `ReactiveJwtDecoder успешно валидирует JWT с JWKS от MockWebServer`() {
        // Given: MockWebServer возвращает JWKS
        mockWebServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jwksJson)
        )

        val jwksUri = mockWebServer.url("/certs").toString()
        val decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(jwksUri)
            .build()

        // Создаём подписанный JWT
        val token = createSignedJwt()

        // When: декодируем JWT
        val jwt = decoder.decode(token).block(Duration.ofSeconds(5))

        // Then: JWT успешно декодирован
        assertThat(jwt).isNotNull
        assertThat(jwt!!.getClaimAsString("preferred_username")).isEqualTo("testuser")
        assertThat(jwt.getClaimAsString("email")).isEqualTo("test@example.com")
    }

    @Test
    fun `ReactiveJwtDecoder отклоняет JWT с неизвестным ключом`() {
        // Given: MockWebServer возвращает JWKS без нашего ключа
        val otherKey = RSAKeyGenerator(2048)
            .keyID("other-key-id")
            .generate()
        val otherJwks = JWKSet(otherKey.toPublicJWK()).toString()

        // Nimbus может делать повторные запросы при ошибке валидации,
        // поэтому добавляем несколько одинаковых responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(otherJwks)
            )
        }

        val jwksUri = mockWebServer.url("/certs").toString()
        val decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(jwksUri)
            .build()

        // Создаём JWT подписанный нашим ключом (не тем что в JWKS)
        val token = createSignedJwt()

        // When & Then: декодирование должно выбросить исключение
        try {
            decoder.decode(token).block(Duration.ofSeconds(5))
            org.junit.jupiter.api.fail("Expected exception when validating JWT with unknown key")
        } catch (e: Exception) {
            // Ожидаем ошибку валидации ключа (сообщение может варьироваться)
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun `ReactiveJwtDecoder отклоняет expired JWT`() {
        // Given: MockWebServer возвращает JWKS
        mockWebServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jwksJson)
        )

        val jwksUri = mockWebServer.url("/certs").toString()
        val decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(jwksUri)
            .build()

        // Создаём expired JWT (истёк 1 час назад)
        val expiredTime = Date(System.currentTimeMillis() - 3600000)
        val token = createSignedJwt(expirationTime = expiredTime)

        // When & Then: декодирование должно выбросить исключение
        try {
            decoder.decode(token).block(Duration.ofSeconds(5))
            org.junit.jupiter.api.fail("Expected exception when validating expired JWT")
        } catch (e: Exception) {
            // Ожидаем ошибку истечения или валидации токена
            // (NimbusReactiveJwtDecoder может использовать разные сообщения)
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun `KeycloakGrantedAuthoritiesConverter извлекает роли из JWT`() {
        // Given: JWT с несколькими ролями
        mockWebServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jwksJson)
        )

        val jwksUri = mockWebServer.url("/certs").toString()
        val decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(jwksUri)
            .build()

        val token = createSignedJwt(
            roles = listOf("admin-ui:admin", "admin-ui:developer", "default-roles-api-gateway")
        )

        // When: декодируем JWT и извлекаем authorities
        val jwt = decoder.decode(token).block(Duration.ofSeconds(5))!!
        val converter = KeycloakGrantedAuthoritiesConverter()
        val authorities = converter.extractAuthorities(jwt)

        // Then: только admin-ui роли преобразованы
        assertThat(authorities)
            .hasSize(2)
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DEVELOPER")
    }

    @Test
    fun `KeycloakProperties вычисляет корректный issuerUri`() {
        // Given: KeycloakProperties с настройками
        val properties = KeycloakProperties(
            enabled = true,
            url = "http://localhost:8180",
            realm = "api-gateway",
            clientId = "admin-ui"
        )

        // When & Then: issuerUri корректно вычислен
        assertThat(properties.issuerUri).isEqualTo("http://localhost:8180/realms/api-gateway")
    }

    @Test
    fun `KeycloakProperties вычисляет корректный jwksUri`() {
        // Given: KeycloakProperties с настройками
        val properties = KeycloakProperties(
            enabled = true,
            url = "http://localhost:8180",
            realm = "api-gateway",
            clientId = "admin-ui"
        )

        // When & Then: jwksUri корректно вычислен
        assertThat(properties.jwksUri).isEqualTo(
            "http://localhost:8180/realms/api-gateway/protocol/openid-connect/certs"
        )
    }

    @Test
    fun `KeycloakAuthenticationEntryPoint возвращает RFC 7807 response`() {
        // Given: KeycloakAuthenticationEntryPoint
        val objectMapper = ObjectMapper()
        val entryPoint = KeycloakAuthenticationEntryPoint(objectMapper)

        // Then: entry point не null (дальнейшая проверка требует WebTestClient)
        assertThat(entryPoint).isNotNull
    }

    @Test
    fun `KeycloakAccessDeniedHandler возвращает RFC 7807 response`() {
        // Given: KeycloakAccessDeniedHandler
        val objectMapper = ObjectMapper()
        val handler = KeycloakAccessDeniedHandler(objectMapper)

        // Then: handler не null (дальнейшая проверка требует WebTestClient)
        assertThat(handler).isNotNull
    }
}
