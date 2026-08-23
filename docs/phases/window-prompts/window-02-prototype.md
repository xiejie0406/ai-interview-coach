# 窗口 02 提示词：探索性原型与状态矩阵

```text
你负责 AI Interview Coach 阶段 4 的探索性原型文档。当前 PRD 未批准，因此只能产出 Draft 探索原型，不能宣布评审通过。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

完整读取：项目/用户 AGENTS.md、docs/specs/README.md、docs/product/prd.md、docs/review/canonical-product-recovery-draft.md、docs/phases/README.md、docs/phases/implementation-readiness.md、docs/phases/phase-03 至 phase-15 的相关页面/状态描述，以及 product-spec、feature-spec、documentation-spec。

独占输出目录：
docs/prototypes/FEAT-INTERVIEW-001/

至少创建：
- prototype.md：用户旅程、页面地图、交互决定、评审入口
- state-matrix.md：每页默认/已有、空/加载、校验/系统/外部失败、无权限/拒绝/取消、超时/重复/恢复/完成
- wireframes.md：用文本/Mermaid 表达关键页面，不创建生产前端

必须覆盖：
公开首页/FAQ、登录/首次引导、题库/单题练习、面试配置/计划确认、文本面试、麦克风拒绝/ASR修正/TTS失败与文本降级、报告/复练、额度不足、隐私删除部分失败、管理员发布和敏感访问拒绝。

规则：
- 截断区域用“待 Canonical PRD 恢复确认”标记，不补写成事实。
- 同时展示正常、错误、拒绝、取消和恢复；不能只画 Golden Case。
- 原型状态关联可读的 REQ/BR/AC，但不新增正式编号。
- 无障碍至少考虑键盘、焦点、标签、对比度、窄屏和语音文字等价。

禁止修改 Canonical PRD、技术架构、决策登记、Phase、源码；不使用 UI 生成器创建应用；不宣布 Approved/UAT Pass。

交接时列出需要产品 owner 决定的交互歧义、可能退回 PRD 的语义、实际文件和未执行项。
```
