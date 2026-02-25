# Production Deployment Guide

## Keycloak Environment Variables (ОБЯЗАТЕЛЬНО!)

После Story 12.9.1 legacy cookie auth удалён, Keycloak теперь **ОБЯЗАТЕЛЕН** для работы Admin UI.

### Требуемые Environment Variables

При сборке frontend для production **ОБЯЗАТЕЛЬНО** установите следующие переменные:

```bash
# Keycloak Server URL (без /realms/...)
export VITE_KEYCLOAK_URL=https://keycloak.ymorozov.ru  # или ваш Keycloak URL

# Keycloak Realm name
export VITE_KEYCLOAK_REALM=api-gateway

# Keycloak Client ID
export VITE_KEYCLOAK_CLIENT_ID=gateway-admin-ui
```

### Build команда для production

```bash
# Set environment variables ПЕРЕД build
export VITE_KEYCLOAK_URL=https://keycloak.ymorozov.ru
export VITE_KEYCLOAK_REALM=api-gateway
export VITE_KEYCLOAK_CLIENT_ID=gateway-admin-ui

# Build frontend
cd frontend/admin-ui
npm run build

# Или через Docker
docker build -f docker/Dockerfile.admin-ui \
  --build-arg VITE_KEYCLOAK_URL=https://keycloak.ymorozov.ru \
  --build-arg VITE_KEYCLOAK_REALM=api-gateway \
  --build-arg VITE_KEYCLOAK_CLIENT_ID=gateway-admin-ui \
  -t admin-ui:production .
```

### Если переменные НЕ установлены

При попытке логина пользователь увидит ошибку:
```
Keycloak configuration error: Missing required environment variables: VITE_KEYCLOAK_URL, VITE_KEYCLOAK_REALM, VITE_KEYCLOAK_CLIENT_ID.
Please contact your system administrator to configure Keycloak integration.
```

### Проверка что переменные установлены

После build проверьте что переменные попали в bundle:

```bash
# Проверка в production build
grep -r "VITE_KEYCLOAK_URL" frontend/admin-ui/dist/assets/*.js
# Должно показать актуальный URL (не "undefined")
```

---

## Keycloak Setup на Production

Убедитесь что на production сервере:

1. **Keycloak запущен и доступен** по URL из `VITE_KEYCLOAK_URL`
2. **Realm создан** с именем из `VITE_KEYCLOAK_REALM`
3. **Client создан** с ID из `VITE_KEYCLOAK_CLIENT_ID`
4. **Пользователи созданы** в Keycloak realm
5. **Roles настроены:** `admin-ui:admin`, `admin-ui:security`, `admin-ui:developer`

См. Story 12.1 для деталей настройки Keycloak.

---

## Troubleshooting

### Проблема: "Page not loading" или "Missing required environment variables"

**Причина:** Environment variables не установлены при build.

**Решение:**
1. Пересобрать frontend с правильными env vars (см. выше)
2. Перезапустить production сервер

### Проблема: "Keycloak configuration error" при логине

**Причина:** Keycloak URL недоступен или неправильный.

**Решение:**
1. Проверить что Keycloak доступен: `curl https://keycloak.ymorozov.ru`
2. Проверить что realm существует: `curl https://keycloak.ymorozov.ru/realms/api-gateway`
3. Проверить что client настроен в Keycloak Admin Console

---

## Migration от Cookie Auth

Если на production ещё используется старая версия с cookie auth:

1. **Сначала** установите Keycloak на production
2. **Настройте** realm и users
3. **Затем** deploy новую версию frontend с env vars
4. **Проверьте** что login работает через Keycloak

**Rollback Plan:** Если Keycloak не работает, откат на commit `7b750e5` (последняя версия с cookie auth).

---

*Created: 2026-02-25*
*Story: 12.9.1 — Remove Legacy Cookie Auth*
