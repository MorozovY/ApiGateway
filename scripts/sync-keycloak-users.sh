#!/bin/sh
# sync-keycloak-users.sh
# Синхронизирует Keycloak users в PostgreSQL для корректной работы audit_logs FK constraint
# Story 13.13: E2E CI — синхронизация test users в PostgreSQL
#
# Использование:
#   ./scripts/sync-keycloak-users.sh
#
# Переменные окружения:
#   KEYCLOAK_URL - URL Keycloak (default: https://keycloak.ymorozov.ru)
#   KEYCLOAK_ADMIN_USER - Admin username (default: admin)
#   KEYCLOAK_ADMIN_PASSWORD - Admin password (required)
#   POSTGRES_HOST - PostgreSQL host (default: postgres)
#   POSTGRES_USER - PostgreSQL user (default: gateway)
#   POSTGRES_PASSWORD - PostgreSQL password (required)
#   POSTGRES_DB - PostgreSQL database (default: gateway)

set -e

# Конфигурация с fallback значениями
KEYCLOAK_URL="${KEYCLOAK_URL:-https://keycloak.ymorozov.ru}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-api-gateway}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-kcAdmin2026Secure}"

POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-gateway}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-GwDb2026SecurePass}"
POSTGRES_DB="${POSTGRES_DB:-gateway}"

echo "=== Keycloak to PostgreSQL User Sync ==="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "PostgreSQL: $POSTGRES_USER@$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB"

# Получаем admin token из Keycloak
echo "Получаем Keycloak admin token..."
ADMIN_TOKEN=$(curl -sf -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN_USER}" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD}" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "ERROR: Не удалось получить Keycloak admin token"
  exit 1
fi

echo "Keycloak admin token получен"

# Файл для SQL команд
SQL_FILE="/tmp/sync_users.sql"
echo "" > "$SQL_FILE"

# Функция для синхронизации пользователя
sync_user() {
  USERNAME="$1"
  ROLE="$2"

  echo "Ищем $USERNAME в Keycloak..."

  # Получаем user data из Keycloak
  USER_DATA=$(curl -sf \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users?username=${USERNAME}&exact=true" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  # Извлекаем UUID и email
  KC_USER_ID=$(echo "$USER_DATA" | jq -r '.[0].id // empty')
  KC_EMAIL=$(echo "$USER_DATA" | jq -r '.[0].email // empty')

  if [ -z "$KC_USER_ID" ]; then
    echo "  WARNING: Пользователь $USERNAME не найден в Keycloak, пропускаем"
    return 0
  fi

  # Устанавливаем email по умолчанию если пустой
  if [ -z "$KC_EMAIL" ]; then
    KC_EMAIL="${USERNAME}@example.com"
  fi

  echo "  Keycloak ID: $KC_USER_ID, email: $KC_EMAIL"

  # Формируем SQL для UPSERT (ON CONFLICT по username обновляем id)
  cat >> "$SQL_FILE" << EOF
-- Синхронизация $USERNAME с Keycloak UUID
INSERT INTO users (id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES ('${KC_USER_ID}', '${USERNAME}', '${KC_EMAIL}', '\$2a\$10\$placeholder', '${ROLE}', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET id = EXCLUDED.id, email = EXCLUDED.email, updated_at = NOW();
EOF
}

# Синхронизируем пользователей
sync_user "test-admin" "admin"
sync_user "test-developer" "developer"
sync_user "test-security" "security"
sync_user "admin" "admin"
sync_user "developer" "developer"
sync_user "security" "security"

# Проверяем что есть SQL команды
if [ ! -s "$SQL_FILE" ]; then
  echo "WARNING: Нет пользователей для синхронизации"
  exit 0
fi

# Выполняем SQL
echo ""
echo "Выполняем синхронизацию в PostgreSQL..."
PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "$SQL_FILE"

echo ""
echo "=== Синхронизация завершена ==="

# Проверяем результат
echo ""
echo "Проверка синхронизированных пользователей:"
PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT id, username, email, role FROM users WHERE username LIKE 'test-%' OR username IN ('admin', 'developer', 'security') ORDER BY username;"

# Очищаем временный файл
rm -f "$SQL_FILE"
