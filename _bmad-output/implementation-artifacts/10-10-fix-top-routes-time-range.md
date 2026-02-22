# Story 10.10: Fix Top Routes Time Range Filter

Status: draft

## Story

As a **Security/Admin user**,
I want "Top Routes by Requests" widget to respect the selected time range,
so that I can analyze traffic patterns for different periods.

## Bug Report

**Воспроизведение:**
1. Перейти на страницу Metrics
2. Изменить Time Range (например, с "Last 1 hour" на "Last 24 hours")
3. Наблюдать: виджет "Top Routes by Requests" показывает те же данные

**Ожидаемое поведение:** Данные должны пересчитываться согласно выбранному time range.

## Acceptance Criteria

### AC1: Top Routes реагирует на изменение time range
**Given** пользователь на странице Metrics
**When** изменяется time range selector
**Then** виджет "Top Routes by Requests" обновляется с новыми данными

### AC2: Time range передаётся в API запрос
**Given** выбран time range "Last 24 hours"
**When** запрашиваются top routes
**Then** API получает параметры `from` и `to` соответствующие 24 часам

### AC3: Loading state при смене time range
**Given** пользователь меняет time range
**When** данные загружаются
**Then** виджет показывает loading spinner

## Analysis Summary

### Компоненты для исследования

| Компонент | Путь | Роль |
|-----------|------|------|
| MetricsPage | `features/metrics/components/MetricsPage.tsx` | Страница с time range selector |
| TopRoutesWidget | `features/metrics/components/TopRoutesWidget.tsx` (?) | Виджет top routes |
| useMetrics hook | `features/metrics/hooks/useMetrics.ts` (?) | Запрос данных |
| metricsApi | `features/metrics/api/metricsApi.ts` (?) | API client |

### Возможные причины бага

1. **Time range не передаётся в props** — TopRoutesWidget не получает текущий time range
2. **API не использует time range** — запрос всегда без параметров from/to
3. **useQuery key не включает time range** — React Query кэширует старые данные
4. **Backend игнорирует time range** — API не фильтрует по времени

## Tasks / Subtasks

- [ ] Task 1: Research current implementation
  - [ ] 1.1 Найти MetricsPage и определить как передаётся time range
  - [ ] 1.2 Найти TopRoutesWidget и проверить props
  - [ ] 1.3 Проверить API endpoint для top routes
  - [ ] 1.4 Проверить backend — поддерживает ли time range параметры

- [ ] Task 2: Fix time range propagation (AC: #1, #2)
  - [ ] 2.1 Передать time range в TopRoutesWidget props
  - [ ] 2.2 Добавить from/to параметры в API запрос
  - [ ] 2.3 Добавить time range в React Query key для корректного кэширования

- [ ] Task 3: Add loading state (AC: #3)
  - [ ] 3.1 Показывать Skeleton/Spinner при isLoading

- [ ] Task 4: Manual verification
  - [ ] 4.1 Проверить "Last 1 hour" — данные соответствуют
  - [ ] 4.2 Проверить "Last 24 hours" — данные отличаются
  - [ ] 4.3 Проверить "Last 7 days" — данные отличаются

## API Dependencies Checklist

**Проверить backend:**

| Endpoint | Параметры | Статус |
|----------|-----------|--------|
| `GET /api/v1/metrics/top-routes` (?) | `from`, `to` | ❓ Проверить |

**Если backend не поддерживает time range — нужна backend story.**

## Dev Notes

### React Query key pattern

```typescript
// Правильно — time range в key
useQuery({
  queryKey: ['top-routes', timeRange],
  queryFn: () => fetchTopRoutes(timeRange)
})

// Неправильно — кэшируется один результат
useQuery({
  queryKey: ['top-routes'],
  queryFn: () => fetchTopRoutes(timeRange)
})
```

### Time range format

```typescript
interface TimeRange {
  from: string  // ISO datetime
  to: string    // ISO datetime
}

// Или preset
type TimeRangePreset = '1h' | '24h' | '7d' | '30d'
```

## Change Log

- **2026-02-22:** Hotfix story created from SM chat session (bug report by Yury)
