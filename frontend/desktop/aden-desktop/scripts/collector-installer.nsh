!macro customInstall
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INSTDIR\resources\collector\register-native-host.ps1" -ExecutablePath "$INSTDIR\resources\collector\AdenCollectorHost.exe" -ManifestDirectory "$INSTDIR\resources\collector"'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_ICONEXCLAMATION|MB_OK "商品采集扩展连接注册失败。已有注册未被覆盖；请检查安装目录内的注册脚本输出后重试。"
  ${EndIf}
!macroend

!macro customUnInstall
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INSTDIR\resources\collector\unregister-native-host.ps1" -ManifestPath "$INSTDIR\resources\collector\com.aden.collector.dev.json"'
  Pop $0
!macroend
