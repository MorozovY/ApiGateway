# Story 16.8: Auto-refresh метрик с индикатором

Status: done

## Story

As a **DevOps Engineer**,
I want metrics to auto-refresh periodically,
so that I can monitor the system without manual page refresh.

## Acceptance Criteria

### AC1: Toggle и селектор интервала
**Given** страница Metrics
**When** пользователь открывает страницу
**Then** отображается toggle "Auto-refresh" (по умолчанию выключен)
**And** рядом селектор интервала: 15s, 30s, 60s

### AC2: Автоматическое обновление
**Given** Auto-refresh включён с интервалом 30s
**When** прошло 30 секунд
**Then** данные метрик обновляются автоматически
**And** отображается индикатор "Обновлено только что" / "Обновлено 15 сек назад"

### AC3: Page Visibility API
**Given** Auto-refresh включён
**When** вкладка браузера неактивна
**Then** refresh приостанавливается (Page Visibility API)
**And** возобновляется при возврате на вкладку

### AC4: Сброс при смене Time Range
**Given** Auto-refresh включён
**When** пользователь меняет Time Range
**Then** данные немедленно обновляются
**And** countdown сбрасывается

### AC5: Сохранение настроек
**Given** настройки Auto-refresh
**When** пользователь уходит со страницы и возвращается
**Then** настройки сохранены (localStorage)

## Tasks / Subtasks

- [x] Task 1: Создать компонент AutoRefreshControl (AC1)
  - [x] 1.1 Создать `AutoRefreshControl.tsx` в `src/features/metrics/components/`
  - [x] 1.2 Switch toggle для включения/выключения
  - [x] 1.3 Select для интервала (15s, 30s, 60s)
  - [x] 1.4 Unit тесты

- [x] Task 2: Индикатор времени обновления (AC2)
  - [x] 2.1 Создать `LastUpdatedIndicator.tsx` или встроить в AutoRefreshControl
  - [x] 2.2 Показывать "Обновлено только что" / "X сек назад" / "X мин назад"
  - [x] 2.3 Обновлять каждую секунду когда auto-refresh включён

- [x] Task 3: Интеграция с React Query (AC2, AC3, AC4)
  - [x] 3.1 Создать hook `useAutoRefresh` для управления refetchInterval
  - [x] 3.2 Интегрировать Page Visibility API
  - [x] 3.3 Обновить useMetricsSummary и useTopRoutes для dynamic interval

- [x] Task 4: localStorage persistence (AC5)
  - [x] 4.1 Сохранять enabled и interval в localStorage
  - [x] 4.2 Восстанавливать при загрузке страницы

- [x] Task 5: Интеграция в MetricsPage
  - [x] 5.1 Добавить AutoRefreshControl в header страницы
  - [x] 5.2 Сбрасывать countdown при смене Time Range (AC4)
  - [x] 5.3 Обновить тесты

## Dev Notes

### Архитектурные паттерны

**Текущая реализация:**
- `METRICS_REFRESH_INTERVAL = 10000` (10 сек) — фиксированный
- `refetchInterval` в useQuery — работает всегда
- Нет UI контроля

**Новая реализация:**
- Динамический `refetchInterval` через state
- Toggle для включения/выключения
- Selector для интервала (15s, 30s, 60s)
- Page Visibility API для паузы

**React Query refetchInterval:**
```typescript
const { data } = useQuery({
  queryKey: ['metrics'],
  queryFn: fetchMetrics,
  refetchInterval: autoRefreshEnabled ? intervalMs : false,
  // React Query автоматически обрабатывает Page Visibility
  refetchIntervalInBackground: false, // по умолчанию false — паузит при неактивной вкладке
})
```

**Page Visibility API (дополнительно):**
```typescript
// React Query уже обрабатывает это через refetchIntervalInBackground: false
// Но можно добавить индикатор паузы:
const isPageVisible = usePageVisibility()
```

### Project Structure Notes

