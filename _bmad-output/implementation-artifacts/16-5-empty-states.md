# Story 16.5: Empty States для пустых таблиц

Status: done

## Story

As a **New User**,
I want to see helpful empty states when there's no data,
so that I understand what to do next.

## Acceptance Criteria

### AC1: Routes empty state
**Given** страница Routes без маршрутов
**When** таблица пустая
**Then** отображается кастомный empty state:
- Иллюстрация (Ant Design Empty или кастомная SVG)
- Заголовок: "Маршруты ещё не созданы"
- Описание: "Создайте первый маршрут для начала работы"
- CTA кнопка: "Создать маршрут" (primary)

### AC2: Approvals empty state
**Given** страница Approvals без pending маршрутов
**When** таблица пустая
**Then** отображается:
- Иконка CheckCircle (зелёная)
- Текст: "Нет маршрутов на согласование"
- Описание: "Все маршруты обработаны"

### AC3: Audit Logs empty state (после фильтрации)
**Given** страница Audit Logs без записей (после фильтрации)
**When** таблица пустая
**Then** отображается:
- Текст: "Записи не найдены"
- Описание: "Попробуйте изменить параметры фильтра"
- Кнопка: "Сбросить фильтры"

### AC4: Consumers empty state
**Given** страница Consumers без записей
**When** таблица пустая
**Then** отображается:
- Текст: "Потребители ещё не созданы"
- CTA кнопка: "Создать потребителя"

### AC5: Общие требования к стилю
**Given** все таблицы с empty states
**When** empty state отображается
**Then** стиль соответствует Ant Design guidelines
**And** empty state центрирован вертикально и горизонтально

## Tasks / Subtasks

- [x] Task 1: Создать переиспользуемый компонент EmptyState (AC5)
  - [x] 1.1 Создать `EmptyState.tsx` в `src/shared/components/`
  - [x] 1.2 Props: icon, title, description, action (optional Button)
  - [x] 1.3 Использовать Ant Design Empty как базу
  - [x] 1.4 Добавить unit тесты для EmptyState

- [x] Task 2: Routes empty state (AC1)
  - [x] 2.1 Добавить EmptyState в RoutesTable через `locale={{ emptyText: ... }}`
  - [x] 2.2 CTA "Создать маршрут" → navigate('/routes/new')
  - [x] 2.3 Обновить unit тесты

- [x] Task 3: Approvals empty state (AC2)
  - [x] 3.1 Обновить существующий empty state в ApprovalsPage
  - [x] 3.2 Заменить простой Empty на EmptyState с CheckCircleOutlined
  - [x] 3.3 Зелёная иконка, позитивный тон текста

- [x] Task 4: Audit Logs empty state (AC3)
  - [x] 4.1 Обновить AuditPage empty state
  - [x] 4.2 Добавить кнопку "Сбросить фильтры" в empty state
  - [x] 4.3 Кнопка должна очищать все фильтры (уже есть handleClearFilters)

- [x] Task 5: Consumers empty state (AC4)
  - [x] 5.1 Добавить EmptyState в ConsumersTable
  - [x] 5.2 CTA "Создать потребителя" → navigate или action

## Dev Notes

### Архитектурные паттерны

**Ant Design Empty компонент:**
- `Empty` — базовый компонент с изображением и description
- `Empty.PRESENTED_IMAGE_SIMPLE` — минималистичное изображение
- `Empty.PRESENTED_IMAGE_DEFAULT` — стандартное изображение (больше)
- Children внутри Empty отображаются под description (для CTA кнопок)

**Table locale prop:**
```tsx
<Table
  locale={{
    emptyText: <EmptyState ... />
  }}
/>
```

**Существующие реализации (для справки):**
- `ApprovalsPage.tsx:321-325` — Empty с PRESENTED_IMAGE_SIMPLE
- `AuditPage.tsx:237-247` — Empty с description из span
- `UpstreamsTable.tsx:156-163` — Empty с children для дополнительного текста
- `RecentActivity.tsx:166-169` — Empty в Card

### Project Structure Notes

**Новый файл:**
```
frontend/admin-ui/src/shared/components/
└── EmptyState.tsx      # переиспользуемый компонент
└── EmptyState.test.tsx # unit тесты
```

**Файлы для изменения:**
```
frontend/admin-ui/src/features/
├── routes/components/RoutesTable.tsx           # AC1
├── approval/components/ApprovalsPage.tsx       # AC2 (уже есть Empty, обновить)
├── audit/components/AuditPage.tsx              # AC3 (уже есть Empty, добавить кнопку)
└── consumers/components/ConsumersTable.tsx     # AC4
```

### Technical Requirements

