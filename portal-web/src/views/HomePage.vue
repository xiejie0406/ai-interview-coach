<template>
  <section class="hero page-width">
    <div class="hero-copy">
      <span class="eyebrow">AI INTERVIEW COACH</span>
      <h1>题库学习，文字与语音模拟面试</h1>
      <p>最小 MVP 只专注两件事：快速查题，以及完整完成一场文字或语音面试。</p>
      <div class="actions">
        <RouterLink class="primary-action" to="/interviews/new">开始模拟面试</RouterLink>
        <RouterLink class="primary-action" to="/interviews/new?mode=voice">语音面试</RouterLink>
        <RouterLink class="secondary-action" to="/questions">浏览题库</RouterLink>
      </div>
    </div>
    <div class="hero-card">
      <strong>最小使用闭环</strong>
      <ol>
        <li>选择目标岗位与难度</li>
        <li>完成文字或语音面试</li>
        <li>语音失败时无损切换文字</li>
        <li>暂停、恢复或正常结束会话</li>
      </ol>
    </div>
  </section>
</template>
