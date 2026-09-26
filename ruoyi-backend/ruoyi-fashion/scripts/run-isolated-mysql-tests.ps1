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
foreach ($executable in @($mysqld, $mysql, $mysqlAdmin)) {
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw "未找到 MySQL 工具：$executable" }
}

$instanceRoot = Join-Path $targetRoot ("fashion-mysql-local-" + [guid]::NewGuid().ToString('N'))
$dataRoot = Join-Path $instanceRoot 'data'
$errorLog = Join-Path $instanceRoot 'mysqld-error.log'
$pidFile = Join-Path $instanceRoot 'mysqld.pid'
$serverProcess = $null

function Assert-DisposablePath {
    param([Parameter(Mandatory)][string]$Path)
    $resolvedTarget = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    if (-not $resolvedPath.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理 target 之外的路径：$resolvedPath"
    }
    if ([IO.Path]::GetFileName($resolvedPath.TrimEnd('\')) -notmatch '^fashion-mysql-local-[a-f0-9]{32}$') {
        throw "拒绝清理非 Fashion 临时实例路径：$resolvedPath"
    }
}

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $listener.Start(); return ([Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

try {
    Assert-DisposablePath -Path $instanceRoot
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    $port = Get-FreeTcpPort
    $baseDir = Split-Path -Parent $mysqlBin
    & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$baseDir" "--datadir=$dataRoot" "--log-error=$errorLog"
    if ($LASTEXITCODE -ne 0) { throw "MySQL 临时实例初始化失败，退出码 $LASTEXITCODE" }
    $arguments = @('--no-defaults', "--basedir=`"$baseDir`"", "--datadir=`"$dataRoot`"", "--port=$port",
        '--bind-address=127.0.0.1', '--skip-networking=0', '--mysqlx=0', "--pid-file=`"$pidFile`"",
        "--log-error=`"$errorLog`"", '--secure-file-priv=NULL')
    $serverProcess = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    do {
        if ($serverProcess.HasExited) { throw "MySQL 临时实例提前退出，退出码 $($serverProcess.ExitCode)" }
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'ping' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw '等待 MySQL 临时实例就绪超时' }
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' `
        '--execute=create database fashion_test character set utf8mb4 collate utf8mb4_unicode_ci'
    if ($LASTEXITCODE -ne 0) { throw '创建隔离数据库失败' }
    Write-Host "Fashion 临时 MySQL 已就绪：127.0.0.1:$port"
    & mvn -f (Join-Path $backendRoot 'pom.xml') -pl ruoyi-fashion -am `
        '-Dtest=FashionProvidedMySqlMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Dfashion.test.mysql.url=jdbc:mysql://127.0.0.1:$port/fashion_test" `
        '-Dfashion.test.mysql.username=root' '-Dfashion.test.mysql.password=' '-Dfashion.test.mysql.database=fashion_test' test
    if ($LASTEXITCODE -ne 0) { throw "Fashion MySQL 集成测试失败，退出码 $LASTEXITCODE" }
} catch {
    if (Test-Path -LiteralPath $errorLog -PathType Leaf) { Get-Content -LiteralPath $errorLog -Tail 80 }
    throw
} finally {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
        try { [void]$serverProcess.WaitForExit(10000) } catch {}
        if (-not $serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force; [void]$serverProcess.WaitForExit(10000) }
    }
    if (Test-Path -LiteralPath $instanceRoot) {
        Assert-DisposablePath -Path $instanceRoot
        Remove-Item -LiteralPath $instanceRoot -Recurse -Force
    }
}
