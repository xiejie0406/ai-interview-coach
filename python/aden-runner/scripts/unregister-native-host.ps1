param([Parameter(Mandatory=$true)][string]$ManifestPath)
$ErrorActionPreference='Stop'
$expected=[IO.Path]::GetFullPath($ManifestPath)
$key='HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.aden.collector.dev'
if(Test-Path $key){if((Get-Item $key).GetValue('') -ne $expected){throw '注册指向其他安装，拒绝卸载'};Remove-Item -LiteralPath $key}
Write-Output '已移除当前安装的注册；业务数据和其他安装未删除'
