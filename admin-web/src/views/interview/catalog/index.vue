<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import useUserStore from '@/store/modules/user'
import {
  applyInterviewQuestionCommand,
  createInterviewQuestionDraft,
  createInterviewQuestionVersion,
  createInterviewRubricVersion,
  getAdminInterviewQuestion,
  listAdminInterviewQuestions,
  newCatalogIdempotencyKey
} from '@/api/interview/catalog'

const STATUS_META = Object.freeze({
  DRAFT: { label: '草稿', type: 'info' },
  IN_REVIEW: { label: '审核中', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  PUBLISHED_WITH_DRAFT: { label: '已发布·有草稿', type: 'primary' },
  PUBLISHED_WITH_REVIEW: { label: '已发布·待审核', type: 'warning' },
  RETIRED: { label: '已下线', type: 'danger' }
})

const TARGET_ROLE_OPTIONS = [
  { label: 'Java 后端', value: 'JAVA_BACKEND' },
  { label: 'AI 应用', value: 'AI_APPLICATION' },
  { label: 'Agent 工程师', value: 'AGENT_ENGINEER' }
]

const stateOptions = [
  { label: '全部状态', value: '' },
  ...Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))
]

const loading = ref(false)
const items = ref([])
const nextCursor = ref('')
const listError = ref('')
const listErrorCode = ref('')
const query = reactive({ state: '', cursor: '', limit: 20 })
const selectedId = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailEtag = ref('')
const detailError = ref('')
const detailErrorCode = ref('')
const actionLoading = ref('')
const actionError = ref('')
const actionErrorCode = ref('')

const editorOpen = ref(false)
const editorMode = ref('create')
const editorLoading = ref(false)
const editorError = ref('')
const editorIdempotencyKey = ref('')
const editorForm = reactive(defaultEditorForm())

const rubricOpen = ref(false)
const rubricLoading = ref(false)
const rubricError = ref('')
const rubricIdempotencyKey = ref('')
const rubricForm = reactive(defaultRubricForm())

const commandOpen = ref(false)
const commandLoading = ref(false)
const commandError = ref('')
const commandName = ref('')
const commandIdempotencyKey = ref('')
const commandForm = reactive({
  reasonCode: 'CONTENT_REVIEWED',
  questionVersionId: '',
  rubricVersionId: ''
})
const userStore = useUserStore()

const normalizedItems = computed(() => items.value.map(normalizeSummary))
const selectedState = computed(() => normalizeSummary(detail.value?.question).state)
const selectedDraft = computed(() => detail.value?.draft ?? null)
const selectedPublished = computed(() => detail.value?.published ?? null)
const canCreateVersion = computed(() => Boolean(detail.value?.question?.id)
  && ['DRAFT', 'PUBLISHED', 'PUBLISHED_WITH_DRAFT'].includes(selectedState.value))
const canCreateRubric = computed(() => Boolean(detail.value?.question?.id) && Boolean(selectedDraft.value?.id))
const canSubmitReview = computed(() => ['DRAFT', 'PUBLISHED_WITH_DRAFT'].includes(selectedState.value)
  && Boolean(selectedDraft.value))
const canReview = computed(() => ['IN_REVIEW', 'PUBLISHED_WITH_REVIEW'].includes(selectedState.value))
const canPublish = computed(() => canReview.value && Boolean(selectedDraft.value?.id)
  && Boolean(detail.value?.rubric?.id)
  && detail.value.rubric.questionVersionId === selectedDraft.value.id)
const canRetire = computed(() => selectedState.value === 'PUBLISHED')
const canReviewRole = computed(() => {
  // 审核/发布/下线由后端独立的 review 权限门保护；角色名或编辑权限不能
  // 单独推断为可审核，避免按钮显示后再收到 403。
  const permissions = userStore.permissions ?? []
  return permissions.includes('*:*:*') || permissions.includes('interview:question:review')
})
const editorTitle = computed(() => editorMode.value === 'create' ? '新建题目草稿' : '创建题目新版本')
const commandTitle = computed(() => ({
  'submit-review': '提交审核',
  'reject-review': '驳回审核',
  publish: '发布版本',
  retire: '下线题目'
}[commandName.value] ?? '题库工作流操作'))
const commandType = computed(() => commandName.value === 'retire' || commandName.value === 'reject-review' ? 'danger' : 'primary')

