<script setup>
  import config from './config'
  import { useConfigStore } from '@/store'
  import { onLaunch } from '@dcloudio/uni-app'

  onLaunch(() => {
    initApp()
  })

  // 初始化应用
  function initApp() {
    // 初始化应用配置
    initConfig()
  }

  function initConfig() {
    useConfigStore().setConfig(config)
  }
</script>

<style lang="scss">
  @import '@/static/scss/index.scss'
</style>
