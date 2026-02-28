# GitLab CE — Локальная CI/CD Инфраструктура

Локальный GitLab Community Edition с Nexus Repository Manager для высокопроизводительной CI/CD.

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                     GitLab CE                                │
│                  http://localhost:8929                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │Runner 1 │  │Runner 2 │  │Runner 3 │  │Runner 4 │        │
│  │ (docker)│  │ (docker)│  │ (docker)│  │ (docker)│        │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘        │
│       │            │            │            │              │
│       └────────────┴─────┬──────┴────────────┘              │
│                          │                                   │
│  ┌───────────────────────┴───────────────────────┐          │
│  │           Shared Caches (Docker volumes)       │          │
│  │   gradle_cache │ npm_cache                     │          │
│  └───────────────────────────────────────────────┘          │
│                          │                                   │
│  ┌───────────────────────┴───────────────────────┐          │
│  │              Nexus Repository                  │          │
│  │           http://localhost:8081                │          │
│  │   Maven Central Proxy │ npm Registry Proxy    │          │
│  └───────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

## Требования

- **RAM:** 8GB минимум (GitLab 4GB + Nexus 2GB + Runners 2GB)
- **Disk:** 20GB+ свободного места
- **Docker:** Docker Desktop или Docker Engine
- **Порты:** 8929, 8922, 5050, 8081 должны быть свободны

## Быстрый старт

### 1. Запуск GitLab + Nexus

```bash
cd docker/gitlab
docker-compose up -d
```

⏱️ **Первый запуск:**
- GitLab: 3-5 минут для инициализации
- Nexus: 2-3 минуты для старта

### 2. Проверка статуса

```bash
# Статус всех контейнеров
docker-compose ps

# Логи GitLab (Ctrl+C для выхода)
docker-compose logs -f gitlab

# Логи Nexus
docker-compose logs -f nexus
```

### 3. Получение паролей

**GitLab root:**
```bash
# Пароль доступен первые 24 часа после установки
docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

**Nexus admin:**
```bash
# Initial password (первый вход)
docker exec nexus cat /nexus-data/admin.password
```

### 4. Настройка Nexus (первый раз)

```powershell
# PowerShell
.\setup-nexus.ps1
```

Скрипт создаёт proxy-репозитории:
- `maven-central-proxy` — Maven Central
- `gradle-plugins-proxy` — Gradle Plugin Portal
- `npm-proxy` — npmjs.org

### 5. Регистрация Runners (первый раз)

1. Войдите в GitLab: http://localhost:8929
2. Admin Area → CI/CD → Runners → New instance runner
3. Скопируйте registration token
4. Запустите скрипт:

```powershell
# PowerShell
.\register-runners.ps1 -Token "YOUR_TOKEN"
```

### 6. Проверка готовности

- **GitLab:** http://localhost:8929 (root / пароль из п.3)
- **Nexus:** http://localhost:8081 (admin / admin123)
- **Runners:** http://localhost:8929/admin/runners (должно быть 4 online)

## Сервисы и порты

| Сервис | Порт | Описание |
|--------|------|----------|
| GitLab Web UI | 8929 | Веб-интерфейс и REST API |
| GitLab SSH | 8922 | Git clone/push по SSH |
| Container Registry | 5050 | Docker registry |
| Nexus Repository | 8081 | Maven/npm proxy |

## GitLab Runners (4 шт.)

### Архитектура

4 параллельных runner для максимальной производительности:
- **gitlab-runner-1** — backend-build, backend-test
- **gitlab-runner-2** — frontend-build, frontend-test
- **gitlab-runner-3** — дополнительная параллельность
- **gitlab-runner-4** — дополнительная параллельность

Все runners разделяют:
- Docker socket хоста
- Gradle cache (`gitlab_gradle_cache`)
- npm cache (`gitlab_npm_cache`)

### Регистрация Runners

**Автоматическая (рекомендуется):**

```powershell
# PowerShell — регистрирует все 4 runners
.\register-runners.ps1 -Token "YOUR_REGISTRATION_TOKEN"
```

**Ручная:**

```bash
# Для каждого runner (1-4)
docker exec gitlab-runner-1 gitlab-runner register \
  --non-interactive \
  --url "http://gitlab:8929" \
  --token "YOUR_TOKEN" \
  --executor "docker" \
  --docker-image "alpine:latest" \
  --docker-privileged \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
  --docker-volumes "gitlab_gradle_cache:/cache/gradle" \
  --docker-volumes "gitlab_npm_cache:/cache/npm"
```

### Проверка Runners

```bash
# Статус всех runners
for i in 1 2 3 4; do
  echo "=== gitlab-runner-$i ==="
  docker exec gitlab-runner-$i gitlab-runner list
done