function defaultEditorForm() {
  return {
    stableKey: '',
    title: '',
    stem: '',
    answerPointsText: '',
    misconceptionsText: '',
    followUpTemplatesText: '',
    difficulty: 'MID',
    targetRoles: ['AI_APPLICATION'],
    locale: 'zh-CN',
    contentSourceVersionId: ''
  }
}

function defaultRubricForm() {
  return {
    questionVersionId: '',
    refusalPolicy: '无法根据证据判断时，返回 NEEDS_EVIDENCE，不得猜测。',
    dimensionsText: JSON.stringify([
      { code: 'CORRECTNESS', description: '技术正确性', evidenceRequired: true, criteria: ['概念准确', '能够解释关键取舍'] }
    ], null, 2)
  }
}

function resetEditor() {
  Object.assign(editorForm, defaultEditorForm())
  editorError.value = ''
}

function resetRubric() {
  Object.assign(rubricForm, defaultRubricForm())
  rubricError.value = ''
}

function statusLabel(state) {
  return STATUS_META[state]?.label ?? (state || '未知')
}

function statusType(state) {
  return STATUS_META[state]?.type ?? 'info'
}

function normalizeSummary(value) {
  const source = value && typeof value === 'object' ? value : {}
  const state = source.state ?? source.status ?? 'DRAFT'
  return {
    ...source,
    id: source.id ?? source.questionId ?? '',
    stableKey: source.stableKey ?? source.key ?? source.title ?? '未命名题目',
    state,
    version: Number(source.version ?? source.aggregateVersion ?? 0),
    currentDraftVersion: source.currentDraftVersion ?? source.draftVersion ?? null,
    currentPublishedVersion: source.currentPublishedVersion ?? source.publishedVersion ?? null
  }
}

function unwrapBody(response) {
  if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')
    && (Object.prototype.hasOwnProperty.call(response, 'etag')
      || Object.prototype.hasOwnProperty.call(response, 'correlationId'))) {
    return unwrapBody(response.data)
  }
  if (response && typeof response === 'object' && Number(response.code) >= 200 && Number(response.code) < 300
    && response.data !== undefined) {
    return unwrapBody(response.data)
  }
  return response
}

function extractEtag(response, body) {
  const candidate = response?.etag ?? response?.headers?.etag ?? body?.etag
    ?? body?.question?.etag ?? body?.question?.version
  if (candidate === undefined || candidate === null || candidate === '') return ''
  const text = String(candidate).trim()
  if (/^"v\d+"$/.test(text)) return text
  if (/^v\d+$/.test(text)) return `"${text}"`
  if (/^\d+$/.test(text)) return `"v${text}"`
  return text
}

function splitLines(value) {
  return String(value ?? '').split(/\r?\n/).map(item => item.trim()).filter(Boolean)
}

function contentPayload() {
  return {
    title: editorForm.title.trim(),
    stem: editorForm.stem.trim(),
    answerPoints: splitLines(editorForm.answerPointsText),
    misconceptions: splitLines(editorForm.misconceptionsText),
    followUpTemplates: splitLines(editorForm.followUpTemplatesText),
    difficulty: editorForm.difficulty,
    targetRoles: [...editorForm.targetRoles],
    locale: editorForm.locale,
    contentSourceVersionId: editorForm.contentSourceVersionId.trim() || null
  }
}

function validateContent(payload, requireStableKey = false) {
  if (requireStableKey && !editorForm.stableKey.trim()) return 'stableKey 不能为空。'
  if (!payload.title) return '题目标题不能为空。'
  if (!payload.stem) return '题干不能为空。'
  if (!payload.answerPoints.length) return '至少填写一个答案要点。'
  if (payload.answerPoints.length > 30 || payload.misconceptions.length > 30 || payload.followUpTemplates.length > 30) {
    return '答案要点、常见误区和追问模板最多各 30 条。'
  }
  if (!payload.targetRoles.length) return '至少选择一个目标岗位。'
  return ''
}

function displayError(cause, fallback = '题库请求失败') {
  const status = Number(cause?.status ?? 0)
  const code = cause?.code ?? cause?.response?.data?.error?.code ?? ''
  let message = cause instanceof Error ? cause.message : fallback
  if (code === 'CAPABILITY_UNAVAILABLE' || status === 501) message = '题库管理能力尚未在服务端启用，本次没有写入任何数据。'
  else if (status === 401) message = '登录状态已失效，请重新登录后再试。'
  else if (status === 403) message = '当前账号没有题库管理权限（需要 interview:question:list/edit/add/review 中的对应权限）。'
  else if (status === 409 || status === 412 || status === 428) message = '题目版本已发生变化，请刷新详情后重试，系统未自动覆盖他人修改。'
  else if (status >= 500) message = `${fallback}：服务暂时不可用，请稍后重试。`
  const suffix = [code, cause?.correlationId].filter(Boolean).join(' · ')
  return suffix ? `${message}（${suffix}）` : message
}

