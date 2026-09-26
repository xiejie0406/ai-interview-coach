import { app, BrowserWindow, dialog } from 'electron'
import { AdenAppHost } from './bootstrap/app-host'

declare const __ADEN_PACKAGE_CHANNEL__: 'local-test' | 'release'

const host = new AdenAppHost()
const singleInstance = app.requestSingleInstanceLock()

if (!singleInstance) {
  app.quit()
} else {
  app.on('second-instance', () => host.activate())
  app.whenReady().then(() => {
    app.setAppUserModelId(__ADEN_PACKAGE_CHANNEL__ === 'local-test'
      ? 'com.aden.desktop.localtest' : 'com.aden.desktop')
    try {
      host.start(__dirname)
    } catch (error) {
      const message = error instanceof Error ? error.message : '未知启动错误'
      process.stderr.write(`Aden 启动失败：${message}\n`)
      if (app.isPackaged) dialog.showErrorBox('Aden 启动失败', message)
      app.quit()
    }

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) host.start(__dirname)
      else host.activate()
    })
  })
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
