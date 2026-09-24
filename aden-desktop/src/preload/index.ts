import { contextBridge } from 'electron'
import { createPreloadApi } from './api'

const runtime = Object.freeze({
  platform: process.platform,
  versions: Object.freeze({
    electron: process.versions.electron,
    chrome: process.versions.chrome
  })
})

contextBridge.exposeInMainWorld('adenDesktop', createPreloadApi(runtime))
