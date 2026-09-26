[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ManifestPath,
    [string]$OutputPath,
    [switch]$AllowIncomplete
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$moduleRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$manifestFile = (Resolve-Path -LiteralPath $ManifestPath).Path
if ((Get-Item -LiteralPath $manifestFile).Length -gt 1MB) {
    throw '候选环境输入清单不得超过 1 MiB'
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $reportDirectory = Join-Path $moduleRoot 'target/candidate-validation-preflight'
    $OutputPath = Join-Path $reportDirectory 'candidate-validation-preflight-report.json'
} else {
    $OutputPath = [IO.Path]::GetFullPath($OutputPath)
    $reportDirectory = Split-Path -Parent $OutputPath
}
if ([string]::Equals(
        [IO.Path]::GetFullPath($OutputPath), $manifestFile,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw '输出报告不能覆盖候选环境输入清单'
}

$raw = Get-Content -Raw -LiteralPath $manifestFile
if ($raw -match '(?i)"(?:api[_-]?key|secret(?:value)?|access[_-]?token|refresh[_-]?token|password|credential|client[_-]?secret|private[_-]?key)"\s*:' -or
        $raw -match '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----') {
    throw '输入清单包含疑似秘密值字段或私钥；这里只允许 secretRef/accountRef 等秘密管理引用'
}
try {
    $manifest = $raw | ConvertFrom-Json -Depth 32 -DateKind String
} catch {
    throw "候选环境输入清单不是合法 JSON：$($_.Exception.Message)"
}

$gaps = [Collections.Generic.List[string]]::new()

function Get-ManifestValue {
    param([Parameter(Mandatory)][string[]]$Path)
    $current = $manifest
    foreach ($segment in $Path) {
        if ($null -eq $current) { return $null }
        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) { return $null }
        $current = $property.Value
    }
    return $current
}

function Add-Gap {
    param([Parameter(Mandatory)][string]$Message)
    if (-not $gaps.Contains($Message)) { [void]$gaps.Add($Message) }
}

function Require-Text {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    $value = Get-ManifestValue -Path $Path
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value) -or
            [string]$value -match 'TO_BE_PROVIDED|example\.invalid') {
        Add-Gap "$Label 未提供"
    }
}

function Require-True {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    if ((Get-ManifestValue -Path $Path) -ne $true) { Add-Gap "$Label 未获明确授权" }
}

function Require-HttpsUrl {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    $value = [string](Get-ManifestValue -Path $Path)
    $uri = $null
    if (-not [Uri]::TryCreate($value, [UriKind]::Absolute, [ref]$uri) -or
            $uri.Scheme -ne 'https' -or $uri.Host -match 'example\.invalid$') {
        Add-Gap "$Label 必须是候选环境 HTTPS URL"
    }
}

function Require-SecretReference {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    $value = [string](Get-ManifestValue -Path $Path)
    if ($value -notmatch '^(?:secret|vault|keyvault|sm)://[A-Za-z0-9][A-Za-z0-9._:/-]{2,255}$' -or
            $value -match 'TO_BE_PROVIDED') {
        Add-Gap "$Label 必须是秘密管理引用，不能填写秘密值"
    }
}

