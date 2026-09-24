param([switch]$EnableVoice, [switch]$Migrate)
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'

if (-not (Test-Path $envFile)) {
  throw "Missing local environment file: $envFile"
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^((?:RUOYI|INTERVIEW)_[A-Z0-9_]+)=(.*)$') {
    $settingName = $Matches[1]
    $settingValue = $Matches[2].Trim().Trim('"').Trim("'")
    # 忽略空模板行，避免同名空示例覆盖前面已填写的本地凭据。
    if ($settingValue) { [Environment]::SetEnvironmentVariable($settingName, $settingValue, 'Process') }
  }
}

if ($Migrate) { $env:INTERVIEW_FLYWAY_ENABLED = 'true' }

if ($EnableVoice) {
  $env:INTERVIEW_EXTERNAL_PROVIDER_CALLS_ENABLED = 'true'
  $env:INTERVIEW_OBJECT_STORAGE_WRITES_ENABLED = 'true'
  if (-not $env:INTERVIEW_VOICE_STORAGE_ROOT) { $env:INTERVIEW_VOICE_STORAGE_ROOT = Join-Path $projectRoot '.runtime/private-audio' }
  if (-not $env:INTERVIEW_SENSITIVE_ENVELOPE_KEY_ID) { $env:INTERVIEW_SENSITIVE_ENVELOPE_KEY_ID = $env:INTERVIEW_PERSISTENCE_KEY_ID }
  if (-not $env:INTERVIEW_SENSITIVE_ENVELOPE_KEY_BASE64) { $env:INTERVIEW_SENSITIVE_ENVELOPE_KEY_BASE64 = $env:INTERVIEW_PERSISTENCE_KEY_BASE64 }
  if (-not $env:INTERVIEW_VOLCENGINE_TTS_VOICE) { $env:INTERVIEW_VOLCENGINE_TTS_VOICE = 'zh_female_vv_uranus_bigtts' }
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

$process = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar, '--spring.profiles.active=druid,local' -WorkingDirectory (Split-Path $jar) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
Write-Output "Started RuoYi PID $($process.Id)."
