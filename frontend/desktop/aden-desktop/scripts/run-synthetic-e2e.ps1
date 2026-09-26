[CmdletBinding()]
param([switch]$SkipBuild, [switch]$InteractiveUat, [switch]$InstalledDesktop, [string]$BackendJarPath,
    [ValidateSet('synthetic','collection')][string]$Scenario = 'synthetic',
    [string]$DesktopExecutablePath, [string]$EvidenceDirectory,
    [ValidateRange(0,900)][int]$DebugHoldSeconds = 0)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$desktopRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $desktopRoot '../../..')).Path
$backendRoot = Join-Path $workspaceRoot 'ruoyi-backend'
$runnerRoot = Join-Path $workspaceRoot 'python/aden-runner'
$resultRoot = Join-Path $desktopRoot 'test-results'
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$instanceRoot = Join-Path $resultRoot ("aden-e2e-local-" + [guid]::NewGuid().ToString('N'))
$mysqlRoot = Join-Path $instanceRoot 'mysql'
$mysqlData = Join-Path $mysqlRoot 'data'
$redisData = Join-Path $instanceRoot 'redis'
$logRoot = Join-Path $instanceRoot 'logs'
$mysqlProcess = $null
$mysqlOwnerProcessId = $null
$mysqlPasswordApplied = $false
$redisProcess = $null
$backendProcess = $null
$desktopProcess = $null
$installedConfigPath = $null
$originalInstalledConfig = $null
$mysqlPort = $null
$backendPort = $null
$dbPassword = $null
$backendJar = $null

