import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import ApsOrders from '@/views/aps/orders/index.vue'

const app = createApp(ApsOrders)
app.use(ElementPlus)
app.directive('hasPermi', { mounted: () => undefined })
app.mount('#app')
