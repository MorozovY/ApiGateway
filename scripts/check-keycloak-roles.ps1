param(
    [string]$Username = "developer"
)

$tokenResponse = Invoke-RestMethod -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Method POST -Body @{
    client_id='admin-cli'
    username='admin'
    password='admin'
    grant_type='password'
}

$headers = @{
    Authorization = "Bearer $($tokenResponse.access_token)"
}

# Получаем ID пользователя
$users = Invoke-RestMethod -Uri "http://localhost:8180/admin/realms/api-gateway/users?username=$Username" -Headers $headers
$userId = $users[0].id

Write-Host "=== Realm roles for $Username ==="
$realmRoles = Invoke-RestMethod -Uri "http://localhost:8180/admin/realms/api-gateway/users/$userId/role-mappings/realm" -Headers $headers
$realmRoles | ForEach-Object { Write-Host "  - $($_.name)" }

Write-Host ""
Write-Host "=== All realm roles (available) ==="
$allRoles = Invoke-RestMethod -Uri "http://localhost:8180/admin/realms/api-gateway/roles" -Headers $headers
$allRoles | ForEach-Object { Write-Host "  - $($_.name)" }
