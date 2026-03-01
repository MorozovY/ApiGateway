#!/bin/bash
# Production Deployment Script (Story 13.6)
# Rolling update: gateway-core -> gateway-admin -> admin-ui
# С health check после каждого сервиса

set -e

# Параметры
COMPOSE_PROJECT="${COMPOSE_PROJECT:-apigateway-prod}"
COMPOSE_FILE="${1:-/tmp/docker-compose.prod.yml}"
CI_REGISTRY_IMAGE="${CI_REGISTRY_IMAGE}"
CI_COMMIT_SHA="${CI_COMMIT_SHA}"

echo "=========================================="
echo "Deploying to PRODUCTION environment"
echo "Commit: $CI_COMMIT_SHA"
echo "Pipeline: $CI_PIPELINE_IID"
echo "=========================================="

# Pull images
echo "Pulling images from registry..."
docker pull "$CI_REGISTRY_IMAGE/gateway-admin:$CI_COMMIT_SHA"
docker pull "$CI_REGISTRY_IMAGE/gateway-core:$CI_COMMIT_SHA"
docker pull "$CI_REGISTRY_IMAGE/admin-ui:$CI_COMMIT_SHA"

# Generate compose file
echo "Generating compose file for production..."
chmod +x docker/gitlab/generate-compose.sh
./docker/gitlab/generate-compose.sh prod "$COMPOSE_FILE"
cat "$COMPOSE_FILE"

# Сохраняем текущие images для rollback
echo "Saving current images for rollback..."
docker inspect gateway-core-prod --format='{{.Config.Image}}' 2>/dev/null > /tmp/previous_images_prod.txt || true
docker inspect gateway-admin-prod --format='{{.Config.Image}}' 2>/dev/null >> /tmp/previous_images_prod.txt || true
docker inspect admin-ui-prod --format='{{.Config.Image}}' 2>/dev/null >> /tmp/previous_images_prod.txt || true

# Функция rollback
rollback() {
    echo "Initiating rollback..."
    chmod +x docker/gitlab/rollback.sh
    ENVIRONMENT=prod ./docker/gitlab/rollback.sh prod || true
}

# Rolling update: gateway-core
echo "=========================================="
echo "Rolling update: gateway-core"
echo "=========================================="
docker rm -f gateway-core-prod 2>/dev/null || true
docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d gateway-core
echo "Waiting for gateway-core health check..."
sleep 30
if ! docker exec gateway-core-prod wget -q --spider http://localhost:8080/actuator/health; then
    echo "ERROR: gateway-core health check failed"
    docker logs gateway-core-prod --tail 50
    rollback
    exit 1
fi
echo "gateway-core is healthy"

# Rolling update: gateway-admin
echo "=========================================="
echo "Rolling update: gateway-admin"
echo "=========================================="
docker rm -f gateway-admin-prod 2>/dev/null || true
docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d gateway-admin
echo "Waiting for gateway-admin health check..."
sleep 30
if ! docker exec gateway-admin-prod wget -q --spider http://localhost:8081/actuator/health; then
    echo "ERROR: gateway-admin health check failed"
    docker logs gateway-admin-prod --tail 50
    rollback
    exit 1
fi
echo "gateway-admin is healthy"

# Rolling update: admin-ui
echo "=========================================="
echo "Rolling update: admin-ui"
echo "=========================================="
docker rm -f admin-ui-prod 2>/dev/null || true
docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d admin-ui
echo "Waiting for admin-ui health check..."
sleep 10
if ! docker exec admin-ui-prod wget -q --spider http://localhost:80/; then
    echo "ERROR: admin-ui health check failed"
    docker logs admin-ui-prod --tail 50
    rollback
    exit 1
fi
echo "admin-ui is healthy"

# Create Git tag
echo "=========================================="
echo "Creating Git tag"
echo "=========================================="
apk add --no-cache git 2>/dev/null || true
git config user.email "ci@localhost"
git config user.name "GitLab CI"
TAG_NAME="prod-$(date +%Y-%m-%d)-${CI_PIPELINE_IID}"
git tag -a "$TAG_NAME" -m "Production deployment
Commit: $CI_COMMIT_SHA
Pipeline: $CI_PIPELINE_IID
Triggered by: $GITLAB_USER_LOGIN"

# Push tag в GitLab
git remote set-url origin "https://gitlab-ci-token:${CI_JOB_TOKEN}@${CI_SERVER_HOST}:${CI_SERVER_PORT}/${CI_PROJECT_PATH}.git" 2>/dev/null || true
if git push origin "$TAG_NAME" 2>&1; then
    echo "Tag pushed to GitLab successfully"
else
    echo "WARNING: Tag push to GitLab failed (check CI_JOB_TOKEN permissions)"
fi

# Push tag в GitHub (опционально)
if [ -n "$GITHUB_TOKEN" ]; then
    git remote add github "https://x-access-token:${GITHUB_TOKEN}@github.com/MorozovY/ApiGateway.git" 2>/dev/null || true
    if git push github "$TAG_NAME" 2>&1; then
        echo "Tag pushed to GitHub successfully"
    else
        echo "WARNING: Tag push to GitHub failed"
    fi
else
    echo "GITHUB_TOKEN not set, skipping GitHub tag push"
fi

echo "=========================================="
echo "PRODUCTION DEPLOYMENT SUCCESSFUL"
echo "Tag: $TAG_NAME"
echo "URL: http://localhost:33000"
echo "=========================================="
