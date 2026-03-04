# Story 15.4: Инфо-блоки с описанием вкладок

Status: done

## Story

As a **New User**,
I want to see a description of each tab's purpose and capabilities,
So that I can quickly understand what functionality is available.

## Acceptance Criteria

### AC1: Инфо-блок отображается на каждой вкладке
**Given** пользователь открывает любую вкладку системы
**When** страница загружается
**Then** отображается инфо-блок в верхней части страницы
**And** инфо-блок содержит краткое описание назначения вкладки
**And** инфо-блок содержит список ключевых возможностей

### AC2: Блок можно свернуть/развернуть
**Given** инфо-блок на вкладке
**When** пользователь взаимодействует с ним
**Then** блок можно свернуть/развернуть
**And** состояние сохраняется в localStorage

### AC3: Описания для всех 10 вкладок
**Given** 10 вкладок системы
**When** добавляются инфо-блоки
**Then** описания предоставлены для всех вкладок:
- **Dashboard**: обзор системы, ключевые метрики
- **Routes**: управление маршрутами API Gateway
- **Metrics**: мониторинг производительности и трафика
- **Approvals**: согласование маршрутов перед публикацией
- **Audit Logs**: журнал всех изменений в системе
- **Integrations**: отчёт по upstream интеграциям
- **Users**: управление пользователями и ролями
- **Consumers**: управление API consumers
- **Rate Limits**: настройка политик ограничения трафика
- **Test**: генератор нагрузки для тестирования

### AC4: Дизайн соответствует Ant Design guidelines
**Given** дизайн инфо-блока
**When** блок отображается
**Then** стиль соответствует Ant Design guidelines
**And** блок не мешает основному контенту страницы
**And** блок адаптивен для мобильных устройств

## Tasks / Subtasks

