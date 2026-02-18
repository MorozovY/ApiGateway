# Правила проекта ApiGateway

## Язык документации и комментариев

### Обязательные правила

1. **Комментарии в коде** — только на русском языке
2. **Названия тестов** — только на русском языке (например: `'генерирует UUID когда header отсутствует'`)
3. **Документация** — на русском языке
4. **Commit messages** — на английском языке (стандарт git)
5. **Идентификаторы кода** (переменные, функции, классы) — на английском языке (стандарт программирования)

### Примеры

**Правильно:**
```kotlin
// Генерируем correlation ID если он отсутствует в запросе
val correlationId = exchange.request.headers
    .getFirst(CORRELATION_ID_HEADER)
    ?: UUID.randomUUID().toString()
```

**Неправильно:**
```kotlin
// Generate correlation ID if missing from request
val correlationId = exchange.request.headers
    .getFirst(CORRELATION_ID_HEADER)
    ?: UUID.randomUUID().toString()
```

**Тесты — правильно:**
```kotlin
@Test
fun `генерирует UUID когда X-Correlation-ID header отсутствует`() {
    // ...
}

@Test
fun `сохраняет существующий correlation ID`() {
    // ...
}
```

**Тесты — неправильно:**
```kotlin
@Test
fun `generates UUID when X-Correlation-ID header missing`() {
    // ...
}
```

---

## Reactive Patterns (Spring WebFlux)

### Запрещено

1. **`@PostConstruct`** — использовать `@EventListener(ApplicationReadyEvent::class)` для инициализации
2. **Блокирующие вызовы** (`.block()`, `Thread.sleep()`) — использовать reactive chains
3. **`ThreadLocal`** без context propagation — использовать Reactor Context + MDC bridging
4. **`synchronized` блоки** — использовать `AtomicReference` для thread-safe state

### Обязательно

1. **RFC 7807** для всех error responses
2. **Correlation ID** во всех логах и error responses
3. **snake_case** для колонок PostgreSQL
4. **Testcontainers** для integration tests

---

## Code Review Checklist

Перед отправкой на review убедиться:

- [ ] Комментарии на русском языке
- [ ] Названия тестов на русском языке
- [ ] Нет `@PostConstruct` в reactive контексте
- [ ] Нет блокирующих вызовов
- [ ] Все error responses в RFC 7807 формате
- [ ] Тесты покрывают все AC
- [ ] Нет placeholder тестов

---

## Git-процесс

### Коммит

Просьба "закоммить" означает:
1. `git add` нужных файлов
2. `git commit` с message на английском языке
3. `git push` в GitHub

Оба шага (локальный коммит и push) выполняются всегда вместе, если явно не указано иное.

---

## Конвенции именования

| Область | Конвенция | Пример |
|---------|-----------|--------|
| Классы Kotlin | PascalCase | `CorrelationIdFilter` |
| Функции Kotlin | camelCase | `generateCorrelationId()` |
| Константы | SCREAMING_SNAKE_CASE | `CORRELATION_ID_HEADER` |
| Колонки PostgreSQL | snake_case | `created_at`, `upstream_url` |
| Таблицы PostgreSQL | snake_case (plural) | `routes`, `users` |
| HTTP Headers | X-Header-Name | `X-Correlation-ID` |

---

*Последнее обновление: 2026-02-18 (Epic 4 Retrospective)*
