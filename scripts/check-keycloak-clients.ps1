$tokenResp = Invoke-RestMethod -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Method POST -Body @{
    client_id='admin-cli'
    username='admin'
    password='admin'
    grant_type='password'
}

$clients = Invoke-RestMethod -Uri 'http://localhost:8180/admin/realms/api-gateway/clients' -Headers @{
    Authorization = "Bearer $($tokenResp.access_token)"
}

$clients | ForEach-Object {
    Write-Host "$($_.clientId): directAccessGrantsEnabled=$($_.directAccessGrantsEnabled)"
}
