# FEAT-QBANK-001 任务包

| TASK | 内容 | 状态 | 验证 |
|---|---|---|---|
| TASK-QBANK-01 | 组合根绑定 `SearchPublishedQuestions`，公开租户配置 | Completed | Maven / HTTP |
| TASK-QBANK-02 | 实现公开列表、标题关键词、难度筛选、详情 DTO | Completed | HTTP / Chrome |
| TASK-QBANK-03 | 添加 Agent 模块种子：12 个模块各 50 道深入版标准答案题 | Completed | 数据库 / HTTP |
| TASK-QBANK-04 | 三栏工作台：可收缩模块栏、可搜索题目列表和右侧标准答案 | Completed | Vite / Chrome |
| TASK-QBANK-05 | Chrome 模块、题目、答案深度、搜索和收缩交互回归 | Completed | UAT |
| TASK-QBANK-06 | 当前工作台新增公共题目弹窗、公共题目写入和公开查询 | Completed | HTTP / Chrome |
| TASK-QBANK-07 | 用户级加密答案覆盖、原地编辑保存和系统答案临时查看 | Completed | 数据库 / Chrome |

种子规模：Agent 基础、大模型基础、Prompt Engineering、RAG、知识库、知识库工作流、工具调用、Agent Memory、多 Agent 协作、Agent 评测、Agent 安全、Agent 工程化共 12 个模块，每模块 10 个知识主题 × 5 个问题角度 = 600 道深入版标准答案题。数据库中旧题保留为历史数据，新的模块入口只展示深入版模块题库。

## 禁止动作

本轮只开放当前题库工作台的公共题目新增和用户答案保存；不启用管理后台写入、不接真实模型、不上传录音、不执行支付、不执行 Git 提交或发布。
