<script setup>
import { onBeforeUnmount, ref } from 'vue'

const state = ref('IDLE')
const localPath = ref('')
const durationMs = ref(0)
const error = ref('')
const recorder = uni.getRecorderManager()

recorder.onStart(() => { state.value = 'RECORDING' })
recorder.onStop(result => {
  localPath.value = result.tempFilePath
  durationMs.value = result.duration
  state.value = 'RECORDED_LOCAL'
})
recorder.onError(result => {
  error.value = result.errMsg || '录音失败'
  state.value = 'FAILED'
})

function start() {
  error.value = ''
  recorder.start({ duration: 120000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000, format: 'mp3' })
}

function stop() { recorder.stop() }
onBeforeUnmount(() => { if (state.value === 'RECORDING') recorder.stop() })
</script>

<template>
  <view class="record-page">
    <text class="eyebrow">VOICE ANSWER</text><text class="title">录制语音回答</text>
    <view class="orb" :class="{ active: state === 'RECORDING' }"><text>{{ state === 'RECORDING' ? '录音中' : '准备录音' }}</text></view>
    <button v-if="state !== 'RECORDING'" type="primary" @click="start">开始录音</button>
    <button v-else type="warn" @click="stop">停止录音</button>
    <view v-if="localPath" class="result"><text>本地录音已生成</text><text>{{ durationMs }} ms</text><text class="hint">当前不自动上传；待服务端 preflight、授权和上传协议联调后启用。</text></view>
    <text v-if="error" class="error">{{ error }}</text>
  </view>
</template>

<style scoped>
.record-page { min-height: 100vh; display: flex; flex-direction: column; align-items: center; gap: 32rpx; padding: 70rpx 40rpx; background: #101a34; color: white; }
.eyebrow { color: #91a9ff; font-size: 22rpx; letter-spacing: 4rpx; }
.title { font-size: 44rpx; font-weight: 800; }
.orb { width: 320rpx; height: 320rpx; display: flex; align-items: center; justify-content: center; margin: 60rpx 0; border-radius: 50%; background: #25365f; box-shadow: 0 0 0 24rpx rgba(86,119,224,.12); }
.orb.active { background: #d64d5e; animation: pulse 1.4s infinite; }
.result { display: flex; flex-direction: column; gap: 12rpx; padding: 26rpx; color: #18223a; background: white; border-radius: 20rpx; }
.hint { color: #74809a; line-height: 1.6; }
.error { color: #ff9f9f; }
@keyframes pulse { 50% { transform: scale(1.05); box-shadow: 0 0 0 42rpx rgba(214,77,94,.08); } }
</style>
