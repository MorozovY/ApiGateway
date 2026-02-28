# Story 13.4: Secrets Management via GitLab Variables

Status: ready-for-dev
Story Points: 3

## Story

As a **DevOps Engineer**,
I want all secrets stored in GitLab CI/CD Variables,
So that credentials are not in code and can be rotated safely (FR64, FR65).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Secrets Management (Phase 3, 2026-02-26)
**Business Value:** Централизованное управление секретами через GitLab CI/CD Variables обеспечивает безопасность credentials, позволяет их ротацию без изменения кода и предотвращает случайный commit секретов в репозиторий. Masked variables защищают от утечки в логах.

**Dependencies:**
- Story 13.3 (ready-for-dev): Docker image build использует `$CI_JOB_TOKEN` для registry — этот механизм работает
- Story 13.2 (done): CI pipeline работает, variables передаются в jobs
- Story 13.1 (done): GitLab repository настроен, `$GITHUB_TOKEN` уже используется для mirror sync

## Acceptance Criteria

### AC1: Database Secrets Configured
**Given** GitLab project settings
**When** CI/CD Variables are configured
**Then** the following secrets exist:
- `POSTGRES_PASSWORD` — database password
- `REDIS_PASSWORD` — Redis password (if used)

### AC2: Keycloak Secrets Configured
**Given** GitLab project settings
**When** CI/CD Variables are configured
**Then** the following secrets exist:
- `KEYCLOAK_ADMIN_PASSWORD` — Keycloak admin password
- `KEYCLOAK_CLIENT_SECRET` — client credentials для gateway-admin-ui

### AC3: External Services Secrets Configured
**Given** GitLab project settings
**When** CI/CD Variables are configured
**Then** the following secrets exist:
- `REGISTRY_TOKEN` — Docker registry access (если требуется отдельно от `$CI_JOB_TOKEN`)
- `SSH_PRIVATE_KEY` — deployment key для VMs (для Story 13.5)
- `GITHUB_TOKEN` — для GitHub mirror push (уже существует)

### AC4: Variable Protection Settings
**Given** secrets are defined
**When** they are configured
**Then** all secrets are marked as "Masked"
**And** sensitive secrets are marked as "Protected"
**And** environment-specific secrets use scopes (dev/test/prod)

### AC5: Variables Available in Pipeline
**Given** pipeline runs
**When** job accesses secret
**Then** value is available as environment variable
**And** value is NOT printed in logs (masked)

### AC6: Documented Variables
**Given** `.env.example` in repository
**When** developer reviews it
**Then** all required variables are listed
**And** no actual secrets are present
**And** комментарии указывают какие variables берутся из GitLab CI/CD

### AC7: Secret Rotation Support
**Given** secret rotation needed
**When** variable is updated in GitLab
**Then** next pipeline uses new value
**And** no code change required

## Tasks / Subtasks

