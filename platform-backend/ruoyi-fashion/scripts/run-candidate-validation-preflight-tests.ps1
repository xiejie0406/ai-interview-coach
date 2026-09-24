[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$moduleRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$preflightScript = Join-Path $PSScriptRoot 'test-candidate-validation-preflight.ps1'
$templatePath = Join-Path $PSScriptRoot 'candidate-validation-manifest.example.json'
$testRoot = Join-Path $moduleRoot "target/candidate-preflight-test-$([Guid]::NewGuid().ToString('N'))"

function Write-JsonFile {
    param(
        [Parameter(Mandatory)][object]$Value,
        [Parameter(Mandatory)][string]$Path
    )
    [IO.File]::WriteAllText(
        $Path,
        ($Value | ConvertTo-Json -Depth 32),
        [Text.UTF8Encoding]::new($false)
    )
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json -Depth 32 -DateKind String
}

try {
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null

    $incompleteReportPath = Join-Path $testRoot 'incomplete-report.json'
    & $preflightScript -ManifestPath $templatePath -OutputPath $incompleteReportPath -AllowIncomplete
    $incompleteReport = Read-JsonFile -Path $incompleteReportPath
    if ($incompleteReport.ready -ne $false -or @($incompleteReport.gaps).Count -ne 33) {
        throw '未填写模板必须稳定得到 ready=false 和 33 个差距'
    }

    $manifest = Read-JsonFile -Path $templatePath
    $manifest.authorizationStatus = 'approved'
    $manifest.candidateEnvironment.environmentId = 'candidate-01'
    $manifest.candidateEnvironment.owner = 'environment-owner'
    $manifest.candidateEnvironment.javaBaseUrl = 'https://candidate.internal'
    $manifest.candidateEnvironment.pythonRuntimeUrl = 'https://runtime.candidate.internal'
    $manifest.candidateEnvironment.mysqlDatabaseName = 'fashion_candidate'
    $manifest.candidateEnvironment.objectStorageRef = 'config://candidate/object-storage'
    $manifest.candidateEnvironment.monitoringRef = 'config://candidate/monitoring'
    $manifest.candidateEnvironment.backupRef = 'config://candidate/backup'
    $manifest.authorizations.candidateEnvironmentWrite = $true
    $manifest.authorizations.providerCalls = $true
    $manifest.authorizations.testDataUse = $true
    $manifest.authorizations.approvedBy = 'execution-approver'
    $manifest.authorizations.approvedAt = '2026-09-13T10:00:00+08:00'
    $manifest.provider.textProviderCode = 'text-provider'
    $manifest.provider.visualProviderCode = 'visual-provider'
    $manifest.provider.accountRef = 'secret://candidate/provider-account'
    $manifest.provider.secretRef = 'secret://candidate/provider-credential'
    $manifest.provider.dataTermsRef = 'policy://provider-data-terms-v1'
    $manifest.dataset.manifestRef = 'evidence://approved-dataset-v1'
    $manifest.dataset.approvedProductCount = 80
    $manifest.dataset.categoryCounts.top = 20
    $manifest.dataset.categoryCounts.bottom = 20
    $manifest.dataset.categoryCounts.hat = 20
    $manifest.dataset.categoryCounts.shoe = 20
    $manifest.trial.feeCapCny = 100
    $manifest.desktop.application = 'WPS'
    $manifest.desktop.version = '12.1.0'
    $manifest.desktop.downloadPathRef = 'evidence://candidate-download-path'
    $manifest.uat.owner = 'business-owner'
    $manifest.uat.role = '业务负责人'
    $manifest.uat.window = '2026-09-14T09:00:00+08:00/2026-09-14T12:00:00+08:00'
    $manifest.uat.decisionAuthority = $true

    $completeManifestPath = Join-Path $testRoot 'complete-manifest.json'
    $completeReportPath = Join-Path $testRoot 'complete-report.json'
    Write-JsonFile -Value $manifest -Path $completeManifestPath
    & $preflightScript -ManifestPath $completeManifestPath -OutputPath $completeReportPath -AllowIncomplete
    $completeReport = Read-JsonFile -Path $completeReportPath
    if ($completeReport.ready -ne $true -or @($completeReport.gaps).Count -ne 0) {
        throw "完整合成输入必须得到 ready=true 和 0 个差距：$(@($completeReport.gaps) -join '；')"
    }
    $workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $moduleRoot '../..')).Path
    $runtimeRoot = Join-Path $workspaceRoot 'fashion-ai-runtime'
    $pythonCheck = @'
from pathlib import Path
import sys

from fashion_ai.evaluation.candidate_readiness import CandidatePreflightReport

