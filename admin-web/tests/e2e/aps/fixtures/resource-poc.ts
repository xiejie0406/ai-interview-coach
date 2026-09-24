import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import ApsResources from '@/views/aps/resources/index.vue'

const app = createApp(ApsResources)
app.use(ElementPlus)
app.directive('hasPermi', { mounted: () => undefined })
app.mount('#app')
