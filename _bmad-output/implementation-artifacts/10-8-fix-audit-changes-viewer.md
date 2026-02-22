# Story 10.8: Fix Audit Changes Viewer

Status: done

## Story

As a **Security/Admin user**,
I want to see actual change details when expanding audit log entries,
so that I can understand what happened during approve/reject/submit/rollback actions.

## Bug Report

**Severity:** MEDIUM

**Воспроизведение:**
1. Перейти в Audit Logs
2. Найти запись с action = approved/rejected/submitted/rolledback
3. Раскрыть запись (иконка expand)
4. Наблюдать: "До изменения" и "После изменения" показывают пустые прямоугольники с текстом "null"

**Entity ID примера:** `90c57d84-88bc-4acd-bcd2-73adba90f0ae`

**Root Cause (подтверждён):**
- Backend (ApprovalService) сохраняет changes в формате: `{"previousStatus": "pending", "newStatus": "published", "approvedAt": "..."}`
- Frontend (ChangesViewer) ожидает формат: `{"before": {...}, "after": {...}}`
- AuditLogsTable деструктурирует `record.changes.before` и `record.changes.after`, теряя остальные поля
- `changes.before` и `changes.after` = `undefined` → `formatJson(undefined)` → отображается "null"

## Acceptance Criteria

### AC1: Changes отображаются для всех action types
**Given** audit log entry с любым action (created, updated, deleted, approved, rejected, submitted, published, route.rolledback)
**When** пользователь раскрывает запись
**Then** changes отображаются корректно (не "null")

### AC2: Generic JSON viewer для нестандартных changes
**Given** changes без структуры before/after (например, `{previousStatus, newStatus, approvedAt}`)
**When** ChangesViewer рендерит данные
**Then** показывается formatted JSON с заголовком "Детали изменения"

### AC3: Существующий diff функционал сохранён
**Given** action = "updated" с before/after структурой
**When** ChangesViewer рендерит данные
**Then** показывается side-by-side diff (красный/зелёный) как раньше

## Analysis Summary

### Backend Changes Format по Action

| Action | Backend Format | Frontend receives | Status |
|--------|---------------|-------------------|--------|
| **created** | `{"after": {...}}` | `before=undefined, after={...}` | ✅ Работает |
| **updated** | `{"before": {...}, "after": {...}}` | `before={...}, after={...}` | ✅ Работает |
| **deleted** | `{"before": {...}}` | `before={...}, after=undefined` | ✅ Работает |
| **submitted** | `{"newStatus": "pending", "submittedAt": "..."}` | `before=undefined, after=undefined` | ❌ БАГ |
| **approved** | `{"previousStatus": "pending", "newStatus": "published", "approvedAt": "..."}` | `before=undefined, after=undefined` | ❌ БАГ |
| **rejected** | `{"previousStatus": "pending", "newStatus": "rejected", "rejectedAt": "...", "rejectionReason": "..."}` | `before=undefined, after=undefined` | ❌ БАГ |
| **published** | `{"publishedAt": "...", "approvedBy": "..."}` | `before=undefined, after=undefined` | ❌ БАГ |
| **route.rolledback** | `{"previousStatus": "published", "newStatus": "draft", "rolledbackAt": "...", "rolledbackBy": "..."}` | `before=undefined, after=undefined` | ❌ БАГ |

### Решение: Generic Display Mode

1. **Изменить props** ChangesViewer — передавать весь `changes` объект вместо destructured before/after
2. **Добавить режим 'generic'** — когда нет before/after, показывать весь changes как JSON
3. **Сохранить существующую логику** для actions с before/after структурой

## Tasks / Subtasks

