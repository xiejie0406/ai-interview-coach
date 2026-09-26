import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import ApsExecution from '@/views/aps/execution/index.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: ApsExecution }]
})

createApp(ApsExecution).use(router).use(ElementPlus).mount('#app')
