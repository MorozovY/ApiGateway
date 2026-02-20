package com.company.gateway.admin.config

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Конфигурация Micrometer метрик для gateway-admin.
 *
 * Настраивает publishPercentiles для вычисления p95 и p99 latency
 * на стороне клиента (без Prometheus).
 *
 * ВАЖНО: Эта конфигурация применяется только к метрикам,
 * записанным в MeterRegistry gateway-admin. Метрики из gateway-core
 * (MetricsFilter) требуют Prometheus для полноценного анализа.
 */
@Configuration
class MetricsConfig {

    companion object {
        /**
         * Название метрики latency запросов (совпадает с gateway-core).
         */
        const val REQUEST_DURATION_METRIC = "gateway_request_duration_seconds"

        /**
         * Percentiles для client-side вычисления.
         */
        val PERCENTILES = doubleArrayOf(0.5, 0.95, 0.99)
    }

    /**
     * Настраивает publishPercentiles для метрики gateway_request_duration_seconds.
     *
     * Это позволяет MetricsService читать p95 и p99 через
     * timer.takeSnapshot().percentileValues() без необходимости Prometheus.
     */
    @Bean
    fun percentilesMeterFilter(): MeterFilter {
        return object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig? {
                if (id.name == REQUEST_DURATION_METRIC) {
                    return DistributionStatisticConfig.builder()
                        .percentiles(*PERCENTILES)
                        .build()
                        .merge(config)
                }
                return config
            }
        }
    }
}
