# Story 16.9: OS-specific keyboard shortcuts

Status: done

## Story

As a **Power User**,
I want keyboard shortcuts to show correct modifier keys for my OS,
so that I know which keys to press.

## Acceptance Criteria

### AC1: macOS modifier key
**Given** пользователь на macOS
**When** видит tooltip с keyboard shortcut
**Then** отображается символ ⌘ (Command): "⌘+N"

### AC2: Windows/Linux modifier key
**Given** пользователь на Windows/Linux
**When** видит tooltip с keyboard shortcut
**Then** отображается "Ctrl": "Ctrl+N"

### AC3: Tooltip на кнопке "Новый маршрут"
**Given** кнопка "Новый маршрут" на странице Routes
**When** пользователь наводит курсор
**Then** tooltip показывает OS-specific shortcut ("⌘+N" или "Ctrl+N")

### AC4: Работающий shortcut на Routes
**Given** страница Routes
**When** пользователь нажимает ⌘+N (Mac) или Ctrl+N (Win/Linux)
**Then** открывается форма создания маршрута
**And** shortcut работает одинаково на всех ОС

### AC5: Подсказка shortcuts на Approvals
**Given** страница Approvals
**When** пользователь видит таблицу pending routes
**Then** есть UI подсказка о доступных shortcuts (A — одобрить, R — отклонить)
**And** при нажатии "A" или "R" на строке применяется соответствующее действие (текущее поведение)

## Tasks / Subtasks

- [x] Task 1: Создать утилиту keyboard (AC1, AC2)
  - [x] 1.1 Создать `src/shared/utils/keyboard.ts`
  - [x] 1.2 Функция `getModifierKey(): 'Ctrl' | '⌘'` — определение модификатора по OS
  - [x] 1.3 Функция `formatShortcut(key: string): string` — форматирование shortcut (e.g., "⌘+N")
  - [x] 1.4 Обновить `src/shared/utils/index.ts` для экспорта
  - [x] 1.5 Unit тесты в `keyboard.test.ts`

- [x] Task 2: Обновить RoutesPage (AC3, AC4)
  - [x] 2.1 Импортировать `formatShortcut` из `@shared/utils`
  - [x] 2.2 Заменить hardcoded "Ctrl+N" в Tooltip на `formatShortcut('N')`
  - [x] 2.3 Обновить тесты в `RoutesPage.test.tsx`

- [x] Task 3: Добавить подсказку shortcuts на Approvals (AC5)
  - [x] 3.1 Добавить Typography.Text под таблицей с подсказкой
  - [x] 3.2 Текст: "Клавиши: A — одобрить, R — отклонить (при фокусе на строке)"
  - [x] 3.3 Добавлено в Table footer

## Dev Notes

### Текущая реализация shortcuts

**RoutesPage.tsx (строки 31-42):**
```typescript
useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    // Проверяем Ctrl+N или Cmd+N (для Mac)
    if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
      e.preventDefault()
      handleCreateRoute()
    }
  }
  window.addEventListener('keydown', handleKeyDown)
  return () => window.removeEventListener('keydown', handleKeyDown)
}, [handleCreateRoute])
```

**Проблема:** Tooltip показывает hardcoded "Ctrl+N" (строка 55):
```tsx
<Tooltip title="Ctrl+N">
```

**ApprovalsPage.tsx (строки 162-168):**
```typescript
const handleRowKeyDown = (e: React.KeyboardEvent, route: PendingRoute) => {
  if (e.key === 'a' || e.key === 'A') {
    handleApprove(route.id)
  } else if (e.key === 'r' || e.key === 'R') {
    handleOpenRejectModal(route)
  }
}
```

**Проблема:** Нет UI подсказки о доступных shortcuts.

### Архитектурные паттерны

**Определение OS:**
```typescript
// Современный способ (Chromium 93+, Safari 15+)
const isMac = navigator.userAgentData?.platform === 'macOS'

// Fallback для старых браузеров
const isMacFallback = /Mac|iPhone|iPad|iPod/.test(navigator.platform)

// Комбинированный подход
function isMacOS(): boolean {
  if ('userAgentData' in navigator && navigator.userAgentData?.platform) {
    return navigator.userAgentData.platform === 'macOS'
  }
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform)
}
```

**Символы модификаторов:**
- macOS: `⌘` (Command), `⌥` (Option), `⇧` (Shift), `⌃` (Control)
- Windows/Linux: `Ctrl`, `Alt`, `Shift`

### Project Structure Notes

**Новые файлы:**
```
frontend/admin-ui/src/shared/utils/
├── keyboard.ts          # утилита определения OS и форматирования shortcuts
└── keyboard.test.ts     # unit тесты
```

**Файлы для изменения:**
```
frontend/admin-ui/src/shared/utils/index.ts           # добавить экспорт
frontend/admin-ui/src/features/routes/components/RoutesPage.tsx    # OS-specific tooltip
frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx  # подсказка shortcuts
```

### Technical Requirements

**keyboard.ts API:**
```typescript
/**
 * Определяет, является ли текущая ОС macOS.
 */
export function isMacOS(): boolean {
  if ('userAgentData' in navigator && navigator.userAgentData?.platform) {
    return navigator.userAgentData.platform === 'macOS'
  }
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform)
}

/**
 * Возвращает символ модификатора для текущей ОС.
 * macOS: '⌘', Windows/Linux: 'Ctrl'
 */
export function getModifierKey(): '⌘' | 'Ctrl' {
  return isMacOS() ? '⌘' : 'Ctrl'
}

/**
 * Форматирует keyboard shortcut для текущей ОС.
 * @param key - основная клавиша (e.g., 'N', 'S', 'Enter')
 * @param includeModifier - включить модификатор (default: true)
 * @returns форматированный shortcut (e.g., '⌘+N' или 'Ctrl+N')
 */
export function formatShortcut(key: string, includeModifier = true): string {
  if (!includeModifier) return key
  return `${getModifierKey()}+${key}`
}
```

