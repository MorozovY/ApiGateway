# Story 16.1: Унификация языка интерфейса на русский

## Метаданные

| Поле | Значение |
|------|----------|
| Epic | 16 — UI/UX Consistency & Improvements |
| Story | 16.1 |
| Status | Done |
| Effort | Medium |
| Priority | High |
| Completed | 2026-03-04 |

---

## User Story

**As a** User,
**I want** all interface elements to be in one language (Russian),
**So that** I don't experience cognitive load from language switching.

---

## Acceptance Criteria

### AC1: Sidebar навигация на русском
- [x] Dashboard → Главная
- [x] Users → Пользователи
- [x] Consumers → Потребители
- [x] Routes → Маршруты
- [x] Rate Limits → Лимиты
- [x] Approvals → Согласования
- [x] Audit Logs → Аудит
- [x] Integrations → Интеграции
- [x] Metrics → Метрики
- [x] Test → Тестирование

### AC2: Dashboard на русском
- [x] "Welcome, {username}!" → "Добро пожаловать, {username}!"
- [x] "Role: Admin" → "Роль: Администратор"
- [x] Developer → Разработчик
- [x] Security → Безопасность

### AC3: Заголовки таблиц на русском

**RoutesTable:**
- [x] Path → Путь
- [x] Upstream URL → URL сервиса
- [x] Methods → Методы
- [x] Rate Limit → Лимит
- [x] Status → Статус
- [x] Auth → Авторизация
- [x] Author → Автор
- [x] Created → Создано
- [x] Actions → Действия

**UsersTable:**
- [x] Username → Пользователь
- [x] Email → Email (оставлено)
- [x] Role → Роль
- [x] Status → Статус
- [x] Actions → Действия

**ConsumersTable:**
- [x] Client ID → ID клиента
- [x] Status → Статус
- [x] Rate Limit → Лимит
- [x] Created → Создано
- [x] Actions → Действия

**RateLimitsTable:**
- [x] Name → Название
- [x] Description → Описание
- [x] Requests/sec → Запросов/сек
- [x] Burst Size → Размер всплеска
- [x] Used By → Используется
- [x] Actions → Действия

**ApprovalsTable:**
- [x] Path → Путь
- [x] Upstream URL → URL сервиса
- [x] Methods → Методы
- [x] Submitted By → Отправил
- [x] Submitted At → Отправлено
- [x] Actions → Действия

**AuditLogsTable:**
- [x] Timestamp → Время

**TopRoutesTable (Metrics):**
- [x] Path → Путь
- [x] Total Requests → Всего запросов

**UpstreamsTable:**
- [x] Path → Путь
- [x] Rate Limit → Лимит
- [x] Upstream Host → Хост сервиса

### AC4: Кнопки и действия
- [x] New Route → Новый маршрут
- [x] New User → Новый пользователь
- [x] New Consumer → Новый потребитель
- [x] New Rate Limit → Новый лимит
- [x] Edit → Редактировать
- [x] Delete → Удалить
- [x] View → Просмотр
- [x] Save → Сохранить
- [x] Cancel → Отмена
- [x] Submit for Approval → На согласование (Отправить на согласование)
- [x] Approve → Одобрить
- [x] Reject → Отклонить
- [x] Open in Grafana → Открыть в Grafana

### AC5: Заголовки страниц
- [x] Dashboard → Главная
- [x] Routes → Маршруты
- [x] Users → Пользователи
- [x] Consumers → Потребители
- [x] Rate Limits → Лимиты трафика
- [x] Approvals → Согласования
- [x] Audit Logs → Журнал аудита
- [x] Integrations → Интеграции
- [x] Metrics → Метрики
- [x] Test → Тестирование

### AC6: Метрики страница
- [x] Total Requests → Всего запросов
- [x] RPS → RPS (оставлено)
- [x] Avg Latency → Средняя задержка
- [x] P95 Latency → P95 задержка
- [x] Error Rate → Ошибки
- [x] Active Routes → Активных маршрутов
- [x] Time Range: → Период:
- [x] Top Routes by Requests → Топ маршрутов по запросам
- [x] Open in Grafana → Открыть в Grafana

### AC7: Header
- [x] Admin Panel → Панель управления

### AC8: Фильтры и поиск
- [x] "Все статусы" — уже на русском
- [x] "Поиск по path, upstream..." — обновлено на русский
- [x] "Сбросить фильтры" — обновлено на русский

---

## Файлы изменённые

### Layouts
| Файл | Изменения |
|------|-----------|
| `src/layouts/Sidebar.tsx` | Labels меню (10 пунктов) на русском |
| `src/layouts/Sidebar.test.tsx` | Обновлены тесты для русских labels |
| `src/layouts/MainLayout.tsx` | "Admin Panel" → "Панель управления" |
| `src/layouts/MainLayout.test.tsx` | Обновлены тесты |

