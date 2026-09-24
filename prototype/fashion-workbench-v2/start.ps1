$ErrorActionPreference = 'Stop'
$fashionNode = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $fashionNode) {
    $fashionBundledNode = 'C:\Users\admin\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    if (Test-Path -LiteralPath $fashionBundledNode) { $fashionNode = $fashionBundledNode }
}
if (-not $fashionNode) { throw '未找到 Node.js。请在有 Node.js 的电脑执行 node serve.cjs。' }
& $fashionNode (Join-Path $PSScriptRoot 'serve.cjs')
