# ApiGateway Production Deployment Guide

> ⚠️ **УСТАРЕЛО (2026-03-02):** Эта документация описывает standalone deployment с nginx, локальными PostgreSQL/Redis и Prometheus/Grafana.
>
> **Текущая архитектура (Story 13.8-13.12):**
> - Reverse proxy: **Traefik** (централизованный, infra проект)
> - Database: **PostgreSQL** (централизованный, infra проект)
> - Cache: **Redis** (централизованный, infra проект)
> - Monitoring: **Prometheus + Grafana** (централизованный, infra проект)
>
> Для deployment используйте `docker-compose.ci-base.yml` с подключением к centralized infrastructure.
> Удалённые файлы: `docker-compose.prod.yml`, `nginx/`, `prometheus/`, `grafana/`

---

Полное руководство по развертыванию ApiGateway на production сервере (LEGACY).

## Требования

- **ОС**: Ubuntu 22.04 LTS
- **Минимальные ресурсы**:
  - **CPU**: 1 core
  - **RAM**: 1GB (без мониторинга) или 2GB+ (с мониторингом)
  - **Диск**: 15GB+
- **Домен**: ymorozov.ru с настроенными DNS записями
- **SSH доступ**: root или sudo пользователь

## Структура файлов

```
deploy/
├── docker-compose.prod.yml    # Production Docker Compose конфигурация
├── .env.example               # Шаблон переменных окружения
├── .env                       # Реальные переменные (создать вручную)
├── setup-server.sh            # Скрипт первоначальной настройки сервера
├── build.sh                   # Скрипт сборки приложений
├── deploy.sh                  # Скрипт развертывания
├── install-certbot.sh         # Скрипт установки SSL (Let's Encrypt)
├── nginx/                     # Nginx reverse proxy конфигурация
│   ├── nginx.conf             # Главный конфиг nginx
│   └── conf.d/
│       └── default.conf       # Виртуальный хост для ymorozov.ru
├── prometheus/                # Prometheus конфигурация
│   └── prometheus.yml
└── grafana/                   # Grafana конфигурация
    ├── provisioning/
    └── dashboards/
```

## Шаг 1: Настройка DNS

Создайте A-запись для домена:

```
ymorozov.ru         A    <IP сервера>
www.ymorozov.ru     A    <IP сервера>
```

Проверка:
```bash
# На вашем компьютере
ping ymorozov.ru
```

## Шаг 2: Первоначальная настройка сервера

Подключитесь к серверу по SSH:

```bash
ssh root@<IP сервера>
```

Скачайте код проекта на сервер:

```bash
# Вариант 1: Через Git (если репозиторий публичный)
cd /opt
git clone <URL репозитория> apigateway
cd apigateway/deploy

# Вариант 2: Копирование через scp (с вашего компьютера)
# Выполнить на вашем компьютере:
scp -r G:\Projects\ApiGateway root@<IP>:/opt/apigateway
```

Запустите скрипт настройки сервера:

```bash
cd /opt/apigateway/deploy
chmod +x *.sh
./setup-server.sh
```

**Что делает setup-server.sh:**
- Обновляет систему
- Устанавливает Docker и Docker Compose
- Настраивает firewall (UFW)
- Создает 2GB swap файл (критично для 1GB RAM!)
- Создает необходимые директории

## Шаг 3: Настройка переменных окружения

Создайте `.env` файл из шаблона:

```bash
cd /opt/apigateway/deploy
cp .env.example .env
vim .env  # или nano .env
```

**ВАЖНО! Измените следующие значения:**

