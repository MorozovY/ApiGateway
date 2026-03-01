#!/bin/bash
# Генерирует docker-compose.yml для CI deployment
# Story 13.5: Deployment Pipeline
#
# Использование:
#   ./generate-compose.sh [environment] [output_file]
#
# Примеры:
#   ./generate-compose.sh dev /tmp/docker-compose.ci.yml
#   ./generate-compose.sh test /tmp/docker-compose.test.yml
#
# Требуемые переменные окружения:
#   CI_REGISTRY_IMAGE, CI_COMMIT_SHA
#   DATABASE_URL, POSTGRES_USER, POSTGRES_PASSWORD
#   REDIS_HOST, REDIS_PORT

set -euo pipefail

ENVIRONMENT="${1:-dev}"
OUTPUT_FILE="${2:-/tmp/docker-compose.ci.yml}"

# Порты в зависимости от environment
# (используем нестандартные порты чтобы не конфликтовать с инфраструктурой)
case "$ENVIRONMENT" in
  dev)
    ADMIN_PORT="28081"
    CORE_PORT="28080"
    UI_PORT="23000"
    ;;
  test)
    ADMIN_PORT="18081"
    CORE_PORT="18080"
    UI_PORT="13000"
    ;;
  prod)
    ADMIN_PORT="38081"
    CORE_PORT="38080"
    UI_PORT="33000"
    ;;
  *)
    echo "Unknown environment: $ENVIRONMENT"
    exit 1
    ;;
esac

NETWORK_NAME="gateway-${ENVIRONMENT}"
CONTAINER_SUFFIX="-${ENVIRONMENT}"

# Исправление hostname для Docker network
# Vault может содержать "infra-postgres" или другие hostname,
# но на Docker network postgres-net контейнер называется "postgres"
FIXED_DATABASE_URL=$(echo "${DATABASE_URL}" | sed 's/infra-postgres/postgres/g')
FIXED_REDIS_HOST=$(echo "${REDIS_HOST}" | sed 's/infra-redis/redis/g')

# Извлечение database name из DATABASE_URL
# Формат: r2dbc:postgresql://host:port/dbname или postgresql://host:port/dbname
# Используем fallback "gateway" если не удаётся извлечь
EXTRACTED_DB_NAME=$(echo "${DATABASE_URL}" | sed -n 's|.*://[^/]*/\([^?]*\).*|\1|p')
POSTGRES_DB="${POSTGRES_DB:-${EXTRACTED_DB_NAME:-gateway}}"

cat > "$OUTPUT_FILE" << COMPOSE_EOF
services:
  gateway-admin:
    image: ${CI_REGISTRY_IMAGE}/gateway-admin:${CI_COMMIT_SHA}
    container_name: gateway-admin${CONTAINER_SUFFIX}
    environment:
      - SPRING_R2DBC_URL=${FIXED_DATABASE_URL}
      - SPRING_R2DBC_USERNAME=${POSTGRES_USER}
      - SPRING_R2DBC_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB:-gateway}
      - SPRING_FLYWAY_USER=${POSTGRES_USER}
      - SPRING_FLYWAY_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_HOST=${FIXED_REDIS_HOST}
      - SPRING_DATA_REDIS_PORT=${REDIS_PORT}
      - SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
    ports:
      - "${ADMIN_PORT}:8081"
    networks:
      - ${NETWORK_NAME}
      - postgres-net
      - redis-net
    restart: unless-stopped

  gateway-core:
    image: ${CI_REGISTRY_IMAGE}/gateway-core:${CI_COMMIT_SHA}
    container_name: gateway-core${CONTAINER_SUFFIX}
    environment:
      - SPRING_R2DBC_URL=${FIXED_DATABASE_URL}
      - SPRING_R2DBC_USERNAME=${POSTGRES_USER}
      - SPRING_R2DBC_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB:-gateway}
      - SPRING_FLYWAY_USER=${POSTGRES_USER}
      - SPRING_FLYWAY_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_HOST=${FIXED_REDIS_HOST}
      - SPRING_DATA_REDIS_PORT=${REDIS_PORT}
      - SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
    ports:
      - "${CORE_PORT}:8080"
    networks:
      - ${NETWORK_NAME}
      - postgres-net
      - redis-net
    restart: unless-stopped

  admin-ui:
    image: ${CI_REGISTRY_IMAGE}/admin-ui:${CI_COMMIT_SHA}
    container_name: admin-ui${CONTAINER_SUFFIX}
    ports:
      - "${UI_PORT}:80"
    networks:
      - ${NETWORK_NAME}
    restart: unless-stopped

networks:
  ${NETWORK_NAME}:
    driver: bridge
  postgres-net:
    external: true
  redis-net:
    external: true
COMPOSE_EOF

echo "Generated $OUTPUT_FILE for $ENVIRONMENT environment"
