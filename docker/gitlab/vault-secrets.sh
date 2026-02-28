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

# Логин через AppRole с проверкой HTTP статуса
LOGIN_RESPONSE=$(curl -sw '\n%{http_code}' \
    --request POST \
    --data "{\"role_id\":\"$VAULT_ROLE_ID\",\"secret_id\":\"$VAULT_SECRET_ID\"}" \
    "$VAULT_ADDR/v1/auth/approle/login" 2>/dev/null)

HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -n1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

# Проверка HTTP статуса
if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: Vault login failed with HTTP $HTTP_CODE"
    case "$HTTP_CODE" in
        000) echo "  → Network error: cannot reach Vault at $VAULT_ADDR" ;;
        400) echo "  → Bad request: invalid role_id or secret_id format" ;;
        401) echo "  → Unauthorized: invalid credentials" ;;
        403) echo "  → Forbidden: AppRole may be disabled or sealed" ;;
        503) echo "  → Vault is sealed or unavailable" ;;
        *) echo "  → Unexpected error" ;;
    esac
    exit 1
fi

VAULT_TOKEN=$(echo "$LOGIN_BODY" | jq -r '.auth.client_token')

if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
    echo "ERROR: Vault AppRole login succeeded but no token returned"
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

# Счётчик ошибок
ERRORS=0

# Функция для безопасного получения и проверки secret
get_and_check() {
    local path=$1
    local key=$2
    local var_name=$3
    local required=${4:-true}

    local value
    value=$(get_secret "$path" "$key")
    local status=$?

    if [ $status -ne 0 ] || [ -z "$value" ]; then
        if [ "$required" = "true" ]; then
            echo "ERROR: Required secret $path/$key not found" >&2
            ERRORS=$((ERRORS + 1))
        else
            echo "WARNING: Optional secret $path/$key not found" >&2
        fi
        echo ""
    else
        echo "$value"
    fi
}

# Получаем database secrets (все обязательные)
export POSTGRES_USER=$(get_and_check "apigateway/database" "POSTGRES_USER" "POSTGRES_USER" true)
export POSTGRES_PASSWORD=$(get_and_check "apigateway/database" "POSTGRES_PASSWORD" "POSTGRES_PASSWORD" true)
export DATABASE_URL=$(get_and_check "apigateway/database" "DATABASE_URL" "DATABASE_URL" true)

# Получаем redis secrets (обязательные)
export REDIS_HOST=$(get_and_check "apigateway/redis" "REDIS_HOST" "REDIS_HOST" true)
export REDIS_PORT=$(get_and_check "apigateway/redis" "REDIS_PORT" "REDIS_PORT" true)
export REDIS_URL=$(get_and_check "apigateway/redis" "REDIS_URL" "REDIS_URL" false)

# Получаем keycloak secrets (обязательные для production)
export KEYCLOAK_ADMIN_USERNAME=$(get_and_check "apigateway/keycloak" "KEYCLOAK_ADMIN_USERNAME" "KEYCLOAK_ADMIN_USERNAME" true)
export KEYCLOAK_ADMIN_PASSWORD=$(get_and_check "apigateway/keycloak" "KEYCLOAK_ADMIN_PASSWORD" "KEYCLOAK_ADMIN_PASSWORD" true)

# Проверка критических ошибок
if [ $ERRORS -gt 0 ]; then
    echo "ERROR: $ERRORS required secret(s) not found. Aborting."
    unset VAULT_TOKEN
    exit 1
fi

echo "Secrets loaded successfully:"
echo "  ✓ POSTGRES_USER, POSTGRES_PASSWORD, DATABASE_URL"
echo "  ✓ REDIS_HOST, REDIS_PORT"
echo "  ✓ KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD"

# Очищаем токен из памяти (security)
unset VAULT_TOKEN