- [x] Task 1: Создать компонент PageInfoBlock (AC: #1, #2, #4)
  - [x] 1.1 Создать `frontend/admin-ui/src/shared/components/PageInfoBlock.tsx`
  - [x] 1.2 Использовать Ant Design Collapse + Alert для визуального стиля
  - [x] 1.3 Реализовать сохранение состояния в localStorage (ключ: `pageInfoBlock_{pageKey}`)
  - [x] 1.4 Добавить анимацию expand/collapse
  - [x] 1.5 Создать unit-тесты `PageInfoBlock.test.tsx`

- [x] Task 2: Создать конфиг с описаниями вкладок (AC: #3)
  - [x] 2.1 Создать директорию `frontend/admin-ui/src/shared/config/` (если не существует)
  - [x] 2.2 Создать `frontend/admin-ui/src/shared/config/pageDescriptions.ts`
  - [x] 2.3 Добавить описания для всех 10 вкладок на русском языке
  - [x] 2.4 Включить список ключевых возможностей для каждой вкладки

- [x] Task 3: Интегрировать PageInfoBlock в страницы (AC: #1, #3)
  - [x] 3.1 DashboardPage — добавить инфо-блок
  - [x] 3.2 RoutesPage — добавить инфо-блок
  - [x] 3.3 MetricsPage — добавить инфо-блок (под HealthCheckSection)
  - [x] 3.4 ApprovalsPage — добавить инфо-блок
  - [x] 3.5 AuditPage — добавить инфо-блок
  - [x] 3.6 IntegrationsPage — добавить инфо-блок
  - [x] 3.7 UsersPage — добавить инфо-блок
  - [x] 3.8 ConsumersPage — добавить инфо-блок
  - [x] 3.9 RateLimitsPage — добавить инфо-блок
  - [x] 3.10 TestPage — добавить инфо-блок

- [x] Task 4: Тестирование (AC: #1, #2, #4)
  - [x] 4.1 Unit-тесты для PageInfoBlock
  - [x] 4.2 Проверить корректность отображения на всех страницах
  - [x] 4.3 Проверить сохранение состояния в localStorage
  - [x] 4.4 Проверить адаптивность на мобильных устройствах

## API Dependencies Checklist

**Секция удалена** — это frontend-only story, API не требуется.

## Dev Notes

### Паттерн shared компонентов (ОБЯЗАТЕЛЬНО СЛЕДОВАТЬ!)

Существующие shared компоненты (`FilterChips.tsx`, `ThemeSwitcher.tsx`) используют следующий паттерн:
1. Экспорт интерфейса Props с JSDoc комментариями
2. Экспорт функционального компонента
3. Русские комментарии в коде
4. data-testid для тестирования
5. Отдельный файл с тестами `*.test.tsx`

**Пример из FilterChips.tsx:**
```typescript
// Переиспользуемый компонент FilterChips для отображения активных фильтров (Story 8.8)
import { Space, Tag } from 'antd'

export interface FilterChip {
  /** Уникальный ключ chip */
  key: string
  // ...
}

export function FilterChips({ chips, className }: FilterChipsProps) {
  // ...
}
```

### Архитектурные решения

**Компонент PageInfoBlock:**
- Использовать `Collapse` из Ant Design для expand/collapse функционала
- Визуальный стиль: Card с иконкой InfoCircleOutlined, subtle background
- localStorage ключ формата: `pageInfoBlock_${pageKey}` (например, `pageInfoBlock_dashboard`)
- Начальное состояние: развёрнут (для новых пользователей)

**Структура описания:**
```typescript
interface PageDescription {
  title: string        // Заголовок вкладки
  description: string  // Краткое описание назначения
  features: string[]   // Список ключевых возможностей
}
```

### Компоненты для изменения

| Файл | Изменение |
|------|-----------|
| `src/shared/components/PageInfoBlock.tsx` | **НОВЫЙ** — основной компонент |
| `src/shared/components/PageInfoBlock.test.tsx` | **НОВЫЙ** — unit-тесты |
| `src/shared/config/pageDescriptions.ts` | **НОВЫЙ** — конфиг описаний |
| `src/features/dashboard/components/DashboardPage.tsx` | Добавить PageInfoBlock |
| `src/features/routes/components/RoutesPage.tsx` | Добавить PageInfoBlock |
| `src/features/metrics/components/MetricsPage.tsx` | Добавить PageInfoBlock |
| `src/features/approval/components/ApprovalsPage.tsx` | Добавить PageInfoBlock |
| `src/features/audit/components/AuditPage.tsx` | Добавить PageInfoBlock |
| `src/features/audit/components/IntegrationsPage.tsx` | Добавить PageInfoBlock |
| `src/features/users/components/UsersPage.tsx` | Добавить PageInfoBlock |
| `src/features/consumers/components/ConsumersPage.tsx` | Добавить PageInfoBlock |
| `src/features/rate-limits/components/RateLimitsPage.tsx` | Добавить PageInfoBlock |
| `src/features/test/components/TestPage.tsx` | Добавить PageInfoBlock |

### Текущая структура страниц

Анализ текущих страниц показывает единый паттерн:
- Заголовок страницы (`Title level={2-4}`)
- Основной контент (Table, Form, Cards)

Инфо-блок добавляется **между заголовком и основным контентом**.

**Path Aliases (из tsconfig.json):**
- `@shared/*` → `src/shared/*`
- `@features/*` → `src/features/*`

**Пример интеграции:**
```tsx
// Страница управления маршрутами (Story 3.4, Story 15.4)
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'

export function RoutesPage() {
  return (
    <Card>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Routes</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRoute}>
          New Route
        </Button>
      </Space>

      {/* Инфо-блок после заголовка (Story 15.4) */}
      <PageInfoBlock pageKey="routes" {...PAGE_DESCRIPTIONS.routes} />

      <RoutesTable />
    </Card>
  )
}
```

### Предлагаемые описания вкладок

| Вкладка | Описание | Возможности |
|---------|----------|-------------|
| Dashboard | Главная страница с обзором системы | Приветствие, роль пользователя, быстрый logout |
| Routes | Управление маршрутами API Gateway | Создание, редактирование, публикация маршрутов; фильтрация по статусу |
| Metrics | Мониторинг производительности | RPS, latency, error rate; top routes; интеграция с Grafana |
| Approvals | Согласование маршрутов | Одобрение/отклонение pending маршрутов; причина отклонения |
| Audit Logs | Журнал изменений | История всех действий; фильтры по пользователю, действию, дате; экспорт CSV |
| Integrations | Upstream-сервисы | Список внешних сервисов; количество маршрутов на каждый; экспорт |
| Users | Управление пользователями | Создание пользователей; назначение ролей; сброс паролей |
| Consumers | API consumers | Создание consumers; генерация client secrets; rate limit per-consumer |
| Rate Limits | Политики ограничения | Создание политик; requests/period; привязка к маршрутам |
| Test | Генератор нагрузки | Выбор маршрута; настройка RPS и duration; отслеживание прогресса |

### Дизайн компонента

**Visual Style:**
```
┌─────────────────────────────────────────────────────────────┐
│ ℹ️ Routes — Управление маршрутами API Gateway       [▲/▼]  │
├─────────────────────────────────────────────────────────────┤
│ На этой странице вы можете:                                 │
│ • Создавать и редактировать маршруты                        │
│ • Публиковать маршруты для прохождения согласования         │
│ • Фильтровать по статусу (draft, pending, published)        │
│ • Использовать Ctrl+N для быстрого создания                 │
└─────────────────────────────────────────────────────────────┘
```

**Свёрнутое состояние:**
```
┌─────────────────────────────────────────────────────────────┐
│ ℹ️ Routes — Управление маршрутами API Gateway       [▼]    │
└─────────────────────────────────────────────────────────────┘
```

### localStorage Schema

```typescript
// Ключ: pageInfoBlock_${pageKey}
// Значение: 'collapsed' | 'expanded' | undefined (default: expanded)

localStorage.getItem('pageInfoBlock_routes') // 'collapsed'
localStorage.setItem('pageInfoBlock_dashboard', 'expanded')
```

### Testing Standards

- Unit-тесты компонента PageInfoBlock:
  - Рендеринг с переданными props
  - Expand/collapse по клику
  - Сохранение состояния в localStorage
  - Восстановление состояния при mount
- Integration: проверка отображения на каждой странице

### References

- [Source: epics.md#Story-15.4] — Acceptance Criteria
- [Source: frontend/admin-ui/src/features/routes/components/RoutesPage.tsx] — паттерн страницы
- [Ant Design Collapse](https://ant.design/components/collapse/) — компонент collapse
- [Ant Design Alert](https://ant.design/components/alert/) — визуальный стиль info

### Предыдущая story (15.3) — контекст

Story 15.3 обновила демо-credentials на странице входа:
- Порядок: admin → security → developer
- Пароли: `Admin@Pass!2026`, `Secure#Pass2026`, `Dev!Pass#2026x`
- Синхронизация UI/Keycloak/Backend

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все 716 unit-тестов прошли успешно (после code review)
- Сборка production успешна (npm run build)
- TypeScript компиляция без ошибок

### Completion Notes List

**Story 15.4 — Инфо-блоки с описанием вкладок**

Реализован компонент PageInfoBlock с полным функционалом:
- ✅ AC1: Инфо-блок отображается на каждой из 10 вкладок
- ✅ AC2: Блок можно свернуть/развернуть, состояние сохраняется в localStorage
- ✅ AC3: Описания для всех 10 вкладок на русском языке с ключевыми возможностями
- ✅ AC4: Дизайн соответствует Ant Design (использован Collapse компонент)

**Технические детали:**
- Компонент использует Ant Design Collapse с кастомным label
- localStorage ключи: `pageInfoBlock_{pageKey}` (expanded/collapsed)
- Типизация: строгий тип PageKey для всех 10 страниц
- 12 unit-тестов для PageInfoBlock

**Обновления в тестах:**
- Исправлены селекторы в тестах UsersPage, ConsumersPage, RateLimitsPage, DashboardPage (использование heading role вместо getByText для заголовков)

### File List

**Новые файлы:**
- frontend/admin-ui/src/shared/components/PageInfoBlock.tsx
- frontend/admin-ui/src/shared/components/PageInfoBlock.test.tsx
- frontend/admin-ui/src/shared/config/pageDescriptions.ts
- frontend/admin-ui/src/shared/config/pageDescriptions.test.ts (code review)

**Изменённые файлы:**
- frontend/admin-ui/src/shared/components/index.ts (code review — добавлен экспорт)
- frontend/admin-ui/src/features/dashboard/components/DashboardPage.tsx
- frontend/admin-ui/src/features/dashboard/components/DashboardPage.test.tsx
- frontend/admin-ui/src/features/routes/components/RoutesPage.tsx
- frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx
- frontend/admin-ui/src/features/audit/components/AuditPage.tsx
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx (code review — удалено дублирование)
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.test.tsx (code review)
- frontend/admin-ui/src/features/users/components/UsersPage.tsx
- frontend/admin-ui/src/features/users/components/UsersPage.test.tsx
- frontend/admin-ui/src/features/consumers/components/ConsumersPage.tsx
- frontend/admin-ui/src/features/consumers/components/ConsumersPage.test.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.test.tsx
- frontend/admin-ui/src/features/test/components/TestPage.tsx

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-03-04
**Outcome:** ✅ APPROVED (with fixes applied)

### Issues Found and Fixed

| Severity | Issue | Fix Applied |
|----------|-------|-------------|
| HIGH | DashboardPage: PageInfoBlock перед заголовком (нарушение паттерна) | Перемещён после заголовка внутрь Card |
| HIGH | PageInfoBlock не экспортирован в index.ts | Добавлен экспорт в shared/components/index.ts |
| HIGH | Отсутствовал тест на responsive (AC4) | Добавлены 3 responsive теста |
| MEDIUM | pageKey имел тип string вместо PageKey | Изменён тип на строгий PageKey |
| MEDIUM | IntegrationsPage: дублирующее описание | Удалён избыточный Typography.Text |
| MEDIUM | Отсутствовал тест для pageDescriptions.ts | Создан pageDescriptions.test.ts |

### Test Results After Fixes

- **Total tests:** 716 (было 707)
- **All passing:** ✅
- **TypeScript:** ✅ No errors

### Change Log

| Date | Author | Change |
|------|--------|--------|
| 2026-03-04 | AI Code Review | Fixed 6 issues (3 HIGH, 3 MEDIUM), added 9 new tests |
