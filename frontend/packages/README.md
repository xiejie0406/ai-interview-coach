# 共享前端契约

这里仅放跨 `admin-web`、`portal-web`、`miniapp/interview-mobile` 的业务契约和无 UI 逻辑。页面组件、若依后台布局和 uni-app 原生适配不得放入共享目录。

当前首批共享内容：AI 面试计划、面试快照、题库摘要、录音能力状态。字段来源为 `contracts/openapi/` 与旧 `frontend/src/features/*/api/` 的对照结果，未经服务端联调前不能视为运行时已验证。
