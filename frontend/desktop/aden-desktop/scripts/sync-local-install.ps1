param(
    [string]$InstallDir = (Join-Path $env:LOCALAPPDATA 'Programs\Aden Local Test')
)

$ErrorActionPreference = 'Stop'
$projectDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installRoot = [System.IO.Path]::GetFullPath($InstallDir).TrimEnd('\')
$allowedRoot = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Programs')).TrimEnd('\') + '\'
if (-not $installRoot.StartsWith($allowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw '同步目标必须位于当前用户的 LocalAppData\Programs 中。'
}

$appDir = Join-Path $installRoot 'resources\app'
$markerPath = Join-Path $appDir 'local-test.json'
$exePath = Join-Path $installRoot 'Aden Local Test.exe'
if (-not (Test-Path -LiteralPath $exePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
    throw "未找到 Aden Local Test 安装目录：$installRoot"
}
$marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
if ($marker.channel -ne 'local-test') {
    throw '安装目录不是 local-test 渠道，已停止同步。'
}

$running = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    try { $_.Path -and $_.Path.Equals($exePath, [System.StringComparison]::OrdinalIgnoreCase) }
    catch { $false }
}
if ($running) {
    throw '请先退出 Aden Local Test，再同步代码。'
}

$previousChannel = $env:ADEN_PACKAGE_CHANNEL
try {
    $env:ADEN_PACKAGE_CHANNEL = 'local-test'
    Push-Location $projectDir
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "桌面构建失败，退出码 $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    $env:ADEN_PACKAGE_CHANNEL = $previousChannel
}

$source = Join-Path $projectDir 'out'
$target = Join-Path $appDir 'out'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path $appDir "out-staging-$stamp"
$backup = Join-Path $appDir "out-backup-$stamp"
if ((Test-Path -LiteralPath $staging) -or (Test-Path -LiteralPath $backup)) {
    throw '同步临时目录已存在，请稍后重试。'
}

New-Item -ItemType Directory -Path $staging | Out-Null
try {
    Copy-Item -Path (Join-Path $source '*') -Destination $staging -Recurse -Force
    foreach ($entry in @('main\index.js', 'preload\index.cjs', 'renderer\index.html')) {
        $built = Join-Path $source $entry
        $copied = Join-Path $staging $entry
        if (-not (Test-Path -LiteralPath $copied -PathType Leaf) -or
            (Get-FileHash -LiteralPath $built).Hash -ne (Get-FileHash -LiteralPath $copied).Hash) {
            throw "同步文件校验失败：$entry"
        }
    }
    Move-Item -LiteralPath $target -Destination $backup
    try {
        Move-Item -LiteralPath $staging -Destination $target
    } catch {
        Move-Item -LiteralPath $backup -Destination $target
        throw
    }
} catch {
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
    throw
}

Write-Host "已同步到 $target"
Write-Host "旧版本保留在 $backup，可在退出程序后手工恢复。"
