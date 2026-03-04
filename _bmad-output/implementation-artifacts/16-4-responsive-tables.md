# Story 16.4: Responsive таблицы и карточки метрик

Status: done

## Story

As a **User**,
I want tables and metrics cards to be responsive,
so that I can use the system on different screen sizes.

## Acceptance Criteria

### AC1: Таблица маршрутов на экране < 1280px
**Given** таблица маршрутов на экране < 1280px
**When** пользователь просматривает таблицу
**Then** менее важные колонки скрыты (Rate Limit, Author)
**And** основные колонки видны (Path, Status, Methods, Actions)
**And** можно раскрыть row для просмотра скрытых данных

### AC2: Карточки метрик на экране < 1280px
**Given** метрики страница на экране < 1280px
**When** отображаются summary cards
**Then** карточки переносятся на новую строку
**And** используется responsive span: `{ xs: 24, sm: 12, md: 8, lg: 4 }`

### AC3: Сворачивание методов
**Given** таблица маршрутов
**When** колонка Methods содержит много методов (>3)
**Then** методы сворачиваются с кнопкой "ещё +N"
**And** по клику раскрываются все методы

### AC4: Touch-friendly размеры
**Given** responsive таблица
**When** пользователь на мобильном устройстве
**Then** горизонтальный скролл отсутствует или минимален
**And** touch-friendly размеры кнопок (min 44x44px)

## Tasks / Subtasks

- [x] Task 1: Responsive таблица маршрутов (AC1, AC3, AC4)
  - [x] 1.1 Добавить `responsive` breakpoints для колонок RoutesTable (скрыть Rate Limit, Author на < md)
  - [x] 1.2 Реализовать `expandable` row для просмотра скрытых данных
  - [x] 1.3 Создать компонент `MethodsTags` с логикой сворачивания (>3 методов → "ещё +N")
  - [x] 1.4 Обеспечить минимальный размер touch-targets (44x44px) для кнопок Actions
  - [x] 1.5 Добавить unit тесты для новых компонентов

- [x] Task 2: Responsive карточки метрик (AC2)
  - [x] 2.1 Обновить MetricsPage — заменить фиксированные `Col span={4}` на responsive spans
  - [x] 2.2 Применить responsive span: `{ xs: 24, sm: 12, md: 8, lg: 4 }`
  - [x] 2.3 Проверить визуально на разных breakpoints

- [x] Task 3: Общие responsive улучшения
  - [x] 3.1 Применить responsive pattern к другим таблицам (Consumers, Audit) с обновлением тестов
  - [x] 3.2 Обновить CSS для минимизации горизонтального скролла

## Dev Notes

### Архитектурные паттерны

**Ant Design Table Responsive:**
- Использовать `responsive` prop в column definition: `{ responsive: ['md'] }` — колонка видна только на md и выше
- Breakpoints: xs (<576px), sm (≥576px), md (≥768px), lg (≥992px), xl (≥1200px), xxl (≥1400px)
- Для AC1: скрыть колонки Rate Limit и Author на `['lg']` (видны только на lg и выше)

**Expandable Rows:**
- Уже реализовано в: `ConsumersTable.tsx`, `AuditLogsTable.tsx`, `UpstreamsTable.tsx`
- Паттерн: `expandable={{ expandedRowRender, rowExpandable: () => true }}`
- Для RoutesTable: в expandedRowRender показывать скрытые поля (Rate Limit, Author, Created, Auth status)

**Responsive Col Spans:**
- Текущий паттерн в QuickStats.tsx (Story 16.2): `<Col xs={24} sm={12} md={6}>` — правильный подход
- MetricsPage использует фиксированные `span={4}` — нужно обновить на responsive

**Methods Tags сворачивание:**
- Создать компонент `CollapsibleMethods` или `MethodsTags`
- Логика: если methods.length > 3 → показать первые 3 + кнопку "ещё +N"
- По клику toggle показывать все методы
- Использовать Ant Design `Tag` и `Button type="link"`

### Project Structure Notes

**Файлы для изменения:**
```
frontend/admin-ui/src/
├── features/
│   ├── routes/
│   │   └── components/
│   │       ├── RoutesTable.tsx          # AC1, AC3, AC4 — главные изменения
│   │       └── CollapsibleMethods.tsx   # новый компонент (AC3)
│   └── metrics/
│       └── components/
│           └── MetricsPage.tsx          # AC2 — responsive Col spans
└── shared/
    └── components/
        └── CollapsibleMethods.tsx       # опционально — shared если переиспользуется
```

**Существующие паттерны expandable rows:**
- `frontend/admin-ui/src/features/consumers/components/ConsumersTable.tsx:257` — expandable
- `frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx:186` — expandable
- `frontend/admin-ui/src/features/audit/components/UpstreamsTable.tsx:186` — expandable

**Существующие responsive patterns:**
- `frontend/admin-ui/src/features/dashboard/components/QuickStats.tsx:54` — `<Col xs={24} sm={12} md={6}>`

### Technical Requirements

**Ant Design Version:** проект использует antd 5.x
- Responsive column: `column.responsive: Breakpoint[]` — массив breakpoints где колонка видна
- Row/Col responsive spans: `xs`, `sm`, `md`, `lg`, `xl`, `xxl`

**Breakpoint Convention (AC1 requirement — < 1280px):**
- lg breakpoint = 992px — колонки скрытые на lg будут показаны на 992px+
- xl breakpoint = 1200px — ближе к требованию 1280px
- Рекомендация: использовать `responsive: ['xl']` для колонок Rate Limit, Author (скрыты до 1200px)

**Touch Targets (AC4):**
- WCAG 2.1 рекомендует минимум 44x44px
- Ant Design Button default: достаточный размер, но проверить icon-only buttons
- Применить `style={{ minWidth: 44, minHeight: 44 }}` для icon buttons в Actions

### Testing Requirements

**Unit тесты:**
```typescript
// CollapsibleMethods.test.tsx
describe('CollapsibleMethods', () => {
  it('показывает все методы если <= 3', () => { ... })
  it('сворачивает методы если > 3 и показывает "ещё +N"', () => { ... })
  it('разворачивает методы по клику', () => { ... })
})

// RoutesTable.test.tsx (обновить)
describe('responsive columns (AC1)', () => {
  it('скрывает Rate Limit колонку на маленьких экранах', () => { ... })
  it('показывает expandable row с полными данными', () => { ... })
})
```

**Visual Testing:**
- Проверить на breakpoints: 576px (xs), 768px (sm), 992px (lg), 1200px (xl)
- Playwright: можно использовать `page.setViewportSize({ width: 768, height: 1024 })`

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 16.4]
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx — текущая реализация]
- [Source: frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx:143-203 — текущие Col spans]
- [Source: frontend/admin-ui/src/features/dashboard/components/QuickStats.tsx:54 — responsive pattern]
- [Source: frontend/admin-ui/src/features/consumers/components/ConsumersTable.tsx:257 — expandable example]
- [Ant Design Table Column responsive](https://ant.design/components/table#column)
- [Ant Design Grid Responsive](https://ant.design/components/grid#components-grid-demo-responsive)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

(to be filled during implementation)

### Completion Notes List

(to be filled after implementation)

### Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-03-04 | SM | Story created — ready for dev |

### File List

(to be filled after implementation)