function Convert-AwareTimestamp {
    param([Parameter(Mandatory)][string]$Value)
    $parsed = [DateTimeOffset]::MinValue
    if ($Value -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?(?:Z|[+-]\d{2}:\d{2})$' -or
            -not [DateTimeOffset]::TryParse(
                $Value, [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::RoundtripKind, [ref]$parsed)) {
        return $null
    }
    return $parsed
}

function Require-AwareTimestamp {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    $value = [string](Get-ManifestValue -Path $Path)
    if ($null -eq (Convert-AwareTimestamp -Value $value)) {
        Add-Gap "$Label 必须是带时区的 ISO 8601 时间"
    }
}

function Require-AwareWindow {
    param([Parameter(Mandatory)][string[]]$Path, [Parameter(Mandatory)][string]$Label)
    $value = [string](Get-ManifestValue -Path $Path)
    $parts = @($value -split '/', 3)
    if ($parts.Count -ne 2) {
        Add-Gap "$Label 必须是带时区且结束晚于开始的 ISO 8601 时间区间"
        return
    }
    $start = Convert-AwareTimestamp -Value $parts[0]
    $end = Convert-AwareTimestamp -Value $parts[1]
    if ($null -eq $start -or $null -eq $end -or $end -le $start) {
        Add-Gap "$Label 必须是带时区且结束晚于开始的 ISO 8601 时间区间"
    }
}

function Get-ManifestInt {
    param([Parameter(Mandatory)][string[]]$Path)
    $value = Get-ManifestValue -Path $Path
    if ($null -eq $value) { return 0 }
    $parsed = 0
    if (-not [int]::TryParse([string]$value, [ref]$parsed)) { return 0 }
    return $parsed
}

if ((Get-ManifestValue -Path @('schemaVersion')) -ne '1.0') { Add-Gap 'schemaVersion 必须为 1.0' }
if ((Get-ManifestValue -Path @('authorizationStatus')) -ne 'approved') {
    Add-Gap '候选环境执行包尚未标记为 approved'
}

Require-Text @('candidateEnvironment','environmentId') '候选环境标识'
Require-Text @('candidateEnvironment','owner') '候选环境负责人'
Require-HttpsUrl @('candidateEnvironment','javaBaseUrl') 'Java 服务地址'
Require-HttpsUrl @('candidateEnvironment','pythonRuntimeUrl') 'Python Runtime 地址'
Require-Text @('candidateEnvironment','mysqlDatabaseName') 'MySQL 数据库名'
Require-Text @('candidateEnvironment','objectStorageRef') '对象存储配置引用'
Require-Text @('candidateEnvironment','monitoringRef') '监控配置引用'
Require-Text @('candidateEnvironment','backupRef') '备份位置引用'

Require-True @('authorizations','candidateEnvironmentWrite') '候选环境写入'
Require-True @('authorizations','providerCalls') '限额内 Provider 调用'
Require-True @('authorizations','testDataUse') '获批测试数据使用'
Require-Text @('authorizations','approvedBy') '执行授权人'
Require-AwareTimestamp @('authorizations','approvedAt') '执行授权时间'

Require-Text @('provider','textProviderCode') '文本 Provider'
Require-Text @('provider','visualProviderCode') '视觉 Provider'
Require-SecretReference @('provider','accountRef') 'Provider 账号'
Require-SecretReference @('provider','secretRef') 'Provider 凭据'
Require-Text @('provider','dataTermsRef') 'Provider 数据条款依据'

Require-Text @('dataset','manifestRef') '获批数据集清单引用'
$productCount = Get-ManifestInt @('dataset','approvedProductCount')
if ($productCount -lt 80) { Add-Gap '获批内部商品少于 PRD 要求的 80 个' }
$categoryTotal = 0
foreach ($category in @('top','bottom','hat','shoe')) {
    $count = Get-ManifestInt @('dataset','categoryCounts',$category)
    $categoryTotal += $count
    if ($count -lt 1) { Add-Gap "商品集未覆盖品类 $category" }
}
if ($categoryTotal -ne $productCount) { Add-Gap '四品类商品数之和必须等于 approvedProductCount' }
$coverage = @((Get-ManifestValue -Path @('dataset','coverageTags')))
foreach ($tag in @('solid','stripe','plaid','logo','small_text','dark','light','flat_lay','mannequin')) {
    if ($coverage -notcontains $tag) { Add-Gap "商品集缺少质量覆盖标签 $tag" }
}

if ((Get-ManifestInt @('trial','taskCount')) -ne 40) { Add-Gap '真实视觉试点任务数必须为 40' }
if ((Get-ManifestInt @('trial','tasksPerCategory')) -ne 10) { Add-Gap '每个品类组试点任务数必须为 10' }
if ((Get-ManifestInt @('trial','candidatesPerFirstRound')) -ne 3) { Add-Gap '每任务首轮候选图必须为 3 张' }
if ((Get-ManifestInt @('trial','maxRetryRounds')) -ne 1) { Add-Gap '每任务最多只允许重试 1 轮' }
$qualityGate = Get-ManifestInt @('trial','minimumDeliverableTasksPerCategory')
if ($qualityGate -ne 8) { Add-Gap '各品类可交付门槛必须固定为 PRD 的 8 个任务' }
Require-True @('trial','preserveAllFailures') '失败样本完整保留'
$feeCapValue = Get-ManifestValue -Path @('trial','feeCapCny')
$feeCap = [decimal]0
$feeCapText = if ($null -eq $feeCapValue) {
    ''
} else {
    [Convert]::ToString($feeCapValue, [Globalization.CultureInfo]::InvariantCulture)
}
if ($feeCapText -notmatch '^(?:0|[1-9]\d*)(?:\.\d{1,2})?$' -or
        -not [decimal]::TryParse(
            $feeCapText, [Globalization.NumberStyles]::AllowDecimalPoint,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$feeCap) -or
        $feeCap -lt [decimal]0.01) {
    Add-Gap '人民币费用上限必须不少于 0.01 元且最多两位小数'
}
$stopConditions = @((Get-ManifestValue -Path @('trial','stopConditions')))
foreach ($condition in @(
        'security_violation','fee_cap_reached','unknown_unresolved','category_below_8_of_10',
        'critical_identity_error','four_category_missing_item','recovery_inconsistent','performance_gate_failed')) {
    if ($stopConditions -notcontains $condition) { Add-Gap "停止条件缺少 $condition" }
}

$desktopApplication = [string](Get-ManifestValue -Path @('desktop','application'))
if ($desktopApplication -notmatch '^(?:PowerPoint|WPS)$') { Add-Gap '目标桌面应用必须明确为 PowerPoint 或 WPS' }
Require-Text @('desktop','version') '目标桌面应用版本'
Require-Text @('desktop','downloadPathRef') '候选环境下载链路'
Require-Text @('uat','owner') 'UAT 负责人'
Require-Text @('uat','role') 'UAT 业务角色'
Require-AwareWindow @('uat','window') 'UAT 执行窗口'
Require-True @('uat','decisionAuthority') 'UAT 接受决定权限'

if ((Get-ManifestValue -Path @('release','authorized')) -eq $true) {
    Add-Gap '本执行包不包含发布授权；release.authorized 必须保持 false'
}

$hashBytes = [Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($manifestFile))
$report = [ordered]@{
    schemaVersion = '1.0'
    checkedAt = [DateTime]::UtcNow.ToString('o')
    sourceManifestSha256 = [Convert]::ToHexString($hashBytes).ToLowerInvariant()
    ready = ($gaps.Count -eq 0)
    gaps = @($gaps)
    trialPolicy = [ordered]@{
        feeCapCny = $feeCap.ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
        minimumDeliverableTasksPerCategory = $qualityGate
    }
    safeguards = [ordered]@{
        noNetworkPerformed = $true
        noDatabaseWritePerformed = $true
        noProviderCallPerformed = $true
        secretValuesAccepted = $false
        releaseExcluded = $true
        expectedFashionTableCount = 16
    }
    executionOrder = @(
        '候选环境与授权回读',
        '迁移和精确 16 表核对',
        '容量、权限与恢复验证',
        '40 个真实视觉任务限额试点',
        'PowerPoint/WPS 兼容回归',
        '业务 UAT 与上线就绪判定'
    )
}

New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$temporaryOutput = Join-Path $reportDirectory ".$(Split-Path -Leaf $OutputPath).$([Guid]::NewGuid().ToString('N')).tmp"
try {
    [IO.File]::WriteAllText(
        $temporaryOutput,
        ($report | ConvertTo-Json -Depth 8),
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::Move($temporaryOutput, $OutputPath, $true)
} finally {
    Remove-Item -LiteralPath $temporaryOutput -Force -ErrorAction SilentlyContinue
}
if ($gaps.Count -gt 0) {
    if ($AllowIncomplete) {
        Write-Output "BLOCKED: candidate validation manifest has $($gaps.Count) gap(s); report=$OutputPath"
        return
    }
    throw "候选环境输入预检未通过，共 $($gaps.Count) 项；报告：$OutputPath"
}
Write-Output "PASS: candidate validation manifest is ready; offline preflight only; report=$OutputPath"
