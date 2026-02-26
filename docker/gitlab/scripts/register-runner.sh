#!/usr/bin/env bash
# Скрипт регистрации GitLab Runner
# Требуется registration token из GitLab Admin → CI/CD → Runners

set -e

if [ -z "$1" ]; then
    echo "Использование: ./register-runner.sh <REGISTRATION_TOKEN>"
    echo ""
    echo "Получение токена:"
    echo "  1. Войдите в GitLab (http://localhost:8929) как root"
    echo "  2. Admin Area → CI/CD → Runners → New instance runner"
    echo "  3. Скопируйте registration token"
    exit 1
fi

TOKEN=$1

echo "🔧 Регистрация GitLab Runner..."

docker exec -it gitlab-runner gitlab-runner register \
    --non-interactive \
    --url "http://gitlab:8929" \
    --token "$TOKEN" \
    --executor "docker" \
    --docker-image "docker:latest" \
    --description "local-docker-runner" \
    --docker-privileged \
    --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
    --docker-network-mode "gitlab_network"

echo ""
echo "✅ Runner зарегистрирован!"
echo ""
echo "Проверка:"
docker exec -it gitlab-runner gitlab-runner list
