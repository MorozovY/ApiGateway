package com.company.gateway.admin.repository

import com.company.gateway.admin.dto.RouteWithCreator
import com.company.gateway.admin.dto.UpstreamInfo
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
     * @param upstream поиск по части upstream URL (ILIKE, case-insensitive)
     * @param upstreamExact точное совпадение upstream URL (case-sensitive)
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Flux<Route> отфильтрованные маршруты с пагинацией
     */
    fun findWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?,
        upstream: String?,
        upstreamExact: String?,
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
     * @param upstream поиск по части upstream URL (ILIKE, case-insensitive)
     * @param upstreamExact точное совпадение upstream URL (case-sensitive)
     * @return Mono<Long> количество маршрутов
     */
    fun countWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?,
        upstream: String?,
        upstreamExact: String?
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

    /**
     * Находит все маршруты со статусом pending с поддержкой сортировки и пагинации.
     *
     * Выполняет JOIN с таблицей users для получения username создателя.
     * Story 4.3, AC1, AC2, AC3, AC5.
     *
     * @param sortField поле сортировки (submitted_at)
     * @param sortDirection направление сортировки (ASC или DESC)
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Flux<RouteWithCreator> pending маршруты с информацией о создателе
     */
    fun findPendingWithCreator(
        sortField: String,
        sortDirection: String,
        offset: Int,
        limit: Int
    ): Flux<RouteWithCreator>

    /**
     * Подсчитывает общее количество маршрутов со статусом pending.
     *
     * Используется для вычисления total в пагинированном ответе.
     * Story 4.3, AC1, AC3, AC5.
     *
     * @return Mono<Long> количество pending маршрутов
     */
    fun countPending(): Mono<Long>

    /**
     * Возвращает список уникальных upstream хостов с количеством маршрутов.
     *
     * Извлекает hostname:port из upstream_url (удаляет схему http:// или https://).
     * Результат отсортирован по routeCount DESC.
     * Story 7.4, AC3.
     *
     * @return Flux<UpstreamInfo> список уникальных хостов с количеством маршрутов
     */
    fun findUniqueUpstreams(): Flux<UpstreamInfo>
}
