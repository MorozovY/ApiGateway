package com.company.gateway.admin.service

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RouteStatus
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Сервис для проверки владения ресурсами.
 *
 * Используется для реализации правил:
 * - Developer может модифицировать только свои маршруты
 * - Developer может удалять только draft маршруты
 */
@Service
class OwnershipService(
    private val routeRepository: RouteRepository
) {

    /**
     * Проверяет, является ли пользователь владельцем маршрута.
     *
     * Примечание: если Route.createdBy равен null (маршрут без владельца),
     * метод вернёт false — такой маршрут не принадлежит никому.
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя
     * @return Mono<Boolean> - true если пользователь владеет маршрутом
     */
    fun isOwner(routeId: UUID, userId: UUID): Mono<Boolean> {
        return routeRepository.findById(routeId)
            .map { route -> route.createdBy == userId }
            .defaultIfEmpty(false)
    }

    /**
     * Проверяет, может ли пользователь модифицировать маршрут.
     *
     * Developer может модифицировать маршрут только если он его владелец.
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя
     * @return Mono<Boolean> - true если пользователь может модифицировать маршрут
     */
    fun canModifyRoute(routeId: UUID, userId: UUID): Mono<Boolean> {
        return isOwner(routeId, userId)
    }

    /**
     * Проверяет, может ли пользователь удалить маршрут.
     *
     * Developer может удалить маршрут только если:
     * - Он является владельцем маршрута
     * - Маршрут находится в статусе DRAFT
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя
     * @return Mono с результатом проверки
     */
    fun canDeleteRoute(routeId: UUID, userId: UUID): Mono<DeleteCheckResult> {
        return routeRepository.findById(routeId)
            .map { route ->
                when {
                    route.createdBy != userId -> DeleteCheckResult.NotOwner
                    route.status != RouteStatus.DRAFT -> DeleteCheckResult.NotDraft(route.status)
                    else -> DeleteCheckResult.Allowed
                }
            }
            .defaultIfEmpty(DeleteCheckResult.NotFound)
    }
}

/**
 * Результат проверки возможности удаления маршрута.
 */
sealed class DeleteCheckResult {
    /** Удаление разрешено */
    data object Allowed : DeleteCheckResult()

    /** Маршрут не найден */
    data object NotFound : DeleteCheckResult()

    /** Пользователь не является владельцем маршрута */
    data object NotOwner : DeleteCheckResult()

    /** Маршрут не в статусе DRAFT */
    data class NotDraft(val currentStatus: RouteStatus) : DeleteCheckResult()
}
