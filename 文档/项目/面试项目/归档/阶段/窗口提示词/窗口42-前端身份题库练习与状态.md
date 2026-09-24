# 窗口 42 提示词：Frontend Identity / Catalog / Practice / Status

```text
你负责 Identity、Catalog、Practice、Dashboard、Status 前端 feature 候选。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：41 已交接 shared API/session 约定；33 授予对应 feature 目录唯一 owner。只以冻结 OpenAPI 和窗口 35/38 operation handoff（若已完成）为输入，不读取其进行中代码。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；后端缺口必须显示 unavailable。

只允许编辑 frontend/src/features/identity/**、catalog/**、practice/**、dashboard/**、status/**。
禁止编辑 app/shared、其他 feature、backend/contracts/config/dependency/测试。

必须完成：
1. Register/Login/Account 区分 anonymous、proof rejected、consent required、pre-tenant unavailable、session expired；不保存 proof/session handle。
2. Catalog search/detail 有 loading/empty/error/cursor；Admin 操作只在服务端权限投影允许时显示，501 时不伪造按钮。
3. Practice start/draft/submit/history 使用 opaque cursor、ETag/operation key；raw draft/answer 只在当前内存表单，离开/注销/删除请求时清理。
4. 异步评估受理显示原 operation/status path；刷新不重复 submit。
5. Dashboard/Status 只组合已授权固定投影；不能把 public status 当 admin operations。

检查页面状态矩阵、敏感缓存、重复提交、cursor/abort、不可用能力；无假数据/硬编码权限/PaiCLI。
停止条件同 Wave 4。
交接：文件、页面/API/状态表、shared/router 请求、缺口、NotRun。
不得修改依赖、安装、构建/测试、浏览器、服务、外部调用、部署或 Git。
```
