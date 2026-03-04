package com.company.gateway.admin.dto

/**
 * DTO для сводки Dashboard.
 *
 * Содержит статистику маршрутов по статусам и дополнительные данные
 * в зависимости от роли пользователя.
 *
 * Story 16.2: Наполнение Dashboard полезным контентом
 *
 * @property routesByStatus количество маршрутов по статусам (draft, pending, published, rejected)
 * @property pendingApprovalsCount количество маршрутов на согласование
 * @property totalUsers общее количество пользователей (только для Admin)
 * @property totalConsumers общее количество consumers (только для Admin)
 * @property systemHealth статус системы: "healthy", "degraded", "down" (только для Admin)
 */
data class DashboardSummaryDto(
    val routesByStatus: Map<String, Int>,
    val pendingApprovalsCount: Int,
    val totalUsers: Int? = null,
    val totalConsumers: Int? = null,
    val systemHealth: String? = null
)
