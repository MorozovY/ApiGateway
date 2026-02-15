package com.company.gateway.common.model

enum class Role {
    DEVELOPER,
    SECURITY,
    ADMIN;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): Role =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Неизвестная роль: $value")
    }
}
