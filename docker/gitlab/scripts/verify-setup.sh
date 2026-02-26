#!/usr/bin/env bash
# Скрипт верификации установки GitLab
# Запускать после docker-compose up -d и ожидания инициализации

set -e

echo "🔍 Проверка GitLab Infrastructure..."
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# 1. Проверка контейнеров
echo "1️⃣ Проверка Docker контейнеров..."
if docker ps --format '{{.Names}}' | grep -q "^gitlab$"; then
    check_pass "GitLab контейнер запущен"
else
    check_fail "GitLab контейнер не найден"
    exit 1
fi

if docker ps --format '{{.Names}}' | grep -q "^gitlab-runner$"; then
    check_pass "GitLab Runner контейнер запущен"
else
    check_warn "GitLab Runner контейнер не найден (может ожидать healthcheck)"
fi

echo ""

# 2. Проверка GitLab Web UI
echo "2️⃣ Проверка GitLab Web UI..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8929/-/readiness | grep -q "200"; then
    check_pass "GitLab Web UI доступен (http://localhost:8929)"
else
    check_warn "GitLab ещё инициализируется. Подождите 3-5 минут."
fi

echo ""

# 3. Проверка Container Registry
echo "3️⃣ Проверка Container Registry..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:5050/v2/ | grep -qE "200|401"; then
    check_pass "Container Registry доступен (http://localhost:5050)"
else
    check_warn "Container Registry недоступен. Проверьте insecure-registries."
fi

echo ""

# 4. Проверка volumes
echo "4️⃣ Проверка Docker volumes..."
for vol in gitlab_config gitlab_logs gitlab_data gitlab_runner_config; do
    if docker volume ls -q | grep -q "^${vol}$"; then
        check_pass "Volume ${vol} существует"
    else
        check_fail "Volume ${vol} не найден"
    fi
done

echo ""

# 5. Начальный пароль root
echo "5️⃣ Получение пароля root..."
PASS=$(docker exec gitlab cat /etc/gitlab/initial_root_password 2>/dev/null | grep "Password:" | awk '{print $2}')
if [ -n "$PASS" ]; then
    # Маскируем середину пароля для безопасности в shared терминалах
    PASS_LEN=${#PASS}
    if [ "$PASS_LEN" -gt 8 ]; then
        PASS_MASKED="${PASS:0:4}****${PASS: -4}"
    else
        PASS_MASKED="****"
    fi
    echo ""
    echo "📋 Credentials для входа в GitLab:"
    echo "   URL:      http://localhost:8929"
    echo "   Username: root"
    echo "   Password: $PASS_MASKED (частично скрыт)"
    echo ""
    echo "   Полный пароль: docker exec gitlab cat /etc/gitlab/initial_root_password"
    echo ""
    check_warn "Смените пароль после первого входа!"
else
    check_warn "Пароль недоступен (возможно, уже сменён или прошло 24 часа)"
fi

echo ""
echo "🎉 Проверка завершена!"
