# 若依迁移 Wave 01：三端工程与用户前台骨架

> 文档类型：开发记录
> 文档状态：Draft
> 风险等级：L3
> 当前阶段：阶段 7 开发实现 / InProgress
> 证据结果：NotRun（未执行依赖安装、构建、服务启动或联调）

## 已完成

- 基于完整 RuoYi-Vue3 上游建立 `apps/admin-web/`；
- 基于完整 RuoYi-App `vue3` 分支建立 `apps/mobile/`；
- 建立独立 `apps/portal-web/` 用户前台 Vue 3 工程；
- 建立 `packages/interview-contracts/` 共享契约基线；
- 后台增加 AI 面试总览、题库入口和独立 `/api/v1/*` 请求客户端；
- 前台增加首页、登录、题库、面试配置、面试会话和学习中心入口；
- 移动端增加面试配置、会话恢复和本地录音适配骨架；
- 清空移动端上游演示微信 AppID，避免误连接示例资源。

## 未执行

- 未安装 npm/pnpm 依赖；
- 未执行 `vue-tsc`、`vite build`、uni-app 编译或 Maven 构建；
- 未启动后端或前端服务；
- 未调用真实 API、录音、ASR、TTS、Provider 或小程序开发者工具；
- 未修改现有 `backend/`、数据库、Flyway 或 `frontend/`；
- 未进行用户验收、发布或 Git 提交。

## 下一波入口

1. 先完成三个前端工程的依赖和静态检查授权；
2. 对照 `contracts/openapi/` 修正 `/api/v1` 请求响应；
3. 建立 portal/mobile 的 Cookie Session 或小程序 session exchange 方案；
4. 迁移录音 preflight、MediaRecorder、RecorderManager、WebSocket 和 ASR 转写确认；
5. 再开始后台题库 CRUD 与报告审核页面。
