#!/bin/bash
# Smoke Tests для ApiGateway
# Story 13.5: Deployment Pipeline — Dev & Test Environments
#
# Использование:
#   ./smoke-test.sh [base_url]
#
# Примеры:
#   ./smoke-test.sh http://localhost
#   ./smoke-test.sh http://gateway.dev.local
#
# Переменные окружения:
#   BASE_URL - базовый URL (альтернатива аргументу)
#   SMOKE_TEST_TIMEOUT - таймаут для каждого теста (default: 10s)

set -euo pipefail

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL="${1:-${BASE_URL:-http://localhost}}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
TIMEOUT="${SMOKE_TEST_TIMEOUT:-10}"
TESTS_PASSED=0
TESTS_FAILED=0

# Порты в зависимости от environment (соответствует generate-compose.sh)
case "$ENVIRONMENT" in
  dev)
    ADMIN_PORT="${ADMIN_PORT:-28081}"
    CORE_PORT="${CORE_PORT:-28080}"
    UI_PORT="${UI_PORT:-23000}"
    ;;
  test)
    ADMIN_PORT="${ADMIN_PORT:-18081}"
    CORE_PORT="${CORE_PORT:-18080}"
    UI_PORT="${UI_PORT:-13000}"
    ;;
  local)
    # Локальная разработка — стандартные порты
    ADMIN_PORT="${ADMIN_PORT:-8081}"
    CORE_PORT="${CORE_PORT:-8080}"
    UI_PORT="${UI_PORT:-3000}"
    ;;
  *)
    # Fallback на стандартные порты
    ADMIN_PORT="${ADMIN_PORT:-8081}"
    CORE_PORT="${CORE_PORT:-8080}"
    UI_PORT="${UI_PORT:-3000}"
    ;;
esac

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_test() { echo -e "${BLUE}[TEST]${NC} $1"; }

# Результат теста
test_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

test_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

# HTTP GET с проверкой статуса
http_get() {
    local url=$1
    local expected_status=${2:-200}

    local response
    response=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "$expected_status" ]; then
        return 0
    else
        return 1
    fi
}

# HTTP GET с проверкой JSON поля
http_get_json() {
    local url=$1
    local jq_filter=$2
    local expected_value=$3

    local response
    response=$(curl -sf --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "{}")

    local actual_value
    actual_value=$(echo "$response" | jq -r "$jq_filter" 2>/dev/null || echo "null")

    if [ "$actual_value" = "$expected_value" ]; then
        return 0
    else
        echo "Expected: $expected_value, Got: $actual_value" >&2
        return 1
    fi
}

# ============================================================================
# SMOKE TESTS
# ============================================================================

test_gateway_admin_health() {
    log_test "Gateway Admin Health Check"

    local url="${BASE_URL}:${ADMIN_PORT}/actuator/health"

    if http_get_json "$url" '.status' 'UP'; then
        test_pass "Gateway Admin is UP"
    else
        test_fail "Gateway Admin health check failed (${url})"
    fi
}

test_gateway_admin_info() {
    log_test "Gateway Admin Info Endpoint"

    local url="${BASE_URL}:${ADMIN_PORT}/actuator/info"

    if http_get "$url" "200"; then
        test_pass "Gateway Admin info endpoint accessible"
    else
        test_fail "Gateway Admin info endpoint failed (${url})"
    fi
}

test_gateway_core_health() {
    log_test "Gateway Core Health Check"

    local url="${BASE_URL}:${CORE_PORT}/actuator/health"

    if http_get_json "$url" '.status' 'UP'; then
        test_pass "Gateway Core is UP"
    else
        test_fail "Gateway Core health check failed (${url})"
    fi
}

