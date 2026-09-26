[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$moduleRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$backendRoot = (Resolve-Path -LiteralPath (Join-Path $moduleRoot '..')).Path
$jarPath = Join-Path $backendRoot 'ruoyi-admin/target/ruoyi-admin.jar'
$ruoyiSql = Join-Path $backendRoot 'sql/ry_20260417.sql'
$targetRoot = Join-Path $moduleRoot 'target'
New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null
$targetRoot = (Resolve-Path -LiteralPath $targetRoot).Path

$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
foreach ($file in @($jarPath, $ruoyiSql, $mysqld, $mysql, $mysqlAdmin)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "缺少 Java 重启演练依赖：$file"
    }
}

$instanceRoot = Join-Path $targetRoot ("fashion-java-restart-" + [guid]::NewGuid().ToString('N'))
$dataRoot = Join-Path $instanceRoot 'mysql-data'
$mysqlErrorLog = Join-Path $instanceRoot 'mysqld-error.log'
$mysqlPidFile = Join-Path $instanceRoot 'mysqld.pid'
$database = 'fashion_java_restart'
$mysqlPort = $null
$javaPort = $null
$mysqlProcess = $null
$javaProcess = $null
$javaRun = 0
$serviceKeyId = 'java-restart-key'
$serviceKeyBase64 = $null

