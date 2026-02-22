# Story 10.10: Fix Top Routes Time Range Filter

Status: ready-for-dev

## Story

As a **Security/Admin user**,
I want "Top Routes by Requests" widget to respect the selected time range,
so that I can analyze traffic patterns for different periods.

## Bug Report

**Severity:** MEDIUM

**Воспроизведение:**
1. Перейти на страницу Metrics
2. Изменить Time Range (например, с "5m" на "24h")
3. Наблюдать: виджет "Top Routes by Requests" показывает те же данные

**Root Cause (подтверждён):**
Баг проходит через весь стек:

| Компонент | Текущее поведение | Проблема |
|-----------|-------------------|----------|
| **MetricsPage** | `useTopRoutes('requests', 10)` | Period не передаётся в hook |
| **useTopRoutes** | Принимает только `sortBy, limit` | Нет параметра period |
| **Query Key** | `['metrics', 'top-routes', sortBy, limit]` | Period не в ключе → кэширует один результат |
| **metricsApi** | `getTopRoutes(sortBy, limit)` | Не передаёт period в API |
| **Backend API** | `GET /top-routes?by=X&limit=Y` | Не принимает period |
| **MetricsService** | Hardcoded `FIVE_MINUTES` | Игнорирует time range |

## Acceptance Criteria

### AC1: Top Routes реагирует на изменение time range
**Given** пользователь на странице Metrics
**When** изменяется time range selector
**Then** виджет "Top Routes by Requests" обновляется с новыми данными

### AC2: Time range передаётся в API запрос
**Given** выбран time range "24h"
**When** запрашиваются top routes
**Then** API получает параметр `period=24h`

### AC3: Loading state при смене time range
**Given** пользователь меняет time range
**When** данные загружаются
**Then** виджет показывает loading spinner

**Note:** AC3 уже работает — `topRoutesLoading` передаётся в TopRoutesTable.

## Analysis Summary

### Текущая реализация

**MetricsPage.tsx (строки 35, 47, 53):**
```typescript
const [period, setPeriod] = useState<MetricsPeriod>('5m')

// Summary ПОЛУЧАЕТ period ✅
const { data: summary } = useMetricsSummary(period)

// Top Routes НЕ ПОЛУЧАЕТ period ❌
const { data: topRoutes } = useTopRoutes('requests', 10)
```

**useMetrics.ts (строки 37-44):**
```typescript
// НЕТ параметра period
export function useTopRoutes(sortBy: MetricsSortBy = 'requests', limit: number = 10) {
  return useQuery({
    queryKey: QUERY_KEYS.topRoutes(sortBy, limit),  // НЕТ period в ключе
    queryFn: () => metricsApi.getTopRoutes(sortBy, limit),  // НЕТ period
  })
}
```

**Backend MetricsController.kt (строки 91-116):**
```kotlin
@GetMapping("/top-routes")
fun getTopRoutes(
  @RequestParam(defaultValue = "requests") by: String,
  @RequestParam(defaultValue = "10") limit: Int
  // НЕТ period параметра
): Mono<List<TopRouteDto>>
```

**MetricsService.kt:**
- Использует hardcoded `DEFAULT_TOP_ROUTES_PERIOD = MetricsPeriod.FIVE_MINUTES`
- PromQL queries не включают time window

## Tasks / Subtasks

