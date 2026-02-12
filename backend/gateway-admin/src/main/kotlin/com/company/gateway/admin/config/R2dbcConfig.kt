package com.company.gateway.admin.config

import io.r2dbc.spi.ConnectionFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import reactor.core.publisher.Mono

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.company.gateway.admin.repository"])
class R2dbcConfig(
    private val connectionFactory: ConnectionFactory,
    private val stringArrayReadingConverter: StringArrayReadingConverter,
    private val stringListWritingConverter: StringListWritingConverter,
    private val routeStatusReadingConverter: RouteStatusReadingConverter,
    private val routeStatusWritingConverter: RouteStatusWritingConverter
) {

    private val log = LoggerFactory.getLogger(R2dbcConfig::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun verifyDatabaseConnection() {
        Mono.from(connectionFactory.create())
            .flatMap { connection ->
                Mono.from(connection.createStatement("SELECT 1").execute())
                    .doFinally { Mono.from(connection.close()).subscribe() }
            }
            .doOnSuccess { log.info("Database connection established") }
            .doOnError { e -> log.error("Database connection failed: {}", e.message) }
            .subscribe()
    }

    @Bean
    fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val converters = listOf(
            stringArrayReadingConverter,
            stringListWritingConverter,
            routeStatusReadingConverter,
            routeStatusWritingConverter
        )
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters)
    }
}
