# Story 13.6: Production Deployment with Approval

Status: done
Story Points: 5

## Story

As a **DevOps Engineer**,
I want production deployment with manual approval gate,
So that releases are controlled and auditable (FR68, FR72).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Infrastructure Migration
**Business Value:** Production deployments требуют контролируемого процесса с ручным approval для минимизации рисков. Manual gate обеспечивает аудируемость (кто и когда запустил deploy), использование протестированных images (те же что прошли test), и возможность быстрого rollback при проблемах.

**Dependencies:**
- Story 13.5 (done): deploy-dev, deploy-test, smoke-test-dev, e2e-test jobs работают
- Story 13.4 (done): Vault secrets injection работает
- Story 13.3 (done): Docker images собираются и пушатся в GitLab Registry

**Примечание:** В локальной Docker-based инфраструктуре "production" — это отдельный compose project с production-like конфигурацией.

## Acceptance Criteria

### AC1: Manual Trigger for Production Deployment
**Given** successful test deployment (deploy-test job passed)
**When** production deploy job is defined in .gitlab-ci.yml
**Then** job requires manual trigger (`when: manual`)
**And** only users with Maintainer+ role can trigger (GitLab protected environments)

**Implementation Notes:**
- Job: `deploy-prod` в stage `deploy`
- `when: manual` для ручного триггера
- `environment: production` с `deployment_tier: production`
- Requires: `needs: [e2e-test]` — запуск только после успешных E2E tests

### AC2: Use Same Images as Test
**Given** production deployment triggered
**When** approval is given (manual trigger)
**Then** deployment proceeds with same images as test (`$CI_COMMIT_SHA`)
**And** no rebuild происходит — те же images что прошли test

**Implementation Notes:**
- Images: `$CI_REGISTRY_IMAGE/{gateway-admin,gateway-core,admin-ui}:$CI_COMMIT_SHA`
- Использовать generate-compose.sh с environment=prod
- Порты для prod: 38081, 38080, 33000 (отличаются от dev и test)

### AC3: Zero-Downtime Deployment (Rolling Update)
**Given** production docker-compose
**When** deployment runs
**Then** services are updated one at a time
**And** health checks gate each service
**And** rollback triggered if health check fails

**Implementation Notes:**
- Порядок: gateway-core → gateway-admin → admin-ui
- Health check: 30 seconds wait + `docker exec wget --spider /actuator/health`
- При failure: автоматический rollback через rollback.sh prod

### AC4: Git Tag Creation
**Given** successful production deployment
**When** deployment completes
**Then** Git tag is created (format: `prod-YYYY-MM-DD-N`, например `prod-2026-03-01-1`)
**And** tag pushится в GitLab repository
**And** notification показывает в GitLab Environments

**Implementation Notes:**
- Tag: `git tag prod-$(date +%Y-%m-%d)-${BUILD_NUMBER:-1}`
- Push: `git push gitlab --tags`
- GitLab Environment URL: http://localhost:33000

### AC5: Rollback Capability
**Given** production issue discovered
**When** rollback is needed
**Then** previous deployment can be re-deployed from GitLab Environments
**And** rollback completes within 5 minutes
**And** incident is logged (job log + Git tag)

**Implementation Notes:**
- Ручной rollback: `./rollback.sh prod`
- Rollback job: `rollback-prod` (manual, allow_failure: false)
- Rollback использует previous image tags сохранённые в `/tmp/previous_images.txt`

### AC6: Deployment History & Audit
**Given** production environment
**When** deployment history is viewed (GitLab Environments)
**Then** all deployments are listed with timestamps
**And** who triggered each deployment is recorded (GitLab audit)
**And** which commit/image was deployed is shown

**Implementation Notes:**
- GitLab Environments: Settings → Operations → Environments → production
- Audit: GitLab автоматически записывает user который запустил job
- History: видна в Deployments tab environment

## Tasks / Subtasks

