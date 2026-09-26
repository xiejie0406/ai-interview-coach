import type { AdenDesktopApi } from '../shared/contracts/desktop-api'

declare global {
  interface Window {
    adenDesktop: AdenDesktopApi
  }
}

export {}
