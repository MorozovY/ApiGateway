package com.company.gateway.admin.controller

import com.company.gateway.admin.security.RequireRole
import com.company.gateway.common.model.Role
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Контроллер для работы с audit логами.
 *
 * Доступен только для SECURITY и ADMIN ролей.
 * Placeholder реализация для тестирования RBAC.
 * Полная реализация будет добавлена в Epic 7.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequireRole(Role.SECURITY)
class AuditController {

    /**
     * Получение списка audit log записей.
     *
     * Доступно только SECURITY и ADMIN ролям.
     */
    @GetMapping
    fun listAuditLogs(): Mono<ResponseEntity<Map<String, Any>>> {
        // Placeholder — вернуть пустой список
        return Mono.just(
            ResponseEntity.ok(
                mapOf(
                    "items" to emptyList<Any>(),
                    "total" to 0
                )
            )
        )
    }
}
