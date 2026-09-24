[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$fashionModule = Split-Path -Parent $scriptDirectory
$backendRoot = Split-Path -Parent $fashionModule
$repositoryRoot = Split-Path -Parent $backendRoot
$runtimeRoot = Join-Path $repositoryRoot 'fashion-ai-runtime'
$uvCommand = (Get-Command uv -ErrorAction Stop).Source

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
$listener.Stop()

$keyBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
$keyBase64 = [Convert]::ToBase64String($keyBytes)
$runtimeUrl = "http://127.0.0.1:$port"
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("fashion-live-runtime-" + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $temporaryRoot
$stdoutPath = Join-Path $temporaryRoot 'uvicorn.stdout.log'
$stderrPath = Join-Path $temporaryRoot 'uvicorn.stderr.log'
$runtimeHandle = $null

$environmentNames = @(
    'FASHION_AI_AUTH_ACTIVE_KEY_ID',
    'FASHION_AI_AUTH_ACTIVE_KEY_BASE64',
    'FASHION_AI_AUTH_ALLOWED_SERVICES',
    'FASHION_AI_PROVIDER_MODE'
)
$originalEnvironment = @{}
foreach ($name in $environmentNames) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Start-IsolatedRuntime {
    $arguments = @(
        'run', '--project', $runtimeRoot, '--all-extras', '--frozen',
        'uvicorn', 'fashion_ai.main:app', '--host', '127.0.0.1', '--port', "$port",
        '--log-level', 'warning'
    )
    $launcher = Start-Process -FilePath $uvCommand -ArgumentList $arguments `
        -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($launcher.HasExited) {
            $stderr = if (Test-Path -LiteralPath $stderrPath) { Get-Content -LiteralPath $stderrPath -Raw } else { '' }
            throw "Python Runtime 提前退出（$($launcher.ExitCode)）：$stderr"
        }
        try {
            $health = Invoke-RestMethod -Method Get -Uri "$runtimeUrl/health" -TimeoutSec 2
            if ($health.status -eq 'ok') {
                $connection = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop |
                    Where-Object { $_.LocalAddress -eq '127.0.0.1' } |
                    Select-Object -First 1
                if ($null -eq $connection) {
                    throw 'Runtime 已响应但没有找到对应监听进程'
                }
                $server = Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)"
                if ($null -eq $server -or $server.CommandLine -notlike "*uvicorn*--port $port*") {
                    throw '监听进程与本次隔离 Runtime 不匹配'
                }
                return [pscustomobject]@{
                    Launcher = $launcher
                    ServerId = [int]$connection.OwningProcess
                }
            }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    throw '等待 Python Runtime 就绪超时'
}

function Stop-IsolatedRuntime($handle) {
    if ($null -eq $handle) {
        return
    }
    $server = Get-CimInstance Win32_Process -Filter "ProcessId = $($handle.ServerId)" -ErrorAction SilentlyContinue
    if ($null -ne $server -and $server.CommandLine -like "*uvicorn*--port $port*") {
        Stop-Process -Id $handle.ServerId -Force -ErrorAction SilentlyContinue
    }
    $launcher = $handle.Launcher
    if ($null -ne $launcher -and -not $launcher.HasExited) {
        $null = $launcher.WaitForExit(10000)
        if (-not $launcher.HasExited) {
            Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue
            $null = $launcher.WaitForExit(10000)
        }
    }
}

function Invoke-LiveJavaTests {
    $backendPom = Join-Path $backendRoot 'pom.xml'
    $arguments = @(
        '-f', $backendPom,
        '-pl', 'ruoyi-fashion', '-am',
        '-Dtest=FashionLiveRuntimeIntegrationTest',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        "-Dfashion.live-runtime.url=$runtimeUrl",
        "-Dfashion.live-runtime.key-base64=$keyBase64",
        'test'
    )
    & mvn @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Java 真实 Runtime 集成测试失败，退出码 $LASTEXITCODE"
    }
}

try {
    [Environment]::SetEnvironmentVariable('FASHION_AI_AUTH_ACTIVE_KEY_ID', 'live-runtime-test', 'Process')
    [Environment]::SetEnvironmentVariable('FASHION_AI_AUTH_ACTIVE_KEY_BASE64', $keyBase64, 'Process')
    [Environment]::SetEnvironmentVariable('FASHION_AI_AUTH_ALLOWED_SERVICES', 'ruoyi-fashion', 'Process')
    [Environment]::SetEnvironmentVariable('FASHION_AI_PROVIDER_MODE', 'disabled', 'Process')

    $runtimeHandle = Start-IsolatedRuntime
    $firstProcessId = $runtimeHandle.ServerId
    Invoke-LiveJavaTests

    Stop-IsolatedRuntime $runtimeHandle
    $runtimeHandle = Start-IsolatedRuntime
    if ($runtimeHandle.ServerId -eq $firstProcessId) {
        throw 'Python Runtime 重启后进程 ID 未变化'
    }
    Invoke-LiveJavaTests

    Write-Output "PASS: Java/Python live HTTP integration before and after Python restart; port=$port"
} finally {
    Stop-IsolatedRuntime $runtimeHandle
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process')
    }
    [Array]::Clear($keyBytes, 0, $keyBytes.Length)

    $resolvedTemporaryRoot = [IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $temporaryLeaf = Split-Path -Leaf $resolvedTemporaryRoot
    $insideSystemTemp = $resolvedTemporaryRoot.StartsWith(
        $resolvedSystemTemp,
        [StringComparison]::OrdinalIgnoreCase)
    $hasExpectedPrefix = $temporaryLeaf.StartsWith("fashion-live-runtime-")
    if ($insideSystemTemp -and $hasExpectedPrefix) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