function Assert-DisposablePath {
    param([Parameter(Mandatory)][string]$Path)
    $resolvedTarget = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    $insideTarget = $resolvedPath.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)
    $leaf = [IO.Path]::GetFileName($resolvedPath.TrimEnd('\'))
    if (-not $insideTarget -or $leaf -notmatch '^fashion-java-restart-[a-f0-9]{32}$') {
        throw "拒绝清理非 Fashion Java 重启临时路径：$resolvedPath"
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

function Invoke-MySql {
    param([Parameter(Mandatory)][string]$Statement)
    $output = & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' `
        '--default-character-set=utf8mb4' '--batch' '--skip-column-names' `
        "--database=$database" "--execute=$Statement"
    if ($LASTEXITCODE -ne 0) {
        throw "隔离 MySQL 命令失败，退出码 $LASTEXITCODE"
    }
    return @($output)
}

function Initialize-AiWorkerFixture {
    Invoke-MySql @"
insert into fq_ai_agent (
  id,agent_code,name,agent_type,description,current_version_id,status,
  create_by,create_time,update_by,update_time,row_version
) values (
  990100001,'java-restart-agent','Java 重启演练 Agent','requirement','仅用于隔离 Worker 重启验证',null,'active',
  1,now(3),1,now(3),1
);
insert into fq_ai_agent_version (
  id,agent_id,version_no,provider_code,model_name,system_instruction,model_config_json,tools_json,
  handoffs_json,input_schema_json,output_schema_json,guardrails_json,max_steps,timeout_seconds,
  config_hash,status,published_by,published_at,create_by,create_time,update_by,update_time,row_version
) values (
  990100002,990100001,1,'local-unreachable','never-called','隔离重启演练',json_object(),json_array(),
  json_array(),json_object(),json_object(),json_object(),1,30,
  repeat('a',64),'draft',null,null,1,now(3),1,now(3),1
);
insert into fq_ai_conversation (
  id,conversation_no,owner_user_id,customer_id,quote_id,primary_agent_id,title,channel,context_json,
  summary_text,summary_up_to_seq,message_count,last_message_at,status,
  create_by,create_time,update_by,update_time,row_version
) values (
  990100003,'CONV-JAVA-RESTART',1,null,null,990100001,'Java 重启演练','api',json_object(),
  null,0,0,null,'active',1,now(3),1,now(3),1
);
"@ | Out-Null
}

function Add-ExpiredAiRun {
    param(
        [Parameter(Mandatory)][long]$MessageId,
        [Parameter(Mandatory)][long]$RunId,
        [Parameter(Mandatory)][int]$Sequence,
        [Parameter(Mandatory)][string]$Suffix
    )
    Invoke-MySql @"
insert into fq_ai_message (
  id,conversation_id,seq_no,parent_message_id,run_id,role,content_text,content_json,
  attachments_json,provider_item_id,visible_to_user,content_hash,status,error_message,
  create_by,create_time,update_by,update_time,row_version
) values (
  $MessageId,990100003,$Sequence,null,null,'user','已经超时的隔离任务 $Suffix',null,
  json_array(),null,1,repeat('b',64),'complete',null,1,now(3),1,now(3),1
);
insert into fq_ai_run (
  id,run_no,conversation_id,agent_version_id,input_message_id,trigger_type,request_key,
  agent_config_hash,deadline_at,budget_json,context_snapshot_json,status,
  create_by,create_time,update_by,update_time,row_version
) values (
  $RunId,'RUN-JAVA-RESTART-$Suffix',990100003,990100002,$MessageId,'parse_requirement','java-restart-run-$Suffix',
  repeat('a',64),date_sub(now(3),interval 1 minute),json_object(),json_object('source_hash',repeat('b',64)),'queued',
  1,now(3),1,now(3),1
);
"@ | Out-Null
}

function Wait-ExpiredAiRun {
    param([Parameter(Mandatory)][long]$RunId)
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $state = [string]((Invoke-MySql "select concat(status,'|',coalesce(error_code,''),'|',run_attempt,'|',current_step_no) from fq_ai_run where id=$RunId;") | Select-Object -First 1)
        if ($state -eq 'failed|DEADLINE_EXCEEDED|0|0') {
            $stepCount = [int]((Invoke-MySql "select count(*) from fq_ai_run_step where run_id=$RunId;") | Select-Object -First 1)
            if ($stepCount -ne 0) {
                throw "AI Worker 超时处理产生了不应存在的执行步骤：run=$RunId steps=$stepCount"
            }
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "等待 AI Worker 自动处理超时任务失败：run=$RunId state=$state"
}

function Start-IsolatedMySql {
    $baseDir = Split-Path -Parent $mysqlBin
    $arguments = @(
        '--no-defaults', "--basedir=`"$baseDir`"", "--datadir=`"$dataRoot`"", "--port=$mysqlPort",
        '--bind-address=127.0.0.1', '--skip-networking=0', '--mysqlx=0',
        "--pid-file=`"$mysqlPidFile`"", "--log-error=`"$mysqlErrorLog`"", '--secure-file-priv=NULL'
    )
    $script:mysqlProcess = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        if ($script:mysqlProcess.HasExited) {
            throw "隔离 MySQL 提前退出，退出码 $($script:mysqlProcess.ExitCode)"
        }
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' `
            '--connect-timeout=2' 'ping' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw '等待隔离 MySQL 就绪超时'
}

function Stop-IsolatedMySql {
    if ($null -eq $script:mysqlProcess -or $script:mysqlProcess.HasExited) {
        return
    }
    & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' `
        '--connect-timeout=2' 'shutdown' 2>$null | Out-Null
    try { [void]$script:mysqlProcess.WaitForExit(15000) } catch {}
    if (-not $script:mysqlProcess.HasExited) {
        Stop-Process -Id $script:mysqlProcess.Id -Force
        [void]$script:mysqlProcess.WaitForExit(10000)
    }
}

function Start-IsolatedJava {
    $script:javaRun++
    $stdoutPath = Join-Path $instanceRoot "java-$($script:javaRun).stdout.log"
    $stderrPath = Join-Path $instanceRoot "java-$($script:javaRun).stderr.log"
    $jdbcUrl = "jdbc:mysql://127.0.0.1:$mysqlPort/$database" +
        '?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8'
    $arguments = @(
        '-jar', $jarPath,
        '--spring.profiles.active=druid,test',
        "--server.port=$javaPort", '--server.address=127.0.0.1',
        "--spring.datasource.druid.master.url=$jdbcUrl",
        '--spring.datasource.druid.master.username=root', '--spring.datasource.druid.master.password=',
        '--interview.foundation-safety.business-rest-endpoints-enabled=false',
        '--interview.foundation-safety.catalog-rest-endpoints-enabled=false',
        '--interview.catalog.public-tenant-id=local-fashion-restart',
        '--interview.voice-runtime.ticket-store=memory',
        '--aden.enabled=false', '--aps.enabled=false',
        '--fashion.migration.enabled=true', "--fashion.migration.expected-database=$database",
        '--fashion.migration.baseline-approved=true',
        '--fashion.image.worker-enabled=true', '--fashion.delivery.worker-enabled=true',
        '--fashion.ai-runtime.worker-enabled=true',
        '--fashion.ai-runtime.worker-id=java-restart-worker',
        '--fashion.ai-runtime.base-url=http://127.0.0.1:1',
        "--fashion.service-identity.active-key-id=$serviceKeyId",
        "--fashion.service-identity.active-key-base64=$serviceKeyBase64",
        '--logging.level.com.ruoyi.fashion=INFO', '--logging.level.org.springframework=WARN'
    )
    $process = Start-Process -FilePath 'java' -ArgumentList $arguments -WorkingDirectory $instanceRoot `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        if ($process.HasExited) {
            $stdout = if (Test-Path -LiteralPath $stdoutPath) { Get-Content -LiteralPath $stdoutPath -Tail 120 } else { @() }
            $stderr = if (Test-Path -LiteralPath $stderrPath) { Get-Content -LiteralPath $stderrPath -Tail 120 } else { @() }
            throw "隔离 Java 提前退出（$($process.ExitCode)）：`n$($stdout -join "`n")`n$($stderr -join "`n")"
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$javaPort/v3/api-docs" -TimeoutSec 3
            if ($response.StatusCode -eq 200 -and $response.Content -match 'openapi') {
                $server = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
                if ($null -eq $server -or $server.CommandLine -notlike "*ruoyi-admin.jar*server.port=$javaPort*") {
                    throw '监听 Java 进程与本次隔离演练不匹配'
                }
                return $process
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "等待隔离 Java HTTP 就绪超时；日志：$stdoutPath"
}

function Stop-IsolatedJava {
    if ($null -eq $script:javaProcess -or $script:javaProcess.HasExited) {
        return
    }
    $server = Get-CimInstance Win32_Process -Filter "ProcessId = $($script:javaProcess.Id)" -ErrorAction SilentlyContinue
    if ($null -ne $server -and $server.CommandLine -like "*ruoyi-admin.jar*server.port=$javaPort*") {
        Stop-Process -Id $script:javaProcess.Id
    }
    try { [void]$script:javaProcess.WaitForExit(15000) } catch {}
    if (-not $script:javaProcess.HasExited) {
        Stop-Process -Id $script:javaProcess.Id -Force
        [void]$script:javaProcess.WaitForExit(10000)
    }
}

try {
    Assert-DisposablePath -Path $instanceRoot
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    $serviceKeyBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($serviceKeyBytes)
    $serviceKeyBase64 = [Convert]::ToBase64String($serviceKeyBytes)
    [Array]::Clear($serviceKeyBytes, 0, $serviceKeyBytes.Length)
    $mysqlPort = Get-FreeTcpPort
    do { $javaPort = Get-FreeTcpPort } while ($javaPort -eq $mysqlPort)
    $baseDir = Split-Path -Parent $mysqlBin
    & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$baseDir" "--datadir=$dataRoot" `
        "--log-error=$mysqlErrorLog"
    if ($LASTEXITCODE -ne 0) { throw "隔离 MySQL 初始化失败，退出码 $LASTEXITCODE" }
    Start-IsolatedMySql
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' `
        '--default-character-set=utf8mb4' `
        "--execute=create database $database character set utf8mb4 collate utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw '创建隔离 Java 重启数据库失败' }
    $sourcePath = $ruoyiSql.Replace('\', '/')
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$mysqlPort" '--user=root' "--database=$database" `
        '--default-character-set=utf8mb4' "--execute=source $sourcePath"
    if ($LASTEXITCODE -ne 0) { throw '导入 RuoYi 隔离基线失败' }

    $javaProcess = Start-IsolatedJava
    $firstJavaPid = $javaProcess.Id
    $tableCount = [int]((Invoke-MySql "select count(*) from information_schema.tables where table_schema='$database' and left(table_name,3)='fq_';") | Select-Object -First 1)
    if ($tableCount -ne 16) { throw "首次 Java 启动后 fq_* 表数量不是 16：$tableCount" }
    Initialize-AiWorkerFixture
    Add-ExpiredAiRun -MessageId 990100004 -RunId 990100005 -Sequence 1 -Suffix 'before-restart'
    Wait-ExpiredAiRun -RunId 990100005
    Invoke-MySql @"
insert into fq_import_batch (
  id,batch_no,import_type,operation_type,source_code,mapping_snapshot,scope_json,as_of,
  request_key,create_by,create_time,update_by,update_time
) values (
  990000001,'JAVA-RESTART-1','product','verify','restart-test',json_object(),json_object(),now(3),
  'java-restart-test',1,now(3),1,now(3)
);
"@ | Out-Null

    Stop-IsolatedJava
    Add-ExpiredAiRun -MessageId 990100006 -RunId 990100007 -Sequence 2 -Suffix 'after-restart'
    $queuedBeforeRestart = [string]((Invoke-MySql "select status from fq_ai_run where id=990100007;") | Select-Object -First 1)
    if ($queuedBeforeRestart -ne 'queued') { throw "Java 停止期间的 AI Run 状态异常：$queuedBeforeRestart" }
    $javaProcess = Start-IsolatedJava
    if ($javaProcess.Id -eq $firstJavaPid) { throw 'Java 重启后进程 ID 未变化' }
    Wait-ExpiredAiRun -RunId 990100007
    $tableCountAfter = [int]((Invoke-MySql "select count(*) from information_schema.tables where table_schema='$database' and left(table_name,3)='fq_';") | Select-Object -First 1)
    $markerCount = [int]((Invoke-MySql "select count(*) from fq_import_batch where request_key='java-restart-test';") | Select-Object -First 1)
    if ($tableCountAfter -ne 16 -or $markerCount -ne 1) {
        throw "Java 重启后状态核对失败：tables=$tableCountAfter marker=$markerCount"
    }
    Write-Output "PASS: ruoyi-admin Java process restarted with AI/image/delivery workers enabled; AI worker expired 2 runs without claiming/provider call; mysqlPort=$mysqlPort javaPort=$javaPort tables=$tableCountAfter marker=$markerCount"
} catch {
    if (Test-Path -LiteralPath $mysqlErrorLog -PathType Leaf) {
        Get-Content -LiteralPath $mysqlErrorLog -Tail 100
    }
    throw
} finally {
    Stop-IsolatedJava
    Stop-IsolatedMySql
    if (Test-Path -LiteralPath $instanceRoot) {
        Assert-DisposablePath -Path $instanceRoot
        Remove-Item -LiteralPath $instanceRoot -Recurse -Force
    }
}