### Features — Dashboard
| Файл | Изменения |
|------|-----------|
| `src/features/dashboard/components/DashboardPage.tsx` | Welcome, Role на русском |
| `src/features/dashboard/components/DashboardPage.test.tsx` | Обновлены тесты |

### Features — Routes
| Файл | Изменения |
|------|-----------|
| `src/features/routes/components/RoutesPage.tsx` | Title, Button на русском |
| `src/features/routes/components/RoutesTable.tsx` | Column titles на русском |
| `src/features/routes/components/RouteForm.tsx` | Form labels, buttons на русском |
| `src/features/routes/components/RouteDetailsCard.tsx` | Labels на русском |

### Features — Users
| Файл | Изменения |
|------|-----------|
| `src/features/users/components/UsersPage.tsx` | Title, Button на русском |
| `src/features/users/components/UsersTable.tsx` | Column titles на русском |
| `src/features/users/components/UsersPage.test.tsx` | Обновлены тесты |

### Features — Consumers
| Файл | Изменения |
|------|-----------|
| `src/features/consumers/components/ConsumersPage.tsx` | Title, Button, placeholder на русском |
| `src/features/consumers/components/ConsumersTable.tsx` | Column titles, buttons на русском |
| `src/features/consumers/components/ConsumerRateLimitModal.tsx` | Labels, buttons на русском |
| `src/features/consumers/components/*.test.tsx` | Обновлены тесты |

### Features — Rate Limits
| Файл | Изменения |
|------|-----------|
| `src/features/rate-limits/components/RateLimitsPage.tsx` | Title, Button на русском |
| `src/features/rate-limits/components/RateLimitsTable.tsx` | Column titles на русском |
| `src/features/rate-limits/components/*.test.tsx` | Обновлены тесты |

### Features — Approvals
| Файл | Изменения |
|------|-----------|
| `src/features/approval/components/ApprovalsPage.tsx` | Title, Column titles, Buttons, filters на русском |
| `src/features/approval/components/ApprovalsPage.test.tsx` | Обновлены тесты |

### Features — Audit
| Файл | Изменения |
|------|-----------|
| `src/features/audit/components/AuditPage.tsx` | Title, Export button на русском |
| `src/features/audit/components/AuditLogsTable.tsx` | Timestamp → Время, формат даты |
| `src/features/audit/components/IntegrationsPage.tsx` | Title, Export button на русском |
| `src/features/audit/components/UpstreamsTable.tsx` | Column titles на русском |
| `src/features/audit/components/*.test.tsx` | Обновлены тесты |

### Features — Metrics
| Файл | Изменения |
|------|-----------|
| `src/features/metrics/components/MetricsPage.tsx` | Title, Cards, Labels, error/loading states на русском |
| `src/features/metrics/components/TopRoutesTable.tsx` | Column titles на русском |
| `src/features/metrics/components/*.test.tsx` | Обновлены тесты |

### Features — Test
| Файл | Изменения |
|------|-----------|
| `src/features/test/components/TestPage.tsx` | Title на русском |
| `src/features/test/components/TestPage.test.tsx` | Обновлены тесты |

### Shared
| Файл | Изменения |
|------|-----------|
| `src/shared/constants/roles.ts` | ROLE_OPTIONS и ROLE_LABELS на русском |
| `src/shared/config/pageDescriptions.ts` | Все описания страниц на русском |

---

## Definition of Done

- [x] Все AC выполнены
- [x] Unit тесты обновлены и проходят (715 тестов)
- [x] E2E тесты обновлены и проходят (72 теста)
- [x] Визуальная проверка всех страниц
- [x] Код review пройден

---

## E2E тесты обновлены

| Файл | Изменения |
|------|-----------|
| `e2e/tests/02-dashboard.spec.ts` | Role: Admin → Роль: Администратор |
| `e2e/tests/03-routes-list.spec.ts` | Column headers на русском |
| `e2e/tests/04-routes-create.spec.ts` | Form labels на русском, Upstream URL оставлен |
| `e2e/tests/05-routes-edit.spec.ts` | Form labels на русском |
| `e2e/tests/07-approvals.spec.ts` | Column headers, button names на русском |
| `e2e/tests/09-rate-limits.spec.ts` | Title: Rate Limits → Лимиты трафика |
| `e2e/tests/13-consumers-list.spec.ts` | Column headers, filter labels на русском |
| `e2e/tests/14-consumers-crud.spec.ts` | Modal titles, button names на русском |

---

## Технические заметки

Использован Вариант 1 (прямая замена строк) — оптимально для моноязычного проекта.

**Оставлены на английском:**
- Email — стандартное обозначение
- RPS — технический термин
- HTTP методы (GET, POST, PUT, DELETE, PATCH)
- Path placeholders в формах
- URL — технический термин

**Формат дат:** обновлён на русский формат (DD.MM.YYYY HH:mm) через dayjs.
