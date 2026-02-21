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

    /**
     * Поиск пользователей по username или email с пагинацией.
     *
     * Поиск case-insensitive (ILIKE). Используется в UserService.findAll()
     * когда параметр search задан.
     *
     * @param searchPattern паттерн поиска в формате %search%
     * @param limit максимальное количество результатов
     * @param offset смещение от начала
     */
    @Query(
        """
        SELECT * FROM users
        WHERE username ILIKE :searchPattern OR email ILIKE :searchPattern
        ORDER BY created_at ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchUsers(searchPattern: String, limit: Int, offset: Int): Flux<User>

    /**
     * Подсчёт пользователей по поиску username или email.
     *
     * Используется для пагинации при поиске.
     *
     * @param searchPattern паттерн поиска в формате %search%
     */
    @Query(
        """
        SELECT COUNT(*) FROM users
        WHERE username ILIKE :searchPattern OR email ILIKE :searchPattern
        """
    )
    fun countBySearch(searchPattern: String): Mono<Long>

    /**
     * Получение всех активных пользователей для dropdowns.
     *
     * Возвращает всех активных пользователей отсортированных по username.
     * Используется для фильтров в audit logs (Story 8.6).
     */
    @Query("SELECT * FROM users WHERE is_active = true ORDER BY username ASC")
    fun findAllActiveOrderByUsername(): Flux<User>
}
