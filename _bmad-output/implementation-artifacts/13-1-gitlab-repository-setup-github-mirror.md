# Story 13.1: GitLab Repository Setup & GitHub Mirror

Status: review
Story Points: 2
Depends On: 13.0 (Local GitLab Infrastructure)

## Story

As a **DevOps Engineer**,
I want GitLab configured as the primary repository with GitHub as a mirror,
So that we have centralized CI/CD while maintaining GitHub presence (FR69).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Secrets Management (Phase 3, 2026-02-26)
**Business Value:** GitLab как основной репозиторий даёт доступ к встроенному CI/CD, Container Registry, и управлению секретами. GitHub mirror сохраняет публичное присутствие и совместимость с существующими интеграциями.

## Acceptance Criteria

### AC1: Repository Push to Local GitLab
**Given** existing GitHub repository with full history
**And** local GitLab running at http://localhost:8929 (Story 13.0)
**When** repository is pushed to local GitLab
**Then** repository contains full history (все коммиты)
**And** all branches preserved (main, feature branches)
**And** all tags preserved (если есть)
**And** repository visible in GitLab Web UI

### AC2: GitHub Push Mirror
**Given** GitLab repository exists with code
**When** push mirror to GitHub is configured
**Then** manual trigger syncs changes to GitHub
**And** sync can be triggered via CI job or UI button
**And** GitHub repository содержит те же коммиты

### AC3: Branch Protection
**Given** GitLab project settings
**When** default branch protection is configured
**Then** main branch requires merge request
**And** direct push to main is blocked
**And** pipeline must pass before merge (настройка для Story 13.2)

### AC4: Team Access
**Given** team members need access
**When** access is configured in GitLab
**Then** Yury has Owner/Maintainer role
**And** SSH key или access token настроен для git операций

## Tasks / Subtasks

