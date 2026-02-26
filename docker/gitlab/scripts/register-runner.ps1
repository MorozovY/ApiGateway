# Скрипт регистрации GitLab Runner для Windows
# Требуется registration token из GitLab Admin → CI/CD → Runners

# Установка UTF-8 для корректного отображения русских символов и emoji
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

param(
    [Parameter(Mandatory=$true, HelpMessage="Registration token из GitLab Admin → CI/CD → Runners")]
    [string]$Token
)

Write-Host "`n🔧 Регистрация GitLab Runner..." -ForegroundColor Cyan

docker exec -it gitlab-runner gitlab-runner register `
    --non-interactive `
    --url "http://gitlab:8929" `
    --token $Token `
    --executor "docker" `
    --docker-image "docker:latest" `
    --description "local-docker-runner" `
    --docker-privileged `
    --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" `
    --docker-network-mode "gitlab_network"

Write-Host ""
Write-Host "✅ Runner зарегистрирован!" -ForegroundColor Green
Write-Host ""
Write-Host "Проверка:" -ForegroundColor Cyan
docker exec -it gitlab-runner gitlab-runner list