**Новые файлы:**
```
frontend/admin-ui/src/features/metrics/
├── components/
│   ├── AutoRefreshControl.tsx       # toggle + interval selector
│   └── AutoRefreshControl.test.tsx
├── hooks/
│   └── useAutoRefresh.ts            # управление состоянием auto-refresh
└── config/
    └── metricsConfig.ts             # обновить интервалы
```

**Файлы для изменения:**
```
frontend/admin-ui/src/features/metrics/
├── components/MetricsPage.tsx       # интеграция AutoRefreshControl
└── hooks/useMetrics.ts              # dynamic refetchInterval
```

### Technical Requirements

**AutoRefreshControl Props:**
```typescript
interface AutoRefreshControlProps {
  enabled: boolean
  interval: number  // ms
  lastUpdated: Date | null
  onEnabledChange: (enabled: boolean) => void
  onIntervalChange: (interval: number) => void
}
```

**useAutoRefresh Hook:**
```typescript
interface UseAutoRefreshReturn {
  enabled: boolean
  interval: number
  lastUpdated: Date | null
  setEnabled: (enabled: boolean) => void
  setInterval: (interval: number) => void
  resetTimer: () => void
}

function useAutoRefresh(storageKey: string = 'metrics-auto-refresh') {
  // Состояние из localStorage
  const [enabled, setEnabled] = useState(() => {
    const saved = localStorage.getItem(storageKey)
    return saved ? JSON.parse(saved).enabled : false
  })

  const [interval, setInterval] = useState(() => {
    const saved = localStorage.getItem(storageKey)
    return saved ? JSON.parse(saved).interval : 30000
  })

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  // Сохранение в localStorage
  useEffect(() => {
    localStorage.setItem(storageKey, JSON.stringify({ enabled, interval }))
  }, [enabled, interval, storageKey])

  return { enabled, interval, lastUpdated, setEnabled, setInterval, ... }
}
```

**Interval Options:**
```typescript
const AUTO_REFRESH_INTERVALS = [
  { label: '15 сек', value: 15000 },
  { label: '30 сек', value: 30000 },
  { label: '60 сек', value: 60000 },
]
```

**Last Updated Display:**
```typescript
function formatLastUpdated(date: Date | null): string {
  if (!date) return ''
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  if (seconds < 5) return 'Обновлено только что'
  if (seconds < 60) return `Обновлено ${seconds} сек назад`
  const minutes = Math.floor(seconds / 60)
  return `Обновлено ${minutes} мин назад`
}
```

**MetricsPage Integration:**
```tsx
function MetricsPage() {
  const autoRefresh = useAutoRefresh()

  const { data: summary, dataUpdatedAt } = useMetricsSummary(period, {
    refetchInterval: autoRefresh.enabled ? autoRefresh.interval : false,
  })

  // При смене period — сброс таймера
  useEffect(() => {
    autoRefresh.resetTimer()
  }, [period])

  return (
    <Card>
      <Space>
        <Segmented ... />  {/* Time Range */}
        <AutoRefreshControl
          enabled={autoRefresh.enabled}
          interval={autoRefresh.interval}
          lastUpdated={dataUpdatedAt ? new Date(dataUpdatedAt) : null}
          onEnabledChange={autoRefresh.setEnabled}
          onIntervalChange={autoRefresh.setInterval}
        />
      </Space>
    </Card>
  )
}
```

**LocalStorage Key:**
```typescript
const STORAGE_KEY = 'metrics-auto-refresh'
// Format: { enabled: boolean, interval: number }
```

### UI Design

**Layout:**
```
[Time Range: 5m | 15m | 1h | 6h | 24h]  [🔄 Auto-refresh: [OFF] [30s ▾]]  [Обновлено 15 сек назад]
```

**Components:**
- Switch (Ant Design) для toggle
- Select (Ant Design) для интервала
- Text для "Обновлено X сек назад"

### Testing Requirements

