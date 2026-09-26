param([Parameter(Mandatory=$true)][string]$ExecutablePath,[Parameter(Mandatory=$true)][string]$ManifestDirectory)
$ErrorActionPreference = 'Stop'
$exe = (Resolve-Path -LiteralPath $ExecutablePath).Path
if ([IO.Path]::GetExtension($exe) -ne '.exe') { throw 'Native Host 必须是已打包 EXE' }
$directory = [IO.Path]::GetFullPath($ManifestDirectory)
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$manifest = Join-Path $directory 'com.aden.collector.dev.json'
$value = @{name='com.aden.collector.dev';description='Aden 商品采集开发通道';path=$exe;type='stdio';allowed_origins=@('chrome-extension://mnejmmlalapfhnanlnckfdhmfpbahidm/')}
[IO.File]::WriteAllText($manifest, ($value | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
$key = 'HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.aden.collector.dev'
if (Test-Path $key) { $existing=(Get-Item $key).GetValue(''); if ($existing -ne $manifest) { throw '已注册另一份开发安装，请先显式卸载或使用原目录' } }
New-Item -Path $key -Force | Out-Null
Set-Item -LiteralPath $key -Value $manifest
Write-Output "已在当前用户注册：$manifest"
