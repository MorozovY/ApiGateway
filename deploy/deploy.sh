#!/bin/bash
# Скрипт развертывания ApiGateway в production
# Запускает Docker Compose с production конфигурацией

set -e  # Остановка при ошибке

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "ApiGateway Production Deployment"
echo "=========================================="

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo "❌ ОШИБКА: Файл .env не найден!"
    echo ""
    echo "Создайте .env файл:"
    echo "  cp .env.example .env"
    echo "  vim .env  # Заполните реальными значениями"
    echo ""
    exit 1
fi

# Загрузка переменных окружения
source .env

# Проверка критичных переменных
if [ "$POSTGRES_PASSWORD" = "CHANGE_THIS_TO_STRONG_PASSWORD_MIN_32_CHARS" ]; then
    echo "❌ ОШИБКА: Измените пароли в .env файле!"
    exit 1
fi

if [ "$JWT_SECRET" = "CHANGE_THIS_TO_CRYPTOGRAPHICALLY_STRONG_SECRET_MIN_64_CHARACTERS_LONG" ]; then
    echo "❌ ОШИБКА: Измените JWT_SECRET в .env файле!"
    exit 1
fi

echo "✅ Конфигурация проверена"
echo ""

# Выбор режима (с мониторингом или без)
echo "Выберите режим развертывания:"
echo "  1) БЕЗ мониторинга (рекомендуется для 1GB RAM)"
echo "  2) С мониторингом (Prometheus + Grafana, требует 2GB+ RAM)"
echo ""
read -p "Введите номер [1]: " MODE
MODE=${MODE:-1}

if [ "$MODE" = "2" ]; then
    COMPOSE_PROFILES="monitoring"
    echo "Режим: С мониторингом"
else
    COMPOSE_PROFILES=""
    echo "Режим: Без мониторинга"
fi
echo ""

# Остановка старых контейнеров (если есть)
echo "Остановка существующих контейнеров..."
if [ -n "$COMPOSE_PROFILES" ]; then
    docker compose -f docker-compose.prod.yml --profile "$COMPOSE_PROFILES" down
else
    docker compose -f docker-compose.prod.yml down
fi
echo ""

# Запуск новых контейнеров
echo "Запуск контейнеров..."
if [ -n "$COMPOSE_PROFILES" ]; then
    docker compose -f docker-compose.prod.yml --profile "$COMPOSE_PROFILES" up -d
else
    docker compose -f docker-compose.prod.yml up -d
fi
echo ""

# Ожидание готовности сервисов
echo "Ожидание запуска сервисов (это может занять 1-2 минуты)..."
sleep 10

# Проверка статуса
echo ""
echo "Статус контейнеров:"
docker compose -f docker-compose.prod.yml ps
echo ""

# Проверка здоровья
echo "Проверка health checks..."
sleep 30
docker compose -f docker-compose.prod.yml ps

echo ""
echo "=========================================="
echo "✅ Развертывание завершено!"
echo "=========================================="
echo ""
echo "Приложение доступно по адресу:"
echo "  🌐 Admin UI:      http://$DOMAIN/"
echo "  🔧 Admin API:     http://$DOMAIN/api/"
echo "  📚 Swagger UI:    http://$DOMAIN/swagger-ui.html"
echo "  🚀 Gateway Core:  http://$DOMAIN/gateway/"
if [ "$MODE" = "2" ]; then
echo "  📊 Grafana:       http://$DOMAIN/grafana/"
fi
echo ""
echo "Логи контейнеров:"
echo "  docker compose -f docker-compose.prod.yml logs -f"
echo ""
echo "Остановка приложения:"
if [ -n "$COMPOSE_PROFILES" ]; then
echo "  docker compose -f docker-compose.prod.yml --profile $COMPOSE_PROFILES down"
else
echo "  docker compose -f docker-compose.prod.yml down"
fi
echo ""

# Показать использование ресурсов
echo "Использование ресурсов:"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
echo ""
