package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("routes")
data class Route(
    @Id
    val id: UUID? = null,

    val path: String,

    @Column("upstream_url")
    val upstreamUrl: String,

    val methods: List<String> = emptyList(),

    val status: RouteStatus = RouteStatus.DRAFT,

    @Column("created_by")
    val createdBy: UUID? = null,

    @Column("created_at")
    val createdAt: Instant? = null,

    @Column("updated_at")
    val updatedAt: Instant? = null
)

enum class RouteStatus {
    DRAFT, PENDING, PUBLISHED, REJECTED
}