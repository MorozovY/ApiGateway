package com.company.gateway.admin.repository

import com.company.gateway.admin.dto.RouteWithCreator
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Кастомный интерфейс для RouteRepository с поддержкой динамической фильтрации.
 *
 * Используется для реализации сложных запросов с комбинацией фильтров,
 * которые не могут быть выражены через стандартные методы Spring Data R2DBC.
 */
interface RouteRepositoryCustom {

    /**
     * Находит маршруты с применением фильтров и пагинации.
     *
     * Фильтры применяются с AND логикой.
     *
     * @param status фильтр по статусу (опционально)
     * @param createdBy фильтр по автору — UUID пользователя (опционально)
     * @param search строка поиска по path и description (case-insensitive)
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Flux<Route> отфильтрованные маршруты с пагинацией
     */
    fun findWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?,
        offset: Int,
        limit: Int
    ): Flux<Route>

    /**
     * Подсчитывает общее количество маршрутов, соответствующих фильтрам.
     *
     * Используется для вычисления total в пагинированном ответе.
     *
     * @param status фильтр по статусу (опционально)
     * @param createdBy фильтр по автору — UUID пользователя (опционально)
     * @param search строка поиска по path и description (case-insensitive)
     * @return Mono<Long> количество маршрутов
     */
    fun countWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?
    ): Mono<Long>

    /**
     * Находит маршрут по ID с информацией о создателе (JOIN с users).
     *
     * Используется для GET /api/v1/routes/{id} (Story 3.3, AC1).
     *
     * @param id UUID маршрута
     * @return Mono<RouteWithCreator> маршрут с username создателя или empty если не найден
     */
    fun findByIdWithCreator(id: UUID): Mono<RouteWithCreator>

    /**
     * Находит маршруты по паттерну path (LIKE запрос).
     *
     * Используется для определения следующего доступного суффикса при клонировании (Story 3.3, AC4).
     *
     * @param pattern паттерн для поиска (например "/api/orders-copy%")
     * @return Flux<Route> маршруты с path, соответствующим паттерну
     */
    fun findByPathLike(pattern: String): Flux<Route>
}
