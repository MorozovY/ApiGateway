# ✅ Настройка приложения на субпути /ApiGateway

## Текущий статус

```
✅ URL приложения:          http://ymorozov.ru/ApiGateway/
✅ Редирект с корня:        http://ymorozov.ru/ → /ApiGateway/ (301)
✅ API endpoint:            http://ymorozov.ru/ApiGateway/api/v1/*
✅ Auth работает:           POST /ApiGateway/api/v1/auth/login (200)
✅ Работает с VPN:          ДА
✅ Работает без VPN:        ДА
```

## Что было изменено

### 1. Nginx конфигурация (`docker/nginx/nginx.conf`)

**Маршрутизация:**
- `/` → редирект 301 на `/ApiGateway/`
- `/ApiGateway/` → admin-ui (React frontend)
- `/ApiGateway/api/v1/*` → gateway-admin (Admin API)
- `/ApiGateway/api/*` → gateway-core (Gateway Core)

### 2. Vite конфигурация (`frontend/admin-ui/vite.config.ts`)

**Изменения:**
```typescript
export default defineConfig({
  base: '/ApiGateway/',  // Базовый путь для production сборки
  
  server: {
    proxy: {
      '/ApiGateway/api': {
        target: 'http://localhost:8081',
        rewrite: (path) => path.replace(/^\/ApiGateway\/api/, '/api'),
      },
    },
  },
})
```

### 3. React Router (`frontend/admin-ui/src/main.tsx`)

**Изменения:**
```typescript
<BrowserRouter basename="/ApiGateway">
  <AuthProvider>
    <App />
  </AuthProvider>
</BrowserRouter>
```

## Доступ к приложению

| URL | Назначение |
|-----|-----------|
| http://ymorozov.ru/ | Редирект на /ApiGateway/ |
| http://ymorozov.ru/ApiGateway/ | Admin UI (логин форма) |
| POST /ApiGateway/api/v1/auth/login | Аутентификация |
| GET /ApiGateway/api/v1/routes | Routes API |
| GET /ApiGateway/api/v1/users | Users API |

## Credentials

```
URL:      http://ymorozov.ru/ApiGateway/
Username: admin
Password: admin123
```

## Команды для управления

```bash
# Запуск всего стека
docker-compose up -d

# Перезапуск nginx и frontend (после изменений конфигурации)
docker-compose restart nginx admin-ui

# Проверка статуса
docker-compose ps

# Логи
docker-compose logs -f nginx
docker-compose logs -f admin-ui

# Остановка
docker-compose down
```

## Проверка работы

```bash
# Проверка редиректа
curl -i http://ymorozov.ru/
# Ожидается: HTTP/1.1 301 Moved Permanently
#            Location: http://ymorozov.ru/ApiGateway/

# Проверка frontend
curl -s http://ymorozov.ru/ApiGateway/ | head -5
# Ожидается: <!DOCTYPE html>

# Проверка auth API
curl -X POST http://ymorozov.ru/ApiGateway/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# Ожидается: {"userId":"...","username":"admin","role":"admin"}
```

## Измененные файлы

```
G:\Projects\ApiGateway\
├── docker/nginx/nginx.conf              ← обновлен (субпуть /ApiGateway/)
├── frontend/admin-ui/vite.config.ts     ← обновлен (base: '/ApiGateway/')
└── frontend/admin-ui/src/main.tsx       ← обновлен (basename="/ApiGateway")
```

## Troubleshooting

### Проблема: "404 Not Found на /ApiGateway/"
```bash
# Проверить nginx
docker-compose logs nginx --tail=20

# Проверить admin-ui
docker-compose logs admin-ui --tail=20

# Перезапустить
docker-compose restart nginx admin-ui
```

### Проблема: "Auth не работает"
```bash
# Проверить endpoint напрямую
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Проверить через nginx
curl -X POST http://ymorozov.ru/ApiGateway/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Проблема: "Статические файлы не загружаются"
Убедитесь что в Vite конфигурации установлен правильный `base`:
```typescript
base: '/ApiGateway/',
```

И в React Router правильный `basename`:
```typescript
<BrowserRouter basename="/ApiGateway">
```

## Production deployment

Для production сборки:

```bash
# 1. Собрать production build
cd frontend/admin-ui
npm run build

# 2. Обновить nginx для статических файлов
# В docker/nginx/nginx.conf добавить:
location /ApiGateway/ {
    root /var/www;
    try_files $uri $uri/ /ApiGateway/index.html;
}

# 3. Смонтировать build директорию в nginx контейнер
# В docker-compose.yml:
nginx:
  volumes:
    - ./frontend/admin-ui/dist:/var/www/ApiGateway
```

---

**Дата настройки:** 2026-02-21  
**Версия:** nginx/1.29.5, Vite 5.4.21, React Router v6  
**Статус:** ✅ Работает
