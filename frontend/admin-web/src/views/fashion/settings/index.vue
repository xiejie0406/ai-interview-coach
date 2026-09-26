<template>
  <div class="app-container fashion-settings">
    <el-alert
      title="此页只管理非敏感、已注册的业务参数；Provider Secret、人员范围和 Agent 发布配置不在这里维护。"
      type="info"
      :closable="false"
      show-icon
      class="mb20"
    />

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>智能选品设置</span>
          <el-button icon="Refresh" @click="loadSettings">刷新</el-button>
        </div>
      </template>

      <el-form label-position="top">
        <el-row :gutter="24">
          <el-col v-for="setting in settings" :key="setting.key" :xs="24" :md="12">
            <el-form-item :label="setting.name">
              <el-select
                v-if="setting.type === 'DICTIONARY_CODE'"
                v-model="drafts[setting.key]"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="option in dictionaryOptions[setting.dictionaryType ?? ''] ?? []"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="option.status !== '0'"
                />
              </el-select>
              <el-input
                v-else
                v-model="drafts[setting.key]"
                inputmode="decimal"
                :placeholder="setting.defaultValue"
              />
              <div class="setting-help">
                <span>{{ setting.description }}</span>
                <code>{{ setting.key }}</code>
              </div>
              <el-button
                type="primary"
                :loading="savingKey === setting.key"
                :disabled="drafts[setting.key] === setting.value"
                v-hasPermi="['fashion:settings:edit']"
                @click="save(setting)"
              >保存</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDicts } from '@/api/system/dict/data'
import {
  listFashionSettings,
  updateFashionSetting,
  type FashionSetting
} from '@/api/fashion/settings'

interface DictionaryOption {
  label: string
  value: string
  status: string
}

const loading = ref(false)
const savingKey = ref('')
const settings = ref<FashionSetting[]>([])
const drafts = reactive<Record<string, string>>({})
const dictionaryOptions = reactive<Record<string, DictionaryOption[]>>({})
let loadController: AbortController | undefined

async function loadSettings() {
  loadController?.abort()
  loadController = new AbortController()
  loading.value = true
  try {
    const response = await listFashionSettings(loadController.signal)
    settings.value = response.data
    for (const setting of response.data) {
      drafts[setting.key] = setting.value
    }
    const dictionaryTypes = [...new Set(response.data
      .map(setting => setting.dictionaryType)
      .filter((value): value is string => Boolean(value)))]
    await Promise.all(dictionaryTypes.map(loadDictionary))
  } finally {
    loading.value = false
  }
}

async function loadDictionary(dictType: string) {
  const response = await getDicts(dictType)
  dictionaryOptions[dictType] = (response.data ?? []).map((entry: Record<string, unknown>) => ({
    label: String(entry.dictLabel ?? ''),
    value: String(entry.dictValue ?? ''),
    status: String(entry.status ?? '1')
  }))
}

async function save(setting: FashionSetting) {
  savingKey.value = setting.key
  try {
    const response = await updateFashionSetting(setting.key, drafts[setting.key] ?? '')
    setting.value = response.data.afterValue
    drafts[setting.key] = response.data.afterValue
    ElMessage.success('设置已保存并记录变更')
  } finally {
    savingKey.value = ''
  }
}

onMounted(loadSettings)
onBeforeUnmount(() => loadController?.abort())
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.setting-help {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  margin: 6px 0 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.setting-help code {
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}
</style>