$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
$redisBin = 'C:\Program Files\Redis'
$redisServer = Join-Path $redisBin 'redis-server.exe'
$redisCli = Join-Path $redisBin 'redis-cli.exe'

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $listener.Start(); return ([Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Assert-DisposableRoot {
    $resolvedResults = [IO.Path]::GetFullPath($resultRoot).TrimEnd('\') + '\'
    $resolvedInstance = [IO.Path]::GetFullPath($instanceRoot).TrimEnd('\') + '\'
    if (-not $resolvedInstance.StartsWith($resolvedResults, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理 test-results 之外的目录"
    }
    if ([IO.Path]::GetFileName($resolvedInstance.TrimEnd('\')) -notmatch '^aden-e2e-local-[a-f0-9]{32}$') {
        throw "拒绝清理非 Aden E2E 临时目录"
    }
}

function Invoke-Checked {
    param([Parameter(Mandatory)][string]$FilePath, [Parameter(Mandatory)][string[]]$Arguments)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$FilePath 执行失败，退出码 $LASTEXITCODE" }
}

function Wait-HttpReady {
    param([Parameter(Mandatory)][string]$Url, [int]$Seconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($null -ne $backendProcess -and $backendProcess.HasExited) {
            throw "RuoYi 提前退出，退出码 $($backendProcess.ExitCode)"
        }
        try { Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 2 | Out-Null; return }
        catch { Start-Sleep -Milliseconds 500 }
    }
    throw '等待 RuoYi loopback 端点就绪超时'
}

function Assert-LoopbackListener {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Component,
        [Parameter(Mandatory)][string]$ExpectedCommandLineFragment
    )
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) { throw "$Component 未在预期端口 $Port 监听" }
    $nonLoopback = @($listeners | Where-Object { $_.LocalAddress -notin @('127.0.0.1','::1') })
    if ($nonLoopback.Count -gt 0) {
        throw "$Component 出现非 loopback 监听：$($nonLoopback.LocalAddress -join ','):$Port"
    }
    $owned = @($listeners | Where-Object {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($_.OwningProcess)" -ErrorAction SilentlyContinue
        $null -ne $process -and $process.CommandLine -like "*$ExpectedCommandLineFragment*"
    })
    if ($owned.Count -ne $listeners.Count) { throw "$Component 监听进程不属于本轮 E2E" }
    Write-Host "$Component 监听边界已核验：$($listeners.LocalAddress -join ','):$Port"
    return [int]$owned[0].OwningProcess
}

function Source-Sql {
    param([Parameter(Mandatory)][string]$Path)
    $normalized = ([IO.Path]::GetFullPath($Path) -replace '\\','/')
    Invoke-Checked -FilePath $mysql -Arguments @('--protocol=tcp','--default-character-set=utf8mb4','--host=127.0.0.1',"--port=$mysqlPort",'--user=root',"--database=$database","--execute=source $normalized")
}

function Add-SyntheticManagedSecret {
    param([Parameter(Mandatory)][string]$Alias, [Parameter(Mandatory)][string]$Value,
          [Parameter(Mandatory)][string]$Capability, [Parameter(Mandatory)][string]$Mode)
    $key = [Convert]::FromBase64String($env:RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64)
    $nonce = [Security.Cryptography.RandomNumberGenerator]::GetBytes(12)
    $plain = [Text.Encoding]::UTF8.GetBytes($Value)
    $cipher = [byte[]]::new($plain.Length)
    $tag = [byte[]]::new(16)
    $aad = [Text.Encoding]::UTF8.GetBytes("PLATFORM`n$Alias`n1")
    $aes = [Security.Cryptography.AesGcm]::new($key, 16)
    try { $aes.Encrypt($nonce, $plain, $cipher, $tag, $aad) }
    finally { $aes.Dispose(); [Array]::Clear($plain, 0, $plain.Length) }
    $payload = [byte[]]::new($cipher.Length + $tag.Length)
    [Array]::Copy($cipher, 0, $payload, 0, $cipher.Length)
    [Array]::Copy($tag, 0, $payload, $cipher.Length, $tag.Length)
    $nonceHex = [Convert]::ToHexString($nonce)
    $cipherHex = [Convert]::ToHexString($payload)
    $keyId = $env:RUOYI_MANAGED_SECRET_MASTER_KEY_ID
    $sql = "insert into sys_managed_secret (secret_alias,secret_kind,project_code,provider_code,capability_code,auth_mode,display_name,status,active_version,row_version,created_by,created_at,updated_by,updated_at) values ('$Alias','PLATFORM','aden','','$Capability','$Mode','$Alias','ACTIVE',1,1,'e2e',now(3),'e2e',now(3)); insert into sys_managed_secret_version (secret_alias,version_no,provider_code,capability_code,auth_mode,key_id,nonce,ciphertext,created_by,created_at,change_reason) values ('$Alias',1,'','$Capability','$Mode','$keyId',0x$nonceHex,0x$cipherHex,'e2e',now(3),'隔离合成测试');"
    Invoke-Checked -FilePath $mysql -Arguments @('--protocol=tcp','--default-character-set=utf8mb4','--host=127.0.0.1',"--port=$mysqlPort",'--user=root',"--database=$database","--execute=$sql")
}

Assert-DisposableRoot
if ($InstalledDesktop) {
    $installRoot = if ($DesktopExecutablePath) { Split-Path -Parent ([IO.Path]::GetFullPath($DesktopExecutablePath)) } else { Join-Path $env:LOCALAPPDATA 'Programs\Aden Local Test' }
    $installedExe = if ($DesktopExecutablePath) { [IO.Path]::GetFullPath($DesktopExecutablePath) } else { Join-Path $installRoot 'Aden Local Test.exe' }
    $installedConfigPath = Join-Path $installRoot 'resources\app\local-test.json'
    if (-not (Test-Path -LiteralPath $installedExe -PathType Leaf) -or
        -not (Test-Path -LiteralPath $installedConfigPath -PathType Leaf)) {
        throw '未找到当前用户的 Aden Local Test 安装版。'
    }
    $originalInstalledConfig = Get-Content -LiteralPath $installedConfigPath -Raw
    if (($originalInstalledConfig | ConvertFrom-Json).channel -ne 'local-test') {
        throw '安装版配置不是 local-test 渠道。'
    }
}
foreach ($binary in @($mysqld, $mysql, $mysqlAdmin, $redisServer, $redisCli)) {
    if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "缺少 E2E 依赖：$binary" }
}

$savedEnvironment = @{}
$environmentNames = @('MYSQL_PWD','ADEN_E2E_DB_PASSWORD','RUOYI_DB_URL','RUOYI_DB_USERNAME','RUOYI_DB_PASSWORD','RUOYI_MANAGED_SECRET_MASTER_KEY_ID','RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64','ADEN_E2E_API_ORIGIN','ADEN_E2E_WORKSPACE_NAME','ADEN_E2E_RUNNER_TOKEN','ADEN_API_BASE_URL','ADEN_E2E_DESKTOP_EXE','ADEN_E2E_EVIDENCE_DIR','ADEN_COLLECTION_FIXTURE_ORIGIN','ADEN_COLLECTOR_PIPE')
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    New-Item -ItemType Directory -Path $mysqlData,$redisData,$logRoot -Force | Out-Null
    if (-not $SkipBuild) {
        Write-Host '构建 RuoYi、Desktop 与 Runner E2E 入口...'
        # E2E 只需主产物；工作区其他模块的测试由各自门禁执行，避免无关测试源码阻断 Aden 启动。
        # clean 可避免聚合构建复用旧的 ruoyi-admin 可执行包而漏装最新业务模块。
        Invoke-Checked -FilePath 'mvn' -Arguments @('-f',(Join-Path $backendRoot 'pom.xml'),'-pl','ruoyi-admin','-am','clean','install','-Dmaven.test.skip=true')
        Invoke-Checked -FilePath 'npm' -Arguments @('run','build')
        Push-Location $runnerRoot
        try { Invoke-Checked -FilePath 'uv' -Arguments @('sync','--extra','test','--frozen') }
        finally { Pop-Location }
    } else {
        Write-Host '复用现有构建产物；调用方须确认其版本与本轮验收范围相符...'
    }

    $mysqlPort = Get-FreeTcpPort
    $redisPort = Get-FreeTcpPort
    $backendPort = Get-FreeTcpPort
    $database = 'aden_e2e'
    $dbPassword = [guid]::NewGuid().ToString('N')
    $pepper = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $baseDir = Split-Path -Parent $mysqlBin
    $mysqlError = Join-Path $logRoot 'mysql-error.log'
    $mysqlPid = Join-Path $mysqlRoot 'mysqld.pid'

    Invoke-Checked -FilePath $mysqld -Arguments @('--no-defaults','--initialize-insecure',"--basedir=$baseDir","--datadir=$mysqlData","--log-error=$mysqlError")
    $mysqlProcess = Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults',"--basedir=`"$baseDir`"","--datadir=`"$mysqlData`"","--port=$mysqlPort",'--bind-address=127.0.0.1','--skip-networking=0','--mysqlx=0',"--pid-file=`"$mysqlPid`"","--log-error=`"$mysqlError`"",'--secure-file-priv=NULL') -WindowStyle Hidden -PassThru
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while ([DateTime]::UtcNow -lt $deadline) {
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' '--connect-timeout=2' 'ping' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 400
    }
    if (-not $ready) { throw 'MySQL E2E 临时实例启动超时' }
    $mysqlOwnerProcessId = Assert-LoopbackListener -Port $mysqlPort -Component 'MySQL' -ExpectedCommandLineFragment $instanceRoot
    Invoke-Checked -FilePath $mysql -Arguments @('--protocol=tcp','--default-character-set=utf8mb4','--host=127.0.0.1',"--port=$mysqlPort",'--user=root',"--execute=alter user 'root'@'localhost' identified by '$dbPassword'; create database $database character set utf8mb4 collate utf8mb4_unicode_ci;")
    $mysqlPasswordApplied = $true
    $env:MYSQL_PWD = $dbPassword
    $env:ADEN_E2E_DB_PASSWORD = $dbPassword
    Source-Sql -Path (Join-Path $backendRoot 'sql\ry_20260417.sql')

    $jdbc = "jdbc:mysql://127.0.0.1:$mysqlPort/$database"
    $migrationMain = 'com.ruoyi.aden.migration.AdenMigrationCli'
    $migrationPom = Join-Path $backendRoot 'ruoyi-aden\pom.xml'
    [string[]]$mavenMode = if ($SkipBuild) { @('-o') } else { @() }
    foreach ($command in @('baseline','migrate','validate')) {
        $migrationArgs = "$command --url=$jdbc --username=root --expected-database=$database --password-env=ADEN_E2E_DB_PASSWORD"
        Invoke-Checked -FilePath 'mvn' -Arguments ($mavenMode + @('-q','-f',$migrationPom,"-Dexec.mainClass=$migrationMain","-Dexec.args=$migrationArgs",'org.codehaus.mojo:exec-maven-plugin:3.5.0:java'))
    }
    Source-Sql -Path (Join-Path $backendRoot 'sql\aden-permissions.sql')
    if ($Scenario -eq 'collection') { Source-Sql -Path (Join-Path $backendRoot 'sql\aden-collection-permissions.sql') }
    Source-Sql -Path (Join-Path $backendRoot 'ruoyi-aden\src\test\resources\fixtures\aden-e2e-seed.sql')
    if ($Scenario -eq 'collection') {
        $testEnvNames = @('ADEN_TEST_DB_ADMIN_URL','ADEN_TEST_DB_USERNAME','ADEN_TEST_DB_PASSWORD')
        $testEnvPrevious = @{}
        foreach ($name in $testEnvNames) { $testEnvPrevious[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
        try {
            $env:ADEN_TEST_DB_ADMIN_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/mysql?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
            $env:ADEN_TEST_DB_USERNAME = 'root'
            $env:ADEN_TEST_DB_PASSWORD = $dbPassword
            Invoke-Checked -FilePath 'mvn' -Arguments ($mavenMode + @('-f',(Join-Path $backendRoot 'pom.xml'),'-pl','ruoyi-aden','-am','-Dtest=AdenWorkspaceMySqlTest#collectionPersistsVersionsAssetsExportsAndRejectsLateUploads,AdenCollectionFilesTest,AdenTaskTransitionMatrixTest,AdenMigrationStaticContractTest','-Dsurefire.failIfNoSpecifiedTests=false','test'))
        } finally {
            foreach ($name in $testEnvNames) { [Environment]::SetEnvironmentVariable($name, $testEnvPrevious[$name], 'Process') }
        }
    }
    Source-Sql -Path (Join-Path $backendRoot 'sql\ruoyi-managed-secrets.sql')
    $env:RUOYI_MANAGED_SECRET_MASTER_KEY_ID = 'e2e-root-v1'
    $env:RUOYI_MANAGED_SECRET_MASTER_KEY_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $jwt = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    Add-SyntheticManagedSecret -Alias 'platform.ruoyi.jwt' -Value $jwt -Capability 'jwt' -Mode 'hmac'
    Add-SyntheticManagedSecret -Alias 'platform.aden.runner-pepper' -Value (@{keyId='e2e-v1';keyBase64=$pepper} | ConvertTo-Json -Compress) -Capability 'runner' -Mode 'hmac'

    $redisProcess = Start-Process -FilePath $redisServer -ArgumentList @('--port',"$redisPort",'--bind','127.0.0.1','--protected-mode','yes','--appendonly','no','--dir',"`"$redisData`"") -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'redis-out.log') -RedirectStandardError (Join-Path $logRoot 'redis-error.log') -PassThru
    $redisReady = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    while ([DateTime]::UtcNow -lt $deadline) {
        $pong = & $redisCli '-h' '127.0.0.1' '-p' "$redisPort" 'ping' 2>$null
        if ($LASTEXITCODE -eq 0 -and $pong -match 'PONG') { $redisReady = $true; break }
        Start-Sleep -Milliseconds 300
    }
    if (-not $redisReady) { throw 'Redis E2E 临时实例启动超时' }
    [void](Assert-LoopbackListener -Port $redisPort -Component 'Redis' -ExpectedCommandLineFragment $redisData)

    $env:RUOYI_DB_URL = "${jdbc}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $env:RUOYI_DB_USERNAME = 'root'
    $env:RUOYI_DB_PASSWORD = $dbPassword
    $sourceBackendJar = if ($BackendJarPath) {
        (Resolve-Path -LiteralPath $BackendJarPath).Path
    } else {
        $jarDir = Join-Path $backendRoot 'ruoyi-admin\target'
        $defaultJar = Join-Path $jarDir 'ruoyi-admin.jar'
        if ((Test-Path -LiteralPath $defaultJar -PathType Leaf) -and
            (Get-Item -LiteralPath $defaultJar).Length -ge 10000000) {
            $defaultJar
        } else {
            $candidate = @(Get-ChildItem -LiteralPath $jarDir -Filter 'ruoyi-admin-*.jar' -File |
                Where-Object { $_.Length -ge 10000000 } |
                Sort-Object LastWriteTime -Descending |
                Where-Object {
                    $entries = & jar tf $_.FullName
                    $LASTEXITCODE -eq 0 -and
                    @($entries | Where-Object { $_ -like 'BOOT-INF/lib/ruoyi-aden-*.jar' }).Count -gt 0 -and
                    @($entries | Where-Object { $_ -like 'BOOT-INF/lib/logback-classic-*.jar' }).Count -gt 0
                } | Select-Object -First 1)
            if ($candidate.Count -ne 1) { throw '找不到包含 Aden 与日志依赖的完整 RuoYi 可执行 JAR；请先完成后端构建。' }
            Write-Host "默认 JAR 不是完整包，隔离验收改用 $($candidate[0].FullName)"
            $candidate[0].FullName
        }
    }
    $sourceJarInfo = Get-Item -LiteralPath $sourceBackendJar
    if ($sourceJarInfo.Length -lt 10000000) {
        throw "RuoYi E2E 需要完整可执行 JAR，当前文件仅 $($sourceJarInfo.Length) 字节：$sourceBackendJar"
    }
    $backendJar = Join-Path $instanceRoot 'ruoyi-admin-e2e.jar'
    Copy-Item -LiteralPath $sourceBackendJar -Destination $backendJar
    # 共享 ruoyi-admin 还包含与本 Feature 无关的业务 Bean；懒加载确保本验收只实例化真实登录与 Aden 链路。
    $collectionArguments = @()
    if ($Scenario -eq 'collection') {
        $fixturePort = Get-FreeTcpPort
        $env:ADEN_COLLECTION_FIXTURE_ORIGIN = "http://127.0.0.1:$fixturePort"
        $env:ADEN_COLLECTOR_PIPE = "\\.\pipe\aden-collector-test-$([guid]::NewGuid().ToString('N'))"
        $collectionArguments = @('--aden.collection.enabled=true',"--aden.collection.storage-root=$instanceRoot/collection-storage","--aden.collection.fixture-origin=$env:ADEN_COLLECTION_FIXTURE_ORIGIN")
    }
    $backendArguments = @('-jar',"`"$backendJar`"","--server.address=127.0.0.1","--server.port=$backendPort",'--spring.profiles.active=druid,test','--spring.main.lazy-initialization=true','--fashion.image.worker-enabled=false','--fashion.delivery.worker-enabled=false','--interview.enabled=false','--logging.level.com.ruoyi.aden=DEBUG','--aden.enabled=true',"--aden.schema.expected-database=$database",'--spring.data.redis.host=127.0.0.1',"--spring.data.redis.port=$redisPort",'--spring.data.redis.database=15') + $collectionArguments
    $backendProcess = Start-Process -FilePath 'java' -ArgumentList $backendArguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'backend-out.log') -RedirectStandardError (Join-Path $logRoot 'backend-error.log') -PassThru
    $origin = "http://127.0.0.1:$backendPort"
    Wait-HttpReady -Url "$origin/captchaImage"
    [void](Assert-LoopbackListener -Port $backendPort -Component 'RuoYi' -ExpectedCommandLineFragment $backendJar)

    $loginBody = @{ username='aden_e2e'; password='admin123'; code=''; uuid='' } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$origin/login" -Method Post -ContentType 'application/json' -Body $loginBody
    if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace($login.token)) { throw '合成用户登录失败' }
    $operatorHeaders = @{ Authorization = "Bearer $($login.token)" }
    $workspaceName = 'Aden E2E Workspace'
    $workspace = Invoke-RestMethod -Uri "$origin/api/v1/aden/admin/workspaces" -Method Post -Headers $operatorHeaders -ContentType 'application/json' -Body (@{ displayName=$workspaceName } | ConvertTo-Json -Compress)
    $enrollment = Invoke-RestMethod -Uri "$origin/api/v1/aden/workspaces/$($workspace.workspaceId)/runners:enroll" -Method Post -Headers $operatorHeaders -ContentType 'application/json' -Body (@{ displayName='E2E Runner'; capabilities=@('CORE') } | ConvertTo-Json -Compress)
    if ([string]::IsNullOrWhiteSpace($enrollment.credentialToken)) { throw 'Runner enrollment 未返回一次性凭据' }

    $env:ADEN_E2E_API_ORIGIN = $origin
    $env:ADEN_E2E_WORKSPACE_NAME = $workspaceName
    $env:ADEN_E2E_RUNNER_TOKEN = $enrollment.credentialToken
    if ($InstalledDesktop) {
        $testConfig = @{ channel='local-test'; apiBaseUrl=$origin } | ConvertTo-Json -Compress
        [IO.File]::WriteAllText($installedConfigPath, $testConfig, [Text.UTF8Encoding]::new($false))
        $env:ADEN_E2E_DESKTOP_EXE = $installedExe
        if ($EvidenceDirectory) { $env:ADEN_E2E_EVIDENCE_DIR = [IO.Path]::GetFullPath($EvidenceDirectory) }
        elseif (-not $InteractiveUat -and $Scenario -eq 'synthetic') {
            $env:ADEN_E2E_EVIDENCE_DIR = Join-Path $workspaceRoot '文档\项目\智能体桌面端项目\功能\FEAT-ADEN-002-桌面安装包与本地同步\证据\2026-09-26-安装版验收\screenshots'
        }
    }
    Push-Location $desktopRoot
    try {
        if ($DebugHoldSeconds -gt 0) {
            $contextPath = Join-Path $resultRoot 'collection-debug-context.json'
            $stopPath = Join-Path $resultRoot 'collection-debug-finish'
            if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
            @{ ADEN_E2E_API_ORIGIN=$env:ADEN_E2E_API_ORIGIN; ADEN_E2E_WORKSPACE_NAME=$env:ADEN_E2E_WORKSPACE_NAME;
               ADEN_E2E_DESKTOP_EXE=$env:ADEN_E2E_DESKTOP_EXE; ADEN_COLLECTION_FIXTURE_ORIGIN=$env:ADEN_COLLECTION_FIXTURE_ORIGIN;
               ADEN_COLLECTOR_PIPE=$env:ADEN_COLLECTOR_PIPE; ADEN_E2E_EVIDENCE_DIR=$env:ADEN_E2E_EVIDENCE_DIR
             } | ConvertTo-Json | Set-Content -LiteralPath $contextPath -Encoding utf8
        }
        try { Invoke-Checked -FilePath 'node' -Arguments @("tests/e2e/$Scenario.e2e.mjs") }
        catch {
            if ($DebugHoldSeconds -gt 0) {
                Write-Host "技术测试失败，隔离服务最多保留 $DebugHoldSeconds 秒供定向复验；创建 $stopPath 可立即清理。上下文不含凭据。"
                $deadline = [DateTime]::UtcNow.AddSeconds($DebugHoldSeconds)
                while ([DateTime]::UtcNow -lt $deadline -and -not (Test-Path -LiteralPath $stopPath)) { Start-Sleep -Seconds 2 }
            }
            throw
        }
    }
    finally { Pop-Location }
    Write-Host "Aden 本地合成 E2E 通过：MySQL/Redis/RuoYi 均为本轮 loopback 临时实例。"
    if ($InteractiveUat) {
        $adminLogin = Invoke-RestMethod -Uri "$origin/login" -Method Post -ContentType 'application/json' -Body (@{ username='admin'; password='admin123'; code=''; uuid='' } | ConvertTo-Json -Compress)
        if ($adminLogin.code -ne 200 -or [string]::IsNullOrWhiteSpace($adminLogin.token)) { throw '隔离环境管理员登录失败，无法开启验证码' }
        $adminHeaders = @{ Authorization = "Bearer $($adminLogin.token)" }
        $config = Invoke-RestMethod -Uri "$origin/system/config/4" -Headers $adminHeaders
        if ($config.code -ne 200 -or $config.data.configKey -ne 'sys.account.captchaEnabled') { throw '隔离环境验证码配置不符合预期' }
        $config.data.configValue = 'true'
        $updated = Invoke-RestMethod -Uri "$origin/system/config" -Method Put -Headers $adminHeaders -ContentType 'application/json' -Body ($config.data | ConvertTo-Json -Depth 8 -Compress)
        if ($updated.code -ne 200) { throw '隔离环境启用验证码失败' }
        $challenge = Invoke-RestMethod -Uri "$origin/captchaImage"
        if ($challenge.code -ne 200 -or $challenge.captchaEnabled -ne $true -or [string]::IsNullOrWhiteSpace($challenge.img)) { throw '验证码开启后未返回图片' }

        $env:ADEN_API_BASE_URL = $origin
        $electronProfile = Join-Path $instanceRoot 'electron-profile'
        if ($InstalledDesktop) {
            $desktopProcess = Start-Process -FilePath $installedExe -ArgumentList @("--user-data-dir=$electronProfile") -PassThru
        } else {
            $electronExe = Join-Path $desktopRoot 'node_modules\electron\dist\electron.exe'
            $desktopMain = Join-Path $desktopRoot 'out\main\index.js'
            $desktopProcess = Start-Process -FilePath $electronExe -ArgumentList @("--user-data-dir=$electronProfile",$desktopMain) -WorkingDirectory $desktopRoot -PassThru
        }
        Write-Host "Aden 合成验收窗口已打开；API=$origin；账号=aden_e2e；验证码已开启。关闭本窗口后自动清理隔离环境。"
        Wait-Process -Id $desktopProcess.Id
    }
} catch {
    if (Test-Path -LiteralPath $logRoot) {
        foreach ($name in @('backend-error.log','backend-out.log','redis-error.log','redis-out.log','mysql-error.log')) {
            $path = Join-Path $logRoot $name
            if (Test-Path -LiteralPath $path) { Write-Host "$name 末尾："; Get-Content -LiteralPath $path -Tail 250 }
        }
    }
    throw
} finally {
    if ($null -ne $installedConfigPath -and $null -ne $originalInstalledConfig) {
        [IO.File]::WriteAllText($installedConfigPath, $originalInstalledConfig, [Text.UTF8Encoding]::new($false))
    }
    if ($null -eq $mysqlOwnerProcessId -and (Test-Path -LiteralPath $instanceRoot)) {
        $ownedMySql = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -like "*$instanceRoot*" })
        if ($ownedMySql.Count -eq 1) { $mysqlOwnerProcessId = [int]$ownedMySql[0].ProcessId }
    }
    if ($null -ne $mysqlOwnerProcessId -and $null -ne $mysqlPort) {
        try {
            if ($mysqlPasswordApplied) { $env:MYSQL_PWD = $dbPassword }
            else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
            & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
            if (Get-Process -Id $mysqlOwnerProcessId -ErrorAction SilentlyContinue) {
                [void](Wait-Process -Id $mysqlOwnerProcessId -Timeout 10 -ErrorAction SilentlyContinue)
            }
        } catch { Write-Warning "MySQL 临时实例优雅关停失败：$($_.Exception.Message)" }
    }
    if ($null -ne $mysqlOwnerProcessId) {
        try {
            $liveMySql = Get-CimInstance Win32_Process -Filter "ProcessId = $mysqlOwnerProcessId" -ErrorAction SilentlyContinue
            if ($null -ne $liveMySql -and $liveMySql.CommandLine -like "*$instanceRoot*") {
                Stop-Process -Id $mysqlOwnerProcessId -Force -ErrorAction Stop
                [void](Wait-Process -Id $mysqlOwnerProcessId -Timeout 10 -ErrorAction SilentlyContinue)
            }
        } catch { Write-Warning "MySQL E2E 子进程 $mysqlOwnerProcessId 清理失败：$($_.Exception.Message)" }
    }
    foreach ($ownedProcess in @($desktopProcess,$backendProcess,$redisProcess,$mysqlProcess)) {
        if ($null -ne $ownedProcess) {
            try {
                $liveProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($ownedProcess.Id)"
                $ownsDisposablePath = $null -ne $liveProcess -and $liveProcess.CommandLine -like "*$instanceRoot*"
                $ownsBackend = $null -ne $liveProcess -and $null -ne $backendJar -and $null -ne $backendPort -and
                    $liveProcess.CommandLine -like "*$backendJar*" -and $liveProcess.CommandLine -like "*--server.port=$backendPort*"
                if ($ownsDisposablePath -or $ownsBackend) {
                    Stop-Process -Id $ownedProcess.Id -Force -ErrorAction Stop
                    [void]$ownedProcess.WaitForExit(10000)
                }
            } catch { Write-Warning "E2E 临时进程 $($ownedProcess.Id) 清理失败：$($_.Exception.Message)" }
        }
    }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    if (Test-Path -LiteralPath $instanceRoot) {
        Assert-DisposableRoot
        try { Remove-Item -LiteralPath $instanceRoot -Recurse -Force }
        catch { Write-Warning "E2E 临时目录清理失败，可按精确路径恢复清理：$instanceRoot；$($_.Exception.Message)" }
    }
}
