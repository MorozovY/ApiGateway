#!/bin/bash
# Генерирует docker-compose.yml для CI deployment
# Story 13.5: Deployment Pipeline
# Updated: Traefik integration (Story 13.8)
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

# Конфигурация в зависимости от environment
# - Порты для прямого доступа (fallback)
# - Traefik hostname для routing
case "$ENVIRONMENT" in
  dev)
    ADMIN_PORT="28081"
    CORE_PORT="28080"
    UI_PORT="23000"
    TRAEFIK_HOST="gateway.ymorozov.ru"
    ;;
  test)
    ADMIN_PORT="18081"
    CORE_PORT="18080"
    UI_PORT="13000"
    TRAEFIK_HOST="gateway-test.ymorozov.ru"
    ;;
  prod)
    ADMIN_PORT="38081"
    CORE_PORT="38080"
    UI_PORT="33000"
    TRAEFIK_HOST="gateway.ymorozov.ru"
    ;;
  *)
    echo "Unknown environment: $ENVIRONMENT"
    exit 1
    ;;
esac

NETWORK_NAME="gateway-${ENVIRONMENT}"
CONTAINER_SUFFIX="-${ENVIRONMENT}"
ROUTER_SUFFIX="-${ENVIRONMENT}"

# Исправление hostname для Docker network
# Vault может содержать "infra-postgres", "gateway-redis" или другие hostname,
# но на Docker network контейнеры называются "postgres" и "redis"
FIXED_DATABASE_URL=$(echo "${DATABASE_URL}" | sed 's/infra-postgres/postgres/g; s/gateway-postgres/postgres/g')
FIXED_REDIS_HOST=$(echo "${REDIS_HOST}" | sed 's/infra-redis/redis/g; s/gateway-redis/redis/g')

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
    labels:
      # === TRAEFIK ROUTING ===
      - "traefik.enable=true"
      # --- Admin API Router (/api/v1/*) ---
      - "traefik.http.routers.gateway-admin-api${ROUTER_SUFFIX}.rule=Host(\`${TRAEFIK_HOST}\`) && PathPrefix(\`/api/v1\`)"
      - "traefik.http.routers.gateway-admin-api${ROUTER_SUFFIX}.entrypoints=websecure"
      - "traefik.http.routers.gateway-admin-api${ROUTER_SUFFIX}.tls.certresolver=letsencrypt"
      - "traefik.http.routers.gateway-admin-api${ROUTER_SUFFIX}.service=gateway-admin${ROUTER_SUFFIX}"
      # --- Swagger UI Router ---
      - "traefik.http.routers.gateway-admin-swagger${ROUTER_SUFFIX}.rule=Host(\`${TRAEFIK_HOST}\`) && (PathPrefix(\`/swagger-ui\`) || PathPrefix(\`/v3/api-docs\`) || PathPrefix(\`/webjars\`))"
      - "traefik.http.routers.gateway-admin-swagger${ROUTER_SUFFIX}.entrypoints=websecure"
      - "traefik.http.routers.gateway-admin-swagger${ROUTER_SUFFIX}.tls.certresolver=letsencrypt"
      - "traefik.http.routers.gateway-admin-swagger${ROUTER_SUFFIX}.service=gateway-admin${ROUTER_SUFFIX}"
      # --- Service Definition ---
      - "traefik.http.services.gateway-admin${ROUTER_SUFFIX}.loadbalancer.server.port=8081"
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
      - traefik-net
      - postgres-net
      - redis-net
    restart: unless-stopped

  gateway-core:
    image: ${CI_REGISTRY_IMAGE}/gateway-core:${CI_COMMIT_SHA}
    container_name: gateway-core${CONTAINER_SUFFIX}
    labels:
      # === TRAEFIK ROUTING ===
      - "traefik.enable=true"
      # --- Gateway Core API Router (/api/* except /api/v1) ---
      - "traefik.http.routers.gateway-core-api${ROUTER_SUFFIX}.rule=Host(\`${TRAEFIK_HOST}\`) && PathPrefix(\`/api\`) && !PathPrefix(\`/api/v1\`)"
      - "traefik.http.routers.gateway-core-api${ROUTER_SUFFIX}.entrypoints=websecure"
      - "traefik.http.routers.gateway-core-api${ROUTER_SUFFIX}.tls.certresolver=letsencrypt"
      - "traefik.http.routers.gateway-core-api${ROUTER_SUFFIX}.service=gateway-core${ROUTER_SUFFIX}"
      # --- Strip /api prefix (gateway-core expects / not /api/) ---
      - "traefik.http.routers.gateway-core-api${ROUTER_SUFFIX}.middlewares=gateway-core-strip${ROUTER_SUFFIX}"
      - "traefik.http.middlewares.gateway-core-strip${ROUTER_SUFFIX}.stripprefix.prefixes=/api"
      # --- Service Definition ---
      - "traefik.http.services.gateway-core${ROUTER_SUFFIX}.loadbalancer.server.port=8080"
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
      - traefik-net
      - postgres-net
      - redis-net
    restart: unless-stopped

  admin-ui:
    image: ${CI_REGISTRY_IMAGE}/admin-ui:${CI_COMMIT_SHA}
    container_name: admin-ui${CONTAINER_SUFFIX}
    labels:
      # === TRAEFIK ROUTING ===
      - "traefik.enable=true"
      # --- Admin UI Router (catch-all for frontend) ---
      - "traefik.http.routers.admin-ui${ROUTER_SUFFIX}.rule=Host(\`${TRAEFIK_HOST}\`)"
      - "traefik.http.routers.admin-ui${ROUTER_SUFFIX}.entrypoints=websecure"
      - "traefik.http.routers.admin-ui${ROUTER_SUFFIX}.tls.certresolver=letsencrypt"
      - "traefik.http.routers.admin-ui${ROUTER_SUFFIX}.service=admin-ui${ROUTER_SUFFIX}"
      # Самый низкий приоритет — catch-all после /api/* и /swagger-ui*
      - "traefik.http.routers.admin-ui${ROUTER_SUFFIX}.priority=1"
      # --- Service Definition ---
      - "traefik.http.services.admin-ui${ROUTER_SUFFIX}.loadbalancer.server.port=80"
    ports:
      - "${UI_PORT}:80"
    networks:
      - ${NETWORK_NAME}
      - traefik-net
    restart: unless-stopped

networks:
  ${NETWORK_NAME}:
    driver: bridge
  traefik-net:
    external: true
  postgres-net:
    external: true
  redis-net:
    external: true
COMPOSE_EOF

echo "Generated $OUTPUT_FILE for $ENVIRONMENT environment"