test_admin_ui_accessible() {
    log_test "Admin UI Accessibility"

    local url="${BASE_URL}:${UI_PORT}/"

    if http_get "$url" "200"; then
        test_pass "Admin UI is accessible (port ${UI_PORT})"
    else
        # Fallback на порт 80 (nginx в production)
        url="${BASE_URL}/"
        if http_get "$url" "200"; then
            test_pass "Admin UI is accessible (port 80)"
        else
            test_fail "Admin UI not accessible (tried :${UI_PORT} and :80)"
        fi
    fi
}

test_admin_ui_assets() {
    log_test "Admin UI Assets Loading"

    # Проверяем что index.html содержит ссылки на assets
    local url="${BASE_URL}:${UI_PORT}/"
    local response
    response=$(curl -sf --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "")

    if echo "$response" | grep -q "assets/"; then
        test_pass "Admin UI assets references found (port ${UI_PORT})"
    else
        # Fallback на порт 80
        url="${BASE_URL}/"
        response=$(curl -sf --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "")
        if echo "$response" | grep -q "assets/"; then
            test_pass "Admin UI assets references found (port 80)"
        else
            test_fail "Admin UI assets not found in HTML"
        fi
    fi
}

test_keycloak_realm() {
    log_test "Keycloak Realm Availability"

    # Keycloak может быть на разных портах
    local urls=(
        "${BASE_URL}:8180/realms/api-gateway"
        "${BASE_URL}/auth/realms/api-gateway"
        "http://keycloak:8080/realms/api-gateway"
    )

    for url in "${urls[@]}"; do
        if http_get_json "$url" '.realm' 'api-gateway' 2>/dev/null; then
            test_pass "Keycloak realm 'api-gateway' is available"
            return
        fi
    done

    # Keycloak опционален для smoke tests
    log_warn "Keycloak realm check skipped (not accessible)"
}

test_swagger_ui() {
    log_test "Swagger UI Availability"

    local url="${BASE_URL}:${ADMIN_PORT}/swagger-ui.html"

    if http_get "$url" "200"; then
        test_pass "Swagger UI is accessible"
    else
        # Попробуем redirect на swagger-ui/index.html
        url="${BASE_URL}:${ADMIN_PORT}/swagger-ui/index.html"
        if http_get "$url" "200"; then
            test_pass "Swagger UI is accessible (index.html)"
        else
            test_fail "Swagger UI not accessible"
        fi
    fi
}

test_api_routes_endpoint() {
    log_test "API Routes Endpoint"

    local url="${BASE_URL}:${ADMIN_PORT}/api/v1/routes"

    # Endpoint может требовать auth, проверяем 200 или 401/403
    local response
    response=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "200" ] || [ "$response" = "401" ] || [ "$response" = "403" ]; then
        test_pass "API Routes endpoint responding (HTTP $response)"
    else
        test_fail "API Routes endpoint failed (HTTP $response)"
    fi
}

# ============================================================================
# MAIN
# ============================================================================

print_summary() {
    echo ""
    echo "=========================================="
    echo "SMOKE TESTS SUMMARY"
    echo "=========================================="
    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    echo "=========================================="

    if [ $TESTS_FAILED -gt 0 ]; then
        return 1
    fi
    return 0
}

main() {
    echo "=========================================="
    echo "SMOKE TESTS"
    echo "Environment: ${ENVIRONMENT}"
    echo "Base URL: ${BASE_URL}"
    echo "Ports: Admin=${ADMIN_PORT}, Core=${CORE_PORT}, UI=${UI_PORT}"
    echo "Timeout: ${TIMEOUT}s"
    echo "=========================================="
    echo ""

    # Выполняем тесты
    test_gateway_admin_health
    test_gateway_admin_info
    test_gateway_core_health
    test_admin_ui_accessible
    test_admin_ui_assets
    test_swagger_ui
    test_api_routes_endpoint
    test_keycloak_realm

    # Итоги
    if print_summary; then
        log_info "All critical smoke tests passed!"
        exit 0
    else
        log_error "Some smoke tests failed!"
        exit 1
    fi
}

# Запуск
main "$@"
