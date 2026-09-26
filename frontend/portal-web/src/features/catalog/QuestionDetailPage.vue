<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiRequest } from '@/shared/api/client'

type QuestionDetail = {
  id: string
  title: string
  category: string
  difficulty: string
  prompt: string
  referenceAnswer: string[]
}

const route = useRoute()
const question = ref<QuestionDetail | null>(null)
const loading = ref(false)
const error = ref('')
const answer = computed(() => question.value?.referenceAnswer ?? [])

async function load() {
  loading.value = true
  error.value = ''
  try {
    question.value = await apiRequest<QuestionDetail>(`/questions/${encodeURIComponent(String(route.params.questionId))}`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '题目加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-width section-page question-detail">
    <RouterLink class="back-link" to="/questions">← 返回题库</RouterLink>
    <p v-if="loading" class="muted">正在加载题目…</p>
    <div v-else-if="error" class="error-message" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></div>
    <template v-else-if="question">
      <div class="question-heading"><span class="badge">{{ question.difficulty }}</span><span class="muted">{{ question.category }}</span></div>
      <h1>{{ question.title }}</h1>
      <article class="content-card"><h2>题目</h2><p class="question-prompt">{{ question.prompt }}</p></article>
      <article class="content-card answer-card"><h2>参考答案</h2><ol><li v-for="item in answer" :key="item">{{ item }}</li></ol></article>
    </template>
  </section>
</template>
