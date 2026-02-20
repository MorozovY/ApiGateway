package com.company.gateway.core.util

/**
 * Утилита для нормализации путей в метриках для контроля cardinality.
 *
 * Заменяет динамические сегменты (UUID, числовые ID) на placeholders,
 * чтобы избежать взрыва количества уникальных label values в Prometheus.
 *
 * Примеры:
 * - `/api/orders/123` → `/api/orders/{id}`
 * - `/users/550e8400-e29b-41d4-a716-446655440000` → `/users/{uuid}`
 * - `/static/file.txt` → `/static/file.txt` (без изменений)
 */
object PathNormalizer {

    /**
     * UUID pattern: 8-4-4-4-12 hex characters (case-insensitive).
     * Формат: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    private val UUID_PATTERN = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )

    /**
     * Numeric ID pattern: одна или более цифр (целый сегмент).
     */
    private val NUMERIC_ID_PATTERN = Regex("^\\d+$")

    /**
     * Нормализует путь, заменяя динамические сегменты на placeholders.
     *
     * @param path исходный путь (например, `/api/orders/123`)
     * @return нормализованный путь (например, `/api/orders/{id}`)
     */
    fun normalize(path: String): String {
        if (path.isEmpty()) {
            return path
        }

        return path.split("/")
            .joinToString("/") { segment ->
                normalizeSegment(segment)
            }
    }

    /**
     * Нормализует отдельный сегмент пути.
     */
    private fun normalizeSegment(segment: String): String {
        return when {
            // Пустой сегмент (от leading/trailing/double slash) — оставляем
            segment.isEmpty() -> segment

            // UUID — заменяем на {uuid}
            UUID_PATTERN.matches(segment) -> "{uuid}"

            // Числовой ID — заменяем на {id}
            NUMERIC_ID_PATTERN.matches(segment) -> "{id}"

            // Всё остальное — оставляем без изменений
            else -> segment
        }
    }
}
