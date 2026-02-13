package com.company.gateway.core.config

import com.company.gateway.common.model.RouteStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.stereotype.Component

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.company.gateway.core.repository"])
class R2dbcConfig {

    @Bean
    fun r2dbcCustomConversions(
        stringArrayReadingConverter: CoreStringArrayReadingConverter,
        stringListWritingConverter: CoreStringListWritingConverter,
        routeStatusReadingConverter: CoreRouteStatusReadingConverter,
        routeStatusWritingConverter: CoreRouteStatusWritingConverter
    ): R2dbcCustomConversions {
        val converters = listOf(
            stringArrayReadingConverter,
            stringListWritingConverter,
            routeStatusReadingConverter,
            routeStatusWritingConverter
        )
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters)
    }
}

@ReadingConverter
@Component
class CoreStringArrayReadingConverter : Converter<Array<String>, List<String>> {
    override fun convert(source: Array<String>): List<String> = source.toList()
}

@WritingConverter
@Component
class CoreStringListWritingConverter : Converter<List<String>, Array<String>> {
    override fun convert(source: List<String>): Array<String> = source.toTypedArray()
}

@ReadingConverter
@Component
class CoreRouteStatusReadingConverter : Converter<String, RouteStatus> {
    override fun convert(source: String): RouteStatus = RouteStatus.valueOf(source.uppercase())
}

@WritingConverter
@Component
class CoreRouteStatusWritingConverter : Converter<RouteStatus, String> {
    override fun convert(source: RouteStatus): String = source.name.lowercase()
}