CandidatePreflightReport.model_validate_json(Path(sys.argv[1]).read_text(encoding="utf-8"))
print("PASS: PowerShell preflight report accepted by strict Python model")
'@
    & uv run --project $runtimeRoot --frozen --extra test python -c $pythonCheck $completeReportPath
    if ($LASTEXITCODE -ne 0) {
        throw 'PowerShell 预检报告无法被 Python 最终门严格解析'
    }

    $naiveManifest = Read-JsonFile -Path $completeManifestPath
    $naiveManifest.authorizations.approvedAt = '2026-09-13T10:00:00'
    $naiveManifestPath = Join-Path $testRoot 'naive-time-manifest.json'
    $naiveReportPath = Join-Path $testRoot 'naive-time-report.json'
    Write-JsonFile -Value $naiveManifest -Path $naiveManifestPath
    & $preflightScript -ManifestPath $naiveManifestPath -OutputPath $naiveReportPath -AllowIncomplete
    $naiveReport = Read-JsonFile -Path $naiveReportPath
    if ($naiveReport.ready -ne $false -or
            @($naiveReport.gaps) -notcontains '执行授权时间 必须是带时区的 ISO 8601 时间') {
        throw '无时区执行授权时间必须被稳定拒绝'
    }

    $secretManifest = Read-JsonFile -Path $completeManifestPath
    $secretManifest.provider | Add-Member -NotePropertyName password -NotePropertyValue 'do-not-store'
    $secretManifestPath = Join-Path $testRoot 'secret-manifest.json'
    Write-JsonFile -Value $secretManifest -Path $secretManifestPath
    $secretRejected = $false
    try {
        & $preflightScript -ManifestPath $secretManifestPath -OutputPath (Join-Path $testRoot 'secret-report.json')
    } catch {
        $secretRejected = $_.Exception.Message -match '疑似秘密值字段或私钥'
    }
    if (-not $secretRejected) {
        throw '包含密码字段的候选清单必须在生成报告前拒绝'
    }

    $manifestHashBefore = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($completeManifestPath))
    )
    $overlapRejected = $false
    try {
        & $preflightScript -ManifestPath $completeManifestPath -OutputPath $completeManifestPath
    } catch {
        $overlapRejected = $_.Exception.Message -match '不能覆盖候选环境输入清单'
    }
    $manifestHashAfter = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($completeManifestPath))
    )
    if (-not $overlapRejected -or $manifestHashBefore -ne $manifestHashAfter) {
        throw '输入输出重叠必须在授权清单写入前拒绝'
    }

    $raisedGateManifest = Read-JsonFile -Path $completeManifestPath
    $raisedGateManifest.trial.minimumDeliverableTasksPerCategory = 9
    $raisedGateManifestPath = Join-Path $testRoot 'raised-gate-manifest.json'
    $raisedGateReportPath = Join-Path $testRoot 'raised-gate-report.json'
    Write-JsonFile -Value $raisedGateManifest -Path $raisedGateManifestPath
    & $preflightScript -ManifestPath $raisedGateManifestPath -OutputPath $raisedGateReportPath -AllowIncomplete
    $raisedGateReport = Read-JsonFile -Path $raisedGateReportPath
    if ($raisedGateReport.ready -ne $false -or
            @($raisedGateReport.gaps) -notcontains '各品类可交付门槛必须固定为 PRD 的 8 个任务') {
        throw '视觉质量门槛不是 8/10 时必须拒绝，避免预检与 evaluator 漂移'
    }

    $invalidWindowManifest = Read-JsonFile -Path $completeManifestPath
    $invalidWindowManifest.uat.window = '2026-09-14T12:00:00+08:00/2026-09-14T09:00:00+08:00'
    $invalidWindowManifestPath = Join-Path $testRoot 'invalid-window-manifest.json'
    $invalidWindowReportPath = Join-Path $testRoot 'invalid-window-report.json'
    Write-JsonFile -Value $invalidWindowManifest -Path $invalidWindowManifestPath
    & $preflightScript -ManifestPath $invalidWindowManifestPath -OutputPath $invalidWindowReportPath -AllowIncomplete
    $invalidWindowReport = Read-JsonFile -Path $invalidWindowReportPath
    if ($invalidWindowReport.ready -ne $false -or
            @($invalidWindowReport.gaps) -notcontains 'UAT 执行窗口 必须是带时区且结束晚于开始的 ISO 8601 时间区间') {
        throw 'UAT 时间区间无时区或结束不晚于开始时必须拒绝'
    }

    $invalidFeeManifest = Read-JsonFile -Path $completeManifestPath
    $invalidFeeManifest.trial.feeCapCny = 0.001
    $invalidFeeManifestPath = Join-Path $testRoot 'invalid-fee-manifest.json'
    $invalidFeeReportPath = Join-Path $testRoot 'invalid-fee-report.json'
    Write-JsonFile -Value $invalidFeeManifest -Path $invalidFeeManifestPath
    & $preflightScript -ManifestPath $invalidFeeManifestPath -OutputPath $invalidFeeReportPath -AllowIncomplete
    $invalidFeeReport = Read-JsonFile -Path $invalidFeeReportPath
    if ($invalidFeeReport.ready -ne $false -or
            @($invalidFeeReport.gaps) -notcontains '人民币费用上限必须不少于 0.01 元且最多两位小数') {
        throw '人民币费用上限低于一分或超过两位小数时必须拒绝'
    }

    Write-Output 'PASS: candidate validation preflight 9/9; no network/database/provider action performed'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        $resolvedRoot = (Resolve-Path -LiteralPath $testRoot).Path
        $expectedPrefix = $moduleRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedRoot.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "拒绝清理模块目录外路径：$resolvedRoot"
        }
        Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    }
}
