$tokenResp = Invoke-RestMethod -Uri 'http://localhost:8180/realms/api-gateway/protocol/openid-connect/token' -Method POST -Body @{
    client_id='gateway-admin-ui'
    username='developer'
    password='developer123'
    grant_type='password'
}

Write-Host "Token obtained, testing change-password..."

try {
    $result = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/auth/change-password' -Method POST -Headers @{
        Authorization = "Bearer $($tokenResp.access_token)"
        'Content-Type' = 'application/json'
    } -Body '{"currentPassword":"developer123","newPassword":"newpass123"}'
    Write-Host "Success: $($result | ConvertTo-Json)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) {
        Write-Host "Response: $($_.ErrorDetails.Message)"
    }
}
