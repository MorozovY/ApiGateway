# Story 13.0: Local GitLab Infrastructure Setup

Status: done
Story Points: 5

## Story

As a **DevOps Engineer**,
I want GitLab CE running locally in Docker,
So that I have full control over CI/CD infrastructure (prerequisite for FR60-FR72).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Secrets Management (Phase 3, 2026-02-26)
**Business Value:** Локальный GitLab обеспечивает полный контроль над CI/CD инфраструктурой, независимость от внешних сервисов, возможность работы offline. Это prerequisite для всех остальных stories Epic 13.

## Acceptance Criteria

### AC1: GitLab CE Running
**Given** Docker environment on local machine
**When** GitLab docker-compose is started
**Then** GitLab CE is accessible at http://localhost:8929
**And** root user can login with initial password
**And** Web UI fully functional

### AC2: GitLab Runner Registered
**Given** GitLab CE is running
**When** GitLab Runner container is started
**Then** Runner is registered with GitLab instance
**And** Runner appears in Admin → Runners
**And** Runner can pick up jobs

### AC3: Container Registry Enabled
**Given** GitLab CE is running
**When** Container Registry is configured
**Then** Registry is accessible at localhost:5050
**And** docker login localhost:5050 succeeds
**And** docker push/pull works

### AC4: Data Persistence
**Given** GitLab stack is running with data
**When** docker-compose down && docker-compose up -d
**Then** all repositories preserved
**And** all settings preserved
**And** all CI/CD variables preserved

### AC5: Documentation
**Given** GitLab infrastructure is set up
**When** setup is complete
**Then** README in gitlab folder documents:
- How to start/stop GitLab
- How to access Web UI
- How to get initial root password
- How to register additional runners
- Network requirements for CI/CD

## Tasks / Subtasks