# Или в PowerShell
1..4 | ForEach-Object {
  Write-Host "=== gitlab-runner-$_ ==="
  docker exec gitlab-runner-$_ gitlab-runner list
}
```

## Nexus Repository Manager

### Назначение

Nexus проксирует внешние репозитории для:
- **Ускорения builds** — зависимости кэшируются локально
- **Экономии bandwidth** — скачивание из сети только при первом запросе
- **Offline режима** — работа без интернета с закэшированными зависимостями

### Репозитории

| Репозиторий | Proxy URL | Описание |
|-------------|-----------|----------|
| `maven-central-proxy` | https://repo1.maven.org/maven2/ | Maven Central |
| `gradle-plugins-proxy` | https://plugins.gradle.org/m2/ | Gradle plugins |
| `npm-proxy` | https://registry.npmjs.org/ | npm packages |

### Использование в CI

CI jobs автоматически используют Nexus если он доступен:

```yaml
# .gitlab-ci.yml (уже настроено)
variables:
  NEXUS_URL: "http://nexus:8081"
script:
  - |
    if curl -sf "$NEXUS_URL/service/rest/v1/status" > /dev/null; then
      echo "Using Nexus proxy"
      # Gradle/npm настраиваются автоматически
    fi
```

### Ручная настройка (локальная разработка)

**Gradle (`~/.gradle/init.gradle.kts`):**
```kotlin
settingsEvaluated {
    pluginManagement {
        repositories {
            maven { url = uri("http://localhost:8081/repository/gradle-plugins-proxy/") }
            gradlePluginPortal()
        }
    }
}
allprojects {
    repositories {
        maven { url = uri("http://localhost:8081/repository/maven-central-proxy/") }
        mavenCentral()
    }
}
```

**npm (`~/.npmrc`):**
```
registry=http://localhost:8081/repository/npm-proxy/
strict-ssl=false
```

## Container Registry

### Настройка Docker для Insecure Registry

Для работы с localhost registry без HTTPS, добавьте в Docker daemon config:

**Windows/Mac (Docker Desktop):**
Settings → Docker Engine → добавить:

```json
{
  "insecure-registries": ["localhost:5050"]
}
```

**Linux:** `/etc/docker/daemon.json`

```json
{
  "insecure-registries": ["localhost:5050"]
}
```

После изменения перезапустите Docker.

### Использование Registry

```bash
# Логин (используйте GitLab credentials или Personal Access Token)
docker login localhost:5050 -u root

# Tag и push образа
docker tag myimage:latest localhost:5050/root/myproject/myimage:latest
docker push localhost:5050/root/myproject/myimage:latest

# Pull образа
docker pull localhost:5050/root/myproject/myimage:latest
```

### CI/CD Docker Images

CI pipeline автоматически собирает и pushит images в Registry:

| Image | Описание | Dockerfile |
|-------|----------|------------|
| `gateway-admin` | Backend Admin API | `docker/Dockerfile.gateway-admin` |
| `gateway-core` | Gateway Core | `docker/Dockerfile.gateway-core` |
| `admin-ui` | Frontend (Nginx) | `docker/Dockerfile.admin-ui.ci` |

**Теги:**
- `$CI_COMMIT_SHA` — полный commit hash (всегда)
- `$CI_COMMIT_REF_SLUG` — branch name (всегда)
- `latest` — только на master branch
- `v1.2.3` — semantic version (если git tag присутствует)

**Pull image из CI:**
```bash
# По commit SHA
docker pull localhost:5050/root/api-gateway/gateway-admin:abc123def

# По branch
docker pull localhost:5050/root/api-gateway/gateway-admin:feat-13-3-docker-image-build-registry

# Latest (master)
docker pull localhost:5050/root/api-gateway/gateway-admin:latest
```

### Registry Cleanup Policy (рекомендуемая настройка)

Для автоматической очистки старых images настройте политику в GitLab UI:

1. GitLab → `root/api-gateway` → Settings → Packages & Registries → Container Registry
2. Cleanup policies → Add cleanup policy:

```
Enabled: Yes
Run cleanup: Every week
Keep the most recent: 10 tags
Remove tags older than: 30 days
Remove tags matching: .*
Do NOT remove tags matching: ^v\d+\.\d+\.\d+$|^latest$|^master$
```

**Важно:**
- Semantic version tags (`v1.0.0`, `v2.1.3`) сохраняются навсегда
- `latest` и `master` tags сохраняются навсегда
- Feature branch tags удаляются через 30 дней
- Минимум 10 последних tags каждого image сохраняются

## Управление

### Остановка GitLab

```bash
docker-compose stop
```

### Запуск после остановки

```bash
docker-compose start
```

### Полная остановка (контейнеры удаляются)

```bash
docker-compose down
```

**Данные сохраняются** в Docker volumes.

### Полная очистка (УДАЛЯЕТ ВСЕ ДАННЫЕ!)

```bash
docker-compose down -v
```

⚠️ **Внимание:** Команда `-v` удаляет все данные, включая репозитории и настройки!

## Персистентность данных

Данные хранятся в Docker volumes:

| Volume | Содержимое |
|--------|------------|
| `gitlab_config` | Конфигурация GitLab |
| `gitlab_logs` | Логи GitLab |
| `gitlab_data` | Репозитории, БД, uploads |
| `gitlab_runner_config_1..4` | Конфигурация runners (по 1 на каждый) |
| `gitlab_gradle_cache` | Shared Gradle cache |
| `gitlab_npm_cache` | Shared npm cache |
| `nexus_data` | Nexus данные и кэши |

Volumes сохраняются между `docker-compose down` и `docker-compose up`.

### Очистка кэшей

```bash
# Очистка Gradle cache (пересобрать зависимости)
docker volume rm gitlab_gradle_cache

