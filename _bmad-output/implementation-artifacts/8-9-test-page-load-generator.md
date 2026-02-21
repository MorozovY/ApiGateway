# Story 8.9: Страница Test с генератором нагрузки

Status: done

## Story

As a **DevOps Engineer**,
I want a Test page with load generator,
so that I can simulate traffic and verify monitoring works.

## Acceptance Criteria

**AC1 — Новый пункт меню Test:**

**Given** пользователь аутентифицирован
**When** пользователь смотрит на sidebar
**Then** пункт меню "Test" отображается с иконкой experiment/flask
**And** пункт меню ведёт на `/test`

**AC2 — Страница Test с контролами генератора:**

**Given** пользователь переходит на `/test`
**When** страница загружена
**Then** отображаются контролы генератора нагрузки:
- Target route selector (dropdown опубликованных маршрутов)
- Requests per second (number input, 1-100)
- Duration (секунды, или "until stopped")
- Start/Stop кнопка

**AC3 — Запуск генерации нагрузки:**

**Given** пользователь выбрал маршрут и настроил параметры
**When** пользователь нажимает "Start"
**Then** запросы отправляются на выбранный маршрут через Gateway
**And** индикатор прогресса показывает: отправленные запросы, success/error count
**And** кнопка Stop становится активной

**AC4 — Остановка генерации:**

**Given** генерация нагрузки запущена
**When** пользователь нажимает "Stop"
**Then** генерация останавливается
**And** показывается summary: total requests, duration, success rate

**AC5 — Интеграция с Metrics:**

**Given** генерация нагрузки запущена
**When** пользователь переходит на `/metrics`
**Then** метрики показывают увеличение трафика (RPS increase)

## Tasks / Subtasks

- [x] Task 1: Добавить пункт меню Test в Sidebar (AC1)
  - [x] Subtask 1.1: Добавить иконку ExperimentOutlined из @ant-design/icons
  - [x] Subtask 1.2: Добавить пункт `/test` в baseMenuItems
  - [x] Subtask 1.3: Добавить route в App.tsx

- [x] Task 2: Создать структуру feature test (AC2)
  - [x] Subtask 2.1: Создать директорию `frontend/admin-ui/src/features/test/`
  - [x] Subtask 2.2: Создать `components/TestPage.tsx`
  - [x] Subtask 2.3: Создать `components/LoadGeneratorForm.tsx`
  - [x] Subtask 2.4: Создать `hooks/useLoadGenerator.ts`
  - [x] Subtask 2.5: Создать `types/loadGenerator.types.ts`
  - [x] Subtask 2.6: Создать index.ts для экспортов

- [x] Task 3: Реализовать UI формы генератора (AC2)
  - [x] Subtask 3.1: Target route dropdown (fetch published routes)
  - [x] Subtask 3.2: Requests per second input (InputNumber, min 1, max 100)
  - [x] Subtask 3.3: Duration selector (InputNumber + Radio: "Fixed" / "Until stopped")
  - [x] Subtask 3.4: Start/Stop кнопка с состоянием

- [x] Task 4: Реализовать логику генерации запросов (AC3, AC4)
  - [x] Subtask 4.1: Создать функцию генерации запросов с setInterval
  - [x] Subtask 4.2: Реализовать счётчики sent/success/error
  - [x] Subtask 4.3: Реализовать progress indicator (Card с Statistic)
  - [x] Subtask 4.4: Реализовать автоостановку при достижении duration

- [x] Task 5: Реализовать Summary после остановки (AC4)
  - [x] Subtask 5.1: Показать total requests sent
  - [x] Subtask 5.2: Показать actual duration
  - [x] Subtask 5.3: Показать success rate (%)
  - [x] Subtask 5.4: Показать average response time (если доступно)

