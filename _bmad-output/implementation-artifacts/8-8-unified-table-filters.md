# Story 8.8: Унифицированные фильтры для всех таблиц

Status: done

## Story

As a **User**,
I want consistent filter UI across all tables,
so that the application has a unified user experience.

## Acceptance Criteria

**AC1 — Единый визуальный стиль фильтров:**

**Given** пользователь находится на любой странице с таблицей (Routes, Users, Rate Limits, Approvals, Audit)
**When** страница загружена
**Then** UI фильтров следует единому паттерну:
- Поле поиска слева (Search Input)
- Dropdown фильтры в ряд
- Активные фильтры показаны как removable chips
- Кнопка "Сбросить фильтры" справа (видна только при наличии активных фильтров)

**AC2 — Active filters chips:**

**Given** пользователь применил один или несколько фильтров
**When** фильтры активны
**Then** активные фильтры отображаются как Tag chips с возможностью закрытия
**And** каждый chip показывает тип фильтра и значение (например: "Статус: Published", "Поиск: orders")

**AC3 — Clear all button:**

**Given** есть хотя бы один активный фильтр
**When** пользователь нажимает "Сбросить фильтры"
**Then** все фильтры сбрасываются к значениям по умолчанию
**And** кнопка скрывается

**AC4 — Users table как эталон:**

**Given** страница Users уже имеет частично реализованные фильтры (Story 8.3)
**When** сравниваем с другими таблицами
**Then** все таблицы визуально соответствуют стилю Users, но расширяются chips

## Tasks / Subtasks

- [x] Task 1: Создать переиспользуемый компонент `FilterChips` (AC2)
  - [x] Subtask 1.1: Создать файл `frontend/admin-ui/src/shared/components/FilterChips.tsx`
  - [x] Subtask 1.2: Реализовать отображение chips как Ant Design Tag с closable
  - [x] Subtask 1.3: Поддержать разные типы фильтров (search, status, date, select)
  - [x] Subtask 1.4: Добавить цветовую маркировку по типу фильтра

- [x] Task 2: Обновить UsersTable (AC1, AC2)
  - [x] Subtask 2.1: Добавить chips для активного поиска
  - [x] Subtask 2.2: Добавить chips для фильтров Role и Status
  - [x] Subtask 2.3: Интегрировать FilterChips компонент

- [x] Task 3: Обновить RoutesTable (AC1, AC2)
  - [x] Subtask 3.1: Заменить существующие chips на FilterChips компонент
  - [x] Subtask 3.2: Стандартизировать ширину поиска (280px)

- [x] Task 4: Обновить ApprovalsPage (AC1, AC2, AC3)
  - [x] Subtask 4.1: Добавить FilterChips для активного поиска
  - [x] Subtask 4.2: Добавить кнопку "Сбросить фильтры"

- [x] Task 5: Обновить RateLimitsTable (AC1, AC2, AC3)
  - [x] Subtask 5.1: Добавить FilterChips для активного поиска
  - [x] Subtask 5.2: Добавить кнопку "Сбросить фильтры"

- [x] Task 6: Обновить AuditFilterBar (AC1, AC2)
  - [x] Subtask 6.1: Добавить FilterChips для всех активных фильтров
  - [x] Subtask 6.2: Показывать chips для date range, user, entity type, action

- [x] Task 7: Добавить тесты
  - [x] Subtask 7.1: Unit тест для FilterChips компонента
  - [x] Subtask 7.2: Тесты интеграции chips в каждой таблице
  - [x] Subtask 7.3: Все тесты проходят

## API Dependencies Checklist

**Эта story НЕ требует backend изменений.**

Все фильтры работают с существующими API endpoints. Story — чисто frontend рефакторинг для унификации UI.

## Dev Notes

### Текущее состояние (анализ)

**Выявленные проблемы в текущих фильтрах:**

| Компонент | Поиск | Dropdown фильтры | Chips активных | Clear all | Проблемы |
|-----------|-------|------------------|-----------------|-----------|----------|
| **UsersTable** | Input.Search (280px) | В колонках | НЕТ | ДА | Нет chips, фильтры в колонках |
| **RoutesTable** | Input.Search (250px) | Status Select | ДА (Tag) | ДА | Хороший пример, но разные размеры |
| **ApprovalsPage** | Input (max 300px) | НЕТ | НЕТ | НЕТ | Минималистичный, нет chips |
| **RateLimitsTable** | Input (max 300px) | НЕТ | НЕТ | НЕТ | Минималистичный, нет chips |
| **AuditFilterBar** | НЕТ | Множество (4) | НЕТ | ДА | Много фильтров, но нет chips |

### Унифицированный паттерн

Структура панели фильтров (по приоритету расположения слева направо):
```
┌────────────────────────────────────────────────────────────────────┐
│  [🔍 Search Input] [Select1] [Select2] ...  [Сбросить фильтры]    │
└────────────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────────────┐
│  [× Поиск: orders] [× Статус: Published] [× Пользователь: admin]  │
└────────────────────────────────────────────────────────────────────┘
```

### FilterChips компонент

