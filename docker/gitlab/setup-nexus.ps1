# Скрипт настройки Nexus Repository Manager (PowerShell)
# Создаёт proxy-репозитории для Maven Central и npmjs
#
# Запускать после первого запуска Nexus:
#   .\setup-nexus.ps1 [-Password <NEW_ADMIN_PASSWORD>]
#
# По умолчанию использует admin/admin123

param(
    [Parameter(Mandatory=$false)]
    [string]$Password = "admin123",

    [Parameter(Mandatory=$false)]
    [string]$NewPassword = ""
)

$NEXUS_URL = "http://localhost:8081"
$ADMIN_USER = "admin"

Write-Host "=== Nexus Repository Manager Setup ===" -ForegroundColor Green

# Ожидаем запуска Nexus
Write-Host "Проверяю доступность Nexus..." -ForegroundColor Yellow
$retries = 0
$maxRetries = 40  # Nexus стартует долго
while ($retries -lt $maxRetries) {
    try {
        $response = Invoke-WebRequest -Uri "$NEXUS_URL/service/rest/v1/status" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "Nexus доступен" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "Ожидаю запуска Nexus... ($retries/$maxRetries)"
        Start-Sleep -Seconds 5
        $retries++
    }
}

if ($retries -eq $maxRetries) {
    Write-Host "Error: Nexus не отвечает" -ForegroundColor Red
    exit 1
}

# Получаем initial admin password если первый запуск
$initialPasswordFile = docker exec nexus cat /nexus-data/admin.password 2>$null
if ($initialPasswordFile) {
    Write-Host "Обнаружен первый запуск Nexus" -ForegroundColor Yellow
    Write-Host "Initial password: $initialPasswordFile"
    $Password = $initialPasswordFile
}

# Создаём credentials для API запросов
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${ADMIN_USER}:${Password}"))
$headers = @{
    "Authorization" = "Basic $base64Auth"
    "Content-Type" = "application/json"
}

# Функция для создания proxy репозитория
function Create-ProxyRepository {
    param(
        [string]$Name,
        [string]$Format,
        [string]$RemoteUrl
    )

    Write-Host "Создаю proxy репозиторий: $Name..." -ForegroundColor Yellow

    $body = @{
        name = $Name
        online = $true
        storage = @{
            blobStoreName = "default"
            strictContentTypeValidation = $true
        }
        proxy = @{
            remoteUrl = $RemoteUrl
            contentMaxAge = 1440
            metadataMaxAge = 1440
        }
        negativeCache = @{
            enabled = $true
            timeToLive = 1440
        }
        httpClient = @{
            blocked = $false
            autoBlock = $true
        }
    }

    # Добавляем специфичные настройки для разных форматов
    if ($Format -eq "maven2") {
        $body["maven"] = @{
            versionPolicy = "MIXED"
            layoutPolicy = "STRICT"
        }
    }

    $jsonBody = $body | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod -Uri "$NEXUS_URL/service/rest/v1/repositories/$Format/proxy" `
            -Method POST `
            -Headers $headers `
            -Body $jsonBody `
            -ErrorAction Stop
        Write-Host "$Name создан успешно" -ForegroundColor Green
    } catch {
        if ($_.Exception.Response.StatusCode -eq 400) {
            Write-Host "$Name уже существует, пропускаю" -ForegroundColor Yellow
        } else {
            Write-Host "Ошибка создания $Name`: $_" -ForegroundColor Red
        }
    }
}

# Функция для создания group репозитория
function Create-GroupRepository {
    param(
        [string]$Name,
        [string]$Format,
        [string[]]$Members
    )

    Write-Host "Создаю group репозиторий: $Name..." -ForegroundColor Yellow

    $body = @{
        name = $Name
        online = $true
        storage = @{
            blobStoreName = "default"
            strictContentTypeValidation = $true
        }
        group = @{
            memberNames = $Members
        }
    }

    if ($Format -eq "maven2") {
        $body["maven"] = @{
            versionPolicy = "MIXED"
            layoutPolicy = "STRICT"
        }
    }

    $jsonBody = $body | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod -Uri "$NEXUS_URL/service/rest/v1/repositories/$Format/group" `
            -Method POST `
            -Headers $headers `
            -Body $jsonBody `
            -ErrorAction Stop
        Write-Host "$Name создан успешно" -ForegroundColor Green
    } catch {
        if ($_.Exception.Response.StatusCode -eq 400) {
            Write-Host "$Name уже существует, пропускаю" -ForegroundColor Yellow
        } else {
            Write-Host "Ошибка создания $Name`: $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "=== Создание Maven репозиториев ===" -ForegroundColor Cyan

# Maven Central proxy
Create-ProxyRepository -Name "maven-central-proxy" -Format "maven2" -RemoteUrl "https://repo1.maven.org/maven2/"

# Gradle Plugin Portal proxy
Create-ProxyRepository -Name "gradle-plugins-proxy" -Format "maven2" -RemoteUrl "https://plugins.gradle.org/m2/"

# Google Maven proxy (для Android/Kotlin)
Create-ProxyRepository -Name "google-maven-proxy" -Format "maven2" -RemoteUrl "https://maven.google.com/"

Write-Host ""
Write-Host "=== Создание npm репозиториев ===" -ForegroundColor Cyan

# npmjs.org proxy
Create-ProxyRepository -Name "npm-proxy" -Format "npm" -RemoteUrl "https://registry.npmjs.org/"

Write-Host ""
Write-Host "=== Настройка завершена ===" -ForegroundColor Green
Write-Host ""
Write-Host "Nexus Web UI: http://localhost:8081"
Write-Host "Credentials: admin / $Password"
Write-Host ""
Write-Host "Репозитории для использования:"
Write-Host ""
Write-Host "Maven/Gradle:"
Write-Host "  http://localhost:8081/repository/maven-central-proxy/"
Write-Host "  http://localhost:8081/repository/gradle-plugins-proxy/"
Write-Host ""
Write-Host "npm:"
Write-Host "  http://localhost:8081/repository/npm-proxy/"
Write-Host ""
Write-Host "Настройка Gradle (settings.gradle.kts):"
Write-Host @"

pluginManagement {
    repositories {
        maven { url = uri("http://localhost:8081/repository/gradle-plugins-proxy/") }
        maven { url = uri("http://localhost:8081/repository/maven-central-proxy/") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("http://localhost:8081/repository/maven-central-proxy/") }
        mavenCentral()
    }
}
"@

Write-Host ""
Write-Host "Настройка npm (.npmrc):"
Write-Host @"

registry=http://localhost:8081/repository/npm-proxy/
"@
