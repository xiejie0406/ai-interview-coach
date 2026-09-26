[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Users\admin\.jdks\ms-17.0.18',
    [string]$MavenHome = 'D:\java-i-exe\apache-maven-3.9.15',
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Windows 是当前批准的目标运行环境。本脚本不安装软件、不使用系统 MySQL 服务、
# 不写真实业务库；API smoke 使用脚本自建并在结束时删除的隔离 MySQL 数据目录。
$scriptRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot 'target\aps-windows-verification'
}
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$summaryPath = Join-Path $outputRoot 'summary.tsv'
$baselinePath = Join-Path $outputRoot 'runtime-baseline.txt'
Set-Content -LiteralPath $summaryPath -Value "name`tstatus`tdetail" -Encoding utf8

$failures = 0
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
$mysqlProcess = $null
$workerProcess = $null
$apiProcess = $null
$temporaryRoot = $null
$mysqlPort = $null

function Add-Result {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][ValidateSet('PASS', 'FAIL')][string]$Status,
        [Parameter(Mandatory)][string]$Detail
    )
    Add-Content -LiteralPath $summaryPath -Value "$Name`t$Status`t$Detail" -Encoding utf8
    Write-Output "[$Status] $Name - $Detail"
    if ($Status -eq 'FAIL') {
        $script:failures++
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

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$ArgumentList,
        [string]$WorkingDirectory = $repositoryRoot
    )
    $stdoutPath = Join-Path $outputRoot "$Name.stdout.log"
    $stderrPath = Join-Path $outputRoot "$Name.stderr.log"
    $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -Wait -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    if ($process.ExitCode -eq 0) {
        Add-Result -Name $Name -Status PASS -Detail "exit=0; logs=$stdoutPath,$stderrPath"
        return $true
    }
    Add-Result -Name $Name -Status FAIL -Detail "exit=$($process.ExitCode); logs=$stdoutPath,$stderrPath"
    return $false
}

function Wait-ProcessReady {
    param(
        [Parameter(Mandatory)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$StdoutPath,
        [Parameter(Mandatory)][string]$StderrPath,
        [Parameter(Mandatory)][string]$ReadyPattern,
        [int]$TimeoutSeconds = 90,
        [string]$HttpUrl,
        [string]$HttpContentPattern
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $combined = ''
        if (Test-Path -LiteralPath $StdoutPath) {
            $combined += Get-Content -LiteralPath $StdoutPath -Raw -ErrorAction SilentlyContinue
        }
        if (Test-Path -LiteralPath $StderrPath) {
            $combined += Get-Content -LiteralPath $StderrPath -Raw -ErrorAction SilentlyContinue
        }
        if ($combined -match $ReadyPattern) {
            if ([string]::IsNullOrWhiteSpace($HttpUrl)) {
                Add-Result -Name $Name -Status PASS -Detail "pid=$($Process.Id); startup marker found; logs=$StdoutPath,$StderrPath"
                return $true
            }
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri $HttpUrl -TimeoutSec 4
                $contentMatches = [string]::IsNullOrWhiteSpace($HttpContentPattern) -or
                    $response.Content -match $HttpContentPattern
                if ($response.StatusCode -eq 200 -and $contentMatches) {
                    Add-Result -Name $Name -Status PASS -Detail "pid=$($Process.Id); HTTP 200 $HttpUrl; logs=$StdoutPath,$StderrPath"
                    return $true
                }
            } catch {
                # 应用日志先出现 Started 时，HTTP 监听可能仍在最后切换；继续等到统一超时。
            }
        }
        if ($Process.HasExited) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    $exitDetail = if ($Process.HasExited) { "process exited with $($Process.ExitCode)" } else { "timeout=${TimeoutSeconds}s" }
    Add-Result -Name $Name -Status FAIL -Detail "$exitDetail; logs=$StdoutPath,$StderrPath"
    return $false
}

function Stop-OwnedProcess {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    Stop-Process -Id $Process.Id -ErrorAction SilentlyContinue
    try { [void]$Process.WaitForExit(15000) } catch { }
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        try { [void]$Process.WaitForExit(10000) } catch { }
    }
}

