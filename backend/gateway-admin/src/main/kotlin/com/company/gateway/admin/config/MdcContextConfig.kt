package com.company.gateway.admin.config

import io.micrometer.context.ContextRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Конфигурация для Reactor Context → MDC (Mapped Diagnostic Context) propagation.
 *
 * В reactive приложениях MDC не работает из коробки, потому что запросы
 * могут переключаться между потоками. Эта конфигурация связывает Reactor Context
 * с SLF4J MDC для корректной передачи traceId, spanId и correlationId
 * через reactive pipeline.
 *
 * Story 14.5: Необходимо для trace-log correlation (AC5).
 *
 * Ключевые возможности:
 * - Включает автоматическую context propagation для Reactor operators
 * - Регистрирует MDC как thread-local accessor для context bridging
 * - Очищает MDC после завершения запроса (предотвращает утечки)
 * - Идемпотентная регистрация предотвращает проблемы при перезагрузке контекста
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
        // Идемпотентная инициализация — пропускаем если уже настроено
        if (!initialized.compareAndSet(false, true)) {
            logger.debug("MDC context propagation already configured, skipping")
            return
        }

        // Включаем автоматическую context propagation для Reactor
        // Это позволяет контексту проходить через operators как flatMap, map и т.д.
        Hooks.enableAutomaticContextPropagation()

        // Регистрируем MDC как thread-local accessor
        // Это связывает Reactor Context с SLF4J MDC
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
