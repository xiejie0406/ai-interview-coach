param(
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
)

$ErrorActionPreference = 'Stop'
$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
$mysqldump = Join-Path $MySqlHome 'bin\mysqldump.exe'
if (-not (Test-Path -LiteralPath $mysqld) -or -not (Test-Path -LiteralPath $mysql) -or
    -not (Test-Path -LiteralPath $mysqldump) -or
    -not (Test-Path -LiteralPath $mysqladmin)) {
    throw "MySQL 8 binaries were not found under: $MySqlHome"
}

$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$runRoot = Join-Path $tempRoot ("aps-imp02-mysql-" + [Guid]::NewGuid().ToString('N'))
$runRoot = [System.IO.Path]::GetFullPath($runRoot)
if (-not $runRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
    -not ([System.IO.Path]::GetFileName($runRoot)).StartsWith('aps-imp02-mysql-')) {
    throw "Refusing an unverified temporary directory: $runRoot"
}

$dataDir = Join-Path $runRoot 'data'
$errorLog = Join-Path $runRoot 'mysql-error.log'
$pidFile = Join-Path $runRoot 'mysql.pid'
$process = $null

try {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    & $mysqld --no-defaults "--basedir=$MySqlHome" "--datadir=$dataDir" --initialize-insecure
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL temporary data directory initialization failed with exit code $LASTEXITCODE"
    }

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    $listener.Stop()

    $arguments = @(
        '--no-defaults',
        "--basedir=`"$MySqlHome`"",
        "--datadir=`"$dataDir`"",
        "--port=$port",
        '--bind-address=127.0.0.1',
        '--mysqlx=0',
        '--skip-log-bin',
        "--log-error=`"$errorLog`"",
        "--pid-file=`"$pidFile`""
    )
    $process = Start-Process -FilePath $mysqld -ArgumentList $arguments -PassThru -WindowStyle Hidden

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & $mysqladmin --protocol=tcp -h 127.0.0.1 -P $port -u root ping --silent 2>$null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        if ($process.HasExited) {
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        $logTail = if (Test-Path -LiteralPath $errorLog) { (Get-Content -LiteralPath $errorLog -Tail 40) -join "`n" } else { 'No MySQL error log was created.' }
        throw "The temporary MySQL instance did not become ready.`n$logTail"
    }

    $env:APS_TEST_MYSQL_ADMIN_URL = "jdbc:mysql://127.0.0.1:$port/mysql"
    $env:APS_TEST_MYSQL_USERNAME = 'root'
    $env:APS_TEST_MYSQL_PASSWORD = ''
    mvn -f platform-backend/aps/pom.xml -pl aps-infrastructure-mysql -am clean test --no-transfer-progress
    if ($LASTEXITCODE -ne 0) {
        throw "APS MySQL migration tests failed with exit code $LASTEXITCODE"
    }

    $permissionSchema = 'aps_imp02_permissions'
    $createPermissionSchema = @"
