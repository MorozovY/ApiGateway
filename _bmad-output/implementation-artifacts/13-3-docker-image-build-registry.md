# Story 13.3: Docker Image Build & Registry

Status: done
Story Points: 5

## Story

As a **DevOps Engineer**,
I want Docker images built and pushed to GitLab Container Registry,
So that deployments use versioned, immutable images (FR62, FR63).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Secrets Management (Phase 3, 2026-02-26)
**Business Value:** Версионированные Docker images обеспечивают воспроизводимые deployments. GitLab Container Registry интегрирован с CI/CD pipeline, что упрощает управление жизненным циклом images. Immutable images предотвращают проблему "works on my machine".

**Dependencies:**
- Story 13.2 (done): CI pipeline с build/test stages работает
- Story 13.1 (done): GitLab repository настроен
- Story 13.0 (done): Local GitLab с Runner работает

## Acceptance Criteria

### AC1: Gateway-Admin Image Build
**Given** successful build stage
**When** docker build job runs for gateway-admin
**Then** image is built with tag `$CI_COMMIT_SHA`
**And** image is pushed to `registry.gitlab.com/$PROJECT/gateway-admin`

### AC2: Gateway-Core Image Build
**Given** successful build stage
**When** docker build job runs for gateway-core
**Then** image is built with tag `$CI_COMMIT_SHA`
**And** image is pushed to `registry.gitlab.com/$PROJECT/gateway-core`

### AC3: Admin-UI Image Build
**Given** successful build stage
**When** docker build job runs for admin-ui
**Then** image is built with tag `$CI_COMMIT_SHA`
**And** image is pushed to `registry.gitlab.com/$PROJECT/admin-ui`

### AC4: Release Tagging
**Given** merge to main branch
**When** release job runs
**Then** images are also tagged with `latest`
**And** semantic version tag if present (v1.2.3)

### AC5: Registry Cleanup Policy
**Given** old images in registry
**When** cleanup policy is configured
**Then** images older than 30 days are removed
**And** tagged releases are preserved

## Tasks / Subtasks

