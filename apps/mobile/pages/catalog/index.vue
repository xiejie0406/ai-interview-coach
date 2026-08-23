<script setup>
import { onMounted, reactive, ref } from 'vue'
import { listQuestions } from '@/api/catalog'

const filters = reactive({ query: '', category: '', difficulty: '' })
const difficulties = ['全部难度', 'JUNIOR', 'MID', 'SENIOR']
const items = ref([])
const loading = ref(false)
const error = ref('')

async function search() {
  loading.value = true; error.value = ''
  try { items.value = (await listQuestions(filters)).items || [] }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '题库加载失败' }
  finally { loading.value = false }
}
function chooseDifficulty(event) { filters.difficulty = event.detail.value === 0 ? '' : difficulties[event.detail.value]; search() }
function open(item) { uni.navigateTo({ url: `/pages/catalog/detail?questionId=${encodeURIComponent(item.id)}` }) }
onMounted(search)
</script>

<template><view class="page"><text class="eyebrow">QUESTION CATALOG</text><text class="title">面试题库</text>
  <view class="filters"><input v-model="filters.query" placeholder="搜索关键词" confirm-type="search" @confirm="search" /><input v-model="filters.category" placeholder="方向，如 JAVA_BACKEND" />
    <picker :range="difficulties" @change="chooseDifficulty"><view class="picker">{{ filters.difficulty || '全部难度' }}</view></picker><button type="primary" size="mini" @click="search">搜索</button></view>
  <text v-if="loading" class="hint">正在加载题库…</text><view v-if="error" class="error"><text>{{ error }}</text><button size="mini" @click="search">重试</button></view>
  <view v-for="item in items" :key="item.id" class="card" @click="open(item)"><text class="badge">{{ item.difficulty }}</text><text class="question-title">{{ item.title }}</text><text class="hint">{{ item.category }} · 查看参考答案 →</text></view>
  <view v-if="!loading && !error && !items.length" class="empty">暂无匹配题目</view></view></template>

<style scoped>.page{min-height:100vh;padding:36rpx 28rpx;background:#f5f7fb;display:flex;flex-direction:column;gap:22rpx}.eyebrow{color:#3157d5;font-size:21rpx;font-weight:800;letter-spacing:3rpx}.title{font-size:48rpx;font-weight:900}.filters,.card,.error{display:flex;flex-direction:column;gap:18rpx;padding:26rpx;background:#fff;border-radius:24rpx}input,.picker{padding:22rpx;border:1px solid #d8deeb;border-radius:14rpx}.badge{align-self:flex-start;padding:6rpx 14rpx;background:#edf2ff;color:#3157d5;border-radius:999rpx}.question-title{font-size:31rpx;font-weight:750}.hint{color:#74809a}.error{color:#b42318}.empty{text-align:center;color:#74809a;padding:80rpx}</style>
