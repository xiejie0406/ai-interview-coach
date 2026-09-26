import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import ApsWorkbench from '@/views/aps/workbench/index.vue'

createApp(ApsWorkbench).use(ElementPlus).mount('#app')