function responseDetail(response) {
  const body = unwrapBody(response)
  return body && body.question ? body : null
}

function responseResourceId(response) {
  const body = unwrapBody(response)
  return body?.question?.id ?? body?.id ?? body?.questionId ?? null
}

async function load(resetCursor = true) {
  if (loading.value) return
  loading.value = true
  listError.value = ''
  listErrorCode.value = ''
  if (resetCursor) query.cursor = ''
  try {
    const page = unwrapBody(await listAdminInterviewQuestions({
      state: query.state || undefined,
      cursor: query.cursor || undefined,
      limit: query.limit
    }))
    if (!page || !Array.isArray(page.items)) {
      throw Object.assign(new Error('服务端未返回 AdminQuestionPage，未将无效响应显示为空列表。'), { code: 'INVALID_RESPONSE' })
    }
    const incoming = page.items.map(normalizeSummary)
    items.value = resetCursor ? incoming : [...items.value, ...incoming]
    nextCursor.value = page.nextCursor ?? ''
  } catch (cause) {
    listError.value = displayError(cause, '题库列表加载失败')
    listErrorCode.value = cause?.code ?? ''
    if (resetCursor) items.value = []
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (!nextCursor.value || loading.value) return
  query.cursor = nextCursor.value
  void load(false)
}

async function openDetail(row) {
  const id = row?.id ?? row
  if (!id) return
  selectedId.value = String(id)
  detailOpen.value = true
  actionError.value = ''
  return reloadDetail()
}

async function reloadDetail() {
  if (!selectedId.value) return
  detailLoading.value = true
  detailError.value = ''
  detailErrorCode.value = ''
  actionError.value = ''
  try {
    const response = await getAdminInterviewQuestion(selectedId.value)
    const body = responseDetail(response)
    if (!body) throw Object.assign(new Error('服务端未返回 AdminQuestionDetail，未显示猜测数据。'), { code: 'INVALID_RESPONSE' })
    body.question = normalizeSummary(body.question)
    detail.value = body
    detailEtag.value = extractEtag(response, body) || `"v${body.question.version}"`
    return true
  } catch (cause) {
    detail.value = null
    detailError.value = displayError(cause, '题目详情加载失败')
    detailErrorCode.value = cause?.code ?? ''
    return false
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(done) {
  if (actionLoading.value || editorLoading.value || rubricLoading.value || commandLoading.value) return
  if (typeof done === 'function') done()
  else detailOpen.value = false
}

function openCreateDialog() {
  resetEditor()
  editorMode.value = 'create'
  editorIdempotencyKey.value = newCatalogIdempotencyKey()
  editorOpen.value = true
}

function fillEditorFromVersion(version) {
  const source = version ?? {}
  editorForm.title = source.title ?? ''
  editorForm.stem = source.stem ?? ''
  editorForm.answerPointsText = (source.answerPoints ?? []).join('\n')
  editorForm.misconceptionsText = (source.misconceptions ?? []).join('\n')
  editorForm.followUpTemplatesText = (source.followUpTemplates ?? []).join('\n')
  editorForm.difficulty = source.difficulty ?? 'MID'
  editorForm.targetRoles = [...(source.targetRoles ?? ['AI_APPLICATION'])]
  editorForm.locale = source.locale ?? 'zh-CN'
  editorForm.contentSourceVersionId = source.contentSourceVersionId ?? ''
}

function openVersionDialog() {
  if (!detail.value?.question?.id) return
  resetEditor()
  editorMode.value = 'version'
  editorIdempotencyKey.value = newCatalogIdempotencyKey()
  editorForm.stableKey = detail.value.question.stableKey ?? ''
  fillEditorFromVersion(detail.value.draft ?? detail.value.published)
  editorOpen.value = true
}

async function saveEditor() {
  if (editorLoading.value) return
  const payload = contentPayload()
  editorError.value = validateContent(payload, editorMode.value === 'create')
  if (editorError.value) return
  editorLoading.value = true
  try {
    const response = editorMode.value === 'create'
      ? await createInterviewQuestionDraft({ stableKey: editorForm.stableKey.trim(), content: payload }, editorIdempotencyKey.value)
      : await createInterviewQuestionVersion(detail.value.question.id, payload, {
        etag: detailEtag.value || `"v${detail.value.question.version}"`,
        idempotencyKey: editorIdempotencyKey.value
      })
    const id = responseResourceId(response) ?? detail.value?.question?.id
    if (!id) throw Object.assign(new Error('服务端未返回题目 ID，创建结果无法确认。'), { code: 'INVALID_RESPONSE' })
    editorOpen.value = false
    await load(true)
    const confirmed = await openDetail(id)
    if (confirmed) ElMessage.success(editorMode.value === 'create' ? '草稿已创建并回读确认。' : '新版本已创建并回读确认。')
    else ElMessage.warning('写请求已返回，但详情回读失败，请勿重复提交，先刷新确认。')
  } catch (cause) {
    editorError.value = displayError(cause, editorMode.value === 'create' ? '创建草稿失败' : '创建新版本失败')
    if (editorMode.value === 'version' && [409, 412, 428].includes(Number(cause?.status))) {
      editorOpen.value = false
      actionError.value = editorError.value
      await reloadDetail()
      actionError.value = editorError.value
    }
  } finally {
    editorLoading.value = false
  }
}

function openRubricDialog() {
  if (!detail.value?.question?.id) return
  resetRubric()
  rubricIdempotencyKey.value = newCatalogIdempotencyKey()
  rubricForm.questionVersionId = detail.value.draft?.id ?? ''
  rubricOpen.value = true
}

function parseDimensions() {
  let dimensions
  try {
    dimensions = JSON.parse(rubricForm.dimensionsText)
  } catch {
    throw Object.assign(new Error('Rubric 维度必须是合法 JSON 数组。'), { code: 'INVALID_RUBRIC_JSON' })
  }
  if (!Array.isArray(dimensions) || !dimensions.length) throw Object.assign(new Error('至少填写一个 Rubric 维度。'), { code: 'INVALID_RUBRIC_DIMENSIONS' })
  if (dimensions.some(item => !item?.code || !item?.description || !Array.isArray(item.criteria) || !item.criteria.length)) {
    throw Object.assign(new Error('每个 Rubric 维度需要 code、description 和至少一条 criteria。'), { code: 'INVALID_RUBRIC_DIMENSIONS' })
  }
  return dimensions
}

async function saveRubric() {
  if (rubricLoading.value) return
  rubricError.value = ''
  if (!rubricForm.questionVersionId) {
    rubricError.value = '请选择要绑定的题目版本。'
    return
  }
  let dimensions
  try { dimensions = parseDimensions() } catch (cause) {
    rubricError.value = cause.message
    return
  }
  if (!rubricForm.refusalPolicy.trim()) {
    rubricError.value = '拒答策略不能为空。'
    return
  }
  rubricLoading.value = true
  try {
    const response = await createInterviewRubricVersion(detail.value.question.id, {
      questionVersionId: rubricForm.questionVersionId,
      dimensions,
      refusalPolicy: rubricForm.refusalPolicy.trim()
    }, {
      etag: detailEtag.value || `"v${detail.value.question.version}"`,
      idempotencyKey: rubricIdempotencyKey.value
    })
    const body = unwrapBody(response)
    if (!body?.id) throw Object.assign(new Error('服务端未返回 Rubric 版本 ID，创建结果无法确认。'), { code: 'INVALID_RESPONSE' })
    rubricOpen.value = false
    ElMessage.success('Rubric 版本已创建，正在回读详情。')
    await reloadDetail()
  } catch (cause) {
    rubricError.value = displayError(cause, '创建 Rubric 版本失败')
  } finally {
    rubricLoading.value = false
  }
}

function openCommand(command) {
  if (!detail.value?.question?.id) return
  if (['reject-review', 'publish', 'retire'].includes(command) && !canReviewRole.value) {
    actionError.value = '当前 RuoYi 角色不具备审核发布权限；服务端仍会执行最终角色校验。'
    return
  }
  commandName.value = command
  commandIdempotencyKey.value = newCatalogIdempotencyKey()
  commandError.value = ''
  commandForm.reasonCode = command === 'reject-review' ? 'CONTENT_REVIEW_REJECTED' : command === 'retire' ? 'CONTENT_RETIRED' : 'CONTENT_REVIEWED'
  commandForm.questionVersionId = detail.value.draft?.id ?? null
  commandForm.rubricVersionId = detail.value.rubric?.id ?? null
  commandOpen.value = true
}

async function executeCommand() {
  if (commandLoading.value) return
  const reasonCode = commandForm.reasonCode.trim().toUpperCase()
  if (!/^[A-Z][A-Z0-9_]{0,95}$/.test(reasonCode)) {
    commandError.value = '原因码必须匹配 [A-Z][A-Z0-9_]{0,95}。'
    return
  }
  if (!detail.value?.question?.id) return
  commandLoading.value = true
  actionLoading.value = commandName.value
  commandError.value = ''
  actionError.value = ''
  try {
    const response = await applyInterviewQuestionCommand(detail.value.question.id, commandName.value, {
      questionVersionId: commandForm.questionVersionId || null,
      rubricVersionId: commandForm.rubricVersionId || null,
      reasonCode
    }, {
      etag: detailEtag.value || `"v${detail.value.question.version}"`,
      idempotencyKey: commandIdempotencyKey.value
    })
    const body = responseDetail(response)
    commandOpen.value = false
    const beforeState = selectedState.value
    if (body) {
      body.question = normalizeSummary(body.question)
      detail.value = body
      detailEtag.value = extractEtag(response, body) || `"v${body.question.version}"`
    } else {
      // 契约要求返回详情；没有详情时只做回读，不把请求提交当作已生效。
      await reloadDetail()
      if (!detail.value) throw Object.assign(new Error('命令响应缺少详情且状态回读失败，结果未知。'), { code: 'COMMAND_RESULT_UNKNOWN' })
    }
    const expectedState = {
      'submit-review': beforeState === 'PUBLISHED_WITH_DRAFT' ? 'PUBLISHED_WITH_REVIEW' : 'IN_REVIEW',
      'reject-review': beforeState === 'PUBLISHED_WITH_REVIEW' ? 'PUBLISHED_WITH_DRAFT' : 'DRAFT',
      publish: 'PUBLISHED',
      retire: 'RETIRED'
    }[commandName.value]
    if (expectedState && selectedState.value !== expectedState) {
      throw Object.assign(new Error(`状态回读为 ${selectedState.value}，预期 ${expectedState}，操作结果待确认。`), { code: 'COMMAND_RESULT_UNKNOWN' })
    }
    ElMessage.success(`${commandTitle.value}已完成并回读确认。`)
    await load(true)
  } catch (cause) {
    commandError.value = displayError(cause, `${commandTitle.value}失败`)
    actionError.value = commandError.value
    actionErrorCode.value = cause?.code ?? ''
    if ([409, 412, 428].includes(Number(cause?.status))) {
      commandOpen.value = false
      const conflictMessage = actionError.value
      await reloadDetail()
      actionError.value = conflictMessage
    }
  } finally {
    commandLoading.value = false
    actionLoading.value = ''
  }
}

function retryList() {
  void load(true)
}

function resetFilters() {
  query.state = ''
  retryList()
}

function retryDetail() {
  actionError.value = ''
  void reloadDetail()
}

onMounted(() => { void load(true) })
</script>

<template>
  <div class="app-container catalog-admin">
    <div class="catalog-header">
      <div>
        <span class="eyebrow">CATALOG GOVERNANCE</span>
        <h1>面试题库工作台</h1>
        <p>草稿、不可变版本、Rubric 与发布状态统一在若依后台管理；服务端负责权限、幂等和版本冲突裁决。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" icon="Plus" @click="openCreateDialog" v-hasPermi="['interview:question:add']">新建题目草稿</el-button>
        <el-button icon="Refresh" :loading="loading" @click="retryList">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="listError"
      :title="listError"
      :type="listErrorCode === 'CAPABILITY_UNAVAILABLE' ? 'warning' : 'error'"
      show-icon
      :closable="false"
      class="state-alert"
    >
      <template #default>
        <span>未使用本地缓存或示例数据。</span>
        <el-button link type="primary" @click="retryList">重试</el-button>
      </template>
    </el-alert>

    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent="retryList">
        <el-form-item label="工作流状态">
          <el-select v-model="query.state" clearable style="width: 190px" @change="retryList">
            <el-option v-for="option in stateOptions" :key="option.value || 'all'" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每页">
          <el-select v-model="query.limit" style="width: 110px" @change="retryList">
            <el-option :value="20" label="20 条" />
            <el-option :value="50" label="50 条" />
            <el-option :value="100" label="100 条" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" :loading="loading" @click="retryList">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="contract-hint">GET /api/v1/admin/questions · 游标分页 · 仅服务端返回的状态会被展示</div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="normalizedItems"
        row-key="id"
        highlight-current-row
        :row-class-name="({ row }) => row.id === selectedId ? 'selected-row' : ''"
        @row-click="openDetail"
      >
        <el-table-column prop="stableKey" label="稳定标识" min-width="230" show-overflow-tooltip />
        <el-table-column label="状态" width="170">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.state)" effect="light">{{ statusLabel(scope.row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="草稿版本" width="110">
          <template #default="scope">v{{ scope.row.currentDraftVersion?.versionNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="发布版本" width="110">
          <template #default="scope">v{{ scope.row.currentPublishedVersion?.versionNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="聚合版本" width="110">
          <template #default="scope">v{{ scope.row.version }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openDetail(scope.row)">详情</el-button>
            <el-button
              v-if="['DRAFT', 'PUBLISHED_WITH_DRAFT'].includes(scope.row.state)"
              v-hasPermi="['interview:question:edit']"
              link
              type="warning"
              :disabled="actionLoading"
              @click.stop="openDetail(scope.row)"
            >审核</el-button>
            <el-button
              v-if="scope.row.state === 'PUBLISHED' && canReviewRole"
              v-hasPermi="['interview:question:review']"
              link
              type="danger"
              :disabled="actionLoading"
              @click.stop="openDetail(scope.row)"
            >下线</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !normalizedItems.length && !listError" class="empty-state">
        <el-empty description="暂无符合条件的题目">
          <el-button type="primary" @click="openCreateDialog" v-hasPermi="['interview:question:add']">创建第一道草稿</el-button>
        </el-empty>
      </div>
      <div v-if="nextCursor" class="load-more">
        <el-button :loading="loading" @click="loadMore">加载更多</el-button>
      </div>
    </el-card>

    <el-drawer v-model="detailOpen" size="760px" :before-close="closeDetail" destroy-on-close>
      <template #header>
        <div class="drawer-title">
          <span>题目工作流</span>
          <el-tag v-if="detail" :type="statusType(selectedState)">{{ statusLabel(selectedState) }}</el-tag>
        </div>
      </template>

      <div v-if="detailLoading" class="detail-skeleton"><el-skeleton :rows="8" animated /></div>
      <el-alert v-else-if="detailError" :title="detailError" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="retryDetail">重新加载</el-button></template>
      </el-alert>
      <template v-else-if="detail">
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="稳定标识">{{ detail.question.stableKey }}</el-descriptions-item>
          <el-descriptions-item label="聚合版本">v{{ detail.question.version }} <span class="etag">{{ detailEtag || 'ETag 未返回' }}</span></el-descriptions-item>
          <el-descriptions-item label="草稿指针">{{ detail.question.currentDraftVersion?.contentHash ?? '无' }}</el-descriptions-item>
          <el-descriptions-item label="发布指针">{{ detail.question.currentPublishedVersion?.contentHash ?? '无' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" class="state-alert">
          <template #default><el-button link type="primary" @click="retryDetail">刷新版本后重试</el-button></template>
        </el-alert>

        <div class="workflow-actions">
          <el-button v-if="canCreateVersion" icon="Edit" @click="openVersionDialog" v-hasPermi="['interview:question:edit']">新建版本</el-button>
          <el-button v-if="canCreateRubric" icon="DocumentChecked" @click="openRubricDialog" v-hasPermi="['interview:question:edit']">创建 Rubric</el-button>
          <el-button v-if="canSubmitReview" type="warning" :loading="actionLoading === 'submit-review'" @click="openCommand('submit-review')" v-hasPermi="['interview:question:edit']">提交审核</el-button>
          <template v-if="canReview">
            <el-button v-if="canReviewRole" v-hasPermi="['interview:question:review']" type="danger" plain :loading="actionLoading === 'reject-review'" @click="openCommand('reject-review')">驳回审核</el-button>
            <el-button v-if="canReviewRole" v-hasPermi="['interview:question:review']" type="primary" :disabled="!canPublish" :loading="actionLoading === 'publish'" @click="openCommand('publish')">发布版本</el-button>
          </template>
          <el-button v-if="canRetire && canReviewRole" v-hasPermi="['interview:question:review']" type="danger" plain :loading="actionLoading === 'retire'" @click="openCommand('retire')">下线</el-button>
        </div>
        <el-alert v-if="(canReview || canRetire) && !canReviewRole" title="当前账号缺少 interview:question:review；驳回、发布和下线按钮已隐藏，服务端仍会执行最终权限校验。" type="warning" :closable="false" class="state-alert" />
        <el-alert v-else-if="canReview && !canPublish" title="发布前必须同时存在当前草稿和绑定该版本的 Rubric；服务端仍会执行最终质量门禁。" type="warning" :closable="false" class="state-alert" />

        <el-tabs class="version-tabs">
          <el-tab-pane label="草稿版本">
            <template v-if="selectedDraft">
              <div class="version-heading"><el-tag type="warning">DRAFT v{{ selectedDraft.versionNo }}</el-tag><span>{{ selectedDraft.title }}</span></div>
              <p class="stem">{{ selectedDraft.stem }}</p>
              <h4>答案要点（{{ selectedDraft.answerPoints?.length ?? 0 }}）</h4>
              <ul><li v-for="point in selectedDraft.answerPoints" :key="point">{{ point }}</li></ul>
              <h4>常见误区（{{ selectedDraft.misconceptions?.length ?? 0 }}）</h4>
              <ul><li v-for="point in selectedDraft.misconceptions" :key="point">{{ point }}</li></ul>
              <h4>追问模板（{{ selectedDraft.followUpTemplates?.length ?? 0 }}）</h4>
              <ul><li v-for="point in selectedDraft.followUpTemplates" :key="point">{{ point }}</li></ul>
            </template>
            <el-empty v-else description="暂无草稿版本" />
          </el-tab-pane>
          <el-tab-pane label="已发布版本">
            <template v-if="selectedPublished">
              <div class="version-heading"><el-tag type="success">PUBLISHED v{{ selectedPublished.versionNo }}</el-tag><span>{{ selectedPublished.title }}</span></div>
              <p class="stem">{{ selectedPublished.stem }}</p>
              <p class="muted">内容哈希：{{ selectedPublished.contentHash }}</p>
            </template>
            <el-empty v-else description="暂无已发布版本" />
          </el-tab-pane>
          <el-tab-pane label="Rubric">
            <template v-if="detail.rubric">
              <div class="version-heading"><el-tag type="primary">RUBRIC v{{ detail.rubric.versionNo }}</el-tag></div>
              <p class="stem">{{ detail.rubric.refusalPolicy }}</p>
              <div v-for="dimension in detail.rubric.dimensions" :key="dimension.code" class="rubric-item">
                <strong>{{ dimension.code }}</strong><span>{{ dimension.description }}</span><el-tag size="small" :type="dimension.evidenceRequired ? 'success' : 'info'">{{ dimension.evidenceRequired ? '需要证据' : '可选证据' }}</el-tag>
                <ul><li v-for="criterion in dimension.criteria" :key="criterion">{{ criterion }}</li></ul>
              </div>
            </template>
            <el-empty v-else description="暂无 Rubric 版本" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="editorOpen" :title="editorTitle" width="760px" append-to-body destroy-on-close :close-on-click-modal="false">
      <el-alert v-if="editorError" :title="editorError" type="error" show-icon :closable="false" class="state-alert" />
      <el-form :model="editorForm" label-width="126px" class="editor-form">
        <el-form-item v-if="editorMode === 'create'" label="稳定标识" required><el-input v-model="editorForm.stableKey" maxlength="160" show-word-limit placeholder="例如 agent.rag.recall.001" /></el-form-item>
        <el-form-item label="题目标题" required><el-input v-model="editorForm.title" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="题干" required><el-input v-model="editorForm.stem" type="textarea" :rows="4" maxlength="8000" show-word-limit /></el-form-item>
        <el-form-item label="答案要点" required><el-input v-model="editorForm.answerPointsText" type="textarea" :rows="4" placeholder="每行一条，最多 30 条" /></el-form-item>
        <el-form-item label="常见误区"><el-input v-model="editorForm.misconceptionsText" type="textarea" :rows="3" placeholder="每行一条，最多 30 条" /></el-form-item>
        <el-form-item label="追问模板"><el-input v-model="editorForm.followUpTemplatesText" type="textarea" :rows="3" placeholder="每行一条，最多 30 条" /></el-form-item>
        <el-form-item label="难度"><el-select v-model="editorForm.difficulty"><el-option label="初级" value="JUNIOR" /><el-option label="中级" value="MID" /><el-option label="高级" value="SENIOR" /></el-select></el-form-item>
        <el-form-item label="目标岗位" required><el-checkbox-group v-model="editorForm.targetRoles"><el-checkbox v-for="role in TARGET_ROLE_OPTIONS" :key="role.value" :label="role.value">{{ role.label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="内容来源版本"><el-input v-model="editorForm.contentSourceVersionId" placeholder="UUID；发布前必须由服务端验证来源" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorOpen = false">取消</el-button><el-button type="primary" :loading="editorLoading" @click="saveEditor">{{ editorMode === 'create' ? '保存草稿' : '保存新版本' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="rubricOpen" title="创建 Rubric 版本" width="760px" append-to-body destroy-on-close :close-on-click-modal="false">
      <el-alert v-if="rubricError" :title="rubricError" type="error" show-icon :closable="false" class="state-alert" />
      <el-form :model="rubricForm" label-width="126px" class="editor-form">
        <el-form-item label="题目版本" required><el-input v-model="rubricForm.questionVersionId" readonly /></el-form-item>
        <el-form-item label="拒答策略" required><el-input v-model="rubricForm.refusalPolicy" type="textarea" :rows="3" maxlength="3000" show-word-limit /></el-form-item>
        <el-form-item label="维度 JSON" required><el-input v-model="rubricForm.dimensionsText" type="textarea" :rows="12" spellcheck="false" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="rubricOpen = false">取消</el-button><el-button type="primary" :loading="rubricLoading" @click="saveRubric">保存 Rubric</el-button></template>
    </el-dialog>

    <el-dialog v-model="commandOpen" :title="commandTitle" width="520px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="commandError" :title="commandError" type="error" show-icon :closable="false" class="state-alert" />
      <el-alert title="操作会写入审计事实，并使用当前 ETag 防止覆盖并发修改。失败时不会伪造成功状态。" type="warning" :closable="false" class="state-alert" />
      <el-form :model="commandForm" label-width="126px" class="editor-form">
        <el-form-item label="审计原因码" required><el-input v-model="commandForm.reasonCode" maxlength="96" @input="commandForm.reasonCode = commandForm.reasonCode.toUpperCase()" /></el-form-item>
        <el-form-item v-if="commandName === 'publish'" label="题目版本"><el-input v-model="commandForm.questionVersionId" readonly /></el-form-item>
        <el-form-item v-if="commandName === 'publish'" label="Rubric 版本"><el-input v-model="commandForm.rubricVersionId" readonly /></el-form-item>
      </el-form>
      <template #footer><el-button @click="commandOpen = false">取消</el-button><el-button :type="commandType" :loading="commandLoading" @click="executeCommand">确认{{ commandTitle }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.catalog-admin { min-height: calc(100vh - 84px); background: #f5f7fb; }
.catalog-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin: -20px -20px 20px; padding: 26px 30px; color: #fff; background: linear-gradient(125deg, #172448, #3157d5); }
.catalog-header h1 { margin: 8px 0; font-size: 28px; }
.catalog-header p { margin: 0; color: rgba(255, 255, 255, .78); }
.eyebrow { font-size: 11px; letter-spacing: .16em; color: #b8c7ff; }
.header-actions { display: flex; flex-shrink: 0; gap: 10px; }
.filter-card, .table-card { margin-bottom: 18px; border: 0; }
.filter-card :deep(.el-card__body) { padding-bottom: 12px; }
.contract-hint, .muted, .etag { color: #909399; font-size: 12px; }
.state-alert { margin: 12px 0; }
.table-card :deep(.el-table__row) { cursor: pointer; }
.table-card :deep(.selected-row > td) { background: var(--el-color-primary-light-9) !important; }
.empty-state { padding: 36px 0; }
.load-more { padding: 16px 0 4px; text-align: center; }
.drawer-title { display: flex; align-items: center; gap: 10px; font-weight: 600; }
.detail-descriptions { margin-bottom: 16px; }
.workflow-actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; }
.version-tabs { margin-top: 18px; }
.version-heading { display: flex; align-items: center; gap: 10px; margin: 10px 0; font-weight: 600; }
.stem { white-space: pre-wrap; line-height: 1.75; color: #303133; }
.version-tabs h4 { margin: 18px 0 6px; }
.version-tabs ul { padding-left: 20px; line-height: 1.7; }
.rubric-item { padding: 12px 0; border-bottom: 1px solid #ebeef5; }
.rubric-item strong, .rubric-item span { margin-right: 10px; }
.rubric-item ul { margin-bottom: 0; }
.editor-form :deep(.el-textarea__inner) { font-family: inherit; }
.detail-skeleton { padding: 8px; }
@media (max-width: 720px) {
  .catalog-header { align-items: flex-start; flex-direction: column; }
  .header-actions { width: 100%; }
  .header-actions .el-button { flex: 1; }
}
</style>
