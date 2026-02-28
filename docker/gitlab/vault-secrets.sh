#!/bin/sh
# Vault Secrets Injection Script for GitLab CI/CD
# Story 13.4: Vault Integration
#
# Использование:
#   source ./docker/gitlab/vault-secrets.sh
#
# Требуемые переменные окружения (из GitLab CI/CD Variables):
#   VAULT_ADDR      - Vault server URL (например: http://vault:8200)
#   VAULT_ROLE_ID   - AppRole Role ID
#   VAULT_SECRET_ID - AppRole Secret ID

set -e

# Проверка зависимостей
if ! command -v curl > /dev/null 2>&1; then
    echo "ERROR: curl не установлен"
    exit 1
fi

if ! command -v jq > /dev/null 2>&1; then
    echo "ERROR: jq не установлен"
    exit 1
fi

# Проверка переменных
if [ -z "$VAULT_ADDR" ]; then
    echo "ERROR: VAULT_ADDR не задан"
    exit 1
fi

if [ -z "$VAULT_ROLE_ID" ]; then
    echo "ERROR: VAULT_ROLE_ID не задан"
    exit 1
fi

if [ -z "$VAULT_SECRET_ID" ]; then
    echo "ERROR: VAULT_SECRET_ID не задан"
    exit 1
fi

echo "Authenticating to Vault via AppRole..."

# Логин через AppRole
VAULT_TOKEN=$(curl -sf \
    --request POST \
    --data "{\"role_id\":\"$VAULT_ROLE_ID\",\"secret_id\":\"$VAULT_SECRET_ID\"}" \
    "$VAULT_ADDR/v1/auth/approle/login" | jq -r '.auth.client_token')

if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
    echo "ERROR: Vault AppRole login failed"
    exit 1
fi

echo "Vault authentication successful"

# Функция для получения secret
get_secret() {
    local path=$1
    local key=$2
    local result

    result=$(curl -sf \
        --header "X-Vault-Token: $VAULT_TOKEN" \
        "$VAULT_ADDR/v1/secret/data/$path" | jq -r ".data.data.$key // empty")

    if [ -z "$result" ]; then
        echo "WARNING: Secret $path/$key not found" >&2
        return 1
    fi

    echo "$result"
}

echo "Fetching secrets from Vault..."

# Получаем database secrets
export POSTGRES_USER=$(get_secret "apigateway/database" "POSTGRES_USER")
export POSTGRES_PASSWORD=$(get_secret "apigateway/database" "POSTGRES_PASSWORD")
export DATABASE_URL=$(get_secret "apigateway/database" "DATABASE_URL")

# Получаем redis secrets
export REDIS_HOST=$(get_secret "apigateway/redis" "REDIS_HOST")
export REDIS_PORT=$(get_secret "apigateway/redis" "REDIS_PORT")
export REDIS_URL=$(get_secret "apigateway/redis" "REDIS_URL")

# Получаем keycloak secrets
export KEYCLOAK_ADMIN_USERNAME=$(get_secret "apigateway/keycloak" "KEYCLOAK_ADMIN_USERNAME")
export KEYCLOAK_ADMIN_PASSWORD=$(get_secret "apigateway/keycloak" "KEYCLOAK_ADMIN_PASSWORD")

echo "Secrets loaded successfully (values hidden)"

# Очищаем токен из памяти (security)
unset VAULT_TOKEN
