param(
    [ValidateSet('smoke', 'full')]
    [string]$Profile = 'smoke',
    [int]$Resources = 100,
    [int]$MaxSolveSeconds = 30,
    [int]$Threads = 1,
    [int]$Seed = 20260913,
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'
$counts = if ($Profile -eq 'full') { '1000,5000,20000' } else { '1000' }

if ($JavaHome) {
    $resolvedJavaHome = [System.IO.Path]::GetFullPath($JavaHome)
    $javaExecutable = Join-Path $resolvedJavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable)) { throw "Java executable not found: $javaExecutable" }
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path
}

Write-Output "APS capacity benchmark profile=$Profile counts=$counts resources=$Resources maxSolveSeconds=$MaxSolveSeconds threads=$Threads seed=$Seed javaHome=$env:JAVA_HOME"
mvn -f ruoyi-backend/aps/pom.xml -pl aps-solver-ortools -am `
    '-Dtest=ApsCapacityBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
    '-Daps.benchmark=true' "-Daps.benchmark.counts=$counts" "-Daps.benchmark.resources=$Resources" `
    "-Daps.benchmark.maxSolveSeconds=$MaxSolveSeconds" "-Daps.benchmark.threads=$Threads" `
    "-Daps.benchmark.seed=$Seed" test --no-transfer-progress
if ($LASTEXITCODE -ne 0) { throw "APS capacity benchmark failed with exit code $LASTEXITCODE" }