CREATE DATABASE $permissionSchema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE $permissionSchema.sys_menu (
  menu_id bigint not null auto_increment,
  menu_name varchar(50) not null,
  parent_id bigint default 0,
  order_num int default 0,
  path varchar(200) default '',
  component varchar(255) default null,
  query varchar(255) default null,
  route_name varchar(50) default '',
  is_frame int default 1,
  is_cache int default 0,
  menu_type char(1) default '',
  visible char(1) default '0',
  status char(1) default '0',
  perms varchar(100) default null,
  icon varchar(100) default '#',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  remark varchar(500) default '',
  primary key (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"@
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 -e $createPermissionSchema
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create the isolated sys_menu test schema.' }

    $permissionSql = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\platform-backend\sql\aps-permissions.sql')).Replace('\', '/')
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 "--database=$permissionSchema" -e "source $permissionSql"
    if ($LASTEXITCODE -ne 0) { throw 'The first APS permission SQL execution failed.' }
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 "--database=$permissionSchema" -e "source $permissionSql"
    if ($LASTEXITCODE -ne 0) { throw 'The second APS permission SQL execution failed.' }
    $permissionCount = (& $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root -N -B "--database=$permissionSchema" -e "SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'aps:%'").Trim()
    if ($permissionCount -ne '29') { throw "Expected 29 APS permission rows after two runs, got $permissionCount" }
    Write-Output "APS permission SQL idempotency: Pass ($permissionCount rows after two executions)"

    $recoverySource = 'aps_imp02_recovery_source'
    $recoveryTarget = 'aps_imp02_recovery_target'
    $migrationSql = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\platform-backend\aps\aps-infrastructure-mysql\src\main\resources\db\migration\aps\V001__aps_baseline.sql')).Replace('\', '/')
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 -e "CREATE DATABASE $recoverySource CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $recoveryTarget CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create recovery drill schemas.' }
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 "--database=$recoverySource" -e "source $migrationSql"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to apply V001 to the recovery source schema.' }
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root "--database=$recoverySource" -e "INSERT INTO aps_workshop (id, workshop_code, workshop_name) VALUES ('00000000-0000-4000-8000-000000000001', 'RECOVERY', 'Recovery Fixture');"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to seed the recovery fixture.' }

    $dumpFile = Join-Path $runRoot 'aps-recovery.sql'
    $dumpArguments = @('--protocol=tcp', '-h127.0.0.1', "-P$port", '-uroot', '--default-character-set=utf8mb4', '--single-transaction', '--set-gtid-purged=OFF', '--skip-comments', $recoverySource)
    $dumpProcess = Start-Process -FilePath $mysqldump -ArgumentList $dumpArguments -PassThru -Wait -WindowStyle Hidden -RedirectStandardOutput $dumpFile
    if ($dumpProcess.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $dumpFile)) { throw 'mysqldump recovery backup failed.' }
    $dumpSql = [System.IO.Path]::GetFullPath($dumpFile).Replace('\', '/')
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root --default-character-set=utf8mb4 "--database=$recoveryTarget" -e "source $dumpSql"
    if ($LASTEXITCODE -ne 0) { throw 'Restoring the APS logical backup failed.' }
    $restoredTableCount = (& $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root -N -B "--database=$recoveryTarget" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$recoveryTarget' AND table_name LIKE 'aps\_%';").Trim()
    $restoredFixtureCount = (& $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root -N -B "--database=$recoveryTarget" -e "SELECT COUNT(*) FROM aps_workshop WHERE workshop_code = 'RECOVERY';").Trim()
    if ($restoredTableCount -ne '29' -or $restoredFixtureCount -ne '1') {
        throw "Recovery verification failed: tables=$restoredTableCount fixture=$restoredFixtureCount"
    }
    & $mysql --protocol=tcp -h 127.0.0.1 -P $port -u root -e "DROP DATABASE $recoverySource; DROP DATABASE $recoveryTarget; DROP DATABASE $permissionSchema;"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to clean isolated verification schemas.' }
    Write-Output "APS logical backup/restore drill: Pass (29 tables, fixture restored)"
}
finally {
    Remove-Item Env:APS_TEST_MYSQL_ADMIN_URL -ErrorAction SilentlyContinue
    Remove-Item Env:APS_TEST_MYSQL_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:APS_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue

    if ($null -ne $process -and -not $process.HasExited) {
        & $mysqladmin --protocol=tcp -h 127.0.0.1 -P $port -u root shutdown 2>$null
        try { $process.WaitForExit(10000) | Out-Null } catch { }
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }
    }

    $resolvedRunRoot = [System.IO.Path]::GetFullPath($runRoot)
    if ($resolvedRunRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        ([System.IO.Path]::GetFileName($resolvedRunRoot)).StartsWith('aps-imp02-mysql-') -and
        (Test-Path -LiteralPath $resolvedRunRoot)) {
        Remove-Item -LiteralPath $resolvedRunRoot -Recurse -Force
    }
}
