# Story 15.2: Починить кнопку "Руководство" внутри системы

Status: done

## Story

As a **User**,
I want the "Guide" button to work correctly,
So that I can access system documentation when needed.

## Корень проблемы

**Story 10.7** добавила кнопку "Руководство" (`/docs/quick-start-guide.html`) в header.
После **Story 13.8** (миграция на Traefik + Caddy) routing сломался:

| Компонент | Значение | Проблема |
|-----------|----------|----------|
| MainLayout.tsx | `href="/docs/quick-start-guide.html"` | ✅ Корректно |
| docs/ folder | `quick-start-guide.html` существует | ✅ Существует |
| Caddy config | `try_files {path} /index.html` | ❌ **Файл не в /srv!** |

**Caddy** ищет файл в `/srv/docs/quick-start-guide.html`, но:
- `frontend/admin-ui/dist` содержит только Vite build
- `docs/quick-start-guide.html` не копируется в dist

## Acceptance Criteria

### AC1: Guide button opens documentation
**Given** пользователь авторизован в системе
**When** пользователь кликает на кнопку "Руководство"
**Then** открывается страница с документацией
**And** документация соответствует текущей версии системы

### AC2: Guide accessible from any page
**Given** кнопка "Руководство"
**When** проверяется её расположение и видимость
**Then** кнопка доступна из любой вкладки системы
**And** иконка и текст кнопки понятны пользователю

## Решение

**Вариант A (Рекомендуемый):** Скопировать документацию в `public/docs/`.

Vite автоматически включает содержимое `public/` в build без изменений.
После сборки файл будет в `dist/docs/quick-start-guide.html`.

**Шаги:**
1. Создать `frontend/admin-ui/public/docs/`
2. Скопировать `docs/quick-start-guide.html` → `frontend/admin-ui/public/docs/`
3. Удалить дубликат из `docs/` или оставить как source of truth

**Почему этот вариант лучше:**
- Минимальное изменение (копирование файла)
- Не меняем Dockerfile
- Не меняем Traefik routing
- Документация становится частью frontend build

## Tasks / Subtasks

- [x] Task 1: Перенести документацию в public/ (AC: #1)
  - [x] 1.1 Создать `frontend/admin-ui/public/docs/` директорию
  - [x] 1.2 Скопировать `docs/quick-start-guide.html` в public/docs/
  - [x] 1.3 Удалить `docs/quick-start-guide.html` из корня (дубликат)

- [x] Task 2: Проверить через CI/CD (AC: #1, #2)
  - [x] 2.1 Push изменений
  - [x] 2.2 Дождаться deploy
  - [x] 2.3 Проверить что https://gateway.ymorozov.ru/docs/quick-start-guide.html отдаёт документацию

## Dev Notes

### Vite public folder

Vite копирует содержимое `public/` в `dist/` без изменений:
- `public/docs/quick-start-guide.html` → `dist/docs/quick-start-guide.html`
- Файлы доступны по root path: `/docs/quick-start-guide.html`

### References

- [Source: Story 10.7] — оригинальная реализация Quick Start Guide
- [Source: Story 13.8] — миграция на Traefik + Caddy
- [Source: frontend/admin-ui/src/layouts/MainLayout.tsx:105] — кнопка Guide

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Completion Notes List

1. **Task 1 (перенос документации):** Файл `quick-start-guide.html` перемещён из `docs/` в `frontend/admin-ui/public/docs/`. Vite автоматически копирует содержимое `public/` в `dist/` при build.

2. **Task 2 (CI/CD верификация):**
   - Коммит `c849d2e` запушен в gitlab/master
   - Docker job `docker-admin-ui` успешно собрал образ
   - Backend docker jobs failed из-за DNS timeout (не влияет на frontend)
   - Вручную обновлён контейнер `admin-ui-dev` на новый образ
   - URL https://gateway.ymorozov.ru/docs/quick-start-guide.html проверен и работает

### File List

- `frontend/admin-ui/public/docs/quick-start-guide.html` (added)
- `docs/quick-start-guide.html` (deleted)
