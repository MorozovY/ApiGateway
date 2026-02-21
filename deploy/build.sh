#!/bin/bash
# Скрипт сборки приложений для production deployment
# Собирает JAR файлы и Docker образы

set -e  # Остановка при ошибке

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "ApiGateway Production Build"
echo "=========================================="
echo "Project root: $PROJECT_ROOT"
echo ""

# Переход в корень проекта
cd "$PROJECT_ROOT"

# 1. Сборка backend (Gradle)
echo "[1/3] Сборка backend (Kotlin/Spring Boot)..."
./gradlew clean build -x test
echo "✅ Backend собран успешно"
echo ""

# 2. Проверка наличия JAR файлов
echo "[2/3] Проверка JAR файлов..."
ADMIN_JAR=$(find backend/gateway-admin/build/libs -name "gateway-admin-*.jar" | head -n 1)
CORE_JAR=$(find backend/gateway-core/build/libs -name "gateway-core-*.jar" | head -n 1)

if [ ! -f "$ADMIN_JAR" ]; then
    echo "❌ ОШИБКА: gateway-admin JAR не найден"
    exit 1
fi

if [ ! -f "$CORE_JAR" ]; then
    echo "❌ ОШИБКА: gateway-core JAR не найден"
    exit 1
fi

echo "✅ gateway-admin: $ADMIN_JAR"
echo "✅ gateway-core: $CORE_JAR"
echo ""

# 3. Сборка Docker образов
echo "[3/3] Сборка Docker образов..."

# Переход в папку deploy для использования production docker-compose
cd "$SCRIPT_DIR"

# Сборка образов
docker compose -f docker-compose.prod.yml build --no-cache

echo ""
echo "=========================================="
echo "✅ Сборка завершена успешно!"
echo "=========================================="
echo ""
echo "Созданные Docker образы:"
docker images | grep -E "gateway-admin|gateway-core|admin-ui" | grep latest
echo ""
echo "Следующие шаги:"
echo "1. Настройте .env файл (cp .env.example .env)"
echo "2. Запустите deploy.sh для развертывания"
echo ""
