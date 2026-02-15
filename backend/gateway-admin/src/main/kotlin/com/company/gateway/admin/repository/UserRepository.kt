package com.company.gateway.admin.repository

import com.company.gateway.common.model.User
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
interface UserRepository : ReactiveCrudRepository<User, UUID> {

    fun findByUsername(username: String): Mono<User>

    fun findByEmail(email: String): Mono<User>

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    fun existsByUsername(username: String): Mono<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    fun existsByEmail(email: String): Mono<Boolean>
}