```bash
# Домен (уже настроен)
DOMAIN=ymorozov.ru

# PostgreSQL пароли (ОБЯЗАТЕЛЬНО изменить!)
POSTGRES_PASSWORD=ВашСильныйПароль32Символа

# Redis пароль (ОБЯЗАТЕЛЬНО изменить!)
REDIS_PASSWORD=ЕщёОдинСильныйПароль32Символа

# Admin пароль (ОБЯЗАТЕЛЬНО изменить!)
ADMIN_PASSWORD=АдминПароль16Символов

# JWT Secret (ОБЯЗАТЕЛЬНО изменить! Минимум 64 символа)
JWT_SECRET=СуперСекретныйКлючДляПодписиJWT64СимволаИБольше

# Grafana admin пароль
GF_SECURITY_ADMIN_PASSWORD=GrafanaАдминПароль

# Email для Let's Encrypt (используется для уведомлений)
LETSENCRYPT_EMAIL=admin@ymorozov.ru
```

**Генерация безопасных паролей:**

```bash
# 32 символа
openssl rand -base64 32

# 64 символа
openssl rand -base64 64
```

## Шаг 4: Сборка приложений

На **вашем компьютере** (Windows):

```bash
cd G:\Projects\ApiGateway\deploy
bash build.sh
```

**Что делает build.sh:**
- Собирает backend JAR файлы (gradle build)
- Собирает Docker образы для всех сервисов

**Альтернатива:** Собрать на сервере (медленнее, но не требует копирования образов):

```bash
# На сервере
cd /opt/apigateway/deploy
./build.sh
```

**Если собирали на компьютере**, перенесите образы на сервер:

```bash
# На вашем компьютере
docker save gateway-admin:latest gateway-core:latest admin-ui:latest -o apigateway-images.tar
scp apigateway-images.tar root@<IP>:/opt/apigateway/

# На сервере
cd /opt/apigateway
docker load -i apigateway-images.tar
rm apigateway-images.tar
```

## Шаг 5: Развертывание приложения

На сервере:

```bash
cd /opt/apigateway/deploy
./deploy.sh
```

Скрипт предложит выбрать режим:
- **1) БЕЗ мониторинга** — рекомендуется для 1GB RAM
- **2) С мониторингом** — требует 2GB+ RAM (Prometheus + Grafana)

**Что делает deploy.sh:**
- Проверяет конфигурацию (.env)
- Останавливает старые контейнеры
- Запускает новые контейнеры
- Проверяет статус и health checks

## Шаг 6: Проверка работы

После развертывания приложение доступно:

- **Admin UI**: http://ymorozov.ru/
- **Admin API**: http://ymorozov.ru/api/
- **Swagger UI**: http://ymorozov.ru/swagger-ui.html
- **Gateway Core**: http://ymorozov.ru/gateway/
- **Grafana** (если включен): http://ymorozov.ru/grafana/

**Проверка статуса контейнеров:**

```bash
cd /opt/apigateway/deploy
docker compose -f docker-compose.prod.yml ps
```

**Просмотр логов:**

```bash
# Все сервисы
docker compose -f docker-compose.prod.yml logs -f

# Конкретный сервис
docker compose -f docker-compose.prod.yml logs -f gateway-admin
docker compose -f docker-compose.prod.yml logs -f gateway-core
docker compose -f docker-compose.prod.yml logs -f admin-ui
```

**Мониторинг ресурсов:**

```bash
docker stats
free -h
df -h
```

## Шаг 7: Настройка SSL (HTTPS)

**После того как приложение работает на HTTP:**

```bash
cd /opt/apigateway/deploy
./install-certbot.sh
```

**Что делает install-certbot.sh:**
- Устанавливает Certbot
- Получает бесплатный SSL сертификат от Let's Encrypt
- Настраивает автоматическое обновление сертификата (cron)

**Активация HTTPS в nginx:**

1. Откройте конфигурацию:
```bash
vim nginx/conf.d/default.conf
```

2. Раскомментируйте HTTPS server блок (строки с `# server {` до `# }`)

3. Раскомментируйте HTTP → HTTPS редирект:
```nginx
location / {
    return 301 https://$server_name$request_uri;
}
```

4. Перезапустите nginx:
```bash
docker compose -f docker-compose.prod.yml restart nginx
```

Теперь приложение доступно по HTTPS: https://ymorozov.ru/

## Управление приложением

### Остановка

