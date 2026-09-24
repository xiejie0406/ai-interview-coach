/// <reference types="vite/client" />
import type { AdenDesktopApi } from '../../shared/contracts/desktop-api'

declare global {
  interface Window {
    adenDesktop: AdenDesktopApi
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}
