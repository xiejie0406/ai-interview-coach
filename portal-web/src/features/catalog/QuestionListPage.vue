<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiRequest, jsonBody } from '@/shared/api/client'
import { useSessionStore } from '@/stores/session'

type Difficulty = 'JUNIOR' | 'MID' | 'SENIOR'
type QuestionSummary = {
  id: string
  versionId: string
  title: string
  category: string
  difficulty: Difficulty
  estimatedMinutes?: number
}
type QuestionDetail = QuestionSummary & {
  prompt: string
  referenceAnswer: string[]
  systemAnswer: string[]
  hasUserAnswer: boolean
}
type QuestionPage = {
  items: QuestionSummary[]
  hasMore: boolean
  nextCursor?: string | null
}

const modules = [
  ['AGENT_BASICS_DEEP_V3', 'Agent 基础', 'AG'],
  ['LLM_FOUNDATION_DEEP_V3', '大模型基础', 'LLM'],
  ['PROMPT_ENGINEERING_DEEP_V3', 'Prompt Engineering', 'PE'],
  ['RAG_DEEP_V3', 'RAG 检索增强', 'RAG'],
  ['KNOWLEDGE_BASE_DEEP_V3', '知识库', 'KB'],
  ['WORKFLOW_DEEP_V3', '知识库工作流', 'WF'],
  ['TOOL_CALLING_DEEP_V3', '工具调用', 'TOOL'],
  ['MEMORY_DEEP_V3', 'Agent Memory', 'MEM'],
  ['MULTI_AGENT_DEEP_V3', '多 Agent 协作', 'MA'],
  ['EVALUATION_DEEP_V3', 'Agent 评测', 'EVAL'],
  ['AGENT_SECURITY_DEEP_V3', 'Agent 安全', 'SAFE'],
  ['AGENT_ENGINEERING_DEEP_V3', 'Agent 工程化', 'ENG'],
] as const