```typescript
// frontend/admin-ui/src/shared/components/FilterChips.tsx

interface FilterChip {
  key: string           // уникальный ID (например: 'search', 'status', 'user')
  label: string         // отображаемый текст (например: 'Поиск: orders')
  color?: string        // цвет Tag (optional, default = blue)
  onClose: () => void   // callback при закрытии
}

interface FilterChipsProps {
  chips: FilterChip[]
  className?: string
}

export const FilterChips: React.FC<FilterChipsProps> = ({ chips, className }) => {
  if (chips.length === 0) return null

  return (
    <Space wrap className={className}>
      {chips.map((chip) => (
        <Tag
          key={chip.key}
          closable
          color={chip.color || 'blue'}
          onClose={chip.onClose}
        >
          {chip.label}
        </Tag>
      ))}
    </Space>
  )
}
```

### Цветовая схема chips

| Тип фильтра | Цвет Tag | Пример |
|-------------|----------|--------|
| Search | `blue` | "Поиск: orders" |
| Status | По статусу | draft=gray, pending=gold, published=green, rejected=red |
| Role | `purple` | "Роль: Admin" |
| User | `cyan` | "Пользователь: admin" |
| Entity Type | `orange` | "Тип: route" |
| Action | `magenta` | "Действие: created" |
| Date Range | `geekblue` | "Дата: 2026-02-01 — 2026-02-21" |
| Upstream | `purple` | "Upstream: payment-service" |

### Стандартные размеры

| Элемент | Ширина |
|---------|--------|
| Search Input | 280px (фиксированная) |
| Single Select | 150-180px |
| Multi-select | minWidth: 200px |
| Date Range Picker | по умолчанию |

### Изменения по компонентам

**1. UsersTable.tsx**

Текущее состояние (lines ~80-100):
```tsx
<Space style={{ marginBottom: 16, width: '100%' }} wrap>
  <Input.Search
    placeholder="Поиск по username или email..."
    allowClear
    value={searchInput}
    onChange={(e) => handleSearchInputChange(e.target.value)}
    style={{ width: 280 }}
    prefix={<SearchOutlined />}
  />
  {hasActiveFilters && (
    <Button type="text" icon={<CloseCircleOutlined />} onClick={handleClearFilters}>
      Сбросить фильтры
    </Button>
  )}
</Space>
```

Добавить после фильтров:
```tsx
<FilterChips
  chips={[
    ...(searchInput ? [{ key: 'search', label: `Поиск: ${searchInput}`, onClose: () => setSearchInput('') }] : []),
    // Role и Status фильтры из column filters (если доступны из state)
  ]}
/>
```

**2. RoutesTable.tsx**

Уже имеет chips — нужно унифицировать с FilterChips компонентом:
- Заменить inline Tag на FilterChips
- Стандартизировать ширину поиска с 250px на 280px

**3. ApprovalsPage.tsx**

Текущее состояние (lines ~220-230):
```tsx
<Input
  placeholder="Поиск по path, upstream..."
  prefix={<SearchOutlined />}
  value={searchText}
  onChange={(e) => setSearchText(e.target.value)}
  allowClear
  style={{ marginBottom: 16, maxWidth: 300 }}
/>
```

Изменить на:
```tsx
<Space style={{ marginBottom: 16, width: '100%' }} wrap>
  <Input.Search
    placeholder="Поиск по path, upstream..."
    allowClear
    value={searchText}
    onChange={(e) => setSearchText(e.target.value)}
    style={{ width: 280 }}
    prefix={<SearchOutlined />}
  />
  {searchText && (
    <Button type="text" icon={<CloseCircleOutlined />} onClick={() => setSearchText('')}>
      Сбросить фильтры
    </Button>
  )}
</Space>
<FilterChips
  chips={searchText ? [{ key: 'search', label: `Поиск: ${searchText}`, onClose: () => setSearchText('') }] : []}
/>
```

**4. RateLimitsTable.tsx**

Аналогично ApprovalsPage — добавить Space wrapper, Input.Search, Clear button и FilterChips.

**5. AuditFilterBar.tsx**

Самый сложный компонент — много фильтров. Добавить FilterChips после всех фильтров:

```tsx
<FilterChips
  chips={[
    ...(dateRange ? [{ key: 'date', label: `Дата: ${dateRange[0]} — ${dateRange[1]}`, color: 'geekblue', onClose: handleClearDateRange }] : []),
    ...(userId ? [{ key: 'user', label: `Пользователь: ${userName}`, color: 'cyan', onClose: handleClearUser }] : []),
    ...(entityType ? [{ key: 'entity', label: `Тип: ${entityType}`, color: 'orange', onClose: handleClearEntityType }] : []),
    ...(actions?.map(action => ({ key: `action-${action}`, label: action, color: 'magenta', onClose: () => handleRemoveAction(action) })) || []),
  ]}
/>
```

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| FilterChips.tsx | `frontend/admin-ui/src/shared/components/` | НОВЫЙ — переиспользуемый компонент |
| FilterChips.test.tsx | `frontend/admin-ui/src/shared/components/` | НОВЫЙ — тесты компонента |
| UsersTable.tsx | `frontend/admin-ui/src/features/users/components/` | Добавить chips |
| RoutesTable.tsx | `frontend/admin-ui/src/features/routes/components/` | Рефакторинг chips |
| ApprovalsPage.tsx | `frontend/admin-ui/src/features/approval/components/` | Добавить chips + clear |
| RateLimitsTable.tsx | `frontend/admin-ui/src/features/rate-limits/components/` | Добавить chips + clear |
| AuditFilterBar.tsx | `frontend/admin-ui/src/features/audit/components/` | Добавить chips |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.8]
- [Source: frontend/admin-ui/src/features/users/components/UsersTable.tsx] — текущий эталон
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx] — пример с chips
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx] — минимальные фильтры
- [Source: frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.tsx] — минимальные фильтры
- [Source: frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx] — сложные фильтры
- [Source: _bmad-output/implementation-artifacts/8-7-approvals-search-upstream.md] — предыдущая story

### Тестовые команды

```bash
# Frontend unit тесты
cd frontend/admin-ui
npm run test:run

# Тесты конкретного компонента
cd frontend/admin-ui && npm run test:run -- FilterChips
cd frontend/admin-ui && npm run test:run -- UsersTable
cd frontend/admin-ui && npm run test:run -- RoutesTable
cd frontend/admin-ui && npm run test:run -- ApprovalsPage
cd frontend/admin-ui && npm run test:run -- RateLimitsTable
cd frontend/admin-ui && npm run test:run -- AuditFilterBar
```

### Связанные stories

- Story 8.3 — Поиск пользователей по username и email (базовый паттерн Users)
- Story 8.5 — Поиск Routes по Path и Upstream URL
- Story 8.6 — Исправить комбобокс пользователей в Audit Logs
- Story 8.7 — Расширить поиск Approvals на Upstream URL

### Git commits из предыдущих stories (контекст)

```
578c18d fix: add search term highlighting to Approvals page (Story 8.7)
215c1ab feat: implement Story 8.7 — Approvals search by path and upstream URL
483ec41 fix: code review fixes for Story 8.6
b0b2a8a feat: implement Story 8.6 — Audit Logs user dropdown fix
76c008d fix: add search term highlighting to Upstream URL column (Story 8.5)
198d415 feat: implement Story 8.5 — Routes search by path and upstream URL
d9c5927 feat: implement Story 8.4 — Author column and Rate Limit display in Routes
745d7e5 fix: move search input to filters panel in UsersTable
7f305fe feat: implement Story 8.3 — Users search by username and email
```

### Паттерны из предыдущих stories

**Story 8.3 показала паттерн:**
- Input.Search с фиксированной шириной 280px
- allowClear для очистки
- debounce 300ms для поиска
- hasActiveFilters state для показа/скрытия "Сбросить фильтры"

**Story 8.5 и 8.7 показали паттерн:**
- Поиск по нескольким полям (path OR upstreamUrl)
- Подсветка найденного текста в таблице

**RoutesTable показала паттерн chips:**
- Tag с closable для каждого активного фильтра
- Цветовая маркировка по типу

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- ✅ Task 1: Создан переиспользуемый компонент FilterChips с поддержкой цветовой маркировки и closable tags
- ✅ Task 2: UsersTable обновлён с FilterChips для отображения активного поиска
- ✅ Task 3: RoutesTable — inline chips заменены на FilterChips компонент, ширина поиска 280px
- ✅ Task 4: ApprovalsPage — добавлены FilterChips и кнопка "Сбросить фильтры"
- ✅ Task 5: RateLimitsTable — добавлены FilterChips и кнопка "Сбросить фильтры"
- ✅ Task 6: AuditFilterBar — добавлены FilterChips для всех фильтров (дата, пользователь, тип, действия)
- ✅ Task 7: Все 390 тестов проходят (включая 11 новых интеграционных тестов)

### File List

- frontend/admin-ui/src/shared/components/FilterChips.tsx (NEW)
- frontend/admin-ui/src/shared/components/FilterChips.test.tsx (NEW)
- frontend/admin-ui/src/features/users/components/UsersTable.tsx (MODIFIED)
- frontend/admin-ui/src/features/users/components/UsersPage.test.tsx (MODIFIED)
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx (MODIFIED)
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx (MODIFIED)
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx (MODIFIED)
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.tsx (MODIFIED)
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.test.tsx (MODIFIED)
- frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx (MODIFIED)
- frontend/admin-ui/src/features/audit/components/AuditFilterBar.test.tsx (MODIFIED)

## Change Log

- 2026-02-21: Story 8.8 implementation complete — unified FilterChips component integrated across all tables (Routes, Users, Approvals, Rate Limits, Audit). All 379 tests pass.
- 2026-02-21: Code review fixes — AuditFilterBar: FilterChips перемещён за пределы Space для унификации layout; FilterChips: добавлен marginBottom для визуального отступа; UsersTable: удалён пустой className; добавлены интеграционные тесты для ApprovalsPage, RateLimitsTable, AuditFilterBar. Всего 390 тестов.
