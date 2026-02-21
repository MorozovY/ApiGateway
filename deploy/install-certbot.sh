#!/bin/bash
# Скрипт установки Let's Encrypt SSL сертификата через Certbot
# ВАЖНО: Запускать ПОСЛЕ того как DNS настроен и приложение запущено на порту 80

set -e

echo "=========================================="
echo "Let's Encrypt SSL Certificate Setup"
echo "=========================================="

# Проверка root прав
if [ "$EUID" -ne 0 ]; then
    echo "ОШИБКА: Запустите скрипт с правами root (sudo)"
    exit 1
fi

# Проверка наличия .env
if [ ! -f .env ]; then
    echo "❌ ОШИБКА: .env файл не найден"
    exit 1
fi

# Загрузка переменных
source .env

echo "Домен: $DOMAIN"
echo "Email: $LETSENCRYPT_EMAIL"
echo ""

# Установка Certbot
echo "[1/4] Установка Certbot..."
apt-get update
apt-get install -y certbot

# Остановка nginx для получения сертификата
echo ""
echo "[2/4] Получение SSL сертификата..."
echo "ВНИМАНИЕ: Убедитесь что DNS запись для $DOMAIN указывает на IP этого сервера!"
echo ""
read -p "Продолжить? [y/N]: " CONFIRM
if [ "$CONFIRM" != "y" ]; then
    echo "Отменено"
    exit 0
fi

# Временная остановка nginx
docker compose -f docker-compose.prod.yml stop nginx

# Получение сертификата
certbot certonly \
    --standalone \
    --non-interactive \
    --agree-tos \
    --email "$LETSENCRYPT_EMAIL" \
    -d "$DOMAIN" \
    -d "www.$DOMAIN"

# Копирование сертификатов в nginx/ssl
echo ""
echo "[3/4] Копирование сертификатов..."
mkdir -p nginx/ssl
cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem nginx/ssl/
cp /etc/letsencrypt/live/$DOMAIN/privkey.pem nginx/ssl/
chmod 644 nginx/ssl/*.pem

# Настройка auto-renewal
echo ""
echo "[4/4] Настройка автоматического обновления..."
cat > /etc/cron.d/certbot-renew << EOF
# Certbot renewal для $DOMAIN
0 3 * * * root certbot renew --quiet --deploy-hook "cp /etc/letsencrypt/live/$DOMAIN/*.pem /opt/apigateway/deploy/nginx/ssl/ && docker compose -f /opt/apigateway/deploy/docker-compose.prod.yml restart nginx"
EOF

# Запуск nginx с HTTPS
echo ""
echo "Раскомментируйте HTTPS server блок в nginx/conf.d/default.conf"
echo "и закомментируйте HTTP редирект, затем перезапустите nginx:"
echo ""
echo "  vim nginx/conf.d/default.conf  # Раскомментировать HTTPS server"
echo "  docker compose -f docker-compose.prod.yml up -d nginx"
echo ""

echo "=========================================="
echo "✅ SSL сертификат установлен!"
echo "=========================================="
echo ""
echo "Сертификаты:"
echo "  fullchain.pem: nginx/ssl/fullchain.pem"
echo "  privkey.pem: nginx/ssl/privkey.pem"
echo ""
echo "Автоматическое обновление настроено (каждый день в 3:00 AM)"
echo ""