- [x] Task 1: GitLab CE Docker Setup (AC: #1, #4)
  - [x] 1.1 Создать директорию ~/gitlab (или другую вне проекта)
  - [x] 1.2 Создать docker-compose.yml для GitLab CE
  - [x] 1.3 Настроить volumes для config, logs, data
  - [x] 1.4 Настроить ports: 8929 (HTTP), 8922 (SSH), 5050 (Registry)
  - [x] 1.5 Запустить и дождаться инициализации (~3-5 минут)
  - [x] 1.6 Получить initial root password из logs или файла

- [x] Task 2: GitLab Runner Setup (AC: #2)
  - [x] 2.1 Добавить gitlab-runner service в docker-compose.yml
  - [x] 2.2 Получить registration token из GitLab Admin → Runners
  - [x] 2.3 Зарегистрировать runner с docker executor
  - [x] 2.4 Проверить что runner появился в UI и active

- [x] Task 3: Container Registry Configuration (AC: #3)
  - [x] 3.1 Включить Registry в gitlab.rb или environment variables
  - [x] 3.2 Настроить registry external URL (localhost:5050)
  - [x] 3.3 Для insecure registry: добавить в Docker daemon.json
  - [x] 3.4 Протестировать docker login, push, pull

- [x] Task 4: Network Configuration
  - [x] 4.1 Убедиться что runner может достучаться до GitLab
  - [x] 4.2 Проверить что deployment targets могут pull из registry
  - [x] 4.3 Документировать firewall/network requirements

- [x] Task 5: Documentation (AC: #5)
  - [x] 5.1 Создать README.md в gitlab директории
  - [x] 5.2 Документировать все команды запуска/остановки
  - [x] 5.3 Добавить troubleshooting секцию

## API Dependencies Checklist

N/A — это инфраструктурная story без API зависимостей.

## Technical Notes

### Минимальные требования
- **RAM:** 4GB минимум для GitLab CE
- **Disk:** 10GB+ для data
- **Ports:** 8929, 8922, 5050 должны быть свободны

### Docker Compose пример

```yaml
# ~/gitlab/docker-compose.yml
version: '3.8'

services:
  gitlab:
    image: gitlab/gitlab-ce:latest
    container_name: gitlab
    hostname: gitlab.local
    restart: unless-stopped
    environment:
      GITLAB_OMNIBUS_CONFIG: |
        external_url 'http://localhost:8929'
        gitlab_rails['gitlab_shell_ssh_port'] = 8922
        registry_external_url 'http://localhost:5050'
        registry['enable'] = true
    ports:
      - "8929:8929"
      - "8922:22"
      - "5050:5050"
    volumes:
      - gitlab_config:/etc/gitlab
      - gitlab_logs:/var/log/gitlab
      - gitlab_data:/var/opt/gitlab
    shm_size: '256m'

  gitlab-runner:
    image: gitlab/gitlab-runner:latest
    container_name: gitlab-runner
    restart: unless-stopped
    depends_on:
      - gitlab
    volumes:
      - runner_config:/etc/gitlab-runner
      - /var/run/docker.sock:/var/run/docker.sock

volumes:
  gitlab_config:
  gitlab_logs:
  gitlab_data:
  runner_config:
```

### Initial Root Password

```bash
# После первого запуска (в течение 24 часов):
docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password

# Или через UI: первый вход на http://localhost:8929
# Username: root
# Password: из файла выше
```

### Runner Registration

```bash
# Получить token: GitLab UI → Admin → CI/CD → Runners → Register

docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "http://gitlab:8929" \
  --registration-token "YOUR_TOKEN" \
  --executor "docker" \
  --docker-image "docker:latest" \
  --description "local-docker-runner" \
  --docker-privileged \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock"
```

### Insecure Registry (для localhost)

```json
// /etc/docker/daemon.json (Linux) или Docker Desktop settings
{
  "insecure-registries": ["localhost:5050"]
}
```

## Dev Agent Record

### Implementation Plan
- Создать docker/gitlab директорию внутри проекта для version control конфигурации
- Использовать Docker named volumes для персистентности данных
- GitLab CE + Runner в одном docker-compose.yml
- Healthcheck для автоматического запуска runner после готовности GitLab

### Debug Log
- GitLab 18.9.1 удалил поддержку `grafana['enable']` — исправлено в docker-compose.yml
- Windows Git Bash конвертирует пути — использовать `sh -c` для команд внутри контейнера
- GitLab 18.x использует новый workflow регистрации runner с authentication token (glrt-*)

### Completion Notes
- GitLab CE 18.9.1 успешно запущен на http://localhost:8929
- Container Registry доступен на localhost:5050
- GitLab Runner зарегистрирован с docker executor
- Data persistence проверена: данные сохраняются после docker-compose down/up
- Документация создана: README.md, WINDOWS-SETUP.md, скрипты верификации

### File List
- `docker/gitlab/docker-compose.yml` — GitLab CE + Runner конфигурация
- `docker/gitlab/README.md` — полная документация на русском
- `docker/gitlab/WINDOWS-SETUP.md` — Windows-специфичные инструкции
- `docker/gitlab/scripts/verify-setup.sh` — Bash скрипт верификации
- `docker/gitlab/scripts/verify-setup.ps1` — PowerShell скрипт верификации
- `docker/gitlab/scripts/register-runner.sh` — Bash скрипт регистрации runner
- `docker/gitlab/scripts/register-runner.ps1` — PowerShell скрипт регистрации runner
- `docker/gitlab/.gitignore` — игнорирование локальных данных
- `docker/gitlab/.env.example` — шаблон для environment variables

## Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-02-26 | SM | Story created (Sprint Change Proposal — local GitLab) |
| 2026-02-26 | Dev Agent | Implementation complete: GitLab CE + Runner + Registry configured and tested |
| 2026-02-26 | Code Review | Fixed 7 issues: version pinning, security (password masking), UTF-8 encoding, shebang portability, URL consistency, added .env.example |
