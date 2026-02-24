$tokenResponse = Invoke-RestMethod -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Method POST -Body @{
    client_id='admin-cli'
    username='admin'
    password='admin'
    grant_type='password'
}

$users = Invoke-RestMethod -Uri 'http://localhost:8180/admin/realms/api-gateway/users' -Headers @{
    Authorization = "Bearer $($tokenResponse.access_token)"
}

$users | ForEach-Object {
    Write-Host "$($_.username): enabled=$($_.enabled), email=$($_.email)"
}
