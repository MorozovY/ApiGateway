# Add ymorozov.ru to hosts file
# Run as Administrator!

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$domain = "ymorozov.ru"
$ip = "127.0.0.1"
$entry = "$ip $domain"

Write-Host "Checking hosts file..." -ForegroundColor Cyan

try {
    $hostsContent = Get-Content $hostsPath -Raw
    
    if ($hostsContent -match "ymorozov\.ru") {
        Write-Host "Entry for $domain already exists!" -ForegroundColor Yellow
        Get-Content $hostsPath | Select-String "ymorozov"
    } else {
        Add-Content -Path $hostsPath -Value "`n$entry"
        Write-Host "Added: $entry" -ForegroundColor Green
    }
    
    Write-Host "`nFlushing DNS cache..." -ForegroundColor Cyan
    ipconfig /flushdns | Out-Null
    
    Write-Host "`nTesting connection..." -ForegroundColor Cyan
    $ping = Test-Connection -ComputerName $domain -Count 1 -Quiet
    
    if ($ping) {
        Write-Host "SUCCESS: $domain resolves to localhost" -ForegroundColor Green
        
        try {
            $response = Invoke-WebRequest -Uri "http://$domain/" -TimeoutSec 5 -UseBasicParsing
            Write-Host "SUCCESS: HTTP server responds with code $($response.StatusCode)" -ForegroundColor Green
            Write-Host "`nYou can now open http://$domain in your browser!" -ForegroundColor Green
        } catch {
            Write-Host "WARNING: Cannot connect to HTTP server" -ForegroundColor Yellow
            Write-Host "Make sure Docker containers are running: docker-compose ps" -ForegroundColor Yellow
        }
    } else {
        Write-Host "ERROR: Cannot resolve $domain" -ForegroundColor Red
    }
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Make sure you run this script as Administrator!" -ForegroundColor Yellow
}

Write-Host "`nPress Enter to exit..."
Read-Host