- [x] Task 6: Добавить тесты
  - [x] Subtask 6.1: Unit тест для LoadGeneratorForm
  - [x] Subtask 6.2: Unit тест для useLoadGenerator hook
  - [x] Subtask 6.3: Integration тест для TestPage
  - [x] Subtask 6.4: Unit тест для LoadGeneratorProgress
  - [x] Subtask 6.5: Unit тест для LoadGeneratorSummary
  - [x] Subtask 6.6: Все тесты проходят (436/436 passed)

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/routes?status=published` | GET | `status=published` | ✅ Существует |
| Gateway proxy (through gateway-core:8080) | GET/POST | По выбранному маршруту | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Endpoint для получения published routes существует
- [x] Gateway-core доступен для отправки тестовых запросов
- [x] Нет необходимости в новых backend endpoints — генерация происходит на frontend

**Примечание:** Эта story — чисто frontend. Генерация нагрузки выполняется через JavaScript на клиенте, отправляя HTTP запросы напрямую в gateway-core.

## Dev Notes

### Архитектура решения

**Frontend-only Load Generator:**
```
Browser (TestPage) --HTTP--> gateway-core:8080 --proxy--> Upstream Service
                              │
                              └─> Prometheus metrics
```

Генератор нагрузки работает целиком на frontend:
1. Пользователь выбирает published route
2. JavaScript setInterval отправляет запросы с заданной частотой
3. Счётчики обновляются в реальном времени
4. Метрики собираются gateway-core и видны на /metrics

### Ограничения браузера

**Важно понимать ограничения:**
- Браузер имеет лимит concurrent connections (~6-8 на домен)
- Высокие RPS (>50) могут быть недостижимы из одного браузера
- Для серьёзного нагрузочного тестирования нужны инструменты типа k6, wrk, locust

**Для MVP этого достаточно:**
- Демонстрация работы метрик
- Проверка rate limiting
- Небольшая нагрузка для визуализации в Grafana

### Структура файлов

```
frontend/admin-ui/src/features/test/
├── components/
│   ├── TestPage.tsx              # Главная страница
│   ├── TestPage.test.tsx         # Тесты страницы
│   ├── LoadGeneratorForm.tsx     # Форма настроек генератора
│   ├── LoadGeneratorForm.test.tsx
│   ├── LoadGeneratorProgress.tsx # Индикатор прогресса
│   └── LoadGeneratorSummary.tsx  # Summary после остановки
├── hooks/
│   ├── useLoadGenerator.ts       # Логика генерации
│   └── useLoadGenerator.test.tsx
├── types/
│   └── loadGenerator.types.ts    # TypeScript типы
└── index.ts                      # Экспорты
```

### Типы данных

```typescript
// types/loadGenerator.types.ts

export interface LoadGeneratorConfig {
  routeId: string
  routePath: string
  requestsPerSecond: number
  durationSeconds: number | null  // null = until stopped
}

export interface LoadGeneratorState {
  status: 'idle' | 'running' | 'stopped'
  startTime: number | null
  sentCount: number
  successCount: number
  errorCount: number
  lastError: string | null
  averageResponseTime: number | null
}

export interface LoadGeneratorSummary {
  totalRequests: number
  successCount: number
  errorCount: number
  durationMs: number
  successRate: number
  averageResponseTime: number | null
}
```

### Hook useLoadGenerator

```typescript
// hooks/useLoadGenerator.ts

interface UseLoadGeneratorReturn {
  state: LoadGeneratorState
  start: (config: LoadGeneratorConfig) => void
  stop: () => void
  reset: () => void
  summary: LoadGeneratorSummary | null
}

export function useLoadGenerator(): UseLoadGeneratorReturn {
  const [state, setState] = useState<LoadGeneratorState>(initialState)
  const intervalRef = useRef<number | null>(null)
  const responseTimes = useRef<number[]>([])

  const start = useCallback((config: LoadGeneratorConfig) => {
    // Вычисляем интервал между запросами
    const intervalMs = 1000 / config.requestsPerSecond

    setState(prev => ({ ...prev, status: 'running', startTime: Date.now() }))

    intervalRef.current = window.setInterval(async () => {
      const startTime = performance.now()
      try {
        // Отправляем запрос через gateway-core
        await fetch(`http://localhost:8080${config.routePath}`, {
          method: 'GET',
          mode: 'cors',
        })
        const elapsed = performance.now() - startTime
        responseTimes.current.push(elapsed)
        setState(prev => ({
          ...prev,
          sentCount: prev.sentCount + 1,
          successCount: prev.successCount + 1,
          averageResponseTime: calculateAverage(responseTimes.current),
        }))
      } catch (error) {
        setState(prev => ({
          ...prev,
          sentCount: prev.sentCount + 1,
          errorCount: prev.errorCount + 1,
          lastError: error instanceof Error ? error.message : 'Unknown error',
        }))
      }
    }, intervalMs)

    // Auto-stop по duration
    if (config.durationSeconds) {
      setTimeout(() => stop(), config.durationSeconds * 1000)
    }
  }, [])

  const stop = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    setState(prev => ({ ...prev, status: 'stopped' }))
  }, [])

  // ...
}
```

### CORS Configuration

**Важно:** Для работы load generator нужен CORS между admin-ui (port 3000) и gateway-core (port 8080).

Текущая конфигурация gateway-core уже поддерживает CORS для localhost — проверить `GatewayConfig.kt`.

Если CORS не настроен, добавить в `application.yml`:
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders: "*"
```

