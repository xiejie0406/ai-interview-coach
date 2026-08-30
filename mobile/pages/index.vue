<template>
  <view class="home"><text class="eyebrow">MINIMUM MVP</text><text class="title">题库与模拟面试</text><text class="intro">网页端与 uni-app 共用同一套题目和面试会话。</text>
    <view class="actions"><button type="primary" @click="uni.switchTab({url:'/pages/catalog/index'})">浏览题库</button><button @click="uni.switchTab({url:'/pages/interview/index'})">开始模拟面试</button><button @click="uni.navigateTo({url:'/pages/interview/index?mode=voice'})">语音面试</button><button @click="uni.navigateTo({url:'/pages/login'})">登录</button></view>
  </view>
</template>
<style scoped>.home{min-height:100vh;display:flex;flex-direction:column;justify-content:center;gap:28rpx;padding:52rpx;background:#f5f7fb}.eyebrow{color:#3157d5;font-size:22rpx;font-weight:800;letter-spacing:4rpx}.title{font-size:58rpx;font-weight:900}.intro{color:#74809a;line-height:1.7}.actions{display:flex;flex-direction:column;gap:20rpx;margin-top:24rpx}</style>
