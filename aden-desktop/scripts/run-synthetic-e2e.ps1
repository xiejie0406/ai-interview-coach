[CmdletBinding()]
param([switch]$SkipBuild)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$desktopRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $desktopRoot '..')).Path
$backendRoot = Join-Path $workspaceRoot 'platform-backend'
$runnerRoot = Join-Path $workspaceRoot 'aden-runner'
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

Assert-DisposableRoot
foreach ($binary in @($mysqld, $mysql, $mysqlAdmin, $redisServer, $redisCli)) {
    if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "缺少 E2E 依赖：$binary" }
}

$savedEnvironment = @{}
$environmentNames = @('MYSQL_PWD','ADEN_E2E_DB_PASSWORD','RUOYI_DB_URL','RUOYI_DB_USERNAME','RUOYI_DB_PASSWORD','ADEN_RUNNER_PEPPER_KEY_ID','ADEN_RUNNER_PEPPER_BASE64','ADEN_E2E_API_ORIGIN','ADEN_E2E_WORKSPACE_NAME','ADEN_E2E_RUNNER_TOKEN')
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
        Write-Host '复用本轮已构建产物，继续调试隔离环境与合成链路...'
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
    foreach ($command in @('baseline','migrate','validate')) {
        $migrationArgs = "$command --url=$jdbc --username=root --expected-database=$database --password-env=ADEN_E2E_DB_PASSWORD"
        Invoke-Checked -FilePath 'mvn' -Arguments @('-q','-f',$migrationPom,"-Dexec.mainClass=$migrationMain","-Dexec.args=$migrationArgs",'org.codehaus.mojo:exec-maven-plugin:3.5.0:java')
    }
    Source-Sql -Path (Join-Path $backendRoot 'sql\aden-permissions.sql')
    Source-Sql -Path (Join-Path $backendRoot 'ruoyi-aden\src\test\resources\fixtures\aden-e2e-seed.sql')

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
    $env:ADEN_RUNNER_PEPPER_KEY_ID = 'e2e-v1'
    $env:ADEN_RUNNER_PEPPER_BASE64 = $pepper
    $backendJar = Join-Path $backendRoot 'ruoyi-admin\target\ruoyi-admin.jar'
    # 共享 ruoyi-admin 还包含与本 Feature 无关的业务 Bean；懒加载确保本验收只实例化真实登录与 Aden 链路。
    $backendProcess = Start-Process -FilePath 'java' -ArgumentList @('-jar',"`"$backendJar`"","--server.address=127.0.0.1","--server.port=$backendPort",'--spring.profiles.active=druid,test','--spring.main.lazy-initialization=true','--fashion.image.worker-enabled=false','--fashion.delivery.worker-enabled=false','--logging.level.com.ruoyi.aden=DEBUG','--aden.enabled=true',"--aden.schema.expected-database=$database",'--spring.data.redis.host=127.0.0.1',"--spring.data.redis.port=$redisPort",'--spring.data.redis.database=15') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'backend-out.log') -RedirectStandardError (Join-Path $logRoot 'backend-error.log') -PassThru
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
    Push-Location $desktopRoot
    try { Invoke-Checked -FilePath 'node' -Arguments @('tests/e2e/synthetic.e2e.mjs') }
    finally { Pop-Location }
    Write-Host "Aden 本地合成 E2E 通过：MySQL/Redis/RuoYi 均为本轮 loopback 临时实例。"
} catch {
    if (Test-Path -LiteralPath $logRoot) {
        foreach ($name in @('backend-error.log','backend-out.log','redis-error.log','redis-out.log','mysql-error.log')) {
            $path = Join-Path $logRoot $name
            if (Test-Path -LiteralPath $path) { Write-Host "$name 末尾："; Get-Content -LiteralPath $path -Tail 250 }
        }
    }
    throw
} finally {
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
    foreach ($ownedProcess in @($backendProcess,$redisProcess,$mysqlProcess)) {
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