### UI Design

**Layout TestPage:**
```
┌────────────────────────────────────────────────────────────────┐
│ Test Load Generator                                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌───────────────────────────────────────────────────────────┐│
│  │ Load Generator Settings                                   ││
│  │                                                           ││
│  │ Target Route:  [▼ /api/orders          ]                  ││
│  │                                                           ││
│  │ Requests/sec:  [  10  ] (1-100)                           ││
│  │                                                           ││
│  │ Duration:  ○ Fixed: [ 30 ] seconds                        ││
│  │            ● Until stopped                                ││
│  │                                                           ││
│  │         [ Start Load ]                                    ││
│  └───────────────────────────────────────────────────────────┘│
│                                                                │
│  ┌───────────────────────────────────────────────────────────┐│
│  │ Progress (when running)                                   ││
│  │                                                           ││
│  │   Sent: 150    Success: 148    Errors: 2    Elapsed: 15s ││
│  │                                                           ││
│  │   Average Response Time: 45ms                             ││
│  │                                                           ││
│  │         [ Stop ]                                          ││
│  └───────────────────────────────────────────────────────────┘│
│                                                                │
│  ┌───────────────────────────────────────────────────────────┐│
│  │ Summary (after stop)                                      ││
│  │                                                           ││
│  │   Total Requests: 300                                     ││
│  │   Duration: 30.2s                                         ││
│  │   Success Rate: 98.7%                                     ││
│  │   Avg Response: 42ms                                      ││
│  │                                                           ││
│  │         [ Reset ] [ Download Report ]                     ││
│  └───────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────┘
```

### Sidebar Menu Item

В `Sidebar.tsx` добавить пункт меню:

```tsx
import { ExperimentOutlined } from '@ant-design/icons'

// В baseMenuItems добавить после /metrics:
{
  key: '/test',
  icon: <ExperimentOutlined />,
  label: 'Test',
},
```

### App.tsx Route

```tsx
import { TestPage } from '@features/test'

// В routes добавить:
<Route path="test" element={<TestPage />} />
```

### Ant Design компоненты

| Компонент | Использование |
|-----------|---------------|
| `Card` | Контейнеры для секций |
| `Select` | Target route dropdown |
| `InputNumber` | RPS и Duration inputs |
| `Radio.Group` | Fixed duration vs Until stopped |
| `Button` | Start/Stop/Reset |
| `Statistic` | Counters (Sent, Success, Errors) |
| `Progress` | Progress bar (опционально) |
| `Space` | Spacing между элементами |
| `Row/Col` | Grid layout для статистик |

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| Sidebar.tsx | `frontend/admin-ui/src/layouts/` | Добавить пункт /test |
| App.tsx | `frontend/admin-ui/src/` | Добавить route /test |
| TestPage.tsx | `frontend/admin-ui/src/features/test/components/` | НОВЫЙ |
| LoadGeneratorForm.tsx | `frontend/admin-ui/src/features/test/components/` | НОВЫЙ |
| LoadGeneratorProgress.tsx | `frontend/admin-ui/src/features/test/components/` | НОВЫЙ |
| LoadGeneratorSummary.tsx | `frontend/admin-ui/src/features/test/components/` | НОВЫЙ |
| useLoadGenerator.ts | `frontend/admin-ui/src/features/test/hooks/` | НОВЫЙ |
| loadGenerator.types.ts | `frontend/admin-ui/src/features/test/types/` | НОВЫЙ |
| index.ts | `frontend/admin-ui/src/features/test/` | НОВЫЙ |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.9]
- [Source: frontend/admin-ui/src/layouts/Sidebar.tsx] — паттерн добавления menu items
- [Source: frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx] — паттерн страницы с Card/Statistic
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx] — использование useRoutes для fetch routes
- [Source: _bmad-output/implementation-artifacts/8-8-unified-table-filters.md] — предыдущая story

