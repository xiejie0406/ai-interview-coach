<script setup>
import { onShow } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { createInterviewPlan, confirmInterviewPlan, createInterview } from '@/api/interview'
import { currentAccount } from '@/api/session'

const busy=ref(false), error=ref(''), plan=ref(null), etag=ref('')
const roles=['JAVA_BACKEND','AI_APPLICATION','AGENT_ENGINEER'], levels=['JUNIOR','MID','SENIOR'], modes=['TEXT','CASCADE_VOICE']
const form=reactive({targetRole:'JAVA_BACKEND',targetLevel:'MID',topics:['Spring Boot'],durationMinutes:15,mode:'TEXT'})
onShow(async()=>{try{await currentAccount()}catch{uni.navigateTo({url:'/pages/login'})}})
async function submit(){busy.value=true;error.value='';try{const response=await createInterviewPlan(form);plan.value=response.data;etag.value=response.etag||`"v${response.data.version}"`}catch(cause){error.value=cause instanceof Error?cause.message:'计划创建失败'}finally{busy.value=false}}
async function confirmAndStart(){if(!plan.value)return;busy.value=true;error.value='';try{const confirmed=await confirmInterviewPlan(plan.value.id,plan.value.estimatedUsage.estimateVersion,etag.value);const session=await createInterview({confirmedPlanId:confirmed.data.id,planVersionNo:confirmed.data.planVersionNo});uni.redirectTo({url:`/pages/interview/room?interviewId=${encodeURIComponent(session.data.id)}`})}catch(cause){error.value=cause instanceof Error?cause.message:'面试启动失败'}finally{busy.value=false}}
const pick=(list,key,event)=>{form[key]=list[event.detail.value]}
</script>
<template><view class="page"><view class="hero"><text class="eyebrow">AI INTERVIEW</text><text class="title">配置模拟面试</text><text class="description">文字和语音使用同一会话，可随时降级为文字回答。</text></view><view class="card">
  <text class="label">目标岗位</text><picker :range="roles" @change="pick(roles,'targetRole',$event)"><view class="field">{{form.targetRole}}</view></picker>
  <text class="label">目标级别</text><picker :range="levels" @change="pick(levels,'targetLevel',$event)"><view class="field">{{form.targetLevel}}</view></picker>
  <text class="label">面试模式</text><picker :range="modes" @change="pick(modes,'mode',$event)"><view class="field">{{form.mode==='TEXT'?'文字面试':'语音面试'}}</view></picker>
  <button type="primary" :loading="busy" @click="submit">生成面试计划</button><text v-if="error" class="error">{{error}}</text></view>
  <view v-if="plan" class="card"><text class="section-title">计划已生成</text><text>题量：{{plan.questionCount}}</text><text>预计用量：{{plan.estimatedUsage.quantity}} {{plan.estimatedUsage.unit}}</text><button type="primary" :loading="busy" @click="confirmAndStart">确认并进入面试</button></view></view></template>
<style scoped>.page{min-height:100vh;padding:40rpx 28rpx;background:#f5f7fb;color:#18223a}.hero,.card{display:flex;flex-direction:column;gap:22rpx}.hero{padding:36rpx 14rpx}.eyebrow{color:#3157d5;font-size:22rpx;font-weight:700;letter-spacing:4rpx}.title{font-size:52rpx;font-weight:800}.description{color:#74809a;line-height:1.7}.card{margin-bottom:24rpx;padding:34rpx;background:#fff;border-radius:28rpx}.label{font-size:25rpx;font-weight:600}.field{padding:24rpx;border:1px solid #d8deeb;border-radius:16rpx}.section-title{font-size:32rpx;font-weight:700}.error{color:#b42318}</style>
