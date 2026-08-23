# AI Interview Coach Portal Web

用户前台网页，独立于若依后台管理端。当前为迁移 Wave 1 骨架，承接原 React `frontend/` 的用户侧页面和 `/api/v1/*` 契约。

## 当前已落地

- Vue 3 + TypeScript + Vite + Pinia + Vue Router；
- 同源 Cookie Session 请求层；
- 登录、题库、面试配置、面试会话和学习中心路由；
- 语音面试迁移入口，尚未接入真实录音和 WebSocket。

## 事实边界

依赖未安装、构建未执行、服务未启动、API 未联调。当前代码是迁移实现，不代表验证、验收或发布完成。
