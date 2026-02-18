package com.company.gateway.admin.repository

import com.company.gateway.common.model.User
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
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

    /**
     * Получение пользователей с пагинацией на уровне БД (без full table scan).
     *
     * Сортировка по created_at обеспечивает стабильный порядок при пагинации.
     */
    @Query("SELECT * FROM users ORDER BY created_at ASC LIMIT :limit OFFSET :offset")
    fun findAllPaginated(limit: Int, offset: Int): Flux<User>

    /**
     * Подсчёт активных пользователей по роли (без full table scan).
     *
     * Используется в UserService.deactivate() для проверки наличия хотя бы одного admin.
     */
    @Query("SELECT COUNT(*) FROM users WHERE role = :role AND is_active = true")
    fun countActiveByRole(role: String): Mono<Long>
}
