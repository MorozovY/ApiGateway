# GitLab CE — Локальная CI/CD Инфраструктура

Локальный GitLab Community Edition для разработки и тестирования CI/CD пайплайнов.

## Требования

- **RAM:** 4GB минимум (8GB рекомендуется)
- **Disk:** 10GB+ свободного места
- **Docker:** Docker Desktop или Docker Engine
- **Порты:** 8929, 8922, 5050 должны быть свободны

## Быстрый старт

### 1. Запуск GitLab

```bash
cd docker/gitlab
docker-compose up -d
```

⏱️ **Первый запуск занимает 3-5 минут** — GitLab инициализирует базу данных и сервисы.

### 2. Проверка статуса

```bash
# Статус контейнеров
docker-compose ps

# Логи GitLab (Ctrl+C для выхода)
docker-compose logs -f gitlab

# Ожидание готовности (healthcheck)
docker-compose exec gitlab gitlab-ctl status
```

### 3. Получение пароля root

```bash
# Пароль доступен первые 24 часа после установки
docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

**Важно:** Смените пароль root после первого входа!

### 4. Доступ к Web UI

- **URL:** http://localhost:8929
- **Username:** root
- **Password:** из команды выше

## Сервисы и порты

| Сервис | Порт | Описание |
|--------|------|----------|
| GitLab Web UI | 8929 | Веб-интерфейс и REST API |
| GitLab SSH | 8922 | Git clone/push по SSH |
| Container Registry | 5050 | Docker registry |

## GitLab Runner

### Регистрация Runner (после первого запуска)

1. Войдите в GitLab как root
2. Перейдите: **Admin Area** → **CI/CD** → **Runners**
3. Нажмите **New instance runner**
4. Скопируйте registration token

```bash
# Регистрация runner с docker executor
# Используем hostname "gitlab" для внутренней сети Docker
docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "http://gitlab:8929" \
  --token "YOUR_REGISTRATION_TOKEN" \
  --executor "docker" \
  --docker-image "docker:latest" \
  --description "local-docker-runner" \
  --docker-privileged \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
  --docker-network-mode "gitlab_network"
```

### Проверка Runner

```bash
# Статус runner
docker exec -it gitlab-runner gitlab-runner status

# Список зарегистрированных runners
docker exec -it gitlab-runner gitlab-runner list
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
| `gitlab_logs` | Логи |
| `gitlab_data` | Репозитории, БД, uploads |
| `gitlab_runner_config` | Конфигурация runner |

Volumes сохраняются между `docker-compose down` и `docker-compose up`.

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

Репозиторий содержит `.gitlab-ci.yml` со следующими возможностями:

- **sync-to-github** — ручная синхронизация в GitHub (main branch)
- Build/test stages будут добавлены в Story 13.2+

### GitHub Mirror

GitLab настроен как primary repository, GitHub как mirror:

1. Основная разработка → push в GitLab
2. CI pipeline запускается автоматически
3. После merge в master → manual trigger sync to GitHub
4. GitHub всегда синхронизируется вручную

### Переменные CI/CD

В GitLab → Settings → CI/CD → Variables настроены:

| Variable | Описание |
|----------|----------|
| `GITHUB_TOKEN` | Personal Access Token для sync в GitHub (masked) |

## Дополнительная документация

- [GitLab CE Documentation](https://docs.gitlab.com/ee/)
- [GitLab Runner Documentation](https://docs.gitlab.com/runner/)
- [Container Registry Documentation](https://docs.gitlab.com/ee/user/packages/container_registry/)