**TypeScript типы для userAgentData:**
```typescript
// Расширение Navigator для userAgentData (Navigator API High Entropy)
declare global {
  interface Navigator {
    userAgentData?: {
      platform: string
      brands: Array<{ brand: string; version: string }>
      mobile: boolean
    }
  }
}
```

**RoutesPage.tsx обновление:**
```tsx
import { formatShortcut } from '@shared/utils'

// В компоненте:
<Tooltip title={formatShortcut('N')}>
  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRoute}>
    Новый маршрут
  </Button>
</Tooltip>
```

**ApprovalsPage.tsx подсказка:**
```tsx
// Вариант 1: Под таблицей
<Typography.Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
  💡 Клавиши: A — одобрить, R — отклонить (при фокусе на строке)
</Typography.Text>

// Вариант 2: В Table footer
<Table
  footer={() => (
    <Text type="secondary">
      Клавиши: A — одобрить, R — отклонить (при фокусе на строке)
    </Text>
  )}
/>
```

### Testing Requirements

**Unit тесты keyboard.ts:**
```typescript
describe('keyboard utils', () => {
  describe('isMacOS', () => {
    it('возвращает true для macOS через userAgentData', () => {
      // Mock navigator.userAgentData
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'macOS' },
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })

    it('возвращает false для Windows через userAgentData', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: { platform: 'Windows' },
        configurable: true,
      })
      expect(isMacOS()).toBe(false)
    })

    it('использует fallback navigator.platform когда userAgentData отсутствует', () => {
      Object.defineProperty(navigator, 'userAgentData', {
        value: undefined,
        configurable: true,
      })
      Object.defineProperty(navigator, 'platform', {
        value: 'MacIntel',
        configurable: true,
      })
      expect(isMacOS()).toBe(true)
    })
  })

  describe('getModifierKey', () => {
    it('возвращает ⌘ для macOS', () => { ... })
    it('возвращает Ctrl для Windows/Linux', () => { ... })
  })

  describe('formatShortcut', () => {
    it('форматирует shortcut с модификатором для macOS', () => {
      // Mock macOS
      expect(formatShortcut('N')).toBe('⌘+N')
    })

    it('форматирует shortcut с модификатором для Windows', () => {
      // Mock Windows
      expect(formatShortcut('N')).toBe('Ctrl+N')
    })

    it('возвращает только клавишу когда includeModifier=false', () => {
      expect(formatShortcut('N', false)).toBe('N')
    })
  })
})
```

**Тесты RoutesPage.tsx:**
```typescript
describe('RoutesPage', () => {
  it('показывает OS-specific shortcut в tooltip', () => {
    // Mock для macOS
    render(<RoutesPage />)
    const button = screen.getByRole('button', { name: /новый маршрут/i })
    // Hover и проверка tooltip содержит '⌘+N' или 'Ctrl+N'
  })
})
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 16.9]
- [Source: frontend/admin-ui/src/features/routes/components/RoutesPage.tsx — текущие shortcuts]
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx — keyboard navigation]
- [Source: frontend/admin-ui/src/shared/utils/ — существующие утилиты]
- [Navigator.userAgentData MDN](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/userAgentData)

### Previous Story Intelligence

**Story 16.8 паттерны:**
- localStorage для сохранения настроек пользователя
- Создание hook в `hooks/` папке для переиспользуемой логики
- Unit тесты рядом с компонентами (`*.test.tsx`)

**Story 16.7 паттерны:**
- Изменение иконок в меню через существующие компоненты
- Минимальные изменения для достижения цели

### Edge Cases

1. **Server-Side Rendering (SSR):** `navigator` недоступен на сервере — нужен fallback
2. **Electron/Tauri apps:** могут иметь кастомные platform значения
3. **Mobile devices:** iOS возвращает "iPhone"/"iPad" — тоже macOS семейство
4. **Chrome OS:** возвращает "Chrome OS" — должен использовать Ctrl

### Migration Notes

**Изменение поведения:**
- Tooltip на кнопке "Новый маршрут" будет динамическим
- На Approvals появится подсказка о shortcuts

**Backward Compatibility:**
- Keyboard shortcuts продолжают работать как раньше
- Только UI отображение меняется

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — no issues encountered

### Completion Notes List

1. **Task 1**: Создан `keyboard.ts` с функциями `isMacOS()`, `getModifierKey()`, `formatShortcut()`. Использует Navigator.userAgentData API с fallback на navigator.platform. 18 unit тестов.
2. **Task 2**: RoutesPage обновлён — tooltip на кнопке "Новый маршрут" показывает '⌘+N' на Mac, 'Ctrl+N' на Windows/Linux. Keyboard shortcuts работают одинаково (AC4 unchanged).
3. **Task 3**: ApprovalsPage — добавлена подсказка "💡 Клавиши: A — одобрить, R — отклонить (при фокусе на строке)" в footer таблицы.

### Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-03-04 | SM | Story created — ready for dev |
| 2026-03-04 | Dev Agent | Implementation complete — 872 tests pass |
| 2026-03-04 | Code Review | Review passed — 1 HIGH, 2 MEDIUM fixed; added comment for empty platform behavior, made tooltip test deterministic |

### File List

**New:**
- `frontend/admin-ui/src/shared/utils/keyboard.ts`
- `frontend/admin-ui/src/shared/utils/keyboard.test.ts`

**Modified:**
- `frontend/admin-ui/src/shared/utils/index.ts`
- `frontend/admin-ui/src/features/routes/components/RoutesPage.tsx`
- `frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx`
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx`
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx`
