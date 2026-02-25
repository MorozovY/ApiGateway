#!/bin/bash
# Seed script для создания тестовых consumers в Keycloak (Story 12.9, Task 14)
#
# Использование:
#   bash scripts/seed-keycloak-consumers.sh
#
# Требования:
#   - curl
#   - jq
#   - Keycloak должен быть запущен на localhost:8180

set -e

KEYCLOAK_URL="http://localhost:8180"
REALM="api-gateway"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin"

echo "🔐 Получение admin access token..."
ADMIN_TOKEN=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USERNAME" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" == "null" ]; then
  echo "❌ Ошибка получения admin token"
  exit 1
fi

echo "✅ Admin token получен"

# Функция создания consumer
create_consumer() {
  local CLIENT_ID=$1
  local DESCRIPTION=$2

  echo "📝 Создание consumer: $CLIENT_ID..."

  # Проверяем существование
  EXISTING=$(curl -s -X GET \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    | jq -r ".[] | select(.clientId==\"$CLIENT_ID\") | .id")

  if [ ! -z "$EXISTING" ] && [ "$EXISTING" != "null" ]; then
    echo "⚠️  Consumer $CLIENT_ID уже существует, пропускаем"
    return
  fi

  # Создаём consumer
  curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"clientId\": \"$CLIENT_ID\",
      \"description\": \"$DESCRIPTION\",
      \"enabled\": true,
      \"serviceAccountsEnabled\": true,
      \"directAccessGrantsEnabled\": false,
      \"publicClient\": false,
      \"protocol\": \"openid-connect\"
    }"

  echo "✅ Consumer $CLIENT_ID создан"
}

# Создаём тестовых consumers
create_consumer "test-consumer-alpha" "Test Consumer Alpha (Demo Data)"
create_consumer "test-consumer-beta" "Test Consumer Beta (Demo Data)"
create_consumer "test-consumer-gamma" "Test Consumer Gamma (Demo Data, Disabled)"

# Деактивируем gamma
echo "🔒 Деактивация test-consumer-gamma..."
GAMMA_ID=$(curl -s -X GET \
  "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | jq -r '.[] | select(.clientId=="test-consumer-gamma") | .id')

if [ ! -z "$GAMMA_ID" ] && [ "$GAMMA_ID" != "null" ]; then
  curl -s -X PUT \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$GAMMA_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"id\": \"$GAMMA_ID\",
      \"clientId\": \"test-consumer-gamma\",
      \"description\": \"Test Consumer Gamma (Demo Data, Disabled)\",
      \"enabled\": false,
      \"serviceAccountsEnabled\": true,
      \"directAccessGrantsEnabled\": false,
      \"publicClient\": false,
      \"protocol\": \"openid-connect\"
    }"
  echo "✅ test-consumer-gamma деактивирован"
fi

echo "🎉 Seed завершён! Создано 3 тестовых consumers."
echo ""
echo "📌 ПРИМЕЧАНИЕ: Для полноценного E2E тестирования AC8 (Set Rate Limit)"
echo "   можно добавить rate limits через Admin UI или API:"
echo ""
echo "   curl -X PUT http://localhost:8081/api/v1/consumers/test-consumer-alpha/rate-limit \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -H 'Authorization: Bearer <ADMIN_JWT>' \\"
echo "     -d '{\"requestsPerSecond\": 100, \"burstSize\": 150}'"
echo ""
