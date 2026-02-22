# Story 10.8: Fix Audit Changes Viewer

Status: draft

## Story

As a **Security/Admin user**,
I want to see actual change details when expanding audit log entries,
so that I can understand what happened during approve/reject/submit/rollback actions.

## Bug Report

**Воспроизведение:**
1. Перейти в Audit Logs
2. Найти запись с action = approved/rejected/submitted/rolledback
3. Раскрыть запись (иконка expand)
4. Наблюдать: "До изменения" и "После изменения" показывают пустые прямоугольники с текстом "null"

**Entity ID примера:** `90c57d84-88bc-4acd-bcd2-73adba90f0ae`

**Root Cause:**
- Backend (ApprovalService) сохраняет changes в формате: `{"previousStatus": "pending", "newStatus": "published", "approvedAt": "..."}`
- Frontend (ChangesViewer) ожидает формат: `{"before": {...}, "after": {...}}`
- `changes.before` и `changes.after` = `undefined` → отображается "null"

## Acceptance Criteria

### AC1: Changes отображаются для всех action types
**Given** audit log entry с любым action (created, updated, deleted, approved, rejected, submitted, rolledback, published)
**When** пользователь раскрывает запись
**Then** changes отображаются корректно (не "null")

### AC2: Generic JSON viewer для нестандартных changes
**Given** changes без структуры before/after
**When** ChangesViewer рендерит данные
**Then** показывается formatted JSON с заголовком "Детали изменения"

### AC3: Существующий diff функционал сохранён
**Given** action = "updated" с before/after структурой
**When** ChangesViewer рендерит данные
**Then** показывается side-by-side diff (красный/зелёный) как раньше

## Analysis Summary

### Текущая логика ChangesViewer (строки 64-75)

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

**Проблема:** Логика основана на action type, но не проверяет фактическую структуру changes.

### Backend changes format по action

| Action | Backend format | Frontend expectation | Status |
|--------|---------------|---------------------|--------|
| created | `{"after": {...}}` | after-only | ✅ |
| updated | `{"before": {...}, "after": {...}}` | diff | ✅ |
| deleted | `{"before": {...}}` | before-only | ✅ |
| approved | `{"previousStatus": "...", "newStatus": "...", "approvedAt": "..."}` | after-only | ❌ **БАГ** |
| rejected | `{"previousStatus": "...", "newStatus": "...", "reason": "..."}` | before-only | ❌ **БАГ** |
| submitted | `{"status": "pending", "submittedAt": "..."}` | after-only | ❌ **БАГ** |
| rolledback | `{"previousStatus": "...", "newStatus": "..."}` | ? | ❌ **БАГ** |
| published | `{"publishedAt": "...", "approvedBy": "..."}` | after-only | ❌ **БАГ** |

### Решение

**Вариант выбран:** Улучшить frontend (быстрее, гибче)

**Логика:**
1. Если есть `changes.before` или `changes.after` → использовать текущую логику (diff/before-only/after-only)
2. Если нет → показать generic JSON viewer с заголовком "Детали изменения"

## Tasks / Subtasks

- [ ] Task 1: Update ChangesViewer logic (AC: #1, #2, #3)
  - [ ] 1.1 Добавить проверку наличия `before`/`after` в changes
  - [ ] 1.2 Добавить режим 'generic' для changes без before/after
  - [ ] 1.3 Рендерить generic JSON с заголовком "Детали изменения"
  - [ ] 1.4 Использовать нейтральный стиль (серый фон) для generic режима

- [ ] Task 2: Update unit tests (AC: #1, #2, #3)
  - [ ] 2.1 Тест: `отображает generic JSON для approved без before/after`
  - [ ] 2.2 Тест: `отображает generic JSON для rejected без before/after`
  - [ ] 2.3 Тест: `сохраняет diff режим для updated с before/after`
  - [ ] 2.4 Тест: `сохраняет after-only режим для created с after`

- [ ] Task 3: Manual verification
  - [ ] 3.1 Проверить Entity ID `90c57d84-88bc-4acd-bcd2-73adba90f0ae`
  - [ ] 3.2 Проверить все action types в Audit Logs

## API Dependencies Checklist

**Backend изменения не требуются** — fix только на frontend.

## Dev Notes

### Предлагаемое изменение ChangesViewer.tsx

```typescript
// Определяем режим отображения
const displayMode = useMemo(() => {
  const hasBeforeAfter = before !== undefined || after !== undefined

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
}, [action, before, after])

// Добавить обработку generic режима
if (displayMode === 'generic') {
  // Показать весь changes объект как JSON
  return (
    <Card size="small" title="Детали изменения">
      <div style={{ ...jsonStyles.container, ...jsonStyles.single }}>
        {/* Нужно получить raw changes из props */}
      </div>
    </Card>
  )
}
```

**Проблема:** ChangesViewer получает `before` и `after` отдельно, не весь `changes` объект.

**Решение:** Изменить props — передавать весь `changes` объект:

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

### Стиль для generic режима

```typescript
single: {
  backgroundColor: '#f5f5f5',  // уже существует (строка 37-39)
  border: '1px solid #d9d9d9',
}
```

### Files to modify

- `frontend/admin-ui/src/features/audit/components/ChangesViewer.tsx`
- `frontend/admin-ui/src/features/audit/components/ChangesViewer.test.tsx`
- `frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx` (передача changes)

## Change Log

- **2026-02-22:** Hotfix story created from SM chat session (bug report by Yury)