**Unit тесты AutoRefreshControl:**
```typescript
describe('AutoRefreshControl', () => {
  it('отображает toggle в выключенном состоянии по умолчанию', () => { ... })
  it('показывает selector интервала когда enabled', () => { ... })
  it('вызывает onEnabledChange при клике на toggle', () => { ... })
  it('вызывает onIntervalChange при выборе интервала', () => { ... })
  it('отображает lastUpdated в правильном формате', () => { ... })
})
```

**Unit тесты useAutoRefresh:**
```typescript
describe('useAutoRefresh', () => {
  it('загружает состояние из localStorage', () => { ... })
  it('сохраняет состояние в localStorage при изменении', () => { ... })
  it('возвращает default values если localStorage пустой', () => { ... })
})
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 16.8]
- [Source: frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx — текущая страница]
- [Source: frontend/admin-ui/src/features/metrics/hooks/useMetrics.ts — текущие hooks]
- [Source: frontend/admin-ui/src/features/metrics/config/metricsConfig.ts — конфигурация]
- [React Query refetchInterval](https://tanstack.com/query/latest/docs/framework/react/guides/window-focus-refetching)
- [Page Visibility API](https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API)

### Migration Notes

**Изменение поведения:**
- Текущее: auto-refresh ВСЕГДА включён (10 сек)
- Новое: auto-refresh ВЫКЛЮЧЕН по умолчанию, пользователь включает вручную

**Backward Compatibility:**
- Существующий `METRICS_REFRESH_INTERVAL` можно оставить как fallback
- Новый hook перезаписывает поведение

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Исправлены unit тесты для Ant Design Switch и Select (onChange вызывается с двумя аргументами)
- Добавлен localStorage mock для тестов useAutoRefresh hook

### Completion Notes List

1. **Task 1**: Создан `AutoRefreshControl.tsx` — компонент с Switch toggle и Select для интервала (15s/30s/60s). 18 unit тестов.
2. **Task 2**: Индикатор "Обновлено X сек назад" встроен в AutoRefreshControl с функцией `formatLastUpdated()`. Обновляется каждую секунду через setInterval с cleanup (PA-06). Корректное склонение для русского языка (секунду/секунды/секунд).
3. **Task 3**: Создан `useAutoRefresh` hook с полной интеграцией в React Query. Page Visibility API реализован через `usePageVisibility()` hook с UI индикатором "Приостановлено" (AC3 полностью).
4. **Task 4**: localStorage persistence реализована — настройки saved/loaded автоматически с key `metrics-auto-refresh`.
5. **Task 5**: Интеграция в MetricsPage завершена — auto-refresh control рядом с Time Range selector, countdown сбрасывается при смене периода.

**Code Review Fixes:**
- **HIGH-1**: Добавлен UI индикатор "Приостановлено" когда вкладка неактивна (AC3)
- **HIGH-2**: Добавлены 15 тестов для Page Visibility API
- **MED-1**: Убран `eslint-disable`, исправлены зависимости useEffect
- **MED-2**: Корректное склонение числительных (1 секунду, 2 секунды, 5 секунд)
- **MED-3**: Interval selector теперь всегда виден, но disabled когда auto-refresh выключен

### Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-03-04 | SM | Story created — ready for dev |
| 2026-03-04 | Dev Agent | Implementation complete — all 5 tasks done, 837 tests pass |
| 2026-03-04 | Code Review | Fixed HIGH/MEDIUM issues: AC3 Page Visibility indicator, pluralization, selector UX, 852 tests pass |

### File List

**New files:**
- `frontend/admin-ui/src/features/metrics/components/AutoRefreshControl.tsx`
- `frontend/admin-ui/src/features/metrics/components/AutoRefreshControl.test.tsx`
- `frontend/admin-ui/src/features/metrics/hooks/useAutoRefresh.ts`
- `frontend/admin-ui/src/features/metrics/hooks/useAutoRefresh.test.ts`

**Modified files:**
- `frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx`
- `frontend/admin-ui/src/features/metrics/components/MetricsPage.test.tsx`
- `frontend/admin-ui/src/features/metrics/hooks/useMetrics.ts`
- `frontend/admin-ui/src/features/metrics/index.ts`
