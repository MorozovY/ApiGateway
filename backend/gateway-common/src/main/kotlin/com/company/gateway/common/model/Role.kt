package com.company.gateway.common.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class Role {
    DEVELOPER,
    SECURITY,
    ADMIN;

    // Сериализация в lowercase для JSON ответов
    @JsonValue
    fun toDbValue(): String = name.lowercase()

    companion object {
        // Десериализация из JSON (case-insensitive)
        @JvmStatic
        @JsonCreator
        fun fromDbValue(value: String): Role =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Неизвестная роль: $value")
    }
}