function Assert-DisposableTemporaryRoot {
    param([Parameter(Mandatory)][string]$Path)
    $systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolved = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    $leaf = [IO.Path]::GetFileName($resolved.TrimEnd('\'))
    if (-not $resolved.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase) -or
        $leaf -notmatch '^aps-windows-runtime-[a-f0-9]{32}$') {
        throw "拒绝清理未验证的临时路径：$resolved"
    }
}

try {
    if (-not $IsWindows) {
        Add-Result -Name platform -Status FAIL -Detail '本验证只能在批准的 Windows 目标环境执行'
        throw 'Wrong platform'
    }

    $javaExe = Join-Path $JavaHome 'bin\java.exe'
    $jarExe = Join-Path $JavaHome 'bin\jar.exe'
    $mavenExe = Join-Path $MavenHome 'bin\mvn.cmd'
    $mysqldExe = Join-Path $MySqlHome 'bin\mysqld.exe'
    $mysqlExe = Join-Path $MySqlHome 'bin\mysql.exe'
    $mysqlAdminExe = Join-Path $MySqlHome 'bin\mysqladmin.exe'
    $ruoyiSql = Join-Path $repositoryRoot 'ruoyi-backend\sql\ry_20260417.sql'
    $workerJar = Join-Path $repositoryRoot 'ruoyi-backend\aps\aps-worker\target\aps-worker-3.9.2.jar'
    $apiJar = Join-Path $repositoryRoot 'ruoyi-backend\ruoyi-admin\target\ruoyi-admin.jar'
    $frontendRoot = Join-Path $repositoryRoot 'frontend/admin-web'

    $requiredFiles = @($javaExe, $jarExe, $mavenExe, $mysqldExe, $mysqlExe, $mysqlAdminExe, $ruoyiSql)
    $missingFiles = @($requiredFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
    if ($missingFiles.Count -gt 0) {
        Add-Result -Name prerequisites -Status FAIL -Detail ("missing=" + ($missingFiles -join ','))
        throw 'Missing prerequisites'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules') -PathType Container)) {
        Add-Result -Name prerequisites -Status FAIL -Detail 'frontend/admin-web/node_modules 不存在，不能复验锁定的前端制品'
        throw 'Missing frontend dependencies'
    }

    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + ';' + (Join-Path $MavenHome 'bin') + ';' + $originalPath

    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    $processor = Get-CimInstance Win32_Processor | Select-Object -First 1
    $javaVersion = (& $javaExe -version 2>&1) -join [Environment]::NewLine
    $mavenVersion = (& $mavenExe -version 2>&1) -join [Environment]::NewLine
    $nodeVersion = (& node --version 2>&1) -join [Environment]::NewLine
    $npmVersion = (& npm --version 2>&1) -join [Environment]::NewLine
    @(
        "captured_at_utc=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))"
        "repository=$repositoryRoot"
        "os_caption=$($operatingSystem.Caption)"
        "os_version=$($operatingSystem.Version)"
        "os_build=$($operatingSystem.BuildNumber)"
        "os_architecture=$($operatingSystem.OSArchitecture)"
        "process_architecture=$([Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture)"
        "cpu=$($processor.Name)"
        "physical_cores=$($processor.NumberOfCores)"
        "logical_processors=$($processor.NumberOfLogicalProcessors)"
        "memory_gib=$([Math]::Round($operatingSystem.TotalVisibleMemorySize / 1MB, 1))"
        "java_home=$JavaHome"
        '[java]'
        $javaVersion
        '[maven]'
        $mavenVersion
        "node=$nodeVersion"
        "npm=$npmVersion"
    ) | Set-Content -LiteralPath $baselinePath -Encoding utf8

    Add-Result -Name platform -Status PASS -Detail "$($operatingSystem.Caption) $($operatingSystem.Version) build $($operatingSystem.BuildNumber), $([Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture)"
    if ($javaVersion -match 'version "17\.') {
        Add-Result -Name java-major -Status PASS -Detail (($javaVersion -split "`r?`n")[0])
    } else {
        Add-Result -Name java-major -Status FAIL -Detail "expected Java 17; actual=$javaVersion"
    }
    if ($mavenVersion -match [regex]::Escape($JavaHome)) {
        Add-Result -Name maven-java-home -Status PASS -Detail 'Maven 正在使用选定的 JDK 17'
    } else {
        Add-Result -Name maven-java-home -Status FAIL -Detail 'Maven 输出没有引用选定的 JDK 17'
    }
    Add-Result -Name runtime-baseline -Status PASS -Detail "CPU=$($processor.Name); cores=$($processor.NumberOfCores)/$($processor.NumberOfLogicalProcessors); memory=$([Math]::Round($operatingSystem.TotalVisibleMemorySize / 1MB, 1)) GiB; Node=$nodeVersion; npm=$npmVersion; file=$baselinePath"

    if ($failures -eq 0) {
        [void](Invoke-LoggedCommand -Name 'ortools-jni-smoke' -FilePath $mavenExe -ArgumentList @(
            '-f', (Join-Path $repositoryRoot 'ruoyi-backend\aps\pom.xml'),
            '-pl', 'aps-solver-ortools', '-am',
            '-Dtest=OrToolsNativeSmokeTest', '-Dsurefire.failIfNoSpecifiedTests=false',
            'test', '--no-transfer-progress'
        ))
    }

    if ($failures -eq 0) {
        [void](Invoke-LoggedCommand -Name 'backend-package' -FilePath $mavenExe -ArgumentList @(
            '-f', (Join-Path $repositoryRoot 'ruoyi-backend\pom.xml'),
            '-pl', 'ruoyi-admin,aps/aps-worker', '-am',
            '-DskipTests', 'package', '--no-transfer-progress'
        ))
    }

    if ($failures -eq 0) {
        [void](Invoke-LoggedCommand -Name 'frontend-package' -FilePath (Get-Command npm.cmd).Source `
            -ArgumentList @('--prefix', $frontendRoot, 'run', 'build:prod'))
    }

    if ($failures -eq 0) {
        foreach ($artifact in @($workerJar, $apiJar)) {
            if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
                Add-Result -Name artifact -Status FAIL -Detail "missing=$artifact"
                continue
            }
            $artifactName = if ($artifact -eq $workerJar) { 'worker-artifact' } else { 'api-artifact' }
            [void](Invoke-LoggedCommand -Name $artifactName -FilePath $jarExe -ArgumentList @('tf', $artifact))
        }
        if ($failures -eq 0) {
            $hashPath = Join-Path $outputRoot 'artifact-sha256.txt'
            @($workerJar, $apiJar) | ForEach-Object {
                $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
                "$($hash.Hash)  $($_)"
            } | Set-Content -LiteralPath $hashPath -Encoding utf8
            Add-Result -Name artifact-sha256 -Status PASS -Detail "file=$hashPath"
        }
    }

    if ($failures -eq 0) {
        $workerStdout = Join-Path $outputRoot 'worker-disabled-startup.stdout.log'
        $workerStderr = Join-Path $outputRoot 'worker-disabled-startup.stderr.log'
        $workerProcess = Start-Process -FilePath $javaExe -ArgumentList @(
            '-jar', $workerJar,
            '--aps.enabled=false', '--aps.worker.enabled=false', '--aps.worker.polling-enabled=false'
        ) -WorkingDirectory $outputRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $workerStdout -RedirectStandardError $workerStderr
        [void](Wait-ProcessReady -Process $workerProcess -Name 'worker-disabled-startup' `
            -StdoutPath $workerStdout -StderrPath $workerStderr -ReadyPattern 'Started ApsWorkerApplication' -TimeoutSeconds 60)
        Stop-OwnedProcess -Process $workerProcess
        $workerProcess = $null
    }

    if ($failures -eq 0) {
        $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("aps-windows-runtime-" + [guid]::NewGuid().ToString('N'))
        Assert-DisposableTemporaryRoot -Path $temporaryRoot
        $mysqlDataRoot = Join-Path $temporaryRoot 'mysql-data'
        $mysqlErrorLog = Join-Path $temporaryRoot 'mysqld-error.log'
        $mysqlPidFile = Join-Path $temporaryRoot 'mysqld.pid'
        New-Item -ItemType Directory -Path $mysqlDataRoot -Force | Out-Null
        & $mysqldExe '--no-defaults' '--initialize-insecure' "--basedir=$MySqlHome" "--datadir=$mysqlDataRoot" "--log-error=$mysqlErrorLog"
        if ($LASTEXITCODE -ne 0) {
            Add-Result -Name api-isolated-mysql -Status FAIL -Detail "initialize exit=$LASTEXITCODE; log=$mysqlErrorLog"
        } else {
            $mysqlPort = Get-FreeTcpPort
            $mysqlProcess = Start-Process -FilePath $mysqldExe -ArgumentList @(
                '--no-defaults', "--basedir=`"$MySqlHome`"", "--datadir=`"$mysqlDataRoot`"", "--port=$mysqlPort",
                '--bind-address=127.0.0.1', '--skip-networking=0', '--mysqlx=0', '--skip-log-bin',
                "--pid-file=`"$mysqlPidFile`"", "--log-error=`"$mysqlErrorLog`"", '--secure-file-priv=NULL'
            ) -WindowStyle Hidden -PassThru
            $mysqlReady = $false
            $mysqlDeadline = [DateTime]::UtcNow.AddSeconds(45)
            do {
                if ($mysqlProcess.HasExited) { break }
                & $mysqlAdminExe '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' '--connect-timeout=2' 'ping' 2>$null | Out-Null
                if ($LASTEXITCODE -eq 0) {
                    $mysqlReady = $true
                    break
                }
                Start-Sleep -Milliseconds 300
            } while ([DateTime]::UtcNow -lt $mysqlDeadline)
            if (-not $mysqlReady) {
                Copy-Item -LiteralPath $mysqlErrorLog -Destination (Join-Path $outputRoot 'api-isolated-mysql-error.log') -Force -ErrorAction SilentlyContinue
                Add-Result -Name api-isolated-mysql -Status FAIL -Detail "not ready; log=$mysqlErrorLog"
            } else {
                $databaseName = 'aps_windows_runtime'
                & $mysqlExe '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' '--default-character-set=utf8mb4' "--execute=CREATE DATABASE $databaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                if ($LASTEXITCODE -ne 0) {
                    Add-Result -Name api-isolated-mysql -Status FAIL -Detail '创建隔离 RuoYi schema 失败'
                } else {
                    $sourcePath = $ruoyiSql.Replace('\', '/')
                    & $mysqlExe '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' "--database=$databaseName" '--default-character-set=utf8mb4' "--execute=source $sourcePath"
                    if ($LASTEXITCODE -ne 0) {
                        Add-Result -Name api-isolated-mysql -Status FAIL -Detail '导入 RuoYi 隔离基线失败'
                    } else {
                        Add-Result -Name api-isolated-mysql -Status PASS -Detail "temporary port=$mysqlPort; baseline imported; system service untouched"
                    }
                }
            }
        }
    }

    if ($failures -eq 0) {
        $apiPort = Get-FreeTcpPort
        $apiStdout = Join-Path $outputRoot 'api-startup.stdout.log'
        $apiStderr = Join-Path $outputRoot 'api-startup.stderr.log'
        $jdbcUrl = "jdbc:mysql://127.0.0.1:$mysqlPort/aps_windows_runtime?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8"
        $apiProcess = Start-Process -FilePath $javaExe -ArgumentList @(
            '-jar', $apiJar,
            '--spring.profiles.active=druid,test', "--server.port=$apiPort", '--server.address=127.0.0.1',
            "--spring.datasource.druid.master.url=$jdbcUrl",
            '--spring.datasource.druid.master.username=root', '--spring.datasource.druid.master.password=',
            '--interview.foundation-safety.business-rest-endpoints-enabled=false',
            '--interview.foundation-safety.catalog-rest-endpoints-enabled=false',
            '--interview.catalog.public-tenant-id=aps-windows-runtime',
            '--interview.voice-runtime.ticket-store=memory',
            '--aden.enabled=false',
            '--fashion.migration.enabled=false', '--fashion.image.worker-enabled=false',
            '--fashion.delivery.worker-enabled=false', '--fashion.ai-runtime.worker-enabled=false',
            '--aps.enabled=true', '--aps.api.enabled=true', '--aps.persistence.enabled=false',
            '--aps.reporting.enabled=false', '--aps.solver.enabled=false',
            '--aps.worker.enabled=false', '--aps.worker.polling-enabled=false',
            '--logging.level.com.ruoyi=INFO', '--logging.level.org.springframework=WARN'
        ) -WorkingDirectory $outputRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $apiStdout -RedirectStandardError $apiStderr
        [void](Wait-ProcessReady -Process $apiProcess -Name 'api-startup' `
            -StdoutPath $apiStdout -StderrPath $apiStderr -ReadyPattern 'Started RuoYiApplication' `
            -TimeoutSeconds 120 -HttpUrl "http://127.0.0.1:$apiPort/v3/api-docs" -HttpContentPattern '"openapi"')
        Stop-OwnedProcess -Process $apiProcess
        $apiProcess = $null
    }
} catch {
    if ($failures -eq 0) {
        Add-Result -Name unhandled-error -Status FAIL -Detail $_.Exception.Message
    }
} finally {
    Stop-OwnedProcess -Process $apiProcess
    Stop-OwnedProcess -Process $workerProcess

    if ($null -ne $mysqlProcess -and -not $mysqlProcess.HasExited -and $null -ne $mysqlPort) {
        & $mysqlAdminExe '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
        try { [void]$mysqlProcess.WaitForExit(15000) } catch { }
        if (-not $mysqlProcess.HasExited) {
            Stop-Process -Id $mysqlProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($null -ne $temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        Assert-DisposableTemporaryRoot -Path $temporaryRoot
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }

    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
}

Write-Output "Windows target verification summary: failures=$failures; evidence=$outputRoot"
if ($failures -gt 0) {
    exit 1
}