const difficultyLabels: Record<Difficulty, string> = {
  JUNIOR: '初级',
  MID: '中级',
  SENIOR: '高级',
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const collapsed = ref(false)
const queryDraft = ref('')
const difficultyDraft = ref<Difficulty | ''>('')
const page = ref<QuestionPage>()
const selected = ref<QuestionDetail>()
const listLoading = ref(false)
const detailLoading = ref(false)
const listError = ref('')
const detailError = ref('')
const editingAnswer = ref(false)
const viewingSystemAnswer = ref(false)
const answerDraft = ref('')
const savingAnswer = ref(false)
const answerError = ref('')

const activeCategory = computed(() => stringQuery('category'))
const activeQuery = computed(() => stringQuery('query'))
const activeCursor = computed(() => stringQuery('cursor'))
const selectedQuestionId = computed(() => stringQuery('question'))
const activeDifficulty = computed<Difficulty | ''>(() => {
  const value = stringQuery('difficulty')
  return value === 'JUNIOR' || value === 'MID' || value === 'SENIOR' ? value : ''
})
const activeModule = computed(() => modules.find(([key]) => key === activeCategory.value))

function stringQuery(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function navigate(query: Record<string, string | undefined>) {
  void router.push({ path: '/questions', query })
}

function selectModule(category: string) {
  navigate({ category })
}

function selectQuestion(id: string) {
  editingAnswer.value = false
  viewingSystemAnswer.value = false
  answerError.value = ''
  navigate({
    category: activeCategory.value,
    query: activeQuery.value || undefined,
    difficulty: activeDifficulty.value || undefined,
    cursor: activeCursor.value || undefined,
    question: id,
  })
}

function beginAnswerEdit() {
  if (!selected.value) return
  answerDraft.value = selected.value.referenceAnswer.join('\n\n')
  editingAnswer.value = true
  viewingSystemAnswer.value = false
  answerError.value = ''
}

async function saveAnswer() {
  if (!selected.value || !answerDraft.value.trim()) return
  savingAnswer.value = true
  answerError.value = ''
  try {
    await apiRequest<void>(`/questions/${encodeURIComponent(selected.value.id)}/my-answer`, {
      method: 'PUT',
      body: jsonBody({ answer: answerDraft.value.trim() }),
    })
    editingAnswer.value = false
    viewingSystemAnswer.value = false
    await loadDetail()
  } catch (cause) {
    answerError.value = cause instanceof Error ? cause.message : '答案保存失败'
  } finally {
    savingAnswer.value = false
  }
}

function applyFilters() {
  if (!activeCategory.value) return
  navigate({
    category: activeCategory.value,
    query: queryDraft.value.trim() || undefined,
    difficulty: difficultyDraft.value || undefined,
  })
}

function goFirstPage() {
  navigate({
    category: activeCategory.value,
    query: activeQuery.value || undefined,
    difficulty: activeDifficulty.value || undefined,
  })
}

function goNextPage() {
  if (!page.value?.nextCursor) return
  navigate({
    category: activeCategory.value,
    query: activeQuery.value || undefined,
    difficulty: activeDifficulty.value || undefined,
    cursor: page.value.nextCursor,
  })
}

async function loadQuestions() {
  if (!activeModule.value) {
    page.value = undefined
    return
  }
  listLoading.value = true
  listError.value = ''
  try {
    const params = new URLSearchParams({ category: activeCategory.value, limit: '20' })
    if (activeQuery.value) params.set('query', activeQuery.value)
    if (activeDifficulty.value) params.set('difficulty', activeDifficulty.value)
    if (activeCursor.value) params.set('cursor', activeCursor.value)
    page.value = await apiRequest<QuestionPage>(`/questions?${params.toString()}`)
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : '题库加载失败'
  } finally {
    listLoading.value = false
  }
}

async function loadDetail() {
  if (!selectedQuestionId.value) {
    selected.value = undefined
    return
  }
  detailLoading.value = true
  detailError.value = ''
  try {
    selected.value = await apiRequest<QuestionDetail>(`/questions/${encodeURIComponent(selectedQuestionId.value)}`)
  } catch (cause) {
    detailError.value = cause instanceof Error ? cause.message : '题目加载失败'
  } finally {
    detailLoading.value = false
  }
}

function answerSections(answer: string[]) {
  return answer
    .flatMap((item) => item.split(/(?=【[^】]+】)/))
    .map((item) => {
      const match = item.match(/^【([^】]+)】\s*([\s\S]*)$/)
      return match ? { title: match[1], body: match[2].trim() } : { title: '', body: item.trim() }
    })
    .filter((item) => item.body)
}

watch([activeCategory, activeQuery, activeDifficulty, activeCursor], () => {
  queryDraft.value = activeQuery.value
  difficultyDraft.value = activeDifficulty.value
  void loadQuestions()
}, { immediate: true })

watch(selectedQuestionId, () => void loadDetail(), { immediate: true })
</script>

<template>
  <section class="qbank-workbench" :class="{ 'modules-collapsed': collapsed }" aria-label="面试题库学习工作台">
    <aside class="qbank-modules" aria-label="题库模块">
      <div class="qbank-column-header">
        <span>模块 · {{ modules.length }}</span>
        <button class="qbank-quiet-button" type="button" :aria-expanded="!collapsed" @click="collapsed = !collapsed">
          {{ collapsed ? '展开' : '收缩' }}
        </button>
      </div>
      <button
        v-for="[key, label, abbreviation] in modules"
        :key="key"
        class="qbank-module"
        :class="{ active: activeCategory === key }"
        type="button"
        :title="label"
        :aria-current="activeCategory === key ? 'page' : undefined"
        @click="selectModule(key)"
      >
        <b>{{ abbreviation }}</b>
        <span>{{ label }}</span>
        <small>50 题</small>
      </button>
    </aside>

    <section class="qbank-questions" aria-label="模块题目">
      <div class="qbank-column-header">
        <span>{{ activeModule ? `${activeModule[1]} · 50 题` : '请选择一个模块' }}</span>
      </div>
      <form v-if="activeModule" class="qbank-search" @submit.prevent="applyFilters">
        <label class="sr-only" for="qbank-search-input">搜索当前模块题目</label>
        <input id="qbank-search-input" v-model="queryDraft" placeholder="搜索当前模块题目" />
        <label class="sr-only" for="qbank-difficulty">筛选难度</label>
        <select id="qbank-difficulty" v-model="difficultyDraft" @change="applyFilters">
          <option value="">全部难度</option>
          <option value="JUNIOR">初级</option>
          <option value="MID">中级</option>
          <option value="SENIOR">高级</option>
        </select>
        <button class="sr-only" type="submit">应用筛选</button>
      </form>

      <p v-if="!activeModule" class="qbank-empty">从左侧选择一个模块，开始浏览 50 道深入题目。</p>
      <p v-else-if="listLoading" class="qbank-state" aria-live="polite">正在加载题目…</p>
      <div v-else-if="listError" class="qbank-error" role="alert">
        {{ listError }} <button type="button" @click="loadQuestions">重试</button>
      </div>
      <p v-else-if="!page?.items.length" class="qbank-empty">没有符合当前条件的题目，请调整筛选。</p>
      <template v-else>
        <div class="qbank-question-list">
          <button
            v-for="question in page.items"
            :key="question.versionId"
            class="qbank-question"
            :class="{ active: selectedQuestionId === question.id }"
            type="button"
            :aria-pressed="selectedQuestionId === question.id"
            @click="selectQuestion(question.id)"
          >
            <span>{{ question.title }}</span>
            <small>{{ difficultyLabels[question.difficulty] }}</small>
          </button>
        </div>
        <div class="qbank-pagination">
          <button v-if="activeCursor" class="qbank-quiet-button" type="button" @click="goFirstPage">返回首批</button>
          <button v-if="page.hasMore" class="qbank-primary-button" type="button" @click="goNextPage">下一批题目</button>
        </div>
      </template>
    </section>

    <article class="qbank-answer" aria-live="polite">
      <div v-if="!selectedQuestionId" class="qbank-answer-empty">
        <span class="eyebrow">STANDARD ANSWER</span>
        <h1>选择一道题目</h1>
        <p>点击中间的题目，在这里连续阅读题干和完整标准答案。</p>
      </div>
      <p v-else-if="detailLoading" class="qbank-state">正在加载完整答案…</p>
      <div v-else-if="detailError" class="qbank-error" role="alert">
        {{ detailError }} <button type="button" @click="loadDetail">重试</button>
      </div>
      <template v-else-if="selected">
        <header class="qbank-answer-heading">
          <div>
            <span>{{ activeModule?.[1] ?? selected.category }} · {{ difficultyLabels[selected.difficulty] }}</span>
            <h1>{{ selected.title }}</h1>
          </div>
          <div v-if="session.account" class="qbank-answer-actions">
            <button v-if="!editingAnswer" class="qbank-quiet-button" type="button" @click="beginAnswerEdit">编辑答案</button>
            <button
              v-if="!editingAnswer && selected.hasUserAnswer"
              class="qbank-quiet-button"
              type="button"
              @click="viewingSystemAnswer = !viewingSystemAnswer"
            >{{ viewingSystemAnswer ? '返回我的答案' : '查看系统答案' }}</button>
          </div>
        </header>
        <p class="qbank-prompt">{{ selected.prompt }}</p>
        <section v-if="editingAnswer" class="qbank-answer-editor" aria-label="编辑个人答案">
          <label class="sr-only" for="personal-answer">个人答案</label>
          <textarea id="personal-answer" v-model="answerDraft" rows="18" />
          <p v-if="answerError" class="qbank-error" role="alert">{{ answerError }}</p>
          <div class="qbank-form-actions">
            <button class="qbank-quiet-button" type="button" @click="editingAnswer = false">取消</button>
            <button class="qbank-primary-button" type="button" :disabled="savingAnswer || !answerDraft.trim()" @click="saveAnswer">
              {{ savingAnswer ? '保存中…' : '保存答案' }}
            </button>
          </div>
        </section>
        <section v-else class="qbank-answer-content" aria-label="答案正文">
          <template v-for="section in answerSections(viewingSystemAnswer ? selected.systemAnswer : selected.referenceAnswer)" :key="`${section.title}-${section.body}`">
            <h2 v-if="section.title">{{ section.title }}</h2>
            <p>{{ section.body }}</p>
          </template>
        </section>
      </template>
    </article>
  </section>
</template>
