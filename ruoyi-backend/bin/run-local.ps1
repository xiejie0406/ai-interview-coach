param([switch]$EnableVoice, [switch]$Migrate)
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'

if (-not (Test-Path $envFile)) {
  throw "Missing local environment file: $envFile"
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^((?:RUOYI|INTERVIEW|FASHION|ADEN|APS)_[A-Z0-9_]+)=(.*)$') {
    $settingName = $Matches[1]
    $settingValue = $Matches[2].Trim().Trim('"').Trim("'")
    $bootstrapNames = @('RUOYI_DB_PASSWORD', 'RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64', 'RUOYI_MANAGED_SECRET_PREVIOUS_MASTER_KEY_BASE64', 'RUOYI_MANAGED_SECRET_MASTER_KEY_ID', 'RUOYI_MANAGED_SECRET_PREVIOUS_MASTER_KEY_ID')
    if ($settingValue -and $settingName -match '(API_KEY|ACCESS_TOKEN|KEY_BASE64|_PASSWORD|_PEPPER|KEY_ID)$' -and
        $settingName -notin $bootstrapNames) {
      throw "旧密钥环境变量 $settingName 已停用，请先录入若依密钥模块并清理 .env 中该项。"
    }
    # 忽略空模板行，避免同名空示例覆盖前面已填写的本地凭据。
    if ($settingValue) { [Environment]::SetEnvironmentVariable($settingName, $settingValue, 'Process') }
  }
}

if ($Migrate) { $env:INTERVIEW_FLYWAY_ENABLED = 'true' }

if ($EnableVoice) {
  $env:INTERVIEW_EXTERNAL_PROVIDER_CALLS_ENABLED = 'true'
  $env:INTERVIEW_OBJECT_STORAGE_WRITES_ENABLED = 'true'
  if (-not $env:INTERVIEW_VOICE_STORAGE_ROOT) { $env:INTERVIEW_VOICE_STORAGE_ROOT = Join-Path $projectRoot '.runtime/private-audio' }
  if (-not $env:INTERVIEW_VOLCENGINE_TTS_VOICE) { $env:INTERVIEW_VOLCENGINE_TTS_VOICE = 'zh_female_vv_uranus_bigtts' }
}

$jar = Join-Path $backendRoot 'ruoyi-admin/target/ruoyi-admin.jar'
if (-not (Test-Path $jar)) {
  throw "Missing backend jar: $jar"
}

$runtime = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
$stdout = Join-Path $runtime 'ruoyi-backend-current.out.log'
$stderr = Join-Path $runtime 'ruoyi-backend-current.err.log'
$existing = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
  Write-Output "RuoYi already listens on 8081 (PID $($existing.OwningProcess))."
  exit 0
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
  if (-not $archive.GetEntry('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class')) {
    throw 'ruoyi-admin.jar 不是可执行 Spring Boot JAR；请在占用该文件的 Java 进程结束后重新执行 Maven package。'
  }
} finally {
  $archive.Dispose()
}

# 运行中的 Spring Boot JAR 不能被下一次 Maven package 原地覆盖。
# 按内容哈希复制为不可变启动文件，避免类加载器随后从改写的 JAR 读取到不一致的字节。
$jarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash.ToLowerInvariant()
$launchJar = Join-Path $runtime "ruoyi-admin-$jarHash.jar"
if (-not (Test-Path -LiteralPath $launchJar)) {
  Copy-Item -LiteralPath $jar -Destination $launchJar
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $launchJar).Hash.ToLowerInvariant() -ne $jarHash) {
  throw "启动 JAR 副本校验失败：$launchJar"
}

if ($env:ADEN_ENABLED -ne 'false' -and [string]::IsNullOrWhiteSpace($env:ADEN_EXPECTED_DATABASE)) {
  throw 'Aden 默认启用：请设置 ADEN_EXPECTED_DATABASE 并先完成 schema 迁移，或设置 ADEN_ENABLED=false。'
}
if ($env:APS_ENABLED -ne 'false' -and
    ([string]::IsNullOrWhiteSpace($env:APS_DB_URL) -or [string]::IsNullOrWhiteSpace($env:APS_DB_USERNAME))) {
  throw 'APS API 默认启用：请设置 APS_DB_URL、APS_DB_USERNAME 并先完成 schema 迁移，或设置 APS_ENABLED=false。'
}
if ($env:APS_ENABLED -ne 'false' -and $env:APS_REPORTING_ENABLED -ne 'false' -and
    [string]::IsNullOrWhiteSpace($env:APS_SITE_CODE)) {
  throw 'APS 报表默认启用：请设置 APS_SITE_CODE，或设置 APS_REPORTING_ENABLED=false。'
}

$process = Start-Process -FilePath 'java' -ArgumentList '-jar', $launchJar, '--spring.profiles.active=druid,local' -WorkingDirectory (Split-Path $jar) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
Write-Output "Started RuoYi PID $($process.Id) from immutable JAR SHA256 $jarHash."
