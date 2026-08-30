<script setup>
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getQuestion } from '@/api/catalog'
const question = ref(null); const loading = ref(true); const error = ref(''); let questionId = ''
async function load(){ loading.value=true;error.value='';try{question.value=await getQuestion(questionId)}catch(cause){error.value=cause instanceof Error?cause.message:'题目加载失败'}finally{loading.value=false}}
onLoad(options=>{questionId=options?.questionId||'';if(!questionId){error.value='缺少题目 ID';loading.value=false}else load()})
</script>
<template><view class="page"><text v-if="loading" class="hint">正在加载题目…</text><view v-else-if="error" class="error"><text>{{error}}</text><button size="mini" @click="load">重试</button></view><template v-else-if="question"><view class="meta"><text class="badge">{{question.difficulty}}</text><text class="hint">{{question.category}}</text></view><text class="title">{{question.title}}</text><view class="card"><text class="section-title">题目</text><text class="body">{{question.prompt}}</text></view><view class="card"><text class="section-title">参考答案</text><view v-for="(item,index) in question.referenceAnswer" :key="item" class="answer"><text>{{index+1}}.</text><text class="body">{{item}}</text></view></view></template></view></template>
<style scoped>.page{min-height:100vh;padding:38rpx 28rpx;background:#f5f7fb;display:flex;flex-direction:column;gap:24rpx}.meta{display:flex;gap:16rpx;align-items:center}.badge{padding:6rpx 14rpx;background:#edf2ff;color:#3157d5;border-radius:999rpx}.title{font-size:46rpx;font-weight:900}.card,.error{display:flex;flex-direction:column;gap:20rpx;padding:30rpx;background:#fff;border-radius:26rpx}.section-title{font-size:31rpx;font-weight:800}.body{line-height:1.8;color:#34405b}.answer{display:flex;gap:14rpx}.hint{color:#74809a}.error{color:#b42318}</style>
