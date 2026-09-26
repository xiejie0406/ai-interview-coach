<script setup lang="ts">
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'

import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type {
  ApsAdjustmentIntent,
  ApsGanttModel
} from '@/types/aps/planning'
import {
  createDhtmlxGanttAdapter,
  type ApsGanttAdapter,
  type ApsGanttAdapterFactory
} from '@/components/aps/gantt/adapter'

const props = defineProps<{
  model: ApsGanttModel
  adapterFactory?: ApsGanttAdapterFactory
}>()

const emit = defineEmits<{
  intent: [intent: ApsAdjustmentIntent]
}>()

const host = ref<HTMLElement>()
let adapter: ApsGanttAdapter | undefined

onMounted(() => {
  if (!host.value) {
    throw new Error('DHTMLX Gantt 容器未挂载')
  }
  const factory = props.adapterFactory ?? createDhtmlxGanttAdapter
  adapter = factory(host.value, (intent) => emit('intent', intent))
  adapter.render(props.model)
})

watch(
  () => props.model,
  (model) => adapter?.render(model)
)

onBeforeUnmount(() => {
  adapter?.dispose()
  adapter = undefined
})

defineExpose({
  locate: (taskId: string) => adapter?.locate(taskId) ?? false,
  zoomIn: () => adapter?.zoomIn(),
  zoomOut: () => adapter?.zoomOut()
})
</script>

<template>
  <div ref="host" class="aps-gantt-host" />
</template>

<style scoped>
.aps-gantt-host {
  /* DHTMLX v10 默认主题声明了远程 Inter 字体；APS 主机只使用本地系统字体。 */
  --dhx-gantt-font-family: system-ui, -apple-system, "Segoe UI", Arial, sans-serif;
  min-height: 20rem;
  width: 100%;
}
</style>
