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
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
if (-not (Test-Path -LiteralPath $mysqld -PathType Leaf)) {
    throw "未找到本机 MySQL Server：$mysqld"
}
if (-not (Test-Path -LiteralPath $mysqlAdmin -PathType Leaf)) {
    throw "未找到 mysqladmin：$mysqlAdmin"
}

$instanceRoot = Join-Path $targetRoot ("aden-mysql-local-" + [guid]::NewGuid().ToString('N'))
$dataRoot = Join-Path $instanceRoot 'data'
$errorLog = Join-Path $instanceRoot 'mysqld-error.log'
$pidFile = Join-Path $instanceRoot 'mysqld.pid'
$serverProcess = $null
$previousAdminUrl = [Environment]::GetEnvironmentVariable('ADEN_TEST_DB_ADMIN_URL', 'Process')
$previousUsername = [Environment]::GetEnvironmentVariable('ADEN_TEST_DB_USERNAME', 'Process')
$previousPassword = [Environment]::GetEnvironmentVariable('ADEN_TEST_DB_PASSWORD', 'Process')

function Assert-DisposablePath {
    param([Parameter(Mandatory)][string]$Path)

    $resolvedTarget = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    if (-not $resolvedPath.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理 target 之外的路径：$resolvedPath"
    }
    if ([IO.Path]::GetFileName($resolvedPath.TrimEnd('\')) -notmatch '^aden-mysql-local-[a-f0-9]{32}$') {
        throw "拒绝清理非 Aden 临时实例路径：$resolvedPath"
    }
}

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

try {
    Assert-DisposablePath -Path $instanceRoot
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    $port = Get-FreeTcpPort
    $baseDir = Split-Path -Parent $mysqlBin

    & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$baseDir" "--datadir=$dataRoot" "--log-error=$errorLog"
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL 临时实例初始化失败，退出码 $LASTEXITCODE"
    }

    $serverArguments = @(
        '--no-defaults',
        "--basedir=`"$baseDir`"",
        "--datadir=`"$dataRoot`"",
        "--port=$port",
        '--bind-address=127.0.0.1',
        '--skip-networking=0',
        '--mysqlx=0',
        "--pid-file=`"$pidFile`"",
        "--log-error=`"$errorLog`"",
        '--secure-file-priv=NULL'
    )
    $serverProcess = Start-Process -FilePath $mysqld -ArgumentList $serverArguments -WindowStyle Hidden -PassThru

    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($serverProcess.HasExited) {
            throw "MySQL 临时实例提前退出，退出码 $($serverProcess.ExitCode)"
        }
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'ping' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw '等待 MySQL 临时实例就绪超时'
    }

    [Environment]::SetEnvironmentVariable(
        'ADEN_TEST_DB_ADMIN_URL',
        "jdbc:mysql://127.0.0.1:$port/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        'Process'
    )
    [Environment]::SetEnvironmentVariable('ADEN_TEST_DB_USERNAME', 'root', 'Process')
    [Environment]::SetEnvironmentVariable('ADEN_TEST_DB_PASSWORD', '', 'Process')

    Write-Host "Aden 临时 MySQL 已就绪：127.0.0.1:$port（数据目录位于模块 target，测试后清理）"
    & mvn -f (Join-Path $backendRoot 'pom.xml') -pl ruoyi-aden -am `
        '-Dtest=*MySql*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) {
        throw "Aden MySQL 集成测试失败，退出码 $LASTEXITCODE"
    }
} catch {
    if (Test-Path -LiteralPath $errorLog -PathType Leaf) {
        Write-Host 'MySQL 错误日志末尾：'
        Get-Content -LiteralPath $errorLog -Tail 80
    }
    throw
} finally {
    [Environment]::SetEnvironmentVariable('ADEN_TEST_DB_ADMIN_URL', $previousAdminUrl, 'Process')
    [Environment]::SetEnvironmentVariable('ADEN_TEST_DB_USERNAME', $previousUsername, 'Process')
    [Environment]::SetEnvironmentVariable('ADEN_TEST_DB_PASSWORD', $previousPassword, 'Process')

    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$port" '--user=root' '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
        try {
            [void]$serverProcess.WaitForExit(10000)
        } catch {
            # 下方按精确进程 ID 做最终清理。
        }
        if (-not $serverProcess.HasExited) {
            Stop-Process -Id $serverProcess.Id -Force
            [void]$serverProcess.WaitForExit(10000)
        }
    }

    if (Test-Path -LiteralPath $instanceRoot) {
        Assert-DisposablePath -Path $instanceRoot
        Remove-Item -LiteralPath $instanceRoot -Recurse -Force
    }
}
