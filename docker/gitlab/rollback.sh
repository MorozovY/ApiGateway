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
# CI использует /tmp для compose файлов с разными именами:
# - dev: /tmp/docker-compose.ci.yml (из generate-compose.sh dev)
# - test: /tmp/docker-compose.test.yml (из generate-compose.sh test)
# - prod: /tmp/docker-compose.prod.yml (из generate-compose.sh prod)
case "$ENVIRONMENT" in
    dev)
        COMPOSE_FILE="${COMPOSE_FILE:-/tmp/docker-compose.ci.yml}"
        ;;
    test|prod)
        COMPOSE_FILE="${COMPOSE_FILE:-/tmp/docker-compose.${ENVIRONMENT}.yml}"
        ;;
    *)
        COMPOSE_FILE="${COMPOSE_FILE:-/tmp/docker-compose.${ENVIRONMENT}.yml}"
        ;;
esac
PREVIOUS_IMAGES_FILE="/tmp/previous_images_${ENVIRONMENT}.txt"
ROLLBACK_TAG="${ROLLBACK_TAG:-latest}"

# Максимальное время rollback (2 минуты)
ROLLBACK_TIMEOUT=120

rollback_to_previous() {
    log_info "Attempting rollback to previous version..."

    # Проверяем наличие файла с предыдущими images
    if [ -f "$PREVIOUS_IMAGES_FILE" ] && [ -s "$PREVIOUS_IMAGES_FILE" ]; then
        log_info "Found previous images file: $PREVIOUS_IMAGES_FILE"
        cat "$PREVIOUS_IMAGES_FILE"

        # Извлекаем image tags из файла
        local prev_core_image=$(sed -n '1p' "$PREVIOUS_IMAGES_FILE" | tr -d '[:space:]')
        local prev_admin_image=$(sed -n '2p' "$PREVIOUS_IMAGES_FILE" | tr -d '[:space:]')
        local prev_ui_image=$(sed -n '3p' "$PREVIOUS_IMAGES_FILE" | tr -d '[:space:]')

        # Проверяем что все images найдены
        if [ -z "$prev_core_image" ] || [ -z "$prev_admin_image" ] || [ -z "$prev_ui_image" ]; then
            log_warn "Incomplete previous images data, falling back to :latest"
            rollback_to_latest
            return
        fi

        log_info "Rolling back to previous images:"
        log_info "  gateway-core: $prev_core_image"
        log_info "  gateway-admin: $prev_admin_image"
        log_info "  admin-ui: $prev_ui_image"

        # Создаём compose файл с предыдущими images
        create_rollback_compose "$prev_admin_image" "$prev_core_image" "$prev_ui_image"

        # Перезапускаем с предыдущими images
        local rollback_compose="/tmp/docker-compose.rollback.${ENVIRONMENT}.yml"
        docker rm -f "gateway-admin-${ENVIRONMENT}" "gateway-core-${ENVIRONMENT}" "admin-ui-${ENVIRONMENT}" 2>/dev/null || true
        docker-compose -p "$COMPOSE_PROJECT" -f "$rollback_compose" up -d

        log_info "Rollback to previous version completed"
        return 0
    else
        log_warn "No previous images file found, rolling back to :latest"
        rollback_to_latest
    fi
}

# Создаёт compose файл для rollback с указанными images
# Аргументы: admin_image, core_image, ui_image
create_rollback_compose() {
    local admin_image="${1:-}"
    local core_image="${2:-}"
    local ui_image="${3:-}"

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
        prod)
            admin_port="38081"
            core_port="38080"
            ui_port="33000"
            ;;
        *)
            admin_port="8081"
            core_port="8080"
            ui_port="3000"
            ;;
    esac

    local registry_image="${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}"

    # Используем переданные images или fallback на :latest
    admin_image="${admin_image:-${registry_image}/gateway-admin:${ROLLBACK_TAG}}"
    core_image="${core_image:-${registry_image}/gateway-core:${ROLLBACK_TAG}}"
    ui_image="${ui_image:-${registry_image}/admin-ui:${ROLLBACK_TAG}}"

    # Environment variables (получаем из Vault или текущего окружения)
    local db_url="${DATABASE_URL:-r2dbc:postgresql://postgres:5432/gateway}"
    local pg_user="${POSTGRES_USER:-gateway}"
    local pg_pass="${POSTGRES_PASSWORD:-gateway}"
    local redis_host="${REDIS_HOST:-redis}"
    local redis_port="${REDIS_PORT:-6379}"

    # Исправление hostname для Docker network
    local fixed_db_url=$(echo "${db_url}" | sed 's/infra-postgres/postgres/g')
    local fixed_redis_host=$(echo "${redis_host}" | sed 's/infra-redis/redis/g')

    # Извлечение database name из DATABASE_URL
    local db_name=$(echo "${db_url}" | sed -n 's|.*://[^/]*/\([^?]*\).*|\1|p')
    db_name="${db_name:-gateway}"

    # Создаём compose файл с environment variables
    cat > "$rollback_compose" << EOF
# Rollback compose
# Generated: $(date -Iseconds)
# Environment: ${ENVIRONMENT}

services:
  gateway-admin:
    image: ${admin_image}
    container_name: gateway-admin-${ENVIRONMENT}
    environment:
      - SPRING_R2DBC_URL=${fixed_db_url}
      - SPRING_R2DBC_USERNAME=${pg_user}
      - SPRING_R2DBC_PASSWORD=${pg_pass}
      - SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/${db_name}
      - SPRING_FLYWAY_USER=${pg_user}
      - SPRING_FLYWAY_PASSWORD=${pg_pass}
      - SPRING_DATA_REDIS_HOST=${fixed_redis_host}
      - SPRING_DATA_REDIS_PORT=${redis_port}
      - SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
    ports:
      - "${admin_port}:8081"
    networks:
      - gateway-${ENVIRONMENT}
      - postgres-net
      - redis-net
    restart: unless-stopped

  gateway-core:
    image: ${core_image}
    container_name: gateway-core-${ENVIRONMENT}
    environment:
      - SPRING_R2DBC_URL=${fixed_db_url}
      - SPRING_R2DBC_USERNAME=${pg_user}
      - SPRING_R2DBC_PASSWORD=${pg_pass}
      - SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/${db_name}
      - SPRING_FLYWAY_USER=${pg_user}
      - SPRING_FLYWAY_PASSWORD=${pg_pass}
      - SPRING_DATA_REDIS_HOST=${fixed_redis_host}
      - SPRING_DATA_REDIS_PORT=${redis_port}
      - SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
    ports:
      - "${core_port}:8080"
    networks:
      - gateway-${ENVIRONMENT}
      - postgres-net
      - redis-net
    restart: unless-stopped

  admin-ui:
    image: ${ui_image}
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

    log_info "Generated rollback compose: $rollback_compose"
}

rollback_to_latest() {
    log_info "Rolling back to :latest tag..."

    local registry_image="${CI_REGISTRY_IMAGE:-localhost:5050/root/api-gateway}"

    # Создаём compose с :latest images
    create_rollback_compose \
        "${registry_image}/gateway-admin:${ROLLBACK_TAG}" \
        "${registry_image}/gateway-core:${ROLLBACK_TAG}" \
        "${registry_image}/admin-ui:${ROLLBACK_TAG}"

    local rollback_compose="/tmp/docker-compose.rollback.${ENVIRONMENT}.yml"

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
