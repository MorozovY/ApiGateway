package com.company.gateway.core.config

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Конфигурация Micrometer метрик для gateway.
 *
 * Настраивает:
 * - Общие теги для всех метрик (application)
 * - Histogram buckets для gateway_request_duration_seconds
 */
@Configuration
class MetricsConfig {

    companion object {
        /**
         * Название метрики latency запросов.
         */
        const val REQUEST_DURATION_METRIC = "gateway_request_duration_seconds"

        /**
         * Histogram buckets в секундах для latency метрик.
         * Позволяют вычислять percentiles P50, P95, P99.
         */
        val HISTOGRAM_BUCKETS_SECONDS = doubleArrayOf(
            0.01,  // 10ms
            0.05,  // 50ms
            0.1,   // 100ms
            0.2,   // 200ms
            0.5,   // 500ms
            1.0,   // 1s
            2.0,   // 2s
            5.0    // 5s
        )
    }

    /**
     * Добавляет общий тег application ко всем метрикам.
     */
    @Bean
    fun metricsCommonTags(): MeterFilter {
        return MeterFilter.commonTags(listOf(Tag.of("application", "gateway-core")))
    }

    /**
     * Настраивает histogram buckets для метрики gateway_request_duration_seconds.
     *
     * SLO buckets: 0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0 секунд.
     * Это позволяет Prometheus вычислять histogram_quantile для P50, P95, P99.
     */
    @Bean
    fun histogramBucketsFilter(): MeterFilter {
        return object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig? {
                if (id.name == REQUEST_DURATION_METRIC) {
                    // Конвертируем секунды в наносекунды для SLO (Micrometer использует наносекунды)
                    val sloNanos = HISTOGRAM_BUCKETS_SECONDS.map { seconds ->
                        (seconds * 1_000_000_000).toLong().toDouble()
                    }.toDoubleArray()

                    return DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(*sloNanos)
                        .build()
                        .merge(config)
                }
                return config
            }
        }
    }
}