### Тестовые команды

```bash
# Frontend unit тесты
cd frontend/admin-ui
npm run test:run

# Тесты конкретного компонента
cd frontend/admin-ui && npm run test:run -- TestPage
cd frontend/admin-ui && npm run test:run -- LoadGeneratorForm
cd frontend/admin-ui && npm run test:run -- useLoadGenerator

# Запуск всего стека для ручного тестирования
docker-compose up -d
# Gateway-core на :8080, Admin-UI на :3000
```

### Связанные stories

- Story 6.5 — Basic Metrics View in Admin UI (MetricsPage, Statistic компоненты)
- Story 8.1 — Health Check на странице Metrics (HealthCheckSection паттерн)
- Story 3.4 — Routes List UI (useRoutes hook для получения routes)

### Git commits из предыдущих stories (контекст)

```
e92e199 feat: implement Story 8.8 — unified FilterChips component for all tables
578c18d fix: add search term highlighting to Approvals page (Story 8.7)
215c1ab feat: implement Story 8.7 — Approvals search by path and upstream URL
```

### Паттерны из предыдущих stories

**MetricsPage показала паттерн:**
- Card для секций
- Statistic для отображения числовых значений
- Row/Col для grid layout
- data-testid для тестирования

**Sidebar показала паттерн:**
- baseMenuItems массив
- Icon из @ant-design/icons
- navigate(key) для навигации

### Security Considerations

**Access Control:**
- Страница Test доступна всем аутентифицированным пользователям
- Load generator отправляет запросы от имени текущего браузера
- Rate limiting на gateway-core защищает от злоупотреблений

**Recommendation для production:**
- Рассмотреть ограничение доступа к /test только для admin/devops roles
- Добавить rate limiting на количество одновременных генераторов

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все 410 unit тестов проходят без регрессий

### Completion Notes List

- Реализована полная feature test с генератором нагрузки
- Пункт меню Test добавлен в Sidebar с иконкой ExperimentOutlined
- Route /test добавлен в App.tsx для всех аутентифицированных пользователей
- LoadGeneratorForm: dropdown опубликованных маршрутов, RPS (1-100), duration mode
- useLoadGenerator hook: setInterval генерация, счётчики sent/success/error, auto-stop
- LoadGeneratorProgress: real-time статистика с Ant Design Statistic компонентами
- LoadGeneratorSummary: итоги после остановки с success rate и avg response time
- 46 unit тестов для feature test (все компоненты покрыты)

**Code Review Fixes (2026-02-21):**
- H1: Добавлен useEffect cleanup для предотвращения memory leak при unmount
- H2: Добавлена CORS конфигурация в gateway-core для cross-origin запросов
- M1: Добавлены unit тесты для LoadGeneratorProgress и LoadGeneratorSummary
- M2: URL gateway-core вынесен в environment variable VITE_GATEWAY_URL
- L2: Исправлен конфликт имён в index.ts (type alias LoadGeneratorSummaryData)

### File List

**Новые файлы:**
- frontend/admin-ui/src/features/test/index.ts
- frontend/admin-ui/src/features/test/types/loadGenerator.types.ts
- frontend/admin-ui/src/features/test/hooks/useLoadGenerator.ts
- frontend/admin-ui/src/features/test/hooks/useLoadGenerator.test.tsx
- frontend/admin-ui/src/features/test/components/TestPage.tsx
- frontend/admin-ui/src/features/test/components/TestPage.test.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorForm.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorForm.test.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorProgress.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorProgress.test.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorSummary.tsx
- frontend/admin-ui/src/features/test/components/LoadGeneratorSummary.test.tsx

**Изменённые файлы:**
- frontend/admin-ui/src/layouts/Sidebar.tsx (добавлен пункт /test с ExperimentOutlined)
- frontend/admin-ui/src/App.tsx (добавлен route /test)
- backend/gateway-core/src/main/resources/application.yml (добавлена CORS конфигурация)

## Change Log

- 2026-02-21: Story 8.9 created by create-story workflow
- 2026-02-21: Story 8.9 implemented — Test page with load generator (AC1-AC5)
- 2026-02-21: Code review fixes — memory leak (H1), CORS (H2), missing tests (M1), env var (M2), type conflict (L2)
