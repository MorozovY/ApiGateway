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
# CI использует /tmp для compose файлов, локально — DEPLOY_PATH
COMPOSE_FILE="${COMPOSE_FILE:-/tmp/docker-compose.${ENVIRONMENT}.yml}"
PREVIOUS_IMAGES_FILE="/tmp/previous_images_${ENVIRONMENT}.txt"
ROLLBACK_TAG="${ROLLBACK_TAG:-latest}"

# Максимальное время rollback (2 минуты)
ROLLBACK_TIMEOUT=120

rollback_to_previous() {
    log_info "Attempting rollback to previous version..."

    # Проверяем наличие файла с предыдущими images
    if [ -f "$PREVIOUS_IMAGES_FILE" ] && [ -s "$PREVIOUS_IMAGES_FILE" ]; then
        log_info "Found previous images file"

        # Пытаемся использовать предыдущие images
        # Файл содержит image tags, нужно извлечь и использовать

        # Останавливаем текущие контейнеры
        if [ -f "$COMPOSE_FILE" ]; then
            docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" down --timeout 30 || true
        else
            # Fallback: остановить контейнеры по имени
            log_warn "Compose file not found at $COMPOSE_FILE, stopping containers by name"
            docker rm -f "gateway-admin-${ENVIRONMENT}" "gateway-core-${ENVIRONMENT}" "admin-ui-${ENVIRONMENT}" 2>/dev/null || true
        fi

        log_info "Rollback to previous deployment completed (containers stopped)"
        log_warn "Manual restart with previous images may be required"
        return 0
    else
        log_warn "No previous images file found, rolling back to :latest"
        rollback_to_latest
    fi
}

rollback_to_latest() {
    log_info "Rolling back to :latest tag..."

    local rollback_compose="/tmp/docker-compose.rollback.${ENVIRONMENT}.yml"

    # Определяем порты в зависимости от environment
    local admin_port core_port ui_port
    case "$ENVIRONMENT" in
        dev)
            admin_port="28081"
            core_port="28080"
            ui_port="23000"
            ;;
        test)
            admin_port="18081"
            core_port="18080"
            ui_port="13000"
            ;;
        *)
            admin_port="8081"
            core_port="8080"
            ui_port="3000"
            ;;
    esac

    local registry_image="${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}"

    # Создаём compose файл с :latest tags
    cat > "$rollback_compose" << EOF
# Rollback compose - using :latest tags
# Generated: $(date -Iseconds)
# Environment: ${ENVIRONMENT}

services:
  gateway-admin:
    image: ${registry_image}/gateway-admin:${ROLLBACK_TAG}
    container_name: gateway-admin-${ENVIRONMENT}
    ports:
      - "${admin_port}:8081"
    networks:
      - gateway-${ENVIRONMENT}
      - postgres-net
      - redis-net
    restart: unless-stopped

  gateway-core:
    image: ${registry_image}/gateway-core:${ROLLBACK_TAG}
    container_name: gateway-core-${ENVIRONMENT}
    ports:
      - "${core_port}:8080"
    networks:
      - gateway-${ENVIRONMENT}
      - postgres-net
      - redis-net
    restart: unless-stopped

  admin-ui:
    image: ${registry_image}/admin-ui:${ROLLBACK_TAG}
    container_name: admin-ui-${ENVIRONMENT}
    ports:
      - "${ui_port}:80"
    networks:
      - gateway-${ENVIRONMENT}
    restart: unless-stopped

networks:
  gateway-${ENVIRONMENT}:
    driver: bridge
  postgres-net:
    external: true
  redis-net:
    external: true
EOF

    # Pull latest images
    docker-compose -p "$COMPOSE_PROJECT" -f "$rollback_compose" pull || true

    # Останавливаем текущие контейнеры
    docker rm -f "gateway-admin-${ENVIRONMENT}" "gateway-core-${ENVIRONMENT}" "admin-ui-${ENVIRONMENT}" 2>/dev/null || true

    # Запускаем с latest
    docker-compose -p "$COMPOSE_PROJECT" -f "$rollback_compose" up -d

    log_info "Rollback to :$ROLLBACK_TAG completed"
}

verify_rollback() {
    log_info "Verifying rollback..."

    local start_time=$(date +%s)

    # Простая проверка что контейнеры запустились (по имени)
    for i in $(seq 1 10); do
        local running=0

        # Проверяем каждый контейнер по имени
        docker ps --filter "name=gateway-admin-${ENVIRONMENT}" --filter "status=running" -q | grep -q . && running=$((running + 1))
        docker ps --filter "name=gateway-core-${ENVIRONMENT}" --filter "status=running" -q | grep -q . && running=$((running + 1))
        docker ps --filter "name=admin-ui-${ENVIRONMENT}" --filter "status=running" -q | grep -q . && running=$((running + 1))

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
