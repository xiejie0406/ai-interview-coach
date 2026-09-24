<script setup lang="ts">
import 'vis-timeline/styles/vis-timeline-graph2d.min.css'

import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type {
  ApsAdjustmentIntent,
  ApsTimelineModel
} from '@/types/aps/planning'
import {
  createVisTimelineAdapter,
  type ApsTimelineAdapter,
  type ApsTimelineAdapterFactory
} from '@/components/aps/timeline/adapter'

const props = defineProps<{
  model: ApsTimelineModel
  adapterFactory?: ApsTimelineAdapterFactory
}>()

const emit = defineEmits<{
  intent: [intent: ApsAdjustmentIntent]
}>()

const host = ref<HTMLElement>()
let adapter: ApsTimelineAdapter | undefined

onMounted(() => {
  if (!host.value) {
    throw new Error('vis-timeline 容器未挂载')
  }
  const factory = props.adapterFactory ?? createVisTimelineAdapter
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
  locate: (targetId: string) => adapter?.locate(targetId) ?? false,
  zoomIn: () => adapter?.zoomIn(),
  zoomOut: () => adapter?.zoomOut()
})
</script>

<template>
  <div ref="host" class="aps-timeline-host" />
</template>

<style scoped>
.aps-timeline-host {
  min-height: 20rem;
  width: 100%;
}
</style>
