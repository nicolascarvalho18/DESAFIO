param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root 'backend'
$logDir = Join-Path $root '.local-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$mavenLocal = Join-Path $env:LOCALAPPDATA 'Programs\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd'
$maven = if (Test-Path $mavenLocal) { $mavenLocal } else { (Get-Command mvn.cmd -ErrorAction Stop).Source }
$null = Get-Command java -ErrorAction Stop
$env:SPRING_PROFILES_ACTIVE = 'dev'
Start-Process -FilePath $maven -ArgumentList '-q','spring-boot:run','-Dspring-boot.run.profiles=dev' -WorkingDirectory $backend -RedirectStandardOutput (Join-Path $logDir 'backend.out.log') -RedirectStandardError (Join-Path $logDir 'backend.err.log') -WindowStyle Hidden | Out-Null
$ready = $false
for ($i=0; $i -lt 45; $i++) { try { $null = Invoke-RestMethod 'http://localhost:8080/actuator/health' -TimeoutSec 2; $ready=$true; break } catch { Start-Sleep -Seconds 2 } }
if (-not $ready) { Get-Content (Join-Path $logDir 'backend.out.log') -Tail 60; Get-Content (Join-Path $logDir 'backend.err.log') -Tail 60; throw 'Backend não iniciou. Consulte .local-logs.' }
Write-Host 'API: http://localhost:8080'
Write-Host 'Swagger: http://localhost:8080/swagger-ui/index.html'
Write-Host 'Health: http://localhost:8080/actuator/health'
Write-Host 'Dev login: demo@example.com / Demo123!'
Write-Host 'Desenvolvimento usa H2 persistente; não equivale a PostgreSQL/RabbitMQ de produção.'
if (-not $NoBrowser) { Start-Process 'http://localhost:8080/swagger-ui/index.html' }