```bash
cd /opt/apigateway/deploy

# Без мониторинга
docker compose -f docker-compose.prod.yml down

# С мониторингом
docker compose -f docker-compose.prod.yml --profile monitoring down
```

### Перезапуск отдельного сервиса

```bash
docker compose -f docker-compose.prod.yml restart gateway-admin
docker compose -f docker-compose.prod.yml restart gateway-core
docker compose -f docker-compose.prod.yml restart nginx
```

### Обновление приложения

1. Пересоберите образы (на компьютере или сервере):
```bash
./build.sh
```

2. Перезапустите сервисы:
```bash
./deploy.sh
```

### Резервное копирование PostgreSQL

```bash
# Создание backup
docker exec gateway-postgres-prod pg_dump -U gateway_prod gateway > /opt/apigateway/deploy/backups/backup-$(date +%Y%m%d-%H%M%S).sql

# Восстановление из backup
docker exec -i gateway-postgres-prod psql -U gateway_prod gateway < /opt/apigateway/deploy/backups/backup-20260221-120000.sql
```

### Просмотр логов nginx

```bash
docker exec gateway-nginx-prod tail -f /var/log/nginx/access.log
docker exec gateway-nginx-prod tail -f /var/log/nginx/error.log
```

## Troubleshooting

### Контейнер постоянно перезапускается

Проверьте логи:
```bash
docker compose -f docker-compose.prod.yml logs <service-name>
```

### Не хватает памяти (OOM Killer)

1. Проверьте swap:
```bash
free -h
```

2. Отключите мониторинг:
```bash
docker compose -f docker-compose.prod.yml --profile monitoring down
docker compose -f docker-compose.prod.yml up -d
```

3. Уменьшите лимиты памяти в `docker-compose.prod.yml`

### 502 Bad Gateway от nginx

1. Проверьте, что backend сервисы запущены:
```bash
docker compose -f docker-compose.prod.yml ps
```

2. Проверьте health checks:
```bash
docker inspect gateway-admin-prod | grep -A 10 Health
docker inspect gateway-core-prod | grep -A 10 Health
```

### База данных не запускается

Проверьте логи PostgreSQL:
```bash
docker compose -f docker-compose.prod.yml logs postgres
```

Возможная причина: недостаточно места на диске:
```bash
df -h
```

## Мониторинг и метрики

### Grafana (если включен)

URL: http://ymorozov.ru/grafana/ (или https после настройки SSL)

Логин: `admin`
Пароль: из переменной `GF_SECURITY_ADMIN_PASSWORD` в `.env`

Dashboard: "API Gateway" (автоматически provisioned)

### Prometheus (если включен)

URL: http://localhost:9090 (доступно только через SSH tunnel)

SSH tunnel:
```bash
# На вашем компьютере
ssh -L 9090:localhost:9090 root@<IP сервера>

# Откройте в браузере
http://localhost:9090
```

### Health Checks

- Gateway Admin: http://ymorozov.ru/api/actuator/health
- Gateway Core: http://ymorozov.ru/gateway/actuator/health

## Безопасность

### Рекомендации

1. **Изменить пароли** — все дефолтные пароли в `.env`
2. **Firewall** — открыты только порты 22 (SSH), 80 (HTTP), 443 (HTTPS)
3. **SSH ключи** — настроить вместо пароля root
4. **Fail2ban** — установить для защиты от brute-force SSH
5. **Регулярные обновления**:
```bash
apt-get update && apt-get upgrade -y
```

### Ограничение доступа к Swagger UI

В production рекомендуется закрыть Swagger UI. В `nginx/conf.d/default.conf`:

```nginx
# Закомментировать или удалить:
# location /swagger-ui/ { ... }
# location /v3/api-docs { ... }
```

## Контакты и поддержка

При возникновении проблем:

1. Проверьте логи: `docker compose logs -f`
2. Проверьте ресурсы: `docker stats` и `free -h`
3. Проверьте firewall: `ufw status`
4. Проверьте DNS: `ping ymorozov.ru`

---

**Удачного развертывания! 🚀**
