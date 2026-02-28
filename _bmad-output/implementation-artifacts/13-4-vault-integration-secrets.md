# Story 13.4: Vault Integration for Secrets

Status: ready-for-dev
Story Points: 5

## Story

As a **DevOps Engineer**,
I want secrets stored in HashiCorp Vault and injected into applications,
So that credentials are centrally managed and securely accessed (FR64, FR65, NFR17).

## Feature Context

**Source:** Epic 13 — GitLab CI/CD & Infrastructure Migration (Sprint Change Proposal 2026-02-28)
**Business Value:** Централизованное управление секретами через Vault обеспечивает единый источник правды для credentials, безопасный доступ через short-lived tokens, audit trail всех операций с секретами и возможность автоматической ротации. Интеграция с централизованной инфраструктурой (infra compose group) упрощает управление секретами для нескольких проектов (ApiGateway, n8n).

**Dependencies:**
- Vault работает в infra compose group (prerequisite)
- Story 13.3 (done): Docker images готовы для deployment
- Story 13.2 (done): CI pipeline работает

**Changed from original:** GitLab CI/CD Variables → HashiCorp Vault (Sprint Change Proposal 2026-02-28)

## Acceptance Criteria

### AC1: Vault Secrets Structure Created
**Given** Vault is running in infra compose group
**When** secrets are configured
**Then** the following paths exist in Vault:
- `secret/apigateway/database` — DATABASE_URL, POSTGRES_PASSWORD
- `secret/apigateway/redis` — REDIS_URL, REDIS_PASSWORD
- `secret/apigateway/keycloak` — KEYCLOAK_CLIENT_SECRET, KEYCLOAK_ADMIN_PASSWORD
- `secret/apigateway/jwt` — JWT_SECRET (если ещё используется)

### AC2: AppRole Authentication Configured
**Given** Vault AppRole auth method enabled
**When** CI/CD pipeline needs to authenticate
**Then** AppRole "apigateway-ci" exists with policies:
- Read access to `secret/apigateway/*`
- No write access (read-only for CI)
**And** Role ID и Secret ID доступны для pipeline

### AC3: Applications Retrieve Secrets from Vault
**Given** gateway-admin and gateway-core start
**When** applications initialize
**Then** they retrieve secrets from Vault:
- DATABASE_URL (PostgreSQL connection string)
- REDIS_URL (Redis connection string)
- KEYCLOAK_CLIENT_SECRET
**And** secrets are available as environment variables or Spring properties
**And** applications fail gracefully with clear error if Vault unavailable

### AC4: Secrets Not Exposed in Logs
**Given** applications running with Vault secrets
**When** logs are generated
**Then** secret values are NOT visible in application logs
**And** secret values are NOT visible in CI/CD job logs
**And** only references like `${VAULT:secret/...}` appear if logged

### AC5: CI/CD Pipeline Vault Integration
**Given** GitLab CI pipeline runs
**When** deployment stage executes
**Then** pipeline authenticates to Vault via AppRole
**And** secrets are injected into deployment without hardcoding
**And** `.gitlab-ci.yml` does NOT contain any secret values

### AC6: Local Development Vault Access
**Given** developer runs applications locally
**When** Vault is accessible
**Then** applications can read secrets from Vault using token auth
**And** `.env.example` documents how to get Vault token
**And** fallback to `.env` file works if Vault unavailable (dev only)

### AC7: Documentation Updated
**Given** Vault integration complete
**When** developer reviews documentation
**Then** `docker/gitlab/README.md` contains Vault section
**And** `CLAUDE.md` references Vault for secrets
**And** Setup instructions explain Vault access

## Tasks / Subtasks

