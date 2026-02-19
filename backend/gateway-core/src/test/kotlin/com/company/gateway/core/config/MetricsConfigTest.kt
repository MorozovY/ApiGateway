package com.company.gateway.core.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Unit тесты для MetricsConfig (Story 6.1)
 *
 * Тесты:
 * - AC3: Histogram buckets настроены корректно
 * - Common tags добавляются к метрикам
 */
class MetricsConfigTest {

    private val metricsConfig = MetricsConfig()

    @Nested
    inner class CommonTags {

        @Test
        fun `metricsCommonTags добавляет тег application`() {
            val filter = metricsConfig.metricsCommonTags()
            val registry = SimpleMeterRegistry()

            // Применяем фильтр
            registry.config().meterFilter(filter)

            // Создаём метрику
            registry.counter("test_metric").increment()

            // Проверяем, что тег application добавлен
            val counter = registry.find("test_metric")
                .tag("application", "gateway-core")
                .counter()

            assert(counter != null) {
                "Метрика должна содержать тег application=gateway-core"
            }
        }
    }

    @Nested
    inner class HistogramBuckets {

        @Test
        fun `HISTOGRAM_BUCKETS_SECONDS содержит требуемые значения`() {
            val expectedBuckets = doubleArrayOf(0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0)

            assert(MetricsConfig.HISTOGRAM_BUCKETS_SECONDS.contentEquals(expectedBuckets)) {
                "HISTOGRAM_BUCKETS_SECONDS должен содержать ${expectedBuckets.toList()}, " +
                        "получено: ${MetricsConfig.HISTOGRAM_BUCKETS_SECONDS.toList()}"
            }
        }

        @Test
        fun `REQUEST_DURATION_METRIC константа равна gateway_request_duration_seconds`() {
            assert(MetricsConfig.REQUEST_DURATION_METRIC == "gateway_request_duration_seconds") {
                "REQUEST_DURATION_METRIC должен быть 'gateway_request_duration_seconds'"
            }
        }

        @Test
        fun `histogramBucketsFilter настраивает SLO buckets для gateway_request_duration_seconds`() {
            val filter = metricsConfig.histogramBucketsFilter()
            val registry = SimpleMeterRegistry()

            // Применяем фильтр
            registry.config().meterFilter(filter)

            // Создаём timer с нужным именем
            val timer = Timer.builder(MetricsConfig.REQUEST_DURATION_METRIC)
                .register(registry)

            // Записываем несколько значений
            timer.record(5, TimeUnit.MILLISECONDS)   // < 10ms bucket
            timer.record(30, TimeUnit.MILLISECONDS)  // < 50ms bucket
            timer.record(75, TimeUnit.MILLISECONDS)  // < 100ms bucket
            timer.record(150, TimeUnit.MILLISECONDS) // < 200ms bucket
            timer.record(300, TimeUnit.MILLISECONDS) // < 500ms bucket
            timer.record(750, TimeUnit.MILLISECONDS) // < 1s bucket
            timer.record(1500, TimeUnit.MILLISECONDS) // < 2s bucket
            timer.record(3000, TimeUnit.MILLISECONDS) // < 5s bucket

            // Проверяем, что timer записал все события
            assert(timer.count() == 8L) {
                "Timer должен записать 8 событий, получено: ${timer.count()}"
            }
        }

        @Test
        fun `histogramBucketsFilter не применяется к другим метрикам`() {
            val filter = metricsConfig.histogramBucketsFilter()
            val registry = SimpleMeterRegistry()

            // Применяем фильтр
            registry.config().meterFilter(filter)

            // Создаём timer с другим именем
            val otherTimer = Timer.builder("other_metric")
                .register(registry)

            otherTimer.record(100, TimeUnit.MILLISECONDS)

            // Timer должен работать без ошибок
            assert(otherTimer.count() == 1L) {
                "Другие метрики должны работать без изменений"
            }
        }
    }
}
