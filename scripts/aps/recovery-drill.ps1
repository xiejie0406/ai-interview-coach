param(
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
)

$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'run-local-mysql-migration.ps1'
& $runner -MySqlHome $MySqlHome
if ($LASTEXITCODE -ne 0) {
    throw "APS local migration and recovery drill failed with exit code $LASTEXITCODE"
}
