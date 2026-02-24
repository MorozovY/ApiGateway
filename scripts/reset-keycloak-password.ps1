param(
    [string]$Username = "developer",
    [string]$NewPassword = "developer123"
)

$tokenResponse = Invoke-RestMethod -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Method POST -Body @{
    client_id='admin-cli'
    username='admin'
    password='admin'
    grant_type='password'
}

$headers = @{
    Authorization = "Bearer $($tokenResponse.access_token)"
    'Content-Type' = 'application/json'
}

# Получаем ID пользователя
$users = Invoke-RestMethod -Uri "http://localhost:8180/admin/realms/api-gateway/users?username=$Username" -Headers $headers
$userId = $users[0].id

Write-Host "User ID for $Username : $userId"

# Сбрасываем пароль
$body = @{
    type = "password"
    value = $NewPassword
    temporary = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8180/admin/realms/api-gateway/users/$userId/reset-password" -Method PUT -Headers $headers -Body $body

Write-Host "Password reset to: $NewPassword"
