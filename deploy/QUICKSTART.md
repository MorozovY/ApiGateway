# ApiGateway Production Deployment — Quick Start

Быстрое руководство для развертывания на сервере ymorozov.ru (Ubuntu 22.04, 1GB RAM).

## Предварительные требования

✅ Сервер: Ubuntu 22.04, 1GB RAM, 15GB диск
✅ Root доступ по SSH
✅ DNS: A-запись `ymorozov.ru` → IP сервера

## Шаги развертывания

### 1. Подключиться к серверу

```bash
ssh root@<IP сервера>
```

### 2. Скопировать проект на сервер

**Вариант A: Через Git (рекомендуется)**

```bash
cd /opt
git clone <URL вашего репозитория> apigateway
cd apigateway/deploy
```

**Вариант B: Через SCP (с вашего компьютера)**

```bash
# На вашем компьютере (Windows)
scp -r G:\Projects\ApiGateway root@<IP>:/opt/apigateway

# На сервере
cd /opt/apigateway/deploy
```

### 3. Настроить сервер (один раз)

```bash
chmod +x *.sh
./setup-server.sh
```

⏱️ Время выполнения: ~5 минут
📦 Установит: Docker, Docker Compose, firewall, swap

### 4. Настроить переменные окружения

```bash
cp .env.example .env
vim .env  # Изменить пароли (см. ниже)
```

**Обязательно изменить:**

```bash
POSTGRES_PASSWORD=<Сгенерировать 32 символа>
REDIS_PASSWORD=<Сгенерировать 32 символа>
ADMIN_PASSWORD=<Сгенерировать 16 символов>
JWT_SECRET=<Сгенерировать 64 символа>
GF_SECURITY_ADMIN_PASSWORD=<Сгенерировать>
```

**Генерация паролей:**

```bash
openssl rand -base64 32  # 32 символа
openssl rand -base64 64  # 64 символа
```

### 5. Собрать приложения

**На вашем компьютере (рекомендуется):**

```bash
cd G:\Projects\ApiGateway\deploy
bash build.sh
```

Затем перенести образы на сервер:

```bash
# На компьютере
docker save gateway-admin:latest gateway-core:latest admin-ui:latest -o apigateway-images.tar
scp apigateway-images.tar root@<IP>:/opt/apigateway/

# На сервере
cd /opt/apigateway
docker load -i apigateway-images.tar
rm apigateway-images.tar
```

**ИЛИ на сервере (медленнее):**

```bash
cd /opt/apigateway/deploy
./build.sh
```

⏱️ Время выполнения: ~10-15 минут

### 6. Развернуть приложение

```bash
./deploy.sh
```

Выбрать режим:
- **1** — БЕЗ мониторинга (рекомендуется для 1GB RAM)
- **2** — С мониторингом (требует 2GB+ RAM)

⏱️ Время выполнения: ~2-3 минуты

### 7. Проверить работу

Открыть в браузере:

- **Admin UI**: http://ymorozov.ru/
- **Swagger UI**: http://ymorozov.ru/swagger-ui.html

Проверить статус на сервере:

```bash
docker compose -f docker-compose.prod.yml ps
docker stats
```

### 8. Настроить SSL (опционально, но рекомендуется)

```bash
./install-certbot.sh
```

Затем раскомментировать HTTPS блок в `nginx/conf.d/default.conf` и перезапустить nginx:

```bash
vim nginx/conf.d/default.conf  # Раскомментировать # server { ... }
docker compose -f docker-compose.prod.yml restart nginx
```

✅ Готово! Приложение доступно по HTTPS: https://ymorozov.ru/

## Полезные команды

```bash
# Логи
docker compose -f docker-compose.prod.yml logs -f

# Статус
docker compose -f docker-compose.prod.yml ps

# Перезапуск сервиса
docker compose -f docker-compose.prod.yml restart gateway-admin

# Остановка всего
docker compose -f docker-compose.prod.yml down

# Backup БД
docker exec gateway-postgres-prod pg_dump -U gateway_prod gateway > backup.sql
```

## Troubleshooting

**502 Bad Gateway** → Проверить `docker compose ps` и `docker compose logs`
**Нет памяти** → Отключить мониторинг, проверить `free -h`
**Медленная работа** → Проверить `docker stats`, возможно swap активен

Полное руководство: [README.md](README.md)

---

**Время полного развертывания: ~30 минут** ⏱️
