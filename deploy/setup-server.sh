#!/bin/bash
# Скрипт первоначальной настройки сервера для ApiGateway
# ОС: Ubuntu 22.04
# Требования: root доступ

set -e  # Остановка при ошибке

echo "=========================================="
echo "ApiGateway Server Setup для Ubuntu 22.04"
echo "=========================================="

# Проверка root прав
if [ "$EUID" -ne 0 ]; then
    echo "ОШИБКА: Запустите скрипт с правами root (sudo)"
    exit 1
fi

# 1. Обновление системы
echo ""
echo "[1/7] Обновление системы..."
apt-get update
apt-get upgrade -y

# 2. Установка необходимых пакетов
echo ""
echo "[2/7] Установка базовых пакетов..."
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    wget \
    git \
    vim \
    htop \
    net-tools \
    ufw

# 3. Установка Docker
echo ""
echo "[3/7] Установка Docker..."
if command -v docker &> /dev/null; then
    echo "Docker уже установлен: $(docker --version)"
else
    # Добавление Docker GPG ключа
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    # Добавление репозитория Docker
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Установка Docker
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Запуск Docker
    systemctl enable docker
    systemctl start docker

    echo "Docker установлен: $(docker --version)"
fi

# 4. Настройка Firewall (UFW)
echo ""
echo "[4/7] Настройка Firewall..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# Разрешить SSH (ВАЖНО! Иначе потеряете доступ)
ufw allow 22/tcp comment 'SSH'

# Разрешить HTTP и HTTPS
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'

# Включить firewall
ufw --force enable
ufw status verbose

# 5. Создание директории для приложения
echo ""
echo "[5/7] Создание директорий..."
mkdir -p /opt/apigateway
mkdir -p /opt/apigateway/backups
mkdir -p /opt/apigateway/logs

# 6. Настройка swap (для 1GB RAM сервера КРИТИЧНО!)
echo ""
echo "[6/7] Настройка swap файла..."
if [ -f /swapfile ]; then
    echo "Swap файл уже существует"
else
    # Создание 2GB swap
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile

    # Добавление в fstab для автоматического монтирования
    echo '/swapfile none swap sw 0 0' | tee -a /etc/fstab

    # Настройка swappiness (уменьшаем для производительности)
    sysctl vm.swappiness=10
    echo 'vm.swappiness=10' | tee -a /etc/sysctl.conf

    echo "Swap 2GB создан и активирован"
fi

# 7. Проверка состояния
echo ""
echo "[7/7] Проверка установки..."
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker compose version)"
echo ""
free -h
echo ""
df -h

echo ""
echo "=========================================="
echo "✅ Сервер успешно настроен!"
echo "=========================================="
echo ""
echo "Следующие шаги:"
echo "1. Настройте DNS: A-запись ymorozov.ru -> IP сервера"
echo "2. Скопируйте код проекта в /opt/apigateway/"
echo "3. Настройте .env файл в /opt/apigateway/deploy/"
echo "4. Запустите deploy.sh для развертывания приложения"
echo ""
echo "Для SSL сертификата (рекомендуется):"
echo "  - Запустите install-certbot.sh после настройки DNS"
echo ""