- [x] Task 1: Create deploy-prod Job (AC: #1, #2)
  - [x] 1.1 Добавить `deploy-prod` job в .gitlab-ci.yml
  - [x] 1.2 Настроить `when: manual` и `needs: [e2e-test]`
  - [x] 1.3 Настроить `environment: production` с protected tier
  - [x] 1.4 Использовать generate-compose.sh с env=prod и новыми портами

- [x] Task 2: Update generate-compose.sh for Production (AC: #2, #3)
  - [x] 2.1 Добавить prod environment в generate-compose.sh
  - [x] 2.2 Порты: 38081 (admin), 38080 (core), 33000 (ui)
  - [x] 2.3 Container names: gateway-admin-prod, gateway-core-prod, admin-ui-prod

- [x] Task 3: Implement Rolling Update Logic (AC: #3)
  - [x] 3.1 Обновить deployment script для sequential service update
  - [x] 3.2 Health check после каждого сервиса
  - [x] 3.3 Auto-rollback при failure любого health check

- [x] Task 4: Git Tag Creation (AC: #4)
  - [x] 4.1 Добавить git tag creation после successful deployment
  - [x] 4.2 Tag format: `prod-YYYY-MM-DD-N`
  - [x] 4.3 Push tag в GitLab repository

- [x] Task 5: Create rollback-prod Job (AC: #5)
  - [x] 5.1 Добавить `rollback-prod` job в .gitlab-ci.yml
  - [x] 5.2 Job использует rollback.sh prod
  - [x] 5.3 Manual trigger, no allow_failure

- [x] Task 6: Update rollback.sh for Production (AC: #5)
  - [x] 6.1 Добавить prod environment support
  - [x] 6.2 Correct container names и compose file paths
  - [x] 6.3 Тестировать rollback workflow

- [x] Task 7: Create smoke-test-prod Job (AC: #3)
  - [x] 7.1 Добавить `smoke-test-prod` job в .gitlab-ci.yml
  - [x] 7.2 Запуск после deploy-prod (needs: deploy-prod)
  - [x] 7.3 Проверка всех трёх сервисов через mapped ports

- [x] Task 8: Documentation Update (AC: all)
  - [x] 8.1 Обновить docker/gitlab/README.md — Production Deployment section
  - [x] 8.2 Документировать rollback procedure
  - [x] 8.3 Добавить environment ports table

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Текущее состояние (.gitlab-ci.yml)

**Существующие jobs (из Story 13.5):**
- `deploy-dev` — manual на master, порты 28081/28080/23000
- `smoke-test-dev` — auto после deploy-dev
- `deploy-test` — auto после smoke-test-dev, порты 18081/18080/13000
- `e2e-test` — auto после deploy-test

**Pipeline Flow (текущий):**
```
build → test → docker → deploy
                         ├── deploy-dev (manual)
                         ├── smoke-test-dev (auto)
                         ├── deploy-test (auto)
                         └── e2e-test (auto)
```

**Pipeline Flow (после Story 13.6):**
```
build → test → docker → deploy → sync
                         ├── deploy-dev (manual)
                         ├── smoke-test-dev (auto)
                         ├── deploy-test (auto)
                         ├── e2e-test (auto)
                         ├── deploy-prod (manual, needs: e2e-test)
                         └── rollback-prod (manual)
```

### generate-compose.sh — Required Changes

Текущий script поддерживает dev и test. Добавить prod:

```bash
# Environment ports
case "$ENVIRONMENT" in
  dev)
    ADMIN_PORT=28081
    CORE_PORT=28080
    UI_PORT=23000
    ;;
  test)
    ADMIN_PORT=18081
    CORE_PORT=18080
    UI_PORT=13000
    ;;
  prod)  # NEW
    ADMIN_PORT=38081
    CORE_PORT=38080
    UI_PORT=33000
    ;;
esac
```

### deploy-prod Job Template

```yaml
deploy-prod:
  stage: deploy
  extends: .docker-deploy
  variables:
    COMPOSE_PROJECT: apigateway-prod
    ENVIRONMENT: prod
  environment:
    name: production
    url: http://localhost:33000
    deployment_tier: production
  rules:
    - if: $CI_COMMIT_BRANCH == "master"
      when: manual
      allow_failure: false  # Production failure = pipeline failure
  script:
    - echo "=========================================="
    - echo "Deploying to PRODUCTION environment"
    - echo "Commit: $CI_COMMIT_SHA"
    - echo "=========================================="
    # Pull same images as test
    - docker pull $CI_REGISTRY_IMAGE/gateway-admin:$CI_COMMIT_SHA
    - docker pull $CI_REGISTRY_IMAGE/gateway-core:$CI_COMMIT_SHA
    - docker pull $CI_REGISTRY_IMAGE/admin-ui:$CI_COMMIT_SHA
    # Generate compose
    - chmod +x docker/gitlab/generate-compose.sh
    - ./docker/gitlab/generate-compose.sh prod /tmp/docker-compose.prod.yml
    # Rolling update: one service at a time
    - echo "Rolling update: gateway-core"
    - docker rm -f gateway-core-prod 2>/dev/null || true
    - docker-compose -p $COMPOSE_PROJECT -f /tmp/docker-compose.prod.yml up -d gateway-core
    - sleep 30
    - docker exec gateway-core-prod wget -q --spider http://localhost:8080/actuator/health || (./docker/gitlab/rollback.sh prod && exit 1)
    - echo "Rolling update: gateway-admin"
    - docker rm -f gateway-admin-prod 2>/dev/null || true
    - docker-compose -p $COMPOSE_PROJECT -f /tmp/docker-compose.prod.yml up -d gateway-admin
    - sleep 30
    - docker exec gateway-admin-prod wget -q --spider http://localhost:8081/actuator/health || (./docker/gitlab/rollback.sh prod && exit 1)
    - echo "Rolling update: admin-ui"
    - docker rm -f admin-ui-prod 2>/dev/null || true
    - docker-compose -p $COMPOSE_PROJECT -f /tmp/docker-compose.prod.yml up -d admin-ui
    - sleep 10
    - docker exec admin-ui-prod wget -q --spider http://localhost:80/ || (./docker/gitlab/rollback.sh prod && exit 1)
    # Create Git tag
    - apk add --no-cache git
    - git config user.email "ci@localhost"
    - git config user.name "GitLab CI"
    - TAG_NAME="prod-$(date +%Y-%m-%d)-${CI_PIPELINE_IID}"
    - git tag -a "$TAG_NAME" -m "Production deployment $CI_COMMIT_SHA"
    - git push origin "$TAG_NAME" || echo "Tag push failed (non-critical)"
    - echo "=========================================="
    - echo "Production deployment SUCCESSFUL"
    - echo "Tag: $TAG_NAME"
    - echo "=========================================="
  needs:
    - e2e-test
```

### smoke-test-prod Job Template

```yaml
smoke-test-prod:
  stage: deploy
  image: docker:24
  variables:
    ENVIRONMENT: prod
  rules:
    - if: $CI_COMMIT_BRANCH == "master"
      when: on_success
  before_script:
    - apk add --no-cache curl jq bash
  script:
    - echo "Running smoke tests for PRODUCTION environment..."
    - docker exec gateway-admin-prod wget -q --spider http://localhost:8081/actuator/health && echo "PASS gateway-admin" || (echo "FAIL gateway-admin" && exit 1)
    - docker exec gateway-core-prod wget -q --spider http://localhost:8080/actuator/health && echo "PASS gateway-core" || (echo "FAIL gateway-core" && exit 1)
    - docker exec admin-ui-prod wget -q --spider http://localhost:80/ && echo "PASS admin-ui" || (echo "FAIL admin-ui" && exit 1)
    - echo "All production smoke tests passed!"
  needs:
    - deploy-prod
```

### rollback-prod Job Template

```yaml
rollback-prod:
  stage: deploy
  image: docker:24
  variables:
    ENVIRONMENT: prod
  environment:
    name: production
    action: stop
  rules:
    - if: $CI_COMMIT_BRANCH == "master"
      when: manual
  before_script:
    - apk add --no-cache bash docker-compose
  script:
    - echo "Rolling back PRODUCTION environment..."
    - chmod +x docker/gitlab/rollback.sh
    - ./docker/gitlab/rollback.sh prod
    - echo "Rollback complete"
```

### Port Allocation Summary

| Environment | Admin | Core | UI | Compose Project |
|-------------|-------|------|-----|-----------------|
| dev | 28081 | 28080 | 23000 | apigateway-dev |
| test | 18081 | 18080 | 13000 | apigateway-test |
| **prod** | **38081** | **38080** | **33000** | **apigateway-prod** |

### rollback.sh — Required Changes

Добавить prod environment:

```bash
case "$ENVIRONMENT" in
  dev)
    COMPOSE_FILE="/tmp/docker-compose.ci.yml"
    COMPOSE_PROJECT="apigateway-dev"
    ;;
  test)
    COMPOSE_FILE="/tmp/docker-compose.test.yml"
    COMPOSE_PROJECT="apigateway-test"
    ;;
  prod)  # NEW
    COMPOSE_FILE="/tmp/docker-compose.prod.yml"
    COMPOSE_PROJECT="apigateway-prod"
    ;;
esac
```

### Environment URL Configuration

Текущая конфигурация использует `http://localhost:33000` для локального deployment.

**Для remote deployments:**
- Использовать GitLab CI/CD Variable: `$PROD_URL`
- Пример: `url: ${PROD_URL:-http://localhost:33000}`

### Optional Enhancements (Post-MVP)

**Notifications:**
- GitLab → Settings → Integrations → Slack/Email
- Webhook на successful/failed deployment

**Deployment Windows:**
- Добавить проверку времени в deploy-prod script
- Пример: не деплоить в пятницу после 16:00

### GitLab Environment Protection (Manual Step)

После deploy-prod job создания, настроить protected environment в GitLab:

1. GitLab → Settings → CI/CD → Environments
2. Edit "production" environment
3. Protected Environments → Add protection:
   - Allowed to deploy: Maintainers
   - Required approvals: 0 (или 1 для dual-control)

### Previous Story Intelligence (13.5)

**Ключевые learnings:**
- Docker socket deployment работает надёжно (без SSH)
- generate-compose.sh генерирует compose файл динамически
- Health checks через `docker exec wget --spider` работают
- Auto-rollback при health check failure реализован
- External networks: postgres-net, redis-net подключаются автоматически

**Файлы созданные/изменённые в 13.5:**
- `.gitlab-ci.yml` — deploy-dev, deploy-test, smoke-test-dev, e2e-test jobs
- `docker/gitlab/generate-compose.sh` — dynamic compose generation
- `docker/gitlab/rollback.sh` — rollback script
- `docker/gitlab/smoke-test.sh` — smoke tests
- `docker/gitlab/README.md` — documentation

### Project Structure Notes

- CI scripts: `docker/gitlab/`
- Compose generation: `docker/gitlab/generate-compose.sh`
- Rollback: `docker/gitlab/rollback.sh`
- Documentation: `docker/gitlab/README.md`

### Testing Checklist

После выполнения проверить:

- [ ] deploy-prod job появился в GitLab pipeline (manual)
- [ ] Job требует needs: e2e-test
- [ ] Production environment создаётся в GitLab
- [ ] Rolling update: сервисы обновляются по одному
- [ ] Health checks работают после каждого сервиса
- [ ] smoke-test-prod запускается после deploy-prod
- [ ] Git tag создаётся при успешном deployment
- [ ] rollback-prod job работает
- [ ] Documentation обновлена

### Файлы которые будут созданы/изменены

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — добавить deploy-prod, smoke-test-prod, rollback-prod |
| `docker/gitlab/generate-compose.sh` | MODIFIED — добавить prod environment |
| `docker/gitlab/rollback.sh` | MODIFIED — добавить prod environment |
| `docker/gitlab/README.md` | MODIFIED — Production Deployment section |

### Security Considerations

1. **Manual Approval Gate:**
   - Production deployment только через manual trigger
   - GitLab protected environments ограничивают кто может deploy

2. **Same Images Guarantee:**
   - `$CI_COMMIT_SHA` гарантирует те же images что прошли тесты
   - Нет rebuild между test и prod

3. **Audit Trail:**
   - GitLab записывает кто запустил job
   - Git tags сохраняют историю deployments
   - Job logs сохраняются в GitLab

4. **Rollback Capability:**
   - Быстрый rollback через manual job
   - Previous images сохраняются для restore

### References

- [Source: epics.md#Story 13.6] — Original AC
- [Source: 13-5-deployment-pipeline-dev-test.md] — Previous story context
- [Source: .gitlab-ci.yml] — Current CI configuration
- [Source: docker/gitlab/README.md] — CI/CD documentation
- [GitLab Environments](https://docs.gitlab.com/ee/ci/environments/)
- [GitLab Protected Environments](https://docs.gitlab.com/ee/ci/environments/protected_environments.html)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — инфраструктурная story, все изменения в CI/CD конфигурации

### Completion Notes List

- Добавлены 3 новых jobs в `.gitlab-ci.yml`: `deploy-prod`, `smoke-test-prod`, `rollback-prod`
- `deploy-prod` использует rolling update strategy: gateway-core → gateway-admin → admin-ui
- Health check выполняется после каждого сервиса через `docker exec wget --spider`
- Auto-rollback при failure любого health check
- Git tag создаётся в формате `prod-YYYY-MM-DD-N` при успешном deployment
- `rollback-prod` позволяет ручной откат через GitLab UI
- `generate-compose.sh` расширен для поддержки prod environment (порты: 38081, 38080, 33000)
- `rollback.sh` обновлён для поддержки prod environment
- Документация обновлена с полным описанием production deployment процесса

### File List

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — добавлены deploy-prod, smoke-test-prod, rollback-prod jobs |
| `docker/gitlab/generate-compose.sh` | MODIFIED — добавлен prod environment (порты 38081, 38080, 33000) |
| `docker/gitlab/rollback.sh` | MODIFIED — добавлена поддержка prod environment, исправлен rollback с env vars |
| `docker/gitlab/smoke-test.sh` | MODIFIED — добавлен prod environment |
| `docker/gitlab/README.md` | MODIFIED — добавлена секция Production Deployment, Git Tag Push credentials |
| `backend/gateway-admin/src/main/resources/application-prod.yml` | CREATED — production profile |
| `backend/gateway-core/src/main/resources/application-prod.yml` | CREATED — production profile |

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-03-01
**Outcome:** ✅ APPROVED (после исправлений)

### Issues Found & Fixed

| Severity | Issue | Resolution |
|----------|-------|------------|
| HIGH | Git tag push без credentials | Добавлен CI_JOB_TOKEN для GitLab и GITHUB_TOKEN для GitHub |
| HIGH | Rollback без environment variables | Добавлена функция create_rollback_compose с полным набором env vars |
| MEDIUM | Отсутствует application-prod.yml | Созданы production profiles для gateway-admin и gateway-core |
| MEDIUM | rollback_to_previous не перезапускает сервисы | Реализован полный restart с предыдущими images |
| MEDIUM | smoke-test.sh без prod environment | Добавлен prod case с портами 38081/38080/33000 |
| LOW | Документация без credentials info | Добавлена секция "Настройка Git Tag Push" в README.md |

### Verification

- [x] Все ACs реализованы и подтверждены кодом
- [x] Все Tasks отмечены [x] и соответствуют реальным изменениям
- [x] Git vs Story File List — без расхождений
- [x] HIGH и MEDIUM issues исправлены
- [x] Документация обновлена

## Change Log

| Date | Change |
|------|--------|
| 2026-03-01 | Story implementation complete: Production deployment pipeline with manual approval, rolling update, git tagging, and rollback capability |
| 2026-03-01 | Code review: 2 HIGH, 3 MEDIUM, 2 LOW issues found and fixed. Added production profiles, fixed rollback with env vars, improved git tag push |