# Очистка npm cache
docker volume rm gitlab_npm_cache

# Очистка Nexus (пересобрать все proxy caches)
docker volume rm nexus_data
```

## Troubleshooting

### GitLab не запускается / Долгий старт

```bash
# Проверить логи
docker-compose logs gitlab

# Проверить использование памяти
docker stats gitlab
```

Если памяти не хватает, увеличьте лимиты Docker Desktop.

### Runner не подключается к GitLab

```bash
# Проверить сеть
docker exec -it gitlab-runner ping gitlab.local

# Проверить URL в конфигурации
docker exec -it gitlab-runner cat /etc/gitlab-runner/config.toml
```

### Ошибка "502 Bad Gateway"

GitLab ещё не инициализировался. Подождите 3-5 минут и обновите страницу.

```bash
# Проверить готовность
docker exec -it gitlab gitlab-ctl status
```

### Registry: "Get https://localhost:5050/v2/: http: server gave HTTP response to HTTPS client"

Нужно добавить `localhost:5050` в insecure-registries (см. раздел выше).

### Сброс пароля root

```bash
docker exec -it gitlab gitlab-rake "gitlab:password:reset[root]"
```

## Network Requirements

Для работы CI/CD pipeline:

1. **Runner → GitLab:** Runner должен иметь доступ к GitLab по `http://gitlab.local:8929`
2. **Runner → Registry:** Runner должен push/pull образы на `localhost:5050`
3. **Build jobs → external:** Jobs могут требовать доступ в интернет для скачивания dependencies

## ApiGateway Repository

### Настройка после установки GitLab

После запуска GitLab и регистрации runner, репозиторий ApiGateway настраивается следующим образом:

```bash
# Добавить GitLab как remote (из корня проекта)
git remote add gitlab http://localhost:8929/root/api-gateway.git

# Push всех веток и тегов
git push gitlab --all
git push gitlab --tags

# Проверка remotes
git remote -v
# origin  https://github.com/MorozovY/ApiGateway.git (fetch/push)
# gitlab  http://localhost:8929/root/api-gateway.git (fetch/push)
```

### CI/CD Pipeline

Репозиторий содержит `.gitlab-ci.yml` с оптимизированным build/test pipeline:

**Build Stage (параллельно):**
- **backend-build** — компиляция Gradle (JDK 21, Nexus proxy)
- **frontend-build** — сборка React (Node 20, Nexus npm proxy)

**Test Stage (параллельно):**
- **backend-test** — Gradle тесты + Testcontainers (Docker socket)
  - JUnit reports → GitLab UI
  - JaCoCo coverage reports
- **frontend-test** — Vitest тесты
  - Coverage reports

**Sync Stage:**
- **sync-to-github** — manual sync в GitHub (только master)

**Оптимизации:**
- 4 runners → до 4 jobs параллельно
- Nexus proxy → зависимости из локального кэша
- Shared Gradle/npm caches → переиспользование между jobs
- Docker socket → Testcontainers без DinD

**Ожидаемое время pipeline:**
- Первый запуск (холодный кэш): ~15-20 минут
- Повторные запуски (горячий кэш): ~5-8 минут

### GitHub Mirror

GitLab настроен как primary repository, GitHub как mirror:

1. Основная разработка → push в GitLab
2. CI pipeline запускается автоматически
3. После merge в master → manual trigger sync to GitHub
4. GitHub всегда синхронизируется вручную

### Переменные CI/CD

В GitLab → Settings → CI/CD → Variables настроены:

| Variable | Описание | Тип |
|----------|----------|-----|
| `GITHUB_TOKEN` | Personal Access Token для sync в GitHub | masked |
| `VAULT_ADDR` | Vault server URL (например: http://vault:8200) | variable |
| `VAULT_ROLE_ID` | AppRole Role ID для Vault | masked |
| `VAULT_SECRET_ID` | AppRole Secret ID для Vault | masked, protected |

## Vault Integration (Story 13.4)

**Требуемая версия:** HashiCorp Vault 1.4+ (для KV v2 secrets engine)

### Архитектура Secrets Management

```
┌─────────────────────────────────────────────────────────────┐
│                    HashiCorp Vault                          │
│              (централизованное хранилище)                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  secret/apigateway/                                  │   │
│  │    ├── database    (POSTGRES_USER, POSTGRES_PASSWORD)│   │
│  │    ├── redis       (REDIS_HOST, REDIS_PORT)         │   │
│  │    └── keycloak    (KEYCLOAK_ADMIN_PASSWORD)        │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                    AppRole Auth                              │
│            (apigateway-ci role, read-only)                  │
└─────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┴───────────────┐
           │                               │
    ┌──────▼──────┐               ┌────────▼────────┐
    │  GitLab CI  │               │  Local Dev      │
    │   Pipeline  │               │  (fallback)     │
    │             │               │                 │
    │ vault-      │               │ .env file       │
    │ secrets.sh  │               │ or Vault token  │
    └─────────────┘               └─────────────────┘
```

### Vault Secrets Structure

| Path | Keys | Описание |
|------|------|----------|
| `secret/apigateway/database` | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DATABASE_URL` | PostgreSQL credentials |
| `secret/apigateway/redis` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_URL` | Redis connection |
| `secret/apigateway/keycloak` | `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin |

### AppRole Configuration

Для CI/CD pipeline настроен AppRole:

- **Role:** `apigateway-ci`
- **Policy:** `apigateway-read` (read-only access to `secret/data/apigateway/*`)
- **Token TTL:** 1 hour
- **Secret ID:** без ограничений (можно переиспользовать)

### Настройка Vault Integration

#### 1. Получение Role ID и Secret ID

```bash
# Подключение к Vault (через docker exec если Vault в контейнере)
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=<root-token>

# Role ID (статичный)
vault read auth/approle/role/apigateway-ci/role-id

# Secret ID (генерировать новый при компрометации)
vault write -f auth/approle/role/apigateway-ci/secret-id
```

#### 2. Добавление переменных в GitLab

GitLab → Settings → CI/CD → Variables:

1. `VAULT_ADDR` = `http://vault:8200` (или внешний URL)
2. `VAULT_ROLE_ID` = `<role_id>` (masked)
3. `VAULT_SECRET_ID` = `<secret_id>` (masked, protected)

#### 3. Использование в Pipeline

CI pipeline автоматически загружает secrets через `vault-secrets.sh`:

```yaml
# .gitlab-ci.yml
deploy-job:
  extends: .vault-secrets
  script:
    - echo "DATABASE_URL=$DATABASE_URL"
    - echo "POSTGRES_PASSWORD=****** (hidden)"
```

### Local Development

#### С Vault (если доступен)

```bash
# Получить token
export VAULT_ADDR=http://localhost:8200
vault login

# Или использовать script
source ./docker/gitlab/vault-secrets.sh
```

#### Без Vault (fallback)

Используйте `.env` файл (копия `.env.example` с реальными значениями).

### Secret Rotation

#### Ротация Secret ID

```bash
# Генерировать новый Secret ID
vault write -f auth/approle/role/apigateway-ci/secret-id

# Обновить в GitLab CI/CD Variables
# GitLab → Settings → CI/CD → Variables → VAULT_SECRET_ID → Edit
```

#### Ротация паролей

```bash
# Обновить secret в Vault
vault kv put secret/apigateway/database \
  POSTGRES_USER="gateway" \
  POSTGRES_PASSWORD="new-secure-password" \
  DATABASE_URL="r2dbc:postgresql://infra-postgres:5432/gateway"

# Secrets автоматически обновятся при следующем запуске pipeline
```

### Emergency Access

При недоступности Vault:

1. **CI/CD fallback:** Pipeline ожидает Vault и fail-ит при недоступности
2. **Local dev:** Используйте `.env` файл как fallback
3. **Manual override:** Можно добавить secrets напрямую в GitLab CI/CD Variables (временно)

### Security Best Practices

1. **Никогда не коммитьте secrets** — используйте `.env.example` как шаблон
2. **Mask sensitive variables** — в GitLab отметьте переменные как masked
3. **Protect deployment variables** — VAULT_SECRET_ID должен быть protected
4. **Audit access** — Vault audit log включен
5. **Rotate credentials** — Secret ID каждые 30 дней, пароли по политике

## Дополнительная документация

- [GitLab CE Documentation](https://docs.gitlab.com/ee/)
- [GitLab Runner Documentation](https://docs.gitlab.com/runner/)
- [Container Registry Documentation](https://docs.gitlab.com/ee/user/packages/container_registry/)
