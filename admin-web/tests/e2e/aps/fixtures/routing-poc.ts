import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import ApsRoutes from '@/views/aps/routes/index.vue'

const app = createApp(ApsRoutes)
app.use(ElementPlus)
app.directive('hasPermi', { mounted: () => undefined })
app.mount('#app')
