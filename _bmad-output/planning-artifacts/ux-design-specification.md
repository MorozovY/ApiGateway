---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments:
  - 'prd.md'
  - 'product-brief-ApiGateway-2026-02-10.md'
  - 'architecture.md'
  - 'brainstorming-session-2026-02-10.md'
project_name: 'ApiGateway'
user_name: 'Yury'
date: '2026-02-11'
---

# UX Design Specification ApiGateway

**Author:** Yury
**Date:** 2026-02-11

---

<!-- UX design content will be appended sequentially through collaborative workflow steps -->

## Executive Summary

### Project Vision

ApiGateway Admin UI — внутренний инструмент для self-service управления API маршрутами. Цель: сократить время настройки маршрута с часов до минут, освободить DevOps от рутины, дать Security полный контроль над публикацией.

### Target Users

**Primary Users:**

| Персона | Роль | Частота | Ключевой сценарий |
|---------|------|---------|-------------------|
| **Мария** | Backend Developer | Несколько раз в неделю | Создать маршрут, отправить на согласование |
| **Дмитрий** | Security Specialist | Ежедневно | Проверить и одобрить маршруты, провести аудит |

**Secondary User:**

| Персона | Роль | Частота | Ключевой сценарий |
|---------|------|---------|-------------------|
| **Алексей** | DevOps Engineer | При инцидентах | Найти проблемный маршрут, изменить rate limit |

**User Characteristics:**
- Технические специалисты — комфортны с плотным UI, таблицами, фильтрами
- Desktop-first (офис) — не требуется мобильная адаптация
- Efficiency-focused — ценят скорость и клавиатурные shortcuts

### Key Design Challenges

1. **Workflow Visibility** — каждая роль должна видеть релевантный статус (мои drafts, pending approvals, all published)
2. **Efficient Bulk Operations** — Security обрабатывает много согласований, нужны batch actions
3. **Error Prevention** — валидация до сабмита (path conflicts, invalid URLs)
4. **Powerful Search & Filter** — быстрый поиск по 100+ маршрутам для аудита

### Design Opportunities

1. **Role-Based Dashboard** — персонализированный home screen для каждой роли
2. **Inline Quick Actions** — approve/reject без перехода на детальную страницу
3. **Smart Defaults** — автозаполнение полей на основе существующих паттернов
4. **Keyboard Shortcuts** — для power users (Дмитрий обрабатывает много согласований)

## Core User Experience

### Defining Experience

**Core User Actions по ролям:**

| Роль | Core Action | Критерий успеха |
|------|-------------|-----------------|
| **Мария** | Создать маршрут → Submit for approval | < 2 минут от идеи до сабмита |
| **Дмитрий** | Review → Approve/Reject | < 30 секунд на решение по одному маршруту |
| **Алексей** | Find route → Adjust rate limit | < 1 минута при инциденте |

**Ключевые фокусы:**
- Для Марии: форма создания маршрута должна быть интуитивной и защищать от ошибок
- Для Дмитрия: список pending approvals с inline actions (не надо открывать каждый маршрут)

### Platform Strategy

| Аспект | Решение |
|--------|---------|
| **Platform** | Web SPA (Desktop-first) |
| **Input** | Mouse + Keyboard (shortcuts для power users) |
| **Offline** | Не требуется |
| **Min Resolution** | 1280px width |
| **Optimal Resolution** | 1920px width |

### Effortless Interactions

| Interaction | Как сделать effortless |
|-------------|----------------------|
| **Создание маршрута** | Smart defaults, автозаполнение path из URL |
| **Поиск маршрута** | Instant search, фильтры сохраняются между сессиями |
| **Approve/Reject** | Один клик, без подтверждения для approve |
| **Навигация** | Sidebar всегда видна, breadcrumbs для контекста |

### Critical Success Moments

| Момент | Почему критичен | UX решение |
|--------|-----------------|------------|
| **First route created** | Мария понимает "я могу сама" | Success toast + статус "Pending Approval" |
| **Approval complete** | Дмитрий видит эффект своего действия | Маршрут исчезает из pending, notification автору |
| **Route found during incident** | Алексей быстро находит проблему | Powerful search + recent routes |

### Experience Principles

1. **Efficiency over discovery** — UI для экспертов, не для новичков. Плотный, информативный.
2. **Visibility of status** — Всегда видно статус (draft, pending, published, rejected)
3. **Safe by default** — Валидация предотвращает ошибки ДО сабмита
4. **Keyboard-friendly** — Shortcuts для частых действий (⌘+N новый, ⌘+Enter сабмит)

