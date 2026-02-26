# Скрипт для регистрации GitLab Runners (PowerShell)
# Запускать после docker-compose up -d
#
# Использование: .\register-runners.ps1 [-Token <REGISTRATION_TOKEN>]

param(
    [Parameter(Mandatory=$false)]
    [string]$Token
)

$GITLAB_URL = "http://gitlab:8929"
$GITLAB_EXTERNAL_URL = "http://localhost:8929"

Write-Host "=== GitLab Runners Registration ===" -ForegroundColor Green

# Проверяем что GitLab запущен
Write-Host "Проверяю доступность GitLab..." -ForegroundColor Yellow
$retries = 0
$maxRetries = 30
while ($retries -lt $maxRetries) {
    try {
        $response = Invoke-WebRequest -Uri "$GITLAB_EXTERNAL_URL/-/readiness" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "GitLab доступен" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "Ожидаю запуска GitLab... ($retries/$maxRetries)"
        Start-Sleep -Seconds 5
        $retries++
    }
}

if ($retries -eq $maxRetries) {
    Write-Host "Error: GitLab не отвечает" -ForegroundColor Red
    exit 1
}

# Получаем или запрашиваем token
if ([string]::IsNullOrEmpty($Token)) {
    Write-Host ""
    Write-Host "Registration token не указан." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Для получения token:"
    Write-Host "  1. Откройте http://localhost:8929/admin/runners"
    Write-Host "  2. Нажмите 'New instance runner'"
    Write-Host "  3. Скопируйте registration token"
    Write-Host ""
    $Token = Read-Host "Введите Registration Token"
}

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "Error: Registration token обязателен" -ForegroundColor Red
    exit 1
}

# Функция регистрации runner
function Register-Runner {
    param(
        [string]$RunnerName,
        [int]$RunnerNum
    )

    Write-Host "Регистрирую $RunnerName..." -ForegroundColor Yellow

    # Проверяем, не зарегистрирован ли уже runner
    $listOutput = docker exec $RunnerName gitlab-runner list 2>&1
    if ($listOutput -match "Executor") {
        Write-Host "$RunnerName уже зарегистрирован, пропускаю" -ForegroundColor Yellow
        return
    }

    docker exec $RunnerName gitlab-runner register `
        --non-interactive `
        --url $GITLAB_URL `
        --token $Token `
        --executor "docker" `
        --docker-image "alpine:latest" `
        --description "runner-$RunnerNum" `
        --tag-list "docker" `
        --docker-privileged `
        --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" `
        --docker-volumes "gitlab_gradle_cache:/cache/gradle" `
        --docker-volumes "gitlab_npm_cache:/cache/npm" `
        --docker-extra-hosts "nexus:host-gateway" `
        --docker-extra-hosts "gitlab.local:host-gateway"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "$RunnerName зарегистрирован успешно" -ForegroundColor Green
    } else {
        Write-Host "$RunnerName: ошибка регистрации" -ForegroundColor Red
    }
}

# Регистрируем все 4 runners
for ($i = 1; $i -le 4; $i++) {
    $runnerName = "gitlab-runner-$i"

    # Проверяем что контейнер запущен
    $running = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $runnerName }
    if (-not $running) {
        Write-Host "Контейнер $runnerName не запущен. Запустите: docker-compose up -d" -ForegroundColor Red
        continue
    }

    Register-Runner -RunnerName $runnerName -RunnerNum $i
}

Write-Host ""
Write-Host "=== Регистрация завершена ===" -ForegroundColor Green
Write-Host ""
Write-Host "Проверить статус runners:"
Write-Host "  http://localhost:8929/admin/runners"
Write-Host ""
Write-Host "Или через CLI:"
for ($i = 1; $i -le 4; $i++) {
    Write-Host "  docker exec gitlab-runner-$i gitlab-runner list"
}
