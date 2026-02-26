# Windows-специфичная настройка для GitLab

## Предварительные требования

### 1. Docker Desktop с WSL2

GitLab CE требует значительных ресурсов. Убедитесь что:

1. **Docker Desktop установлен** с WSL2 backend (не Hyper-V)
2. **WSL2 настроен** с достаточными ресурсами

### 2. Настройка ресурсов WSL2

Создайте файл `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
memory=8GB
processors=4
swap=2GB
```

Перезапустите WSL: `wsl --shutdown`

### 3. Настройка Insecure Registry

Для работы с Container Registry на localhost без HTTPS:

1. Откройте **Docker Desktop** → **Settings** → **Docker Engine**
2. Добавьте в JSON конфигурацию:

```json
{
  "insecure-registries": ["localhost:5050"]
}
```

3. Нажмите **Apply & Restart**

## Запуск GitLab

```powershell
cd G:\Projects\ApiGateway\docker\gitlab
docker-compose up -d
```

## Проверка установки

```powershell
# PowerShell скрипт верификации
.\scripts\verify-setup.ps1
```

## Регистрация Runner

```powershell
# После получения токена из GitLab Admin → CI/CD → Runners
.\scripts\register-runner.ps1 -Token "YOUR_REGISTRATION_TOKEN"
```

## Типичные проблемы Windows

### "Cannot connect to the Docker daemon"

Docker Desktop не запущен. Запустите Docker Desktop.

### GitLab очень медленный / не запускается

1. Увеличьте память в `.wslconfig` (минимум 6GB для комфортной работы)
2. Проверьте что антивирус не сканирует Docker volumes

### "Ports are not available"

Порты уже заняты. Проверьте:
```powershell
netstat -ano | findstr :8929
netstat -ano | findstr :5050
```

### Ошибка при push в Registry

1. Убедитесь что `insecure-registries` настроен
2. Перезапустите Docker Desktop после изменения настроек
3. Проверьте логин: `docker login localhost:5050 -u root`

## Пути к данным

Docker Desktop хранит volumes в WSL2 distro. Доступ:
- Explorer: `\\wsl$\docker-desktop-data\data\docker\volumes\`
- WSL: `/mnt/wsl/docker-desktop-data/data/docker/volumes/`