- [x] Task 1: Добавить Docker stage в `.gitlab-ci.yml` (AC: #1, #2, #3)
  - [x] 1.1 Добавить stage `docker` после `test`
  - [x] 1.2 Настроить Docker socket mount (runners уже имеют /var/run/docker.sock)
  - [x] 1.3 Добавить CI_REGISTRY_* variables reference
  - [x] 1.4 Login в GitLab Container Registry (`docker login`)

- [x] Task 2: Docker Build Jobs для каждого сервиса (AC: #1, #2, #3)
  - [x] 2.1 Job `docker-gateway-admin`: build + push image
  - [x] 2.2 Job `docker-gateway-core`: build + push image
  - [x] 2.3 Job `docker-admin-ui`: build + push image
  - [x] 2.4 Настроить needs: зависимость от build stage + artifacts
  - [x] 2.5 Tagging: `$CI_COMMIT_SHA`, `$CI_COMMIT_REF_SLUG`

- [x] Task 3: Оптимизация Dockerfiles (AC: #1, #2, #3)
  - [x] 3.1 Backend Dockerfiles уже оптимальны (JRE Alpine, копируют JAR)
  - [x] 3.2 Добавить `.dockerignore` для ускорения build context
  - [x] 3.3 Создать `Dockerfile.admin-ui.ci` — использует pre-built dist/

- [x] Task 4: Release Tagging (AC: #4)
  - [x] 4.1 Условие: `if [ "$CI_COMMIT_BRANCH" == "master" ]`
  - [x] 4.2 Добавить tag `latest` для images на master
  - [x] 4.3 Добавить semantic version tag из `$CI_COMMIT_TAG`
  - [x] 4.4 Условие: `if [ -n "$CI_COMMIT_TAG" ]`

- [x] Task 5: Registry Cleanup Policy (AC: #5)
  - [x] 5.1 GitLab Settings → Packages & Registries — доступно
  - [x] 5.2 Cleanup policy документирована: older than 30 days
  - [x] 5.3 Preserve regex: `^v\d+\.\d+\.\d+$|^latest$|^master$`
  - [x] 5.4 Документировано в docker/gitlab/README.md

- [x] Task 6: Testing & Documentation
  - [x] 6.1 Push тестовый commit, CI pipeline запущен
  - [x] 6.2 Проверить images в GitLab Container Registry UI
  - [x] 6.3 Pull и запуск image локально для проверки
  - [x] 6.4 Обновить docker/gitlab/README.md

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Текущее состояние

**`.gitlab-ci.yml`:** Уже содержит stages: build, test, sync. Нужно добавить stage `docker` между test и sync.

**Dockerfiles:** Существуют production-ready Dockerfiles:
- `docker/Dockerfile.gateway-admin` — JRE 21 Alpine, копирует JAR
- `docker/Dockerfile.gateway-core` — JRE 21 Alpine, копирует JAR
- `docker/Dockerfile.admin-ui` — multi-stage: Node builder + Nginx

### GitLab Container Registry

Локальный GitLab использует Registry на порту 5050:
- Registry URL: `localhost:5050` (или `gitlab.example.com:5050` в production)
- Login: `docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY`
- Image URL format: `$CI_REGISTRY_IMAGE/<service>:$CI_COMMIT_SHA`

**Важно:** Локальный GitLab может требовать настройки Registry в `docker/gitlab/docker-compose.yml`. Проверить что Registry включен и доступен.

### CI Variables для Docker

GitLab автоматически предоставляет переменные в CI:
```
CI_REGISTRY           = localhost:5050 (or gitlab.example.com:5050)
CI_REGISTRY_USER      = gitlab-ci-token
CI_REGISTRY_PASSWORD  = $CI_JOB_TOKEN
CI_REGISTRY_IMAGE     = localhost:5050/root/api-gateway
CI_COMMIT_SHA         = полный commit hash
CI_COMMIT_SHORT_SHA   = короткий hash (7 chars)
CI_COMMIT_REF_SLUG    = branch name (slugified)
CI_COMMIT_TAG         = git tag (если есть)
```

### Docker-in-Docker (DinD) Configuration

Для сборки images в CI нужен Docker. Варианты:

**1. Docker-in-Docker (DinD) service:**
```yaml
docker-build:
  stage: docker
  image: docker:24
  services:
    - docker:24-dind
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build ...
```

**2. Docker Socket Mount (если runners настроены):**
```yaml
docker-build:
  stage: docker
  image: docker:24
  script:
    - docker login ...
    - docker build ...
  tags:
    - docker-socket
```

**Рекомендация:** Использовать DinD для простоты и изоляции.

### Example Docker Jobs

```yaml
.docker-base:
  stage: docker
  image: docker:24
  services:
    - docker:24-dind
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY

docker-gateway-admin:
  extends: .docker-base
  needs:
    - backend-build
  script:
    - docker build -t $CI_REGISTRY_IMAGE/gateway-admin:$CI_COMMIT_SHA -f docker/Dockerfile.gateway-admin .
    - docker push $CI_REGISTRY_IMAGE/gateway-admin:$CI_COMMIT_SHA
    - |
      if [ "$CI_COMMIT_BRANCH" == "master" ]; then
        docker tag $CI_REGISTRY_IMAGE/gateway-admin:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/gateway-admin:latest
        docker push $CI_REGISTRY_IMAGE/gateway-admin:latest
      fi

docker-gateway-core:
  extends: .docker-base
  needs:
    - backend-build
  script:
    - docker build -t $CI_REGISTRY_IMAGE/gateway-core:$CI_COMMIT_SHA -f docker/Dockerfile.gateway-core .
    - docker push $CI_REGISTRY_IMAGE/gateway-core:$CI_COMMIT_SHA

docker-admin-ui:
  extends: .docker-base
  needs:
    - frontend-build
  script:
    - docker build -t $CI_REGISTRY_IMAGE/admin-ui:$CI_COMMIT_SHA -f docker/Dockerfile.admin-ui .
    - docker push $CI_REGISTRY_IMAGE/admin-ui:$CI_COMMIT_SHA
```

### Dockerfiles — Анализ

**Dockerfile.gateway-admin:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/gateway-admin/build/libs/gateway-admin-*.jar app.jar
EXPOSE 8081
HEALTHCHECK ...
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- Копирует уже собранный JAR из build stage
- Требует: `backend/gateway-admin/build/libs/` должен быть доступен
- В CI: артефакты от backend-build job

**Dockerfile.admin-ui:**
```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY frontend/admin-ui/package*.json ./
RUN npm ci
COPY frontend/admin-ui/ ./
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK ...
CMD ["nginx", "-g", "daemon off;"]
```
- Multi-stage: собирает внутри Docker
- Не требует артефактов от frontend-build (самодостаточный)
- Альтернатива: использовать dist/ из CI артефактов для ускорения

### Build Context Optimization

Backend Dockerfiles ожидают запуск из корня проекта:
```bash
docker build -f docker/Dockerfile.gateway-admin .
```

Это означает что весь проект копируется в build context. Нужен `.dockerignore`:

```
# .dockerignore
.git
node_modules
*.log
.env*
docker/gitlab/
_bmad*
docs/
*.md
!README.md
```

### Registry Cleanup Policy

GitLab → Settings → Packages & Registries → Container Registry:

```
Cleanup policy:
  Enabled: Yes
  Remove tags older than: 30 days
  Remove tags matching: .*
  Do not remove tags matching: ^v\d+\.\d+\.\d+$|^latest$
  Number of tags to keep: 10
```

### Previous Story Intelligence (13.2)

Из Story 13.2:
- DinD service работает для Testcontainers (хотя выбраны GitLab Services)
- Docker socket mount доступен в runners
- Nexus proxy может ускорить npm ci в frontend image build
- JaCoCo и test reports настроены — не влияют на docker stage

### Git Intelligence

Последние коммиты (из sprint-status):
```
5645a5c docs(13.2): code review fixes — frontend coverage, AC/task accuracy
b63f032 fix(ci): increase timeout for async rate limit modal test
6651771 fix(ci): add Mono.delay before async audit log checks
```

### Testing Checklist

После выполнения проверить:

- [ ] Push на branch запускает docker stage
- [ ] `docker-gateway-admin` job успешен
- [ ] `docker-gateway-core` job успешен
- [ ] `docker-admin-ui` job успешен
- [ ] Images видны в GitLab → Packages & Registries → Container Registry
- [ ] Pull image локально: `docker pull localhost:5050/root/api-gateway/gateway-admin:$SHA`
- [ ] Запуск image локально работает
- [ ] На master branch: images tagged `latest`
- [ ] Cleanup policy настроена (GitLab UI)

### Файлы которые будут изменены

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — добавить docker stage и jobs |
| `.dockerignore` | NEW или MODIFIED — оптимизация build context |
| `docker/gitlab/README.md` | MODIFIED — документация Docker Registry |
| `docker/gitlab/docker-compose.yml` | MAYBE — если Registry не настроен |

### Project Structure Notes

```
.gitlab-ci.yml                    # CI configuration
docker/
  Dockerfile.gateway-admin        # Backend admin image
  Dockerfile.gateway-core         # Backend core image
  Dockerfile.admin-ui             # Frontend image (multi-stage)
  Dockerfile.*.dev                # Dev variants (не для CI)
  nginx/nginx.conf                # Nginx config для admin-ui
  gitlab/
    docker-compose.yml            # Local GitLab stack
    README.md                     # GitLab documentation
backend/
  gateway-admin/build/libs/       # JAR артефакты
  gateway-core/build/libs/        # JAR артефакты
frontend/
  admin-ui/dist/                  # Frontend build артефакты
```

### Considerations

1. **Registry Access:** Локальный GitLab на localhost:5050 — проверить что Container Registry включен в gitlab docker-compose
2. **Image Size:** Alpine-based images минимизируют размер
3. **Security:** Не хранить secrets в image layers
4. **Parallel Jobs:** docker jobs могут запускаться параллельно (независимы)

### References

- [Source: epics.md#Story 13.3] — Story requirements
- [Source: 13-2-ci-pipeline-build-test.md] — Previous story context
- [Source: docker/Dockerfile.*] — Existing Dockerfiles
- [Source: .gitlab-ci.yml] — Current CI configuration
- [Source: docker/gitlab/README.md] — GitLab infrastructure docs

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Pipeline #118: All docker jobs successful
- Fixed YAML parsing error (inline comments in script blocks)
- Fixed 413 artifact upload error (increased GitLab limit to 200MB)

### Completion Notes List

- Docker stage добавлен в `.gitlab-ci.yml` с 3 jobs: docker-gateway-admin, docker-gateway-core, docker-admin-ui
- Images успешно собраны и pushed в GitLab Container Registry (localhost:5050)
- Tagging: commit SHA, branch slug, latest (на master), semantic version (на tag)
- Registry cleanup policy документирована в docker/gitlab/README.md
- Images проверены локально: Spring Boot стартует корректно

### File List

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — docker stage, 3 docker jobs, artifact exclude |
| `docker/Dockerfile.admin-ui.ci` | NEW — CI-оптимизированный Dockerfile для admin-ui |
| `.dockerignore` | NEW — исключение ненужных файлов из build context |
| `docker/gitlab/README.md` | MODIFIED — документация Container Registry |

## Change Log

| Дата | Изменение |
|------|-----------|
| 2026-02-28 | Story 13.3 completed: Docker stage в CI, images в Registry |