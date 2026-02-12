package com.company.gateway.admin.config

import com.company.gateway.common.model.RouteStatus
import io.r2dbc.spi.Row
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.stereotype.Component

@ReadingConverter
@Component
class StringArrayReadingConverter : Converter<Array<String>, List<String>> {
    override fun convert(source: Array<String>): List<String> = source.toList()
}

@WritingConverter
@Component
class StringListWritingConverter : Converter<List<String>, Array<String>> {
    override fun convert(source: List<String>): Array<String> = source.toTypedArray()
}

@ReadingConverter
@Component
class RouteStatusReadingConverter : Converter<String, RouteStatus> {
    override fun convert(source: String): RouteStatus =
        RouteStatus.valueOf(source.uppercase())
}

@WritingConverter
@Component
class RouteStatusWritingConverter : Converter<RouteStatus, String> {
    override fun convert(source: RouteStatus): String =
        source.name.lowercase()
}