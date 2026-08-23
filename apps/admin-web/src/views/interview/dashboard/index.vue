<script setup>
const modules = [
  { title: '题库与评分规则', description: '管理问题版本、来源、Rubric 和发布状态', route: '/interview-management/catalog', color: '#3157d5' },
  { title: '语音面试', description: '查看语音入口、同意状态、转写降级与 Provider 未就绪提示', route: '/interview-management/voice', color: '#7a4dd8' },
  { title: '评测与报告', description: '查看评测任务、证据、失败原因和报告状态', color: '#d46b20' },
  { title: 'Provider 状态', description: '查看模型、ASR、TTS 和对象存储健康状态', color: '#14866d' }
]
</script>

<template>
  <div class="app-container interview-dashboard">
    <div class="hero-panel">
      <div>
        <span class="eyebrow">AI INTERVIEW OPERATIONS</span>
        <h1>AI 面试教练管理后台</h1>
        <p>若依负责系统管理与后台工作台，AI 面试领域继续通过独立的 `/api/v1/*` 契约接入。</p>
      </div>
      <el-tag type="warning" effect="dark">迁移 Wave 1</el-tag>
    </div>
    <el-row :gutter="20">
      <el-col v-for="item in modules" :key="item.title" :xs="24" :sm="12" :lg="6">
        <el-card class="module-card" shadow="hover" @click="item.route && $router.push(item.route)">
          <div class="module-mark" :style="{ background: item.color }" />
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </el-card>
      </el-col>
    </el-row>
    <el-alert title="当前仅完成后台壳和首批业务入口，尚未执行构建、联调和用户验收。" type="info" show-icon :closable="false" />
  </div>
</template>

<style scoped lang="scss">
.interview-dashboard { background: #f5f7fb; min-height: calc(100vh - 84px); }
.hero-panel { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; padding: 30px; color: white; border-radius: 18px; background: linear-gradient(125deg, #172448, #3157d5); }
.hero-panel h1 { margin: 8px 0; font-size: 30px; }
.hero-panel p { margin: 0; color: rgba(255,255,255,.78); }
.eyebrow { font-size: 11px; letter-spacing: .16em; color: #b8c7ff; }
.module-card { position: relative; min-height: 180px; margin-bottom: 20px; cursor: pointer; border-radius: 16px; }
.module-card h2 { margin-top: 18px; font-size: 18px; }
.module-card p { color: #75809a; line-height: 1.7; }
.module-mark { width: 42px; height: 6px; border-radius: 999px; }
</style>