## Desired Emotional Response

### Primary Emotional Goals

| Персона | Желаемые эмоции |
|---------|-----------------|
| **Мария** | Уверенность ("я контролирую процесс"), Автономность ("не завишу от DevOps") |
| **Дмитрий** | Контроль ("вижу всю картину"), Эффективность ("быстро справляюсь") |
| **Алексей** | Спокойствие ("система под контролем"), Доверие ("данные точные") |

### Emotional Journey Mapping

| Этап | Желаемая эмоция | UX решение |
|------|-----------------|------------|
| **Вход в систему** | Ориентированность | Dashboard с релевантной информацией для роли |
| **Создание маршрута** | Уверенность | Валидация в реальном времени, подсказки |
| **Ожидание approval** | Информированность | Видимый статус, понятный timeline |
| **Получение reject** | Конструктивность | Чёткая причина отказа, easy fix |
| **Успешная публикация** | Удовлетворение | Success notification, маршрут в списке published |

### Micro-Emotions

| Состояние | Цель | Как достичь |
|-----------|------|-------------|
| **Confidence vs Confusion** | Confidence | Понятные labels, consistent layout |
| **Trust vs Skepticism** | Trust | Реальные данные, быстрый refresh |
| **Accomplishment vs Frustration** | Accomplishment | Clear success states, progress indicators |

### Emotions to Avoid

| Эмоция | Триггер | Как предотвратить |
|--------|---------|-------------------|
| **Frustration** | Форма сбросилась после ошибки | Сохранять данные, показывать inline errors |
| **Anxiety** | "А что если я сломаю production?" | Draft/Approval workflow, нет прямой публикации |
| **Confusion** | "Где мой маршрут?" | Фильтры "My routes", статус-badges |

### Emotional Design Principles

1. **Professional Confidence** — UI создаёт ощущение профессионального инструмента, не "игрушки"
2. **No Surprises** — Предсказуемое поведение, нет hidden actions
3. **Safe to Explore** — Можно смотреть без страха что-то сломать (view vs edit modes)
4. **Progress Visibility** — Всегда видно где ты в процессе (статусы, breadcrumbs)

## UX Pattern Analysis & Inspiration

### Inspiring Products Analysis

**Ant Design Pro:**
- Layout: Collapsible sidebar, breadcrumbs, header actions
- Tables: ProTable с фильтрами, сортировкой, inline actions
- Forms: Stepped forms, dynamic fields, real-time validation
- Cards: Статистика на dashboard, quick actions

**Vercel Dashboard:**
- Status indicators: Colored dots (green/yellow/red) + text
- Deployments list: Compact rows, instant preview
- Minimal chrome: Фокус на контент, не на UI
- Instant feedback: Optimistic updates, progress indicators

**GitHub:**
- Search: Command palette (⌘K), instant results
- Tabs: Horizontal tabs для разделов (Code, Issues, PRs)
- Activity feed: Timeline с actions и статусами
- Diff view: Side-by-side comparison

### Transferable UX Patterns

| Паттерн | Источник | Применение в ApiGateway |
|---------|----------|------------------------|
| **Sidebar Navigation** | Ant Design Pro | Главная навигация с иконками и группировкой |
| **Breadcrumbs** | Ant Design Pro | Контекст на детальных страницах |
| **ProTable** | Ant Design Pro | Список маршрутов с фильтрами и actions |
| **Status Badges** | Vercel | Draft/Pending/Published/Rejected статусы |
| **Toast Notifications** | Vercel | Feedback после actions |
| **Command Palette** | GitHub | Quick search и navigation (Phase 2) |
| **Inline Actions** | GitHub | Approve/Reject без перехода на страницу |

### Anti-Patterns to Avoid

| Anti-Pattern | Проблема | Наше решение |
|--------------|----------|--------------|
| **Modals для всего** | Теряется контекст | Inline editing, slide-over panels |
| **Confirmation для каждого action** | Замедляет работу | Undo вместо confirm (где возможно) |
| **Hidden filters** | Не видно активные фильтры | Visible filter bar с chips |
| **Pagination без count** | Непонятен объём данных | "Showing 1-20 of 156 routes" |
| **Full page refresh** | Потеря состояния | SPA с optimistic updates |

### Design Inspiration Strategy

| Стратегия | Элементы |
|-----------|----------|
| **Adopt (взять как есть)** | Ant Design Pro Layout, ProTable, Form components |
| **Adapt (адаптировать)** | Vercel status badges → наши статусы, GitHub activity → наш audit log |
| **Avoid (избегать)** | AWS Console complexity, Jira overwhelming UI |

