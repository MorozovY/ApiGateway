package com.company.gateway.core.util

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit тесты для PathNormalizer (Story 6.2, Task 4)
 *
 * Тесты:
 * - AC3: Path normalization для контроля cardinality
 * - Числовые ID заменяются на {id}
 * - UUID заменяются на {uuid}
 * - Статические сегменты остаются без изменений
 */
class PathNormalizerTest {

    @Nested
    inner class NumericIdNormalization {

        @Test
        fun `заменяет числовой ID на placeholder {id}`() {
            val result = PathNormalizer.normalize("/api/orders/123")

            assert(result == "/api/orders/{id}") {
                "Ожидалось '/api/orders/{id}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет числовой ID в середине пути`() {
            val result = PathNormalizer.normalize("/api/v1/items/42/details")

            assert(result == "/api/v1/items/{id}/details") {
                "Ожидалось '/api/v1/items/{id}/details', получено: '$result'"
            }
        }

        @Test
        fun `заменяет множественные числовые ID`() {
            val result = PathNormalizer.normalize("/api/users/1/orders/99")

            assert(result == "/api/users/{id}/orders/{id}") {
                "Ожидалось '/api/users/{id}/orders/{id}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет длинные числовые ID`() {
            val result = PathNormalizer.normalize("/api/items/1234567890123")

            assert(result == "/api/items/{id}") {
                "Ожидалось '/api/items/{id}', получено: '$result'"
            }
        }

        @Test
        fun `не заменяет версию API (v1, v2)`() {
            val result = PathNormalizer.normalize("/api/v1/users")

            assert(result == "/api/v1/users") {
                "Версия API не должна заменяться на {id}, получено: '$result'"
            }
        }
    }

    @Nested
    inner class UuidNormalization {

        @Test
        fun `заменяет UUID на placeholder {uuid}`() {
            val result = PathNormalizer.normalize("/users/550e8400-e29b-41d4-a716-446655440000")

            assert(result == "/users/{uuid}") {
                "Ожидалось '/users/{uuid}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет UUID в середине пути`() {
            val result = PathNormalizer.normalize("/api/orders/550e8400-e29b-41d4-a716-446655440000/items")

            assert(result == "/api/orders/{uuid}/items") {
                "Ожидалось '/api/orders/{uuid}/items', получено: '$result'"
            }
        }

        @Test
        fun `заменяет UUID в нижнем регистре`() {
            val result = PathNormalizer.normalize("/api/items/abcdef12-3456-7890-abcd-ef1234567890")

            assert(result == "/api/items/{uuid}") {
                "Ожидалось '/api/items/{uuid}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет UUID в верхнем регистре`() {
            val result = PathNormalizer.normalize("/api/items/ABCDEF12-3456-7890-ABCD-EF1234567890")

            assert(result == "/api/items/{uuid}") {
                "Ожидалось '/api/items/{uuid}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет UUID в смешанном регистре`() {
            val result = PathNormalizer.normalize("/api/items/AbCdEf12-3456-7890-AbCd-Ef1234567890")

            assert(result == "/api/items/{uuid}") {
                "Ожидалось '/api/items/{uuid}', получено: '$result'"
            }
        }

        @Test
        fun `заменяет множественные UUID`() {
            val result = PathNormalizer.normalize(
                "/api/users/550e8400-e29b-41d4-a716-446655440000/orders/660e8400-e29b-41d4-a716-446655440001"
            )

            assert(result == "/api/users/{uuid}/orders/{uuid}") {
                "Ожидалось '/api/users/{uuid}/orders/{uuid}', получено: '$result'"
            }
        }
    }

    @Nested
    inner class StaticPathPreservation {

        @Test
        fun `сохраняет статические пути без изменений`() {
            val result = PathNormalizer.normalize("/static/file.txt")

            assert(result == "/static/file.txt") {
                "Статический путь не должен изменяться, получено: '$result'"
            }
        }

        @Test
        fun `сохраняет пути без динамических сегментов`() {
            val result = PathNormalizer.normalize("/api/users")

            assert(result == "/api/users") {
                "Путь без динамических сегментов не должен изменяться, получено: '$result'"
            }
        }

        @Test
        fun `сохраняет пути с расширениями файлов`() {
            val result = PathNormalizer.normalize("/assets/styles.css")

            assert(result == "/assets/styles.css") {
                "Путь с расширением файла не должен изменяться, получено: '$result'"
            }
        }

        @Test
        fun `сохраняет пути с дефисами в названиях`() {
            val result = PathNormalizer.normalize("/api/user-settings/preferences")

            assert(result == "/api/user-settings/preferences") {
                "Путь с дефисами не должен изменяться (не UUID), получено: '$result'"
            }
        }

        @Test
        fun `сохраняет пустой путь`() {
            val result = PathNormalizer.normalize("")

            assert(result == "") {
                "Пустой путь не должен изменяться, получено: '$result'"
            }
        }

        @Test
        fun `сохраняет корневой путь`() {
            val result = PathNormalizer.normalize("/")

            assert(result == "/") {
                "Корневой путь не должен изменяться, получено: '$result'"
            }
        }
    }

    @Nested
    inner class MixedSegments {

        @Test
        fun `корректно обрабатывает смесь UUID и числовых ID`() {
            val result = PathNormalizer.normalize(
                "/api/users/550e8400-e29b-41d4-a716-446655440000/orders/123"
            )

            assert(result == "/api/users/{uuid}/orders/{id}") {
                "Ожидалось '/api/users/{uuid}/orders/{id}', получено: '$result'"
            }
        }

        @Test
        fun `корректно обрабатывает сложный путь`() {
            val result = PathNormalizer.normalize(
                "/api/v2/organizations/550e8400-e29b-41d4-a716-446655440000/projects/42/tasks/999/details"
            )

            assert(result == "/api/v2/organizations/{uuid}/projects/{id}/tasks/{id}/details") {
                "Ожидалось '/api/v2/organizations/{uuid}/projects/{id}/tasks/{id}/details', получено: '$result'"
            }
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `не заменяет строки похожие на UUID но неправильного формата`() {
            // Слишком короткий первый сегмент
            val result = PathNormalizer.normalize("/api/items/550e840-e29b-41d4-a716-446655440000")

            assert(result == "/api/items/550e840-e29b-41d4-a716-446655440000") {
                "Неполный UUID не должен заменяться, получено: '$result'"
            }
        }

        @Test
        fun `не заменяет строки с буквами не-hex`() {
            val result = PathNormalizer.normalize("/api/items/hello-world")

            assert(result == "/api/items/hello-world") {
                "Строка с не-hex символами не должна заменяться, получено: '$result'"
            }
        }

        @Test
        fun `сохраняет смешанные буквенно-цифровые сегменты`() {
            val result = PathNormalizer.normalize("/api/items/item123abc")

            assert(result == "/api/items/item123abc") {
                "Смешанные буквенно-цифровые сегменты не должны заменяться, получено: '$result'"
            }
        }

        @Test
        fun `обрабатывает trailing slash`() {
            val result = PathNormalizer.normalize("/api/users/123/")

            // Trailing slash сохраняется, ID заменяется
            assert(result == "/api/users/{id}/") {
                "Trailing slash должен сохраняться, получено: '$result'"
            }
        }

        @Test
        fun `обрабатывает double slash`() {
            val result = PathNormalizer.normalize("/api//users/123")

            // Double slash сохраняется как есть (пустой сегмент)
            assert(result == "/api//users/{id}") {
                "Double slash должен обрабатываться корректно, получено: '$result'"
            }
        }
    }
}
