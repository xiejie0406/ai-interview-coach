# FEAT-QBANK-001 验证记录

> 验证结果：Pass（范围内）；Maven 测试代码未执行，前端 `tsc -b` 仍受既有 TypeScript 配置阻塞。

| EV | 实际验证 | 结果 |
|---|---|---|
| EV-QBANK-01 | `mvn -pl backend/interview-boot -am -DskipTests package` | Pass |
| EV-QBANK-02 | `category` 分别请求 12 个模块，每个模块返回 50 条，合计 600 条新模块题 | Pass |
| EV-QBANK-03 | `GET /api/v1/questions?category=RAG_DEEP_V3&limit=100` 返回 50 条 RAG 深入题目 | Pass |
| EV-QBANK-04 | `GET /api/v1/questions?category=AGENT_SECURITY_DEEP_V3&limit=100` 返回 50 条安全深入题目 | Pass |
| EV-QBANK-05 | 题目详情只返回 prompt、referenceAnswer、versionId 等元数据，不返回 answerRequirements/sourceRefs | Pass |
| EV-QBANK-06 | `npx vite build` | Pass |
| EV-QBANK-07 | `npm run build` | Blocked：项目既有 `baseUrl`/`paths` TypeScript 配置与当前 TypeScript 版本不兼容 |
| EV-QBANK-08 | `GET /actuator/health` | Pass：`UP` |
| EV-QBANK-09 | RAG 模块题目详情返回完整标准答案正文 | Pass |
| EV-QBANK-10 | 数据库已存在不可变历史版本时重启，新模块题目幂等插入且每模块保持 50 条 | Pass |
| EV-QBANK-11 | Chrome 三栏工作台包含模块栏、题目列表和答案阅读区，页面标题为 32px | Pass |
| EV-QBANK-12 | Chrome 点击模块收缩、搜索“召回” | Pass：模块栏可收缩，题目结果为 5 条 |
| EV-QBANK-13 | Chrome 标准答案阅读区 | Pass：6 段深入内容，包含结论、工程实现、具体例子、误区和验收方式 |
| EV-QBANK-14 | Chrome 答案区可见内容 | Pass：无来源链接、回答要求、答题思路和在线作答入口 |
| EV-QBANK-15 | Chrome 全屏工作区布局 | Pass：无 `.page-heading`；工作区从导航底部开始，占满可用宽度和高度 |
| EV-QBANK-16 | Chrome 收缩模块栏 | Pass：模块栏由 190px 收缩到 72px，显示 `AG` 等可识别缩写，并可通过无障碍标签重新展开 |
| EV-QBANK-17 | Maven / Vite 构建 | Pass：新增写入适配器、V014/V015 和前端编辑组件编译通过 |
| EV-QBANK-18 | Chrome 未编辑答案状态 | Pass：只显示“编辑答案”，不显示“查看系统答案”“我的答案”或“标准答案”标题 |
| EV-QBANK-19 | Chrome 保存个人答案 | Pass：保存后正文切换为用户答案，并出现“查看系统答案”；系统答案可临时查看并返回 |
| EV-QBANK-20 | Chrome 新增公共题目 | Pass：弹窗保存成功，新题自动选中且公开查询可检索到 |
| EV-QBANK-21 | 跨租户数据作用域 | Pass：个人答案分别记录公共题库租户、用户租户和用户 ID；匿名详情只返回系统基础答案 |
