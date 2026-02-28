#!/bin/bash
# Rollback Script для ApiGateway
# Story 13.5: Deployment Pipeline — Dev & Test Environments
#
# Использование:
#   ./rollback.sh [environment]
#
# Откатывает deployment к предыдущей версии или :latest

set -euo pipefail

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Конфигурация
ENVIRONMENT="${1:-dev}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/apigateway}"
COMPOSE_PROJECT="apigateway-${ENVIRONMENT}"
PREVIOUS_IMAGES_FILE="/tmp/previous_images_${ENVIRONMENT}.txt"
ROLLBACK_TAG="${ROLLBACK_TAG:-latest}"

# Максимальное время rollback (2 минуты)
ROLLBACK_TIMEOUT=120

rollback_to_previous() {
    log_info "Attempting rollback to previous version..."

    cd "$DEPLOY_PATH"

    # Проверяем наличие файла с предыдущими images
    if [ -f "$PREVIOUS_IMAGES_FILE" ] && [ -s "$PREVIOUS_IMAGES_FILE" ]; then
        log_info "Found previous images file"

        # Пытаемся использовать предыдущие images
        # Файл содержит image tags, нужно извлечь и использовать

        # Останавливаем текущие контейнеры
        docker-compose -p "$COMPOSE_PROJECT" down --timeout 30 || true

        # Удаляем CI override чтобы использовать оригинальные images
        rm -f "${DEPLOY_PATH}/docker-compose.ci.yml"

        # Запускаем с оригинальным compose (без override)
        docker-compose -p "$COMPOSE_PROJECT" up -d

        log_info "Rollback to previous deployment completed"
        return 0
    else
        log_warn "No previous images file found, rolling back to :latest"
        rollback_to_latest
    fi
}

rollback_to_latest() {
    log_info "Rolling back to :latest tag..."

    cd "$DEPLOY_PATH"

    # Создаём override с :latest tags
    cat > "${DEPLOY_PATH}/docker-compose.ci.yml" << EOF
# Rollback override - using :latest tags
# Generated: $(date -Iseconds)

version: '3.8'

services:
  gateway-admin:
    image: ${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}/gateway-admin:${ROLLBACK_TAG}

  gateway-core:
    image: ${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}/gateway-core:${ROLLBACK_TAG}

  admin-ui:
    image: ${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}/admin-ui:${ROLLBACK_TAG}
EOF

    # Pull latest images
    docker-compose -p "$COMPOSE_PROJECT" -f docker-compose.yml -f docker-compose.ci.yml pull || true

    # Останавливаем текущие контейнеры
    docker-compose -p "$COMPOSE_PROJECT" down --timeout 30 || true

    # Запускаем с latest
    docker-compose -p "$COMPOSE_PROJECT" -f docker-compose.yml -f docker-compose.ci.yml up -d

    log_info "Rollback to :$ROLLBACK_TAG completed"
}

verify_rollback() {
    log_info "Verifying rollback..."

    cd "$DEPLOY_PATH"

    local start_time=$(date +%s)

    # Простая проверка что контейнеры запустились
    for i in $(seq 1 10); do
        local running=$(docker-compose -p "$COMPOSE_PROJECT" ps --filter "status=running" -q 2>/dev/null | wc -l)

        if [ "$running" -ge 3 ]; then
            log_info "All containers are running after rollback"
            return 0
        fi

        local elapsed=$(($(date +%s) - start_time))
        if [ $elapsed -gt $ROLLBACK_TIMEOUT ]; then
            log_error "Rollback verification timeout exceeded"
            return 1
        fi

        log_warn "Waiting for containers to start... ($running/3 running)"
        sleep 5
    done

    log_error "Rollback verification failed - not all containers started"
    return 1
}

main() {
    log_info "=========================================="
    log_info "Starting ROLLBACK for ${ENVIRONMENT}"
    log_info "=========================================="

    local start_time=$(date +%s)

    # Выполняем rollback
    rollback_to_previous

    # Проверяем результат
    if verify_rollback; then
        local elapsed=$(($(date +%s) - start_time))
        log_info "=========================================="
        log_info "ROLLBACK SUCCESSFUL (${elapsed}s)"
        log_info "=========================================="
        exit 0
    else
        log_error "=========================================="
        log_error "ROLLBACK FAILED"
        log_error "Manual intervention required!"
        log_error "=========================================="
        exit 1
    fi
}

# Запуск
main "$@"
