package com.company.gateway.common.model

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("users")
data class User(
    @Id
    val id: UUID? = null,

    @Column("username")
    val username: String,

    @Column("email")
    val email: String,

    @Column("password_hash")
    val passwordHash: String,

    @Column("role")
    val role: Role = Role.DEVELOPER,

    @Column("is_active")
    val isActive: Boolean = true,

    @CreatedDate
    @Column("created_at")
    val createdAt: Instant? = null,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: Instant? = null
)
