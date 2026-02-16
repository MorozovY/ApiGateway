package com.company.gateway.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    @Profile("dev", "default", "test")
    fun devSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    // Health endpoints - публичный доступ (AC6 Story 1.7)
                    .pathMatchers("/actuator/health/**").permitAll()
                    .pathMatchers("/actuator/info").permitAll()
                    .pathMatchers("/actuator/prometheus").permitAll()
                    // Auth endpoints - публичный доступ (Story 2.2)
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    // OpenAPI/Swagger - только для dev
                    .pathMatchers("/api-docs/**").permitAll()
                    .pathMatchers("/swagger-ui/**").permitAll()
                    .pathMatchers("/swagger-ui.html").permitAll()
                    .pathMatchers("/webjars/**").permitAll()
                    .anyExchange().permitAll()
            }
            .build()
    }

    @Bean
    @Profile("prod")
    fun prodSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    // Health endpoints - публичный доступ (AC6 Story 1.7)
                    .pathMatchers("/actuator/health/**").permitAll()
                    .pathMatchers("/actuator/info").permitAll()
                    .pathMatchers("/actuator/prometheus").permitAll()
                    // Auth endpoints - публичный доступ (Story 2.2)
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    .anyExchange().authenticated()
            }
            .build()
    }
}
