<script setup>
import { listInterviewQuestions } from '@/api/interview/catalog'

const loading = ref(false)
const items = ref([])
const error = ref('')
const query = reactive({ query: '', category: '', difficulty: '', limit: 20 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await listInterviewQuestions(query)
    items.value = page.items ?? []
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '题库加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="app-container">
    <el-form :model="query" inline @submit.prevent="load">
      <el-form-item label="关键词"><el-input v-model="query.query" placeholder="标题或关键词" clearable /></el-form-item>
      <el-form-item label="难度"><el-select v-model="query.difficulty" clearable style="width: 140px"><el-option label="初级" value="JUNIOR" /><el-option label="中级" value="MID" /><el-option label="高级" value="SENIOR" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb20" />
    <el-table v-loading="loading" :data="items">
      <el-table-column prop="title" label="题目" min-width="280" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="160" />
      <el-table-column prop="difficulty" label="难度" width="100" />
      <el-table-column prop="status" label="状态" width="110"><template #default="scope"><el-tag type="success">{{ scope.row.status }}</el-tag></template></el-table-column>
      <el-table-column prop="version" label="版本" width="90" />
    </el-table>
  </div>
</template>
