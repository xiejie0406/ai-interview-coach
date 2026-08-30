$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'

if (-not (Test-Path $envFile)) {
  throw "Missing local environment file: $envFile"
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^RUOYI_(DB_URL|DB_USERNAME|DB_PASSWORD|SERVER_PORT)=(.*)$') {
    [Environment]::SetEnvironmentVariable($Matches[0].Split('=')[0], $Matches[2], 'Process')
  }
}

$jar = Join-Path $backendRoot 'ruoyi-admin/target/ruoyi-admin.jar'
if (-not (Test-Path $jar)) {
  throw "Missing backend jar: $jar"
}

$runtime = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
$stdout = Join-Path $runtime 'platform-backend-current.out.log'
$stderr = Join-Path $runtime 'platform-backend-current.err.log'
$existing = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
  Write-Output "RuoYi already listens on 8081 (PID $($existing.OwningProcess))."
  exit 0
}

$process = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -WorkingDirectory (Split-Path $jar) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
Write-Output "Started RuoYi PID $($process.Id)."
