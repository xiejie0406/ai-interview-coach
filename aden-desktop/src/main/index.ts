import { app, BrowserWindow } from 'electron'
import { AdenAppHost } from './bootstrap/app-host'

const host = new AdenAppHost()
const singleInstance = app.requestSingleInstanceLock()

if (!singleInstance) {
  app.quit()
} else {
  app.on('second-instance', () => host.activate())
  app.whenReady().then(() => {
    app.setAppUserModelId('com.aden.desktop')
    try {
      host.start(__dirname)
    } catch (error) {
      const message = error instanceof Error ? error.message : '未知启动错误'
      process.stderr.write(`Aden 启动失败：${message}\n`)
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
