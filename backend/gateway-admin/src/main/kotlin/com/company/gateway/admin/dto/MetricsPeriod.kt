package com.company.gateway.admin.dto

import java.time.Duration

/**
 * Допустимые значения периода для метрик.
 *
 * Определяет временной интервал для расчёта RPS и агрегации метрик.
 */
enum class MetricsPeriod(val value: String, val duration: Duration) {
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    SIX_HOURS("6h", Duration.ofHours(6)),
    TWENTY_FOUR_HOURS("24h", Duration.ofHours(24));

    companion object {
        /** Период по умолчанию */
        val DEFAULT = FIVE_MINUTES

        /** Список всех допустимых строковых значений */
        val VALID_VALUES: List<String> = entries.map { it.value }

        /**
         * Парсит строку в MetricsPeriod.
         *
         * @param value строковое значение периода (5m, 15m, 1h, 6h, 24h)
         * @return MetricsPeriod или null если значение невалидно
         */
        fun fromString(value: String): MetricsPeriod? =
            entries.find { it.value == value }

        /**
         * Проверяет валидность значения периода.
         *
         * @param value строковое значение периода
         * @return true если значение валидно
         */
        fun isValid(value: String): Boolean =
            entries.any { it.value == value }
    }
}
