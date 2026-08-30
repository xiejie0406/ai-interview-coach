<template>
  <div class="app-container">
    <el-alert title="语音面试使用 RuoYi 当前登录主体和权限。" type="info" :closable="false" show-icon />
    <el-card class="voice-card" shadow="never">
      <h2>语音面试迁移状态</h2>
      <p>入口已接入统一 RuoYi 后端；PostgreSQL 音频持久化、ASR/TTS Provider 和 WebSocket 音频编排尚未就绪。</p>
      <el-tag type="warning">VOICE_NOT_READY</el-tag>
      <p class="muted">用户端在未就绪、无权限、麦克风拒绝或断线时应恢复到文字回答。</p>
    </el-card>
  </div>
</template>

<style scoped>
.voice-card { margin-top: 20px; max-width: 720px; }
.muted { color: #909399; }
</style>