- [x] Task 1: Update ChangesViewer props (AC: #1, #2, #3)
  - [x] 1.1 Изменить interface — принимать `changes` объект целиком
  - [x] 1.2 Добавить проверку наличия `before`/`after` в changes
  - [x] 1.3 Добавить режим `'generic'` в displayMode logic
  - [x] 1.4 Рендерить generic JSON с заголовком "Детали изменения"
  - [x] 1.5 Использовать нейтральный стиль (jsonStyles.single) для generic режима

- [x] Task 2: Update AuditLogsTable (AC: #1)
  - [x] 2.1 Изменить передачу props — `changes={record.changes}` вместо destructured

- [x] Task 3: Update unit tests (AC: #1, #2, #3)
  - [x] 3.1 Тест: `отображает generic JSON для approved без before/after`
  - [x] 3.2 Тест: `отображает generic JSON для rejected без before/after`
  - [x] 3.3 Тест: `отображает generic JSON для submitted без before/after`
  - [x] 3.4 Тест: `отображает generic JSON для route.rolledback без before/after`
  - [x] 3.5 Тест: `сохраняет diff режим для updated с before/after`
  - [x] 3.6 Тест: `сохраняет after-only режим для created с after`

- [x] Task 4: Manual verification
  - [x] 4.1 Проверить Entity ID `90c57d84-88bc-4acd-bcd2-73adba90f0ae` — RateLimit (updated action, has before/after)
  - [x] 4.2 API возвращает все action types с корректными форматами changes

## API Dependencies Checklist

**Backend изменения не требуются** — fix только на frontend.

## Dev Notes

### Текущая структура ChangesViewer

**Props (строки 12-16):**
```typescript
interface ChangesViewerProps {
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  action: AuditAction
}
```

**DisplayMode logic (строки 64-75):**
```typescript
const displayMode = useMemo(() => {
  if (action === 'created' || action === 'approved' || action === 'submitted' || action === 'published') {
    return 'after-only'
  }
  if (action === 'deleted' || action === 'rejected') {
    return 'before-only'
  }
  return 'diff'
}, [action])
```

### Новая структура ChangesViewer

**Props:**
```typescript
interface ChangesViewerProps {
  changes?: {
    before?: Record<string, unknown> | null
    after?: Record<string, unknown> | null
    [key: string]: unknown  // для generic полей
  } | null
  action: AuditAction
}
```

**DisplayMode logic:**
```typescript
const displayMode = useMemo(() => {
  const hasBeforeAfter = changes?.before !== undefined || changes?.after !== undefined

  // Если нет структуры before/after — generic режим
  if (!hasBeforeAfter) {
    return 'generic'
  }

  // Существующая логика для before/after структур
  if (action === 'created' || action === 'approved' || action === 'submitted' || action === 'published') {
    return 'after-only'
  }
  if (action === 'deleted' || action === 'rejected') {
    return 'before-only'
  }
  return 'diff'
}, [action, changes])
```

**Generic rendering (добавить перед существующими режимами):**
```typescript
if (displayMode === 'generic') {
  return (
    <Card size="small" title="Детали изменения">
      <div style={{ ...jsonStyles.container, ...jsonStyles.single }}>
        {formatJson(changes)}
      </div>
    </Card>
  )
}
```

### Изменение AuditLogsTable

**Текущее (строки 77-83):**
```typescript
{record.changes && (
  <ChangesViewer
    before={record.changes.before}
    after={record.changes.after}
    action={record.action}
  />
)}
```

**Новое:**
```typescript
{record.changes && (
  <ChangesViewer
    changes={record.changes}
    action={record.action}
  />
)}
```

### Существующие стили (для generic режима)

```typescript
// Строки 37-39 — уже существуют
single: {
  backgroundColor: '#f5f5f5',
  border: '1px solid #d9d9d9',
}
```

### Files to Modify

| File | Changes |
|------|---------|
| `frontend/admin-ui/src/features/audit/components/ChangesViewer.tsx` | Props, displayMode logic, generic rendering |
| `frontend/admin-ui/src/features/audit/components/ChangesViewer.test.tsx` | Добавить тесты для generic режима |
| `frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx` | Изменить передачу props |

### References

- [Source: ChangesViewer.tsx:12-16] — текущие props
- [Source: ChangesViewer.tsx:64-75] — текущая displayMode logic
- [Source: ChangesViewer.tsx:37-39] — стили для single mode
- [Source: AuditLogsTable.tsx:77-83] — текущая передача props
- [Source: ApprovalService.kt:126-285] — backend changes format

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- API verified: `curl -b cookies.txt "http://localhost:8081/api/v1/audit?limit=10"` returns all action types with correct changes format

### Completion Notes List

- Реализован generic режим в ChangesViewer для changes без структуры before/after
- Props изменены: принимает `changes` объект целиком вместо destructured before/after
- Сохранена обратная совместимость через legacy props (before, after)
- DisplayMode logic: проверяет наличие before/after в changes — если нет, использует generic режим
- AuditLogsTable обновлён: передаёт `changes={record.changes}` вместо destructured props
- Добавлены 6 unit тестов для generic режима
- Все frontend тесты проходят (нет регрессий)
- Все audit тесты проходят

### File List

- `frontend/admin-ui/src/features/audit/components/ChangesViewer.tsx` — добавлен generic режим, новые props
- `frontend/admin-ui/src/features/audit/components/ChangesViewer.test.tsx` — добавлены 6 тестов для generic режима
- `frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx` — изменена передача props в ChangesViewer
- `frontend/admin-ui/src/features/audit/components/AuditLogsTable.test.tsx` — добавлен мок ThemeProvider
- `frontend/admin-ui/src/features/audit/components/RouteHistoryTimeline.test.tsx` — добавлен мок ThemeProvider
- `frontend/admin-ui/src/features/audit/types/audit.types.ts` — добавлен `route.rolledback` action, generic поля в AuditChanges

## Change Log

- **2026-02-22:** Story created from SM chat session (bug report by Yury)
- **2026-02-22:** Full analysis completed, root cause confirmed, status → ready-for-dev
- **2026-02-22:** Implementation complete — generic режим добавлен, все тесты проходят
- **2026-02-22:** Code review fixes — добавлен `route.rolledback` в AuditAction, обновлён AuditChanges interface, @deprecated JSDoc для legacy props
