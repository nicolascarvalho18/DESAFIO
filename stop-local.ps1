$ErrorActionPreference = 'Continue'
$owners = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($owner in $owners) { Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue }
Write-Host 'Processo local da API na porta 8080 encerrado.'
