import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import ApsReports from '@/views/aps/reports/index.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: ApsReports }]
})

createApp(ApsReports).use(router).use(ElementPlus).mount('#app')
