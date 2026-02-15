package com.company.gateway.core.config

import io.micrometer.context.ContextRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configuration for Reactor Context → MDC (Mapped Diagnostic Context) propagation.
 *
 * In reactive applications, MDC doesn't work out of the box because requests
 * may switch between threads. This configuration bridges Reactor's Context
 * with SLF4J MDC to ensure correlation IDs and other diagnostic info
 * flow correctly through the reactive pipeline.
 *
 * Key features:
 * - Enables automatic context propagation for Reactor operators
 * - Registers MDC as a thread-local accessor for context bridging
 * - Ensures MDC is cleared after request completion (prevents leaks)
 * - Uses @PostConstruct for early initialization (before first request)
 * - Idempotent registration prevents issues during context reloads
 */
@Configuration
class MdcContextConfig {

    private val logger = LoggerFactory.getLogger(MdcContextConfig::class.java)

    companion object {
        private const val MDC_ACCESSOR_KEY = "mdc"
        private val initialized = AtomicBoolean(false)
    }

    @PostConstruct
    fun configureContextPropagation() {
        // Idempotent initialization - skip if already configured
        if (!initialized.compareAndSet(false, true)) {
            logger.debug("MDC context propagation already configured, skipping")
            return
        }

        // Enable automatic context propagation for Reactor
        // This allows context to flow through operators like flatMap, map, etc.
        Hooks.enableAutomaticContextPropagation()

        // Register MDC as a thread-local accessor
        // This bridges Reactor Context with SLF4J MDC
        ContextRegistry.getInstance().registerThreadLocalAccessor(
            MDC_ACCESSOR_KEY,
            { MDC.getCopyOfContextMap() ?: emptyMap() },
            { context ->
                if (context != null) {
                    MDC.setContextMap(context)
                }
            },
            { MDC.clear() }
        )

        logger.info("MDC context propagation configured successfully")
    }
}
