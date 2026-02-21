# Story {{epic_num}}.{{story_num}}: {{story_title}}

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a {{role}},
I want {{action}},
so that {{benefit}}.

## Acceptance Criteria

1. [Add acceptance criteria from epics/PRD]

## Tasks / Subtasks

- [ ] Task 1 (AC: #)
  - [ ] Subtask 1.1
- [ ] Task 2 (AC: #)
  - [ ] Subtask 2.1

## API Dependencies Checklist

<!-- ОБЯЗАТЕЛЬНО для UI stories. Удалить секцию для backend-only stories. -->

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/example` | GET | `filter`, `sort` | ✅ Существует / ❌ Требуется |

**Проверки перед началом разработки:**

- [ ] Все необходимые endpoints существуют в backend
- [ ] Query параметры поддерживают все фильтры из AC (включая multi-select если нужен)
- [ ] Response format содержит все поля, необходимые для UI
- [ ] Role-based access настроен корректно (какие роли имеют доступ)
- [ ] Pagination поддерживается (если требуется для списков)

**Если endpoint отсутствует или неполный:**
- Создать backend story перед UI story
- Или добавить backend tasks в эту story

## Dev Notes

- Relevant architecture patterns and constraints
- Source tree components to touch
- Testing standards summary

### Project Structure Notes

- Alignment with unified project structure (paths, modules, naming)
- Detected conflicts or variances (with rationale)

### References

- Cite all technical details with source paths and sections, e.g. [Source: docs/<file>.md#Section]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
