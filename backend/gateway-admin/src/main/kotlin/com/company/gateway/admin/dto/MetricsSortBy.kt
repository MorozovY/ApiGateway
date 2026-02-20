package com.company.gateway.admin.dto

/**
 * Допустимые значения сортировки для top-routes метрик.
 *
 * Определяет критерий сортировки маршрутов в топ-листе.
 */
enum class MetricsSortBy(val value: String) {
    /** Сортировка по количеству запросов */
    REQUESTS("requests"),

    /** Сортировка по среднему latency */
    LATENCY("latency"),

    /** Сортировка по количеству ошибок */
    ERRORS("errors");

    companion object {
        /** Значение по умолчанию */
        val DEFAULT = REQUESTS

        /** Список всех допустимых строковых значений */
        val VALID_VALUES: List<String> = entries.map { it.value }

        /**
         * Парсит строку в MetricsSortBy.
         *
         * @param value строковое значение (requests, latency, errors)
         * @return MetricsSortBy или null если значение невалидно
         */
        fun fromString(value: String): MetricsSortBy? =
            entries.find { it.value == value }

        /**
         * Проверяет валидность значения.
         *
         * @param value строковое значение
         * @return true если значение валидно
         */
        fun isValid(value: String): Boolean =
            entries.any { it.value == value }
    }
}
