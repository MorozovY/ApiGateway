# Скрипт для настройки локального доступа к ymorozov.ru
# Запустить от администратора!

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$domain = "ymorozov.ru"
$ip = "127.0.0.1"
$entry = "$ip $domain"

Write-Host "Проверяю файл hosts..." -ForegroundColor Cyan

$hostsContent = Get-Content $hostsPath -Raw

if ($hostsContent -match [regex]::Escape($domain)) {
    Write-Host "Запись для $domain уже существует" -ForegroundColor Yellow
    Write-Host "Текущая запись:" -ForegroundColor Yellow
    Get-Content $hostsPath | Select-String $domain
    
    $replace = Read-Host "Заменить? (y/n)"
    if ($replace -eq 'y') {
        $hostsContent = $hostsContent -replace ".*$domain.*", $entry
        Set-Content -Path $hostsPath -Value $hostsContent
        Write-Host "Запись обновлена!" -ForegroundColor Green
    }
} else {
    Add-Content -Path $hostsPath -Value "`n$entry"
    Write-Host "Запись добавлена: $entry" -ForegroundColor Green
}

Write-Host "`nОчищаю DNS кеш..." -ForegroundColor Cyan
ipconfig /flushdns | Out-Null
Write-Host "DNS кеш очищен!" -ForegroundColor Green

Write-Host "`nПроверяю подключение..." -ForegroundColor Cyan
$ping = Test-Connection -ComputerName $domain -Count 1 -Quiet
if ($ping) {
    Write-Host "✓ $domain резолвится корректно" -ForegroundColor Green
    
    # Проверяем HTTP доступность
    try {
        $response = Invoke-WebRequest -Uri "http://$domain/" -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ HTTP сервер доступен (код $($response.StatusCode))" -ForegroundColor Green
        }
    } catch {
        Write-Host "✗ HTTP сервер не доступен: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "✗ $domain не резолвится" -ForegroundColor Red
}

Write-Host "`nГотово! Теперь можете открыть http://$domain в браузере" -ForegroundColor Green
Write-Host "Работает и с VPN, и без VPN!" -ForegroundColor Green
