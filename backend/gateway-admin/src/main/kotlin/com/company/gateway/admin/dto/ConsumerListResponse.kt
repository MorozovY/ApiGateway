package com.company.gateway.admin.dto

/**
 * Пагинированный список consumers.
 *
 * @property items список consumers
 * @property total общее количество consumers
 */
data class ConsumerListResponse(
    val items: List<ConsumerResponse>,
    val total: Int
)
