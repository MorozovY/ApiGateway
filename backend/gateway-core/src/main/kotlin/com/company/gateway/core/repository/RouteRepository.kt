package com.company.gateway.core.repository

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.UUID

@Repository
interface RouteRepository : R2dbcRepository<Route, UUID> {
    fun findByStatus(status: RouteStatus): Flux<Route>
}