- [ ] Task 1: Аудит текущих secrets в проекте (AC: #1, #2, #3)
  - [ ] 1.1 Проверить `.env.example` — какие secrets требуются
  - [ ] 1.2 Проверить `docker-compose.yml` — какие secrets используются
  - [ ] 1.3 Проверить `application.yml` — какие secrets читаются из env
  - [ ] 1.4 Составить полный список secrets для CI/CD

- [ ] Task 2: Настройка GitLab CI/CD Variables (AC: #1, #2, #3, #4)
  - [ ] 2.1 Открыть GitLab → Settings → CI/CD → Variables
  - [ ] 2.2 Добавить `POSTGRES_PASSWORD` (Masked: Yes, Protected: Yes)
  - [ ] 2.3 Добавить `REDIS_PASSWORD` если используется (Masked: Yes)
  - [ ] 2.4 Добавить `KEYCLOAK_ADMIN_PASSWORD` (Masked: Yes, Protected: Yes)
  - [ ] 2.5 Добавить `KEYCLOAK_CLIENT_SECRET` (Masked: Yes, Protected: Yes)
  - [ ] 2.6 Проверить существующий `GITHUB_TOKEN` — настроен для sync job

- [ ] Task 3: SSH Key для deployment (AC: #3)
  - [ ] 3.1 Сгенерировать SSH key pair: `ssh-keygen -t ed25519 -C "gitlab-deploy"`
  - [ ] 3.2 Добавить private key как `SSH_PRIVATE_KEY` (File type, Masked: Yes)
  - [ ] 3.3 Документировать куда добавить public key на target VMs

- [ ] Task 4: Environment-specific Variables (AC: #4)
  - [ ] 4.1 Определить scopes для environments: dev, test, prod
  - [ ] 4.2 Если нужны разные passwords для разных environments — добавить scoped variables
  - [ ] 4.3 Документировать naming convention: `{ENV}_POSTGRES_PASSWORD` или использовать scopes

- [ ] Task 5: Обновить .env.example (AC: #6)
  - [ ] 5.1 Добавить секцию "CI/CD Variables (from GitLab)"
  - [ ] 5.2 Добавить placeholder комментарии для каждого secret
  - [ ] 5.3 Указать что эти variables НЕ должны быть в .env файле локально

- [ ] Task 6: Verification & Documentation (AC: #5, #7)
  - [ ] 6.1 Запустить pipeline и проверить что masked values не показываются в логах
  - [ ] 6.2 Проверить что jobs могут читать variables как env vars
  - [ ] 6.3 Обновить `docker/gitlab/README.md` — секция "CI/CD Variables"
  - [ ] 6.4 Документировать процедуру rotation: изменить в GitLab UI → следующий pipeline использует новое значение

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Текущее состояние

**Существующие secrets в `.env.example`:**
```
POSTGRES_PASSWORD=gateway
REDIS_PASSWORD=(не указан — Redis без пароля локально)
JWT_SECRET=your-secret-key-minimum-32-characters-long
ADMIN_PASSWORD=admin123
```

**Note:** `JWT_SECRET` и `ADMIN_PASSWORD` — legacy от cookie-auth. После миграции на Keycloak они НЕ используются. Но могут остаться для backward compatibility.

**Keycloak secrets (из docker-compose.yml):**
```yaml
KEYCLOAK_ADMIN_PASSWORD: admin
KC_DB_PASSWORD: postgres  # Keycloak DB password
```

**`.gitlab-ci.yml` уже использует:**
```yaml
POSTGRES_PASSWORD: gateway  # hardcoded в variables
GITHUB_TOKEN: $GITHUB_TOKEN  # из CI/CD Variables
```

### GitLab CI/CD Variables UI

Путь: **Settings → CI/CD → Variables → Expand**

**Variable Settings:**
- **Type:** Variable (default) или File (для SSH keys)
- **Protected:** Only expose on protected branches (master/main)
- **Masked:** Hide value in job logs (требует >8 chars, base64-safe)
- **Expand variable reference:** Disable если value содержит `$`

### Текущие hardcoded values в `.gitlab-ci.yml`

```yaml
# backend-test job
POSTGRES_PASSWORD: gateway
```

После этой story они должны читаться из GitLab Variables:
```yaml
POSTGRES_PASSWORD: $POSTGRES_PASSWORD
```

### Environment Scopes

GitLab поддерживает environment scopes для variables:
- `*` — все environments
- `dev` — только dev environment
- `prod` — только production

Для локального GitLab достаточно `*` scope, т.к. нет реальных environments пока (Story 13.5 добавит).

### Naming Conventions

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `POSTGRES_PASSWORD` | Database password | Yes | Yes |
| `REDIS_PASSWORD` | Redis password | No | Yes |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin | Yes | Yes |
| `KEYCLOAK_CLIENT_SECRET` | OAuth client secret | Yes | Yes |
| `SSH_PRIVATE_KEY` | Deployment SSH key (File) | Yes | Yes |
| `GITHUB_TOKEN` | GitHub PAT for mirror | Yes | Yes |

### Previous Story Intelligence (13.3)

Из Story 13.3:
- Docker build использует `$CI_JOB_TOKEN` для registry — автоматический token
- `$CI_REGISTRY_*` variables предоставляются GitLab автоматически
- Не нужен отдельный `REGISTRY_TOKEN` если используется `CI_JOB_TOKEN`

### Testing Checklist

После выполнения проверить:

- [ ] GitLab → Settings → CI/CD → Variables содержит все secrets
- [ ] Pipeline запускается и jobs читают variables
- [ ] В job logs masked values показываются как `[MASKED]`
- [ ] `.gitlab-ci.yml` НЕ содержит hardcoded passwords
- [ ] `.env.example` документирует все CI/CD variables

### Файлы которые будут изменены

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — убрать hardcoded passwords, использовать `$VARIABLE` |
| `.env.example` | MODIFIED — добавить секцию CI/CD Variables с документацией |
| `docker/gitlab/README.md` | MODIFIED — документация CI/CD Variables |

### Security Considerations

1. **Masked Variables:** Минимум 8 символов, нет `$` без escape
2. **Protected Variables:** Только для protected branches (master)
3. **File Variables:** Для multi-line secrets (SSH keys) использовать File type
4. **No Logging:** Никогда не делать `echo $SECRET` в scripts

### References

- [Source: epics.md#Story 13.4] — Story requirements
- [Source: 13-3-docker-image-build-registry.md] — Previous story context
- [Source: .gitlab-ci.yml] — Current CI configuration
- [Source: .env.example] — Current environment template
- [Source: docker/gitlab/README.md] — GitLab infrastructure docs

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List