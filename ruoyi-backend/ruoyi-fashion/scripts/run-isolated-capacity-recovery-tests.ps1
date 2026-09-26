[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$moduleRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$backendRoot = (Resolve-Path -LiteralPath (Join-Path $moduleRoot '..')).Path
$targetRoot = Join-Path $moduleRoot 'target'
New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null
$targetRoot = (Resolve-Path -LiteralPath $targetRoot).Path
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
$mysqlDump = Join-Path $mysqlBin 'mysqldump.exe'
foreach ($executable in @($mysqld, $mysql, $mysqlAdmin, $mysqlDump)) {
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw "未找到 MySQL 工具：$executable" }
}

$instanceRoot = Join-Path $targetRoot ("fashion-capacity-mysql-" + [guid]::NewGuid().ToString('N'))
$dataRoot = Join-Path $instanceRoot 'data'
$errorLog = Join-Path $instanceRoot 'mysqld-error.log'
$pidFile = Join-Path $instanceRoot 'mysqld.pid'
$serverProcess = $null
$port = $null

function Assert-DisposablePath {
    param([Parameter(Mandatory)][string]$Path)
    $resolvedTarget = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    if (-not $resolvedPath.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理 target 之外的路径：$resolvedPath"
    }
    if ([IO.Path]::GetFileName($resolvedPath.TrimEnd('\')) -notmatch '^fashion-capacity-mysql-[a-f0-9]{32}$') {
        throw "拒绝清理非 Fashion 容量临时实例路径：$resolvedPath"
    }
}

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $listener.Start(); return ([Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Start-IsolatedServer {
    $baseDir = Split-Path -Parent $mysqlBin
    $arguments = @('--no-defaults', "--basedir=`"$baseDir`"", "--datadir=`"$dataRoot`"", "--port=$port",
        '--bind-address=127.0.0.1', '--skip-networking=0', '--mysqlx=0', "--pid-file=`"$pidFile`"",
        "--log-error=`"$errorLog`"", '--secure-file-priv=NULL')
    $script:serverProcess = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        if ($script:serverProcess.HasExited) {
            throw "MySQL 临时实例提前退出，退出码 $($script:serverProcess.ExitCode)"
        }
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'ping' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw '等待 MySQL 临时实例就绪超时'
}

function Stop-IsolatedServer {
    if ($null -ne $script:serverProcess -and -not $script:serverProcess.HasExited) {
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
        try { [void]$script:serverProcess.WaitForExit(15000) } catch {}
        if (-not $script:serverProcess.HasExited) {
            Stop-Process -Id $script:serverProcess.Id -Force
            [void]$script:serverProcess.WaitForExit(10000)
        }
    }
}

try {
    Assert-DisposablePath -Path $instanceRoot
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    $port = Get-FreeTcpPort
    $baseDir = Split-Path -Parent $mysqlBin
    & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$baseDir" "--datadir=$dataRoot" "--log-error=$errorLog"
    if ($LASTEXITCODE -ne 0) { throw "MySQL 临时实例初始化失败，退出码 $LASTEXITCODE" }
    Start-IsolatedServer
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' `
        '--execute=create database fashion_capacity character set utf8mb4 collate utf8mb4_unicode_ci'
    if ($LASTEXITCODE -ne 0) { throw '创建隔离容量数据库失败' }

    $capacityStarted = [DateTime]::UtcNow
    Write-Host "Fashion 容量实例已就绪：127.0.0.1:$port；开始时间：$($capacityStarted.ToString('o'))"
    & mvn -f (Join-Path $backendRoot 'pom.xml') -pl ruoyi-fashion -am `
        '-Dtest=FashionProductionCandidateCapacityTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Dfashion.capacity.mysql.url=jdbc:mysql://127.0.0.1:$port/fashion_capacity?rewriteBatchedStatements=true" `
        '-Dfashion.capacity.mysql.username=root' '-Dfashion.capacity.mysql.password=' `
        '-Dfashion.capacity.mysql.database=fashion_capacity' test
    if ($LASTEXITCODE -ne 0) { throw "Fashion 容量测试失败，退出码 $LASTEXITCODE" }

    Stop-IsolatedServer
    $restartStarted = [DateTime]::UtcNow
    Start-IsolatedServer
    $restartMillis = [int]([DateTime]::UtcNow - $restartStarted).TotalMilliseconds
    Write-Host "Fashion 隔离 MySQL 已原地重启；耗时 ${restartMillis}ms"
    & mvn -f (Join-Path $backendRoot 'pom.xml') -pl ruoyi-fashion -am `
        '-Dtest=FashionProductionCandidateRecoveryTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Dfashion.recovery.mysql.url=jdbc:mysql://127.0.0.1:$port/fashion_capacity" `
        '-Dfashion.recovery.mysql.username=root' '-Dfashion.recovery.mysql.password=' test
    if ($LASTEXITCODE -ne 0) { throw "Fashion 重启恢复核对失败，退出码 $LASTEXITCODE" }

    $dumpPath = Join-Path $instanceRoot 'fashion-capacity-backup.sql'
    $businessTables = @(
        'fq_import_batch','fq_customer','fq_ai_agent','fq_ai_agent_version','fq_ai_conversation','fq_ai_run',
        'fq_product','fq_stock','fq_quote','fq_quote_combo','fq_quote_detail','fq_quote_image','fq_quote_file',
        'fq_ai_message','fq_import_detail','fq_ai_run_step','fashion_flyway_schema_history'
    )
    & $mysqlDump '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' `
        '--single-transaction' '--skip-add-locks' '--set-gtid-purged=OFF' '--no-tablespaces' `
        "--result-file=$dumpPath" 'fashion_capacity' @businessTables
    if ($LASTEXITCODE -ne 0) { throw "Fashion 隔离逻辑备份失败，退出码 $LASTEXITCODE" }
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' `
        '--execute=create database fashion_recovery character set utf8mb4 collate utf8mb4_unicode_ci'
    if ($LASTEXITCODE -ne 0) { throw '创建隔离恢复数据库失败' }
    $restoreStarted = [DateTime]::UtcNow
    $sourcePath = $dumpPath.Replace('\', '/')
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--database=fashion_recovery' `
        "--execute=source $sourcePath"
    if ($LASTEXITCODE -ne 0) { throw "Fashion 隔离逻辑恢复失败，退出码 $LASTEXITCODE" }
    $restoreMillis = [int]([DateTime]::UtcNow - $restoreStarted).TotalMilliseconds
    Write-Host "Fashion 隔离逻辑备份已恢复到新库；RPO 样本为 0，恢复耗时 ${restoreMillis}ms"
    & mvn -f (Join-Path $backendRoot 'pom.xml') -pl ruoyi-fashion -am `
        '-Dtest=FashionProductionCandidateRecoveryTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Dfashion.recovery.mysql.url=jdbc:mysql://127.0.0.1:$port/fashion_recovery" `
        '-Dfashion.recovery.mysql.username=root' '-Dfashion.recovery.mysql.password=' test
    if ($LASTEXITCODE -ne 0) { throw "Fashion 新库恢复核对失败，退出码 $LASTEXITCODE" }
    Write-Host "Fashion 容量与恢复验证完成：$([DateTime]::UtcNow.ToString('o'))"
} catch {
    if (Test-Path -LiteralPath $errorLog -PathType Leaf) { Get-Content -LiteralPath $errorLog -Tail 100 }
    throw
} finally {
    Stop-IsolatedServer
    if (Test-Path -LiteralPath $instanceRoot) {
        Assert-DisposablePath -Path $instanceRoot
        Remove-Item -LiteralPath $instanceRoot -Recurse -Force
    }
}
