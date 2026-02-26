# Скрипт верификации установки GitLab для Windows
# Запускать после docker-compose up -d и ожидания инициализации

# Установка UTF-8 для корректного отображения русских символов и emoji
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Continue"

Write-Host "`n🔍 Проверка GitLab Infrastructure..." -ForegroundColor Cyan
Write-Host ""

function Check-Pass($message) {
    Write-Host "✓ $message" -ForegroundColor Green
}

function Check-Fail($message) {
    Write-Host "✗ $message" -ForegroundColor Red
}

function Check-Warn($message) {
    Write-Host "⚠ $message" -ForegroundColor Yellow
}

# 1. Проверка контейнеров
Write-Host "1️⃣ Проверка Docker контейнеров..." -ForegroundColor Cyan
$gitlabRunning = docker ps --format '{{.Names}}' | Select-String -Pattern "^gitlab$"
$runnerRunning = docker ps --format '{{.Names}}' | Select-String -Pattern "^gitlab-runner$"

if ($gitlabRunning) {
    Check-Pass "GitLab контейнер запущен"
} else {
    Check-Fail "GitLab контейнер не найден"
    exit 1
}

if ($runnerRunning) {
    Check-Pass "GitLab Runner контейнер запущен"
} else {
    Check-Warn "GitLab Runner контейнер не найден (может ожидать healthcheck)"
}

Write-Host ""

# 2. Проверка GitLab Web UI
Write-Host "2️⃣ Проверка GitLab Web UI..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8929/-/readiness" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Check-Pass "GitLab Web UI доступен (http://localhost:8929)"
    }
} catch {
    Check-Warn "GitLab ещё инициализируется. Подождите 3-5 минут."
}

Write-Host ""

# 3. Проверка Container Registry
Write-Host "3️⃣ Проверка Container Registry..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:5050/v2/" -UseBasicParsing -TimeoutSec 5
    Check-Pass "Container Registry доступен (http://localhost:5050)"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Check-Pass "Container Registry доступен (требует авторизации)"
    } else {
        Check-Warn "Container Registry недоступен. Проверьте insecure-registries."
    }
}

Write-Host ""

# 4. Проверка volumes
Write-Host "4️⃣ Проверка Docker volumes..." -ForegroundColor Cyan
$volumes = @("gitlab_config", "gitlab_logs", "gitlab_data", "gitlab_runner_config")
foreach ($vol in $volumes) {
    $exists = docker volume ls -q | Select-String -Pattern "^$vol$"
    if ($exists) {
        Check-Pass "Volume $vol существует"
    } else {
        Check-Fail "Volume $vol не найден"
    }
}

Write-Host ""

# 5. Начальный пароль root
Write-Host "5️⃣ Получение пароля root..." -ForegroundColor Cyan
try {
    $passContent = docker exec gitlab cat /etc/gitlab/initial_root_password 2>$null
    $passLine = $passContent | Select-String -Pattern "Password:"
    if ($passLine) {
        $password = ($passLine -split ":")[1].Trim()
        # Маскируем середину пароля для безопасности в shared терминалах
        if ($password.Length -gt 8) {
            $maskedPassword = $password.Substring(0, 4) + "****" + $password.Substring($password.Length - 4)
        } else {
            $maskedPassword = "****"
        }
        Write-Host ""
        Write-Host "📋 Credentials для входа в GitLab:" -ForegroundColor Yellow
        Write-Host "   URL:      http://localhost:8929"
        Write-Host "   Username: root"
        Write-Host "   Password: $maskedPassword (частично скрыт)"
        Write-Host ""
        Write-Host "   Полный пароль: docker exec gitlab cat /etc/gitlab/initial_root_password" -ForegroundColor Gray
        Write-Host ""
        Check-Warn "Смените пароль после первого входа!"
    } else {
        Check-Warn "Пароль недоступен (возможно, уже сменён или прошло 24 часа)"
    }
} catch {
    Check-Warn "Не удалось получить пароль"
}

Write-Host ""
Write-Host "🎉 Проверка завершена!" -ForegroundColor Cyan
Write-Host ""
