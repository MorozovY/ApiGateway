package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Per-consumer rate limit policy.
 *
 * Ограничивает количество запросов для конкретного consumer (Keycloak клиента).
 * Consumer rate limit применяется глобально ко всем маршрутам — один consumer
 * имеет единый лимит на ВСЕ API.
 *
 * При наличии обоих лимитов (per-route и per-consumer) применяется
 * более строгий (меньший из двух).
 *
 * @property id уникальный идентификатор записи (UUID)
 * @property consumerId идентификатор consumer (Keycloak client_id / azp claim)
 * @property requestsPerSecond лимит запросов в секунду (> 0)
 * @property burstSize максимальный burst (пик запросов) (> 0)
 * @property createdAt дата и время создания
 * @property updatedAt дата и время последнего обновления
 * @property createdBy ID пользователя (Admin), создавшего лимит
 */
@Table("consumer_rate_limits")
data class ConsumerRateLimit(
    @Id
    val id: UUID? = null,

    /** Consumer ID (Keycloak client_id / azp claim) */
    @Column("consumer_id")
    val consumerId: String,

    /** Лимит запросов в секунду */
    @Column("requests_per_second")
    val requestsPerSecond: Int,

    /** Максимальный burst (пик запросов) */
    @Column("burst_size")
    val burstSize: Int,

    /** Время создания */
    @Column("created_at")
    val createdAt: Instant? = null,

    /** Время последнего обновления */
    @Column("updated_at")
    val updatedAt: Instant? = null,

    /** ID пользователя, создавшего лимит */
    @Column("created_by")
    val createdBy: UUID? = null
)
