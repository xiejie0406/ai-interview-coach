# AI Interview Coach 移动端迁移说明

本目录基于官方 `RuoYi-App` 的 `vue3` 分支创建。`../ruoyi-app/` 是只读上游基线。

当前已增加面试配置、会话恢复和本地录音页面，并移除上游演示微信 AppID。录音只保存在本地，不会自动上传；服务端 preflight、授权、音频上传、ASR 和转写确认尚未联调。
