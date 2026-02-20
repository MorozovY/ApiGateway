package com.company.gateway.admin.dto

/**
 * Ответ со списком уникальных upstream хостов.
 *
 * Story 7.4: Routes by Upstream Filter (FR24, AC3).
 * Позволяет Security Specialist получить обзор всех upstream сервисов
 * и количества маршрутов к каждому из них.
 *
 * @property upstreams список уникальных upstream хостов с количеством маршрутов
 */
data class UpstreamsListResponse(
    val upstreams: List<UpstreamInfo>
)

/**
 * Информация об upstream хосте и количестве маршрутов к нему.
 *
 * @property host hostname:port без схемы (например "order-service:8080")
 * @property routeCount количество маршрутов, указывающих на этот хост
 */
data class UpstreamInfo(
    val host: String,
    val routeCount: Long
)