- [x] Task 1: Push Repository to Local GitLab (AC: #1)
  - [x] 1.1 Войти в GitLab Web UI http://localhost:8929 как root
  - [x] 1.2 Create new project: `api-gateway` (blank project, no README)
  - [x] 1.3 Добавить remote: `git remote add gitlab http://localhost:8929/root/api-gateway.git`
  - [x] 1.4 Push all branches: `git push gitlab --all`
  - [x] 1.5 Push all tags: `git push gitlab --tags`
  - [x] 1.6 Verify: все коммиты видны в GitLab → Repository → Commits

- [x] Task 2: GitHub Mirror Configuration (AC: #2)
  - [x] 2.1 Создать GitHub Personal Access Token (PAT) с правами `repo`
  - [x] 2.2 В GitLab: Settings → CI/CD → Variables
  - [x] 2.3 Добавить `GITHUB_TOKEN` (masked, protected)
  - [x] 2.4 Создать `.gitlab-ci.yml` с manual sync job
  - [x] 2.5 Commit `.gitlab-ci.yml` и push в GitLab
  - [x] 2.6 Протестировать: запустить job, проверить GitHub

- [x] Task 3: Branch Protection Rules (AC: #3)
  - [x] 3.1 GitLab → Settings → Repository → Protected branches
  - [x] 3.2 Protect `master`: Allowed to push = No one
  - [x] 3.3 Allowed to merge = Maintainers
  - [x] 3.4 Require pipeline success (отложено до Story 13.2)

- [x] Task 4: Team Access Configuration (AC: #4)
  - [x] 4.1 Settings → Access Tokens → Project Access Token
  - [x] 4.2 Create token с правами `write_repository`
  - [x] 4.3 Или: SSH key для git операций
  - [x] 4.4 Проверить clone/push работает

- [x] Task 5: Documentation Update
  - [x] 5.1 Добавить секцию в docker/gitlab/README.md про ApiGateway repo
  - [x] 5.2 Обновить главный README.md с GitLab как primary remote

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Prerequisites (Story 13.0 MUST be done)

Перед началом убедиться:
- GitLab CE запущен: http://localhost:8929 отвечает
- Root пароль известен (или сменён)
- GitLab Runner зарегистрирован: Admin → CI/CD → Runners показывает active runner
- Container Registry доступен: localhost:5050

```bash
# Проверка GitLab готовности
curl -s http://localhost:8929/-/health | grep "GitLab OK"

# Проверка Runner (в UI или через API)
docker exec -it gitlab-runner gitlab-runner list
```

### Git Remote Configuration

Текущая конфигурация проекта:
```bash
# Посмотреть текущие remotes
git remote -v

# Ожидаемый результат до story:
origin  https://github.com/MorozovY/ApiGateway.git (fetch/push)

# Ожидаемый результат после story:
origin  https://github.com/MorozovY/ApiGateway.git (fetch/push)
gitlab  http://localhost:8929/root/api-gateway.git (fetch/push)
```

### Push to GitLab Commands

```bash
# Добавить GitLab как remote
git remote add gitlab http://localhost:8929/root/api-gateway.git

# При push потребуется авторизация:
# Username: root
# Password: <GitLab root password или access token>

# Push all branches
git push gitlab --all

# Push all tags
git push gitlab --tags
```

### .gitlab-ci.yml для Mirror

Минимальный `.gitlab-ci.yml` для GitHub sync:

```yaml
stages:
  - sync

sync-to-github:
  stage: sync
  image: alpine/git:latest
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
      when: manual
      allow_failure: true
  script:
    - git config --global user.email "ci@localhost"
    - git config --global user.name "GitLab CI"
    - git remote add github https://oauth2:${GITHUB_TOKEN}@github.com/MorozovY/ApiGateway.git || true
    - git fetch --unshallow || true
    - git push github HEAD:main --force
    - git push github --tags --force
```

**Важно:**
- Job запускается только вручную (`when: manual`)
- `allow_failure: true` — sync не блокирует другие pipelines
- `GITHUB_TOKEN` должен быть в CI/CD Variables (masked!)

### GitHub Personal Access Token

Для создания PAT:
1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token
3. Scopes: `repo` (full control)
4. Copy token СРАЗУ — GitHub показывает его только один раз

### Network Considerations

- GitLab внутри Docker network доступен по hostname `gitlab`
- Для git operations с хоста используем `localhost:8929`
- Для CI jobs внутри Docker используем `http://gitlab:8929`

### Порядок работы после этой Story

1. Основная разработка → push в GitLab
2. CI pipeline запускается автоматически (Story 13.2+)
3. После merge в main → manual trigger sync to GitHub
4. GitHub всегда отстаёт на один sync (это OK для mirror)

### Previous Story Intelligence (13.0)

Из Story 13.0 важно знать:
- GitLab версия: 18.9.1 (не 17.x — новая система runner registration)
- Registration token формат: `glrt-*` (не старый формат)
- Windows Git Bash: пути конвертируются, использовать `sh -c` для команд внутри контейнера
- Data persistence: volumes сохраняют данные между restart

### Testing Checklist

После выполнения всех tasks проверить:

- [ ] `git log --oneline -5` в GitLab Web UI показывает те же коммиты что локально
- [ ] GitLab → Repository → Branches показывает main и другие ветки
- [ ] GitLab → Repository → Tags показывает теги (если есть)
- [ ] Branch protection: попытка `git push gitlab main:main` должна быть rejected
- [ ] CI pipeline: `.gitlab-ci.yml` виден в GitLab → CI/CD → Pipelines
- [ ] GitHub sync: после manual trigger GitHub показывает те же коммиты

### Файлы которые будут изменены

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | NEW — создать в корне проекта |
| `docker/gitlab/README.md` | Добавить секцию про ApiGateway repo |
| `README.md` (main) | Обновить clone instructions |

### Project Structure Notes

- `.gitlab-ci.yml` — в корне проекта (стандартное расположение)
- GitLab CI variables — НЕ в коде, только в GitLab UI
- GitHub PAT — НЕ коммитить, только в CI/CD Variables

### References

- [Source: docker/gitlab/README.md] — документация локального GitLab
- [Source: epics.md#Epic 13] — Epic 13 детали
- [Source: sprint-change-proposal-2026-02-26.md] — причина перехода на локальный GitLab
- [Source: 13-0-local-gitlab-infrastructure.md] — prerequisite story

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Исправлена проблема с Windows Git Bash path conversion в gitlab-runner config.toml
- Исправлена проблема с `clone_url` — добавлен `http://gitlab:8929` для docker network
- Изменён image с `alpine/git` на `alpine` из-за entrypoint конфликта

### Completion Notes List

- ✅ Repository pushed to GitLab with all branches (master, fix/12-9-1-remove-legacy-cookie-auth)
- ✅ GitHub mirror configured with manual sync job (GITHUB_TOKEN in CI/CD Variables)
- ✅ Sync job tested and working — syncs master branch to GitHub on manual trigger
- ✅ Branch protection enabled for master (no direct push, maintainers can merge)
- ✅ Project Access Token created for git operations
- ✅ Documentation updated in docker/gitlab/README.md and README.md

### File List

| File | Change |
|------|--------|
| `.gitlab-ci.yml` | NEW — GitLab CI pipeline with sync-to-github job |
| `docker/gitlab/README.md` | MODIFIED — Added ApiGateway repository section |
| `README.md` | MODIFIED — Added Git Repositories section with GitLab as primary |

## Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-02-26 | SM | Story created from Epic 13 |
| 2026-02-26 | SM | Updated for local GitLab (Sprint Change Proposal) |
| 2026-02-26 | SM | Enhanced with full dev context, marked ready-for-dev |
| 2026-02-26 | Dev Agent | Implemented all tasks, story ready for review |
