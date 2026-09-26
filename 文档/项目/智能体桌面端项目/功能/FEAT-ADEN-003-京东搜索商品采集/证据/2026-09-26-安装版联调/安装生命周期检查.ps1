param([string]$Package,[string]$StorageRoot,[string]$Evidence)
$ErrorActionPreference='Stop'
$installRoot='C:\Users\admin\AppData\Local\Programs\Aden Collection Test'
$resolved=[IO.Path]::GetFullPath($installRoot)
if($resolved -ne 'C:\Users\admin\AppData\Local\Programs\Aden Collection Test'){throw '安装目录边界错误'}
$registry='HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.aden.collector.dev'
$manifest=Join-Path $installRoot 'resources\collector\com.aden.collector.dev.json'
$uninstaller=Join-Path $installRoot 'Uninstall Aden Collection Test.exe'
$oldExe='C:\Users\admin\AppData\Local\Programs\Aden Local Test\Aden Local Test.exe'
if(Get-Process -Name 'Aden Collection Test' -ErrorAction SilentlyContinue){throw '待验收安装版仍在运行'}
$before=@(Get-ChildItem -LiteralPath $StorageRoot -File -Recurse | ForEach-Object { [ordered]@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
if($before.Count -eq 0){throw '缺少真实采集附件，不能证明数据保留'}
$oldHash=(Get-FileHash -LiteralPath $oldExe).Hash
$oldPids=@(Get-Process -Name 'Aden Local Test' | Select-Object -ExpandProperty Id)
$result=[ordered]@{startedAt=(Get-Date).ToString('o');package=$Package;packageSha256=(Get-FileHash -LiteralPath $Package).Hash;installRoot=$resolved;storageFileCount=$before.Count;steps=@();result='InProgress'}
function Save-Evidence { $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Evidence -Encoding utf8 }
function Check-Registration {
  if((Get-ItemPropertyValue -LiteralPath $registry -Name '(default)') -ne $manifest){throw '注册指向非本安装'}
  $hostManifest=Get-Content -LiteralPath $manifest -Raw -Encoding utf8 | ConvertFrom-Json
  if($hostManifest.description -ne 'Aden 商品采集开发通道'){throw ('描述编码错误：'+$hostManifest.description)}
  if($hostManifest.allowed_origins.Count -ne 1 -or $hostManifest.allowed_origins[0] -ne 'chrome-extension://mnejmmlalapfhnanlnckfdhmfpbahidm/'){throw '扩展来源不正确'}
  return $hostManifest
}
try {
  $upgrade=Start-Process -FilePath $Package -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
  if($upgrade.ExitCode -ne 0){throw '升级退出码非0'}
  $registration=Check-Registration
  $result.steps+=@{step='upgrade';exitCode=$upgrade.ExitCode;registryMatches=$true;manifest=$registration}
  Save-Evidence
  $uninstall=Start-Process -FilePath $uninstaller -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
  if($uninstall.ExitCode -ne 0){throw '卸载退出码非0'}
  if(Test-Path -LiteralPath $registry){throw '卸载后仍有本Host注册'}
  if(Test-Path -LiteralPath (Join-Path $installRoot 'Aden Collection Test.exe')){throw '卸载后可执行文件仍存在'}
  foreach($file in $before){if(-not(Test-Path -LiteralPath $file.path) -or (Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256){throw '后端附件被改动'}}
  if((Get-FileHash -LiteralPath $oldExe).Hash -ne $oldHash){throw '其他安装被改动'}
  foreach($oldId in $oldPids){if(-not(Get-Process -Id $oldId -ErrorAction SilentlyContinue)){throw '其他安装进程中断'}}
  $result.steps+=@{step='uninstall';exitCode=$uninstall.ExitCode;registrationRemoved=$true;backendFilesUnchanged=$before.Count;otherInstallationUnchanged=$true;otherProcessesPreserved=$oldPids.Count}
  Save-Evidence
  $install=Start-Process -FilePath $Package -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
  if($install.ExitCode -ne 0){throw '重新安装退出码非0'}
  $registration=Check-Registration
  $result.steps+=@{step='reinstall';exitCode=$install.ExitCode;registryMatches=$true;manifest=$registration;hostSha256=(Get-FileHash -LiteralPath $registration.path).Hash;installedVersion=(Get-Content -LiteralPath (Join-Path $installRoot 'resources\app\package.json') -Raw | ConvertFrom-Json).version}
  $result.result='Pass'
} catch { $result.result='Fail';$result.error=$_.Exception.Message; throw } finally { $result.completedAt=(Get-Date).ToString('o');Save-Evidence }
