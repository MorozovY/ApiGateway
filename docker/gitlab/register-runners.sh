#!/bin/bash
# Скрипт для регистрации GitLab Runners
# Запускать после docker-compose up -d
#
# Использование: ./register-runners.sh [REGISTRATION_TOKEN]
#
# Если token не указан, скрипт попытается получить его через GitLab API

set -e

GITLAB_URL="http://gitlab:8929"
GITLAB_EXTERNAL_URL="http://localhost:8929"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== GitLab Runners Registration ===${NC}"

# Проверяем что GitLab запущен
echo -e "${YELLOW}Проверяю доступность GitLab...${NC}"
until curl -sf "$GITLAB_EXTERNAL_URL/-/readiness" > /dev/null 2>&1; do
    echo "Ожидаю запуска GitLab..."
    sleep 5
done
echo -e "${GREEN}GitLab доступен${NC}"

# Получаем или используем переданный token
if [ -n "$1" ]; then
    REGISTRATION_TOKEN="$1"
    echo -e "${GREEN}Используем переданный registration token${NC}"
else
    echo -e "${YELLOW}Registration token не указан.${NC}"
    echo ""
    echo "Для получения token:"
    echo "  1. Откройте http://localhost:8929/admin/runners"
    echo "  2. Нажмите 'New instance runner'"
    echo "  3. Скопируйте registration token"
    echo ""
    read -p "Введите Registration Token: " REGISTRATION_TOKEN
fi

if [ -z "$REGISTRATION_TOKEN" ]; then
    echo -e "${RED}Error: Registration token обязателен${NC}"
    exit 1
fi

# Функция регистрации runner
register_runner() {
    local RUNNER_NAME=$1
    local RUNNER_NUM=$2

    echo -e "${YELLOW}Регистрирую $RUNNER_NAME...${NC}"

    # Проверяем, не зарегистрирован ли уже runner
    if docker exec $RUNNER_NAME gitlab-runner list 2>&1 | grep -q "Executor"; then
        echo -e "${YELLOW}$RUNNER_NAME уже зарегистрирован, пропускаю${NC}"
        return 0
    fi

    # Регистрируем runner (GitLab 18+ не поддерживает --tag-list с auth токенами)
    docker exec $RUNNER_NAME gitlab-runner register \
        --non-interactive \
        --url "$GITLAB_URL" \
        --token "$REGISTRATION_TOKEN" \
        --executor "docker" \
        --docker-image "alpine:latest" \
        --description "runner-$RUNNER_NUM" \
        --docker-privileged \
        --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
        --docker-volumes "gitlab_gradle_cache:/cache/gradle" \
        --docker-volumes "gitlab_npm_cache:/cache/npm" \
        --docker-extra-hosts "gitlab:host-gateway" \
        --docker-extra-hosts "nexus:host-gateway"

    echo -e "${GREEN}$RUNNER_NAME зарегистрирован успешно${NC}"

    # Добавляем clone_url в конфигурацию (требуется для правильного клонирования)
    echo -e "${YELLOW}Настраиваю clone_url для $RUNNER_NAME...${NC}"
    docker exec $RUNNER_NAME sed -i 's|url = "http://gitlab:8929"|url = "http://gitlab:8929"\n  clone_url = "http://gitlab:8929"|' /etc/gitlab-runner/config.toml
}

# Регистрируем все 4 runners
for i in 1 2 3 4; do
    RUNNER_NAME="gitlab-runner-$i"

    # Проверяем что контейнер запущен
    if ! docker ps --format '{{.Names}}' | grep -q "^${RUNNER_NAME}$"; then
        echo -e "${RED}Контейнер $RUNNER_NAME не запущен. Запустите: docker-compose up -d${NC}"
        continue
    fi

    register_runner "$RUNNER_NAME" "$i"
done

echo ""
echo -e "${GREEN}=== Регистрация завершена ===${NC}"
echo ""
echo "Проверить статус runners:"
echo "  http://localhost:8929/admin/runners"
echo ""
echo "Или через CLI:"
for i in 1 2 3 4; do
    echo "  docker exec gitlab-runner-$i gitlab-runner list"
done
