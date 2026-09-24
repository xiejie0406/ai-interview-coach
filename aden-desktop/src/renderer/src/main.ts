import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useSessionStore } from './stores/session'
import { useConnectionStore } from './stores/connection'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
const session = useSessionStore()
await session.restore()
app.use(router)
useConnectionStore().startListeners()
await router.isReady()
app.mount('#app')