**EmptyState Props Interface:**
```typescript
interface EmptyStateProps {
  // Иконка (опционально, вместо стандартного изображения)
  icon?: React.ReactNode
  // Заголовок (крупный текст)
  title: string
  // Описание (мелкий серый текст)
  description?: string
  // CTA кнопка (опционально)
  action?: {
    label: string
    onClick: () => void
    type?: 'primary' | 'default'  // default: 'primary'
  }
  // Использовать простое изображение Ant Design вместо иконки
  useSimpleImage?: boolean
}
```

**Стилизация:**
- Центрирование: по умолчанию Empty центрирован
- Padding: 48px сверху и снизу для визуального баланса
- Icon size: 48px для кастомных иконок
- Icon color: #1890ff (primary) или #52c41a (success для Approvals)

### Testing Requirements

**Unit тесты EmptyState:**
```typescript
describe('EmptyState', () => {
  it('отображает title и description', () => { ... })
  it('отображает CTA кнопку когда action передан', () => { ... })
  it('вызывает onClick при клике на CTA', () => { ... })
  it('отображает кастомную иконку когда передана', () => { ... })
  it('использует PRESENTED_IMAGE_SIMPLE когда useSimpleImage=true', () => { ... })
})
```

**Обновить существующие тесты:**
- RoutesTable: добавить тест на empty state
- ConsumersTable: добавить тест на empty state
- AuditPage: проверить что кнопка "Сбросить фильтры" работает

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 16.5]
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx:319-326 — существующий Empty]
- [Source: frontend/admin-ui/src/features/audit/components/AuditPage.tsx:235-248 — существующий Empty]
- [Source: frontend/admin-ui/src/features/audit/components/UpstreamsTable.tsx:156-163 — Empty с children]
- [Source: frontend/admin-ui/src/features/rate-limits/components/RateLimitRoutesModal.tsx:64 — простой Empty]
- [Ant Design Empty](https://ant.design/components/empty)

### Existing Empty States Status

| Компонент | Текущее состояние | Требуемые изменения |
|-----------|-------------------|---------------------|
| RoutesTable | Дефолтный Ant Design | Добавить EmptyState с CTA |
| ApprovalsPage | Empty с простым текстом | Добавить CheckCircle иконку |
| AuditPage | Empty с текстом | Добавить кнопку "Сбросить фильтры" |
| ConsumersTable | Дефолтный Ant Design | Добавить EmptyState с CTA |
| UpstreamsTable | ✓ Уже хороший | Не требуется |
| RecentActivity | ✓ Уже хороший | Не требуется |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Unit тесты EmptyState: 10/10 passed
- Unit тесты RoutesTable: 6/6 passed (включая конфигурацию empty state)
- Unit тесты AuditPage: 14/14 passed (включая новые тесты empty state)
- Unit тесты ApprovalsPage: обновлены для нового текста
- Полный прогон: 780/780 tests passed

### Completion Notes List

1. **Task 1:** Создан переиспользуемый компонент `EmptyState` на базе Ant Design `Empty`. Поддерживает кастомные иконки, заголовок, описание и CTA кнопку. Добавлено 10 unit тестов.

2. **Task 2:** RoutesTable теперь показывает кастомный empty state с CTA кнопкой "Создать маршрут", которая навигирует на `/routes/new`.

3. **Task 3:** ApprovalsPage empty state обновлён с позитивным тоном: зелёная иконка CheckCircleOutlined, текст "Нет маршрутов на согласование" / "Все маршруты обработаны".

4. **Task 4:** AuditPage empty state теперь содержит кнопку "Сбросить фильтры" для удобства пользователей при фильтрации без результатов.

5. **Task 5:** ConsumersTable теперь показывает empty state с CTA "Создать потребителя", который открывает модальное окно создания consumer.

### Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-03-04 | SM | Story created — ready for dev |
| 2026-03-04 | Dev Agent | Implementation complete — all ACs satisfied |
| 2026-03-04 | Code Review | Fixed: M1 (экспорт типа), M2 (dark mode), M4 (тесты ConsumersTable) |

### File List

**Новые файлы:**
- `frontend/admin-ui/src/shared/components/EmptyState.tsx`
- `frontend/admin-ui/src/shared/components/EmptyState.test.tsx`

**Изменённые файлы:**
- `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx` — добавлен empty state (AC1)
- `frontend/admin-ui/src/features/routes/components/RoutesTable.test.tsx` — обновлены тесты
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` — обновлён empty state (AC2)
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` — обновлены тесты
- `frontend/admin-ui/src/features/audit/components/AuditPage.tsx` — добавлена кнопка в empty state (AC3)
- `frontend/admin-ui/src/features/audit/components/AuditPage.test.tsx` — обновлены тесты
- `frontend/admin-ui/src/features/consumers/components/ConsumersTable.tsx` — добавлен empty state (AC4)
- `frontend/admin-ui/src/features/consumers/components/ConsumersPage.tsx` — передаёт callback для CTA