- [ ] Task 1: Audit secrets requirements (AC: #1)
  - [ ] 1.1 Проверить `.env.example` — текущий список secrets
  - [ ] 1.2 Проверить `application.yml` — какие secrets читаются
  - [ ] 1.3 Проверить `docker-compose.yml` — какие secrets передаются
  - [ ] 1.4 Составить финальный список secrets для Vault

- [ ] Task 2: Create Vault secrets structure (AC: #1)
  - [ ] 2.1 Подключиться к Vault UI или CLI
  - [ ] 2.2 Создать path `secret/apigateway/database`:
    - `POSTGRES_PASSWORD`: <password>
    - `DATABASE_URL`: `r2dbc:postgresql://infra-postgres:5432/gateway`
  - [ ] 2.3 Создать path `secret/apigateway/redis`:
    - `REDIS_URL`: `redis://infra-redis:6379`
    - `REDIS_PASSWORD`: <password> (если используется)
  - [ ] 2.4 Создать path `secret/apigateway/keycloak`:
    - `KEYCLOAK_CLIENT_SECRET`: <secret>
    - `KEYCLOAK_ADMIN_PASSWORD`: <password>
  - [ ] 2.5 Документировать структуру secrets

- [ ] Task 3: Configure AppRole for CI/CD (AC: #2, #5)
  - [ ] 3.1 Enable AppRole auth method: `vault auth enable approle`
  - [ ] 3.2 Create policy `apigateway-read`:
    ```hcl
    path "secret/data/apigateway/*" {
      capabilities = ["read"]
    }
    ```
  - [ ] 3.3 Create role `apigateway-ci` with policy
  - [ ] 3.4 Get Role ID: `vault read auth/approle/role/apigateway-ci/role-id`
  - [ ] 3.5 Generate Secret ID: `vault write -f auth/approle/role/apigateway-ci/secret-id`
  - [ ] 3.6 Store Role ID и Secret ID в GitLab CI/CD Variables (эти 2 можно хранить в GitLab)

- [ ] Task 4: Configure applications for Vault (AC: #3, #4)
  - [ ] 4.1 Выбрать подход: Spring Cloud Vault или environment injection
  - [ ] 4.2 Если Spring Cloud Vault:
    - Добавить dependency `spring-cloud-starter-vault-config`
    - Настроить `bootstrap.yml` с Vault settings
  - [ ] 4.3 Если environment injection (рекомендуется для простоты):
    - Использовать `vault agent` sidecar или init container
    - Или читать secrets при старте через script
  - [ ] 4.4 Обновить `application.yml` для чтения secrets из env
  - [ ] 4.5 Проверить что secrets не логируются (Spring Boot actuator/env endpoint)

- [ ] Task 5: Update CI/CD pipeline (AC: #5)
  - [ ] 5.1 Добавить step для Vault auth в deployment jobs
  - [ ] 5.2 Использовать `vault` CLI или GitLab Vault integration
  - [ ] 5.3 Убрать hardcoded passwords из `.gitlab-ci.yml`
  - [ ] 5.4 Проверить что secrets не видны в job logs

- [ ] Task 6: Local development setup (AC: #6)
  - [ ] 6.1 Документировать как получить Vault token для dev
  - [ ] 6.2 Обновить `.env.example` с Vault-related variables:
    - `VAULT_ADDR=http://localhost:8200`
    - `VAULT_TOKEN=<get from vault login>`
  - [ ] 6.3 Добавить fallback на `.env` файл для local dev (когда Vault недоступен)
  - [ ] 6.4 Тест: запуск локально с Vault secrets работает

- [ ] Task 7: Documentation (AC: #7)
  - [ ] 7.1 Обновить `docker/gitlab/README.md` — секция "Vault Integration"
  - [ ] 7.2 Обновить `CLAUDE.md` — упомянуть Vault для secrets
  - [ ] 7.3 Документировать secret rotation procedure
  - [ ] 7.4 Документировать emergency access procedure

## API Dependencies Checklist

N/A — это инфраструктурная story без backend API зависимостей.

## Dev Notes

### Текущее состояние secrets

**`.env.example` (текущий):**
```
POSTGRES_PASSWORD=gateway
JWT_SECRET=your-secret-key-minimum-32-characters-long
ADMIN_PASSWORD=admin123
```

**Note:** `JWT_SECRET` и `ADMIN_PASSWORD` — legacy от cookie-auth. После миграции на Keycloak они могут не использоваться. Проверить!

**`docker-compose.yml` secrets:**
```yaml
gateway-admin:
  environment:
    - SPRING_R2DBC_URL=r2dbc:postgresql://postgres:5432/gateway
    - SPRING_R2DBC_PASSWORD=${POSTGRES_PASSWORD:-gateway}
    - SPRING_DATA_REDIS_HOST=redis
```

### Vault Architecture (infra)

Vault работает в infra compose group:
- URL: `http://vault:8200` (internal) или `http://localhost:8200` (external)
- Storage: file-based или Consul (зависит от infra setup)
- Auth methods: token, approle

### Подход к интеграции

**Рекомендуемый: Environment injection (простой)**

Вместо Spring Cloud Vault (который требует зависимость и bootstrap), использовать:

1. **CI/CD:** Vault CLI в pipeline script
```yaml
deploy:
  script:
    - export VAULT_ADDR=$VAULT_ADDR
    - export VAULT_TOKEN=$(vault write -field=token auth/approle/login role_id=$ROLE_ID secret_id=$SECRET_ID)
    - export DATABASE_URL=$(vault kv get -field=DATABASE_URL secret/apigateway/database)
    - docker-compose up -d
```

2. **Local dev:** `.env` файл как fallback
```bash
# .env (local only, not committed)
DATABASE_URL=r2dbc:postgresql://localhost:5432/gateway
REDIS_URL=redis://localhost:6379
```

### Vault Secrets Structure

```
secret/
└── apigateway/
    ├── database
    │   ├── DATABASE_URL: r2dbc:postgresql://infra-postgres:5432/gateway
    │   └── POSTGRES_PASSWORD: <password>
    ├── redis
    │   ├── REDIS_URL: redis://infra-redis:6379
    │   └── REDIS_PASSWORD: <password>
    └── keycloak
        ├── KEYCLOAK_CLIENT_SECRET: <secret>
        └── KEYCLOAK_ADMIN_PASSWORD: <password>
```

### Vault CLI Commands Reference

```bash
# Login (dev)
vault login

# Enable KV secrets engine (если не включен)
vault secrets enable -path=secret kv-v2

# Write secret
vault kv put secret/apigateway/database \
  DATABASE_URL="r2dbc:postgresql://infra-postgres:5432/gateway" \
  POSTGRES_PASSWORD="securepassword"

# Read secret
vault kv get secret/apigateway/database
vault kv get -field=DATABASE_URL secret/apigateway/database

# Enable AppRole
vault auth enable approle

# Create policy
vault policy write apigateway-read - <<EOF
path "secret/data/apigateway/*" {
  capabilities = ["read"]
}
EOF

# Create role
vault write auth/approle/role/apigateway-ci \
  token_policies="apigateway-read" \
  token_ttl=1h \
  token_max_ttl=4h

# Get role ID (static)
vault read auth/approle/role/apigateway-ci/role-id

# Generate secret ID (per-use or limited)
vault write -f auth/approle/role/apigateway-ci/secret-id

# Login with AppRole
vault write auth/approle/login \
  role_id=<role_id> \
  secret_id=<secret_id>
```

### GitLab CI/CD Variables (minimal)

После этой story в GitLab Variables останутся только:
- `VAULT_ADDR` — Vault server URL
- `VAULT_ROLE_ID` — AppRole Role ID (не secret, но sensitive)
- `VAULT_SECRET_ID` — AppRole Secret ID (secret, masked)
- `GITHUB_TOKEN` — для GitHub mirror (существующий)
- `SSH_PRIVATE_KEY` — для deployment (Story 13.5)

Все остальные secrets (passwords, connection strings) в Vault.

### Spring Boot Configuration

**Если используем env variables (рекомендуется):**

```yaml
# application.yml
spring:
  r2dbc:
    url: ${DATABASE_URL:r2dbc:postgresql://localhost:5432/gateway}
    username: gateway
    password: ${POSTGRES_PASSWORD:gateway}
  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}
```

**Если используем Spring Cloud Vault:**

```yaml
# bootstrap.yml
spring:
  cloud:
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      kv:
        backend: secret
        default-context: apigateway
```

### Security Considerations

1. **Secret ID rotation:** AppRole Secret ID должен регулярно ротироваться
2. **Token TTL:** Короткий TTL для tokens (1h), force re-auth
3. **Audit logging:** Vault audit log включен для трассировки доступа
4. **Network:** Vault доступен только из infra network
5. **No hardcoding:** Никогда не хранить secrets в коде или CI files

### Comparison: GitLab Variables vs Vault

| Aspect | GitLab Variables | Vault |
|--------|------------------|-------|
| **Scope** | Per-project | Centralized (multiple projects) |
| **Rotation** | Manual UI | API/CLI, can automate |
| **Audit** | Limited | Full audit trail |
| **Dynamic secrets** | No | Yes (DB credentials) |
| **Access control** | Branch-based | Fine-grained policies |
| **Versioning** | No | Yes (KV v2) |

### Testing Checklist

После выполнения проверить:

- [ ] Vault secrets созданы в `secret/apigateway/*`
- [ ] AppRole настроен и работает
- [ ] Applications стартуют и читают secrets из Vault
- [ ] Secrets НЕ видны в application logs
- [ ] CI pipeline аутентифицируется в Vault
- [ ] Local development работает с Vault или fallback
- [ ] Documentation обновлена

### Файлы которые будут изменены

| Файл | Изменение |
|------|-----------|
| `.gitlab-ci.yml` | MODIFIED — Vault auth в deployment jobs |
| `application.yml` (both services) | MODIFIED — env variable references |
| `.env.example` | MODIFIED — Vault-related variables |
| `docker/gitlab/README.md` | MODIFIED — Vault integration section |
| `CLAUDE.md` | MODIFIED — mention Vault |

### Previous Story Intelligence (13.3)

Из Story 13.3:
- Docker images собираются и pushятся в GitLab Registry
- `$CI_JOB_TOKEN` используется для Registry — аналогичный паттерн для Vault
- Pipeline структура: build → test → docker → deploy (secrets нужны в deploy)

### References

- [Source: sprint-change-proposal-2026-02-28.md] — Sprint Change Proposal
- [Source: 13-3-docker-image-build-registry.md] — Previous story context
- [Source: .gitlab-ci.yml] — Current CI configuration
- [Source: .env.example] — Current environment template
- [HashiCorp Vault AppRole](https://developer.hashicorp.com/vault/docs/auth/approle)
- [Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault)

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