- [ ] Task 1: Frontend — Update metricsApi (AC: #2)
  - [ ] 1.1 Добавить `period: MetricsPeriod` параметр в `getTopRoutes()`
  - [ ] 1.2 Передать period в query params

- [ ] Task 2: Frontend — Update useMetrics hook (AC: #1, #2)
  - [ ] 2.1 Добавить `period` параметр в `useTopRoutes()`
  - [ ] 2.2 Добавить `period` в QUERY_KEYS.topRoutes
  - [ ] 2.3 Передать period в metricsApi.getTopRoutes

- [ ] Task 3: Frontend — Update MetricsPage (AC: #1)
  - [ ] 3.1 Передать `period` в `useTopRoutes('requests', 10, period)`

- [ ] Task 4: Backend — Update MetricsController (AC: #2)
  - [ ] 4.1 Добавить `@RequestParam period: String` с default "5m"
  - [ ] 4.2 Validate period и конвертировать в MetricsPeriod
  - [ ] 4.3 Передать period в metricsService.getTopRoutes

- [ ] Task 5: Backend — Update MetricsService (AC: #2)
  - [ ] 5.1 Добавить `period: MetricsPeriod` параметр в `getTopRoutes()`
  - [ ] 5.2 Передать period в PromQL builder методы

- [ ] Task 6: Backend — Update PromQLBuilder (AC: #2)
  - [ ] 6.1 Добавить `period` параметр в `totalRequestsByRouteIds()`
  - [ ] 6.2 Добавить `period` параметр в `avgLatencyByRouteIds()`
  - [ ] 6.3 Добавить `period` параметр в `totalErrorsByRouteIds()`
  - [ ] 6.4 Использовать `period.value` в PromQL time window

- [ ] Task 7: Tests
  - [ ] 7.1 Frontend: тест `передаёт период в API запрос`
  - [ ] 7.2 Frontend: тест `обновляет кэш при смене периода`
  - [ ] 7.3 Backend: тест endpoint с period параметром

- [ ] Task 8: Manual verification
  - [ ] 8.1 Проверить "5m" — данные за 5 минут
  - [ ] 8.2 Проверить "1h" — данные отличаются
  - [ ] 8.3 Проверить "24h" — данные отличаются
  - [ ] 8.4 Проверить loading spinner при смене period

## API Dependencies Checklist

**Backend изменения ТРЕБУЮТСЯ:**

| Endpoint | Текущие параметры | Новые параметры | Статус |
|----------|-------------------|-----------------|--------|
| `GET /api/v1/metrics/top-routes` | `by`, `limit` | `by`, `limit`, `period` | Требуется добавить |

## Dev Notes

### Frontend Changes

#### 1. metricsApi.ts

**Путь:** `frontend/admin-ui/src/features/metrics/api/metricsApi.ts`

```typescript
// Текущее (строки 45-53):
export async function getTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10
): Promise<TopRoute[]> {
  const { data } = await axios.get<TopRoute[]>(`${BASE_URL}/top-routes`, {
    params: { by: sortBy, limit },
  })
  return data
}

// Новое:
export async function getTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10,
  period: MetricsPeriod = '5m'  // ДОБАВИТЬ
): Promise<TopRoute[]> {
  const { data } = await axios.get<TopRoute[]>(`${BASE_URL}/top-routes`, {
    params: { by: sortBy, limit, period },  // ДОБАВИТЬ period
  })
  return data
}
```

#### 2. useMetrics.ts

**Путь:** `frontend/admin-ui/src/features/metrics/hooks/useMetrics.ts`

```typescript
// Query Keys (строки 10-16):
const QUERY_KEYS = {
  summary: (period: MetricsPeriod) => ['metrics', 'summary', period] as const,
  topRoutes: (sortBy: MetricsSortBy, limit: number, period: MetricsPeriod) =>  // ДОБАВИТЬ period
    ['metrics', 'top-routes', sortBy, limit, period] as const,
  routeMetrics: (routeId: string, period: MetricsPeriod) =>
    ['metrics', 'routes', routeId, period] as const,
}

// Hook (строки 37-44):
export function useTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10,
  period: MetricsPeriod = '5m'  // ДОБАВИТЬ
) {
  return useQuery({
    queryKey: QUERY_KEYS.topRoutes(sortBy, limit, period),  // ДОБАВИТЬ period
    queryFn: () => metricsApi.getTopRoutes(sortBy, limit, period),  // ДОБАВИТЬ period
    refetchInterval: METRICS_REFRESH_INTERVAL,
    staleTime: METRICS_STALE_TIME,
  })
}
```

#### 3. MetricsPage.tsx

**Путь:** `frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx`

```typescript
// Строка 53:
// Текущее:
const { data: topRoutes, ... } = useTopRoutes('requests', 10)

// Новое:
const { data: topRoutes, ... } = useTopRoutes('requests', 10, period)  // ДОБАВИТЬ period
```

### Backend Changes

#### 4. MetricsController.kt

**Путь:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/MetricsController.kt`

```kotlin
// Текущее (строки 91-116):
@GetMapping("/top-routes")
fun getTopRoutes(
  @RequestParam(defaultValue = "requests") by: String,
  @RequestParam(defaultValue = "10") limit: Int
): Mono<List<TopRouteDto>>

// Новое:
@GetMapping("/top-routes")
fun getTopRoutes(
  @RequestParam(defaultValue = "requests") by: String,
  @RequestParam(defaultValue = "10") limit: Int,
  @RequestParam(defaultValue = "5m") period: String  // ДОБАВИТЬ
): Mono<List<TopRouteDto>> {
  val metricsPeriod = MetricsPeriod.fromValue(period)
    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period: $period")

  // ... existing code ...
  metricsService.getTopRoutes(sortBy, validLimit, ownerId, metricsPeriod)  // ДОБАВИТЬ period
}
```

#### 5. MetricsService.kt

**Путь:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/MetricsService.kt`

```kotlin
// Добавить period параметр:
fun getTopRoutes(
  sortBy: MetricsSortBy,
  limit: Int,
  ownerId: UUID? = null,
  period: MetricsPeriod = MetricsPeriod.FIVE_MINUTES  // ДОБАВИТЬ
): Mono<List<TopRouteDto>> {
  // ...
  val query = when (sortBy) {
    MetricsSortBy.REQUESTS -> PromQLBuilder.totalRequestsByRouteIds(routeIds, period)
    MetricsSortBy.LATENCY -> PromQLBuilder.avgLatencyByRouteIds(routeIds, period)
    MetricsSortBy.ERRORS -> PromQLBuilder.totalErrorsByRouteIds(routeIds, period)
  }
  // ...
}
```

#### 6. PromQLBuilder.kt

**Путь:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/client/PromQLBuilder.kt`

```kotlin
// Текущее:
fun totalRequestsByRouteIds(routeIds: List<String>): String

// Новое:
fun totalRequestsByRouteIds(routeIds: List<String>, period: MetricsPeriod): String {
  if (routeIds.isEmpty()) return ""
  val routeIdRegex = routeIds.joinToString("|")
  // Использовать period.value в time window
  return "sum(increase(${METRIC_REQUESTS_TOTAL}{$LABEL_ROUTE_ID=~\"$routeIdRegex\"}[${period.value}])) by ($LABEL_ROUTE_ID)"
}

// Аналогично для avgLatencyByRouteIds и totalErrorsByRouteIds
```

### Files to Modify (8 файлов)

| # | Layer | File | Change |
|---|-------|------|--------|
| 1 | Frontend API | `features/metrics/api/metricsApi.ts` | Add period param |
| 2 | Frontend Hook | `features/metrics/hooks/useMetrics.ts` | Add period to hook + query key |
| 3 | Frontend Component | `features/metrics/components/MetricsPage.tsx` | Pass period to useTopRoutes |
| 4 | Backend Controller | `controller/MetricsController.kt` | Add @RequestParam period |
| 5 | Backend Service | `service/MetricsService.kt` | Add period param |
| 6 | Backend PromQL | `client/PromQLBuilder.kt` | Add period to 3 methods |
| 7 | Frontend Test | `hooks/useMetrics.test.tsx` | Add period tests |
| 8 | Backend Test | `MetricsControllerIntegrationTest.kt` | Add period test |

### Backward Compatibility

Все новые параметры имеют default values (`'5m'`):
- Существующие API вызовы без `period` будут работать с defaults
- No breaking changes

### References

- [Source: MetricsPage.tsx:35,47,53] — period state и hooks
- [Source: useMetrics.ts:37-44] — useTopRoutes hook
- [Source: metricsApi.ts:45-53] — getTopRoutes API
- [Source: MetricsController.kt:91-116] — backend endpoint
- [Source: MetricsService.kt] — service layer
- [Source: PromQLBuilder.kt] — PromQL queries

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

## Change Log

- **2026-02-22:** Story created from SM chat session (bug report by Yury)
- **2026-02-22:** Full stack analysis completed, root cause confirmed, status → ready-for-dev
