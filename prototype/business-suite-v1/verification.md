# HTML 原型验证记录

> 对象：PROTO-BUSINESS-001 / 1.0.0  
> 日期：2026-09-10 至 2026-09-11，Asia/Shanghai  
> 执行者：Codex · 上游：[范围与设计说明](README.md)  
> 环境：Windows，本机 Chrome（headless），工作区附带 Playwright；以 `file://` 打开原型。  
> 数据：独立浏览器上下文内的本地示例数据，未访问真实业务服务或账号。

## 最终检查结果

| 证据 | 关联范围 | 实际结果 | 原始记录 |
| --- | --- | --- | --- |
| EV-01 页面与导航 | 三项目共 21 个主要视图；1440×1000 桌面、390×844 窄屏；总入口切换、图片/图标加载与页面溢出 | Pass，53 项；运行时异常 0 | [smoke-results.json](output/playwright/smoke-results.json) |
| EV-02 核心交互 | AC-PS、AC-ADEN、AC-FASHION，以下 13 组交互与运行时检查 | Pass，13 组 | [flow-results.json](output/playwright/flow-results.json) |
| EV-03 报价交付 | 过期有效期、绕过编辑页的无效草稿、有效报价打印布局与图片边界 | Pass，3 项 | [delivery-results.json](output/playwright/delivery-results.json) |
| EV-04 静态检查 | 三份业务脚本、共享 UI、三个验证脚本 | Pass，`node --check`；19 个静态 HTML 本地资源链接均存在 | 命令与汇总见本节后文 |

核心交互实际执行了：

1. 排程初始设备重叠阻止发布，弹窗无法确认发布。
2. 生成 9 个已齐料订单的可行示例计划，保留 OP-001 锁定设备与开始时间，检查同设备任务不重叠；生成草稿不改变正式版本。
3. 发布必须勾选影响确认，生成新正式版本；OP-001 开工、暂停、恢复、完工，刷新后状态保留。
4. 新订单空产品校验，创建后可搜索，未命中显示空态；CSV 实际产生下载文件。
5. 390px 视口的任务抽屉在屏幕内，Escape 关闭成功。
6. Aden 新建任务、暂停、恢复、人工接管，状态按动作切换。
7. 审批拒绝必填原因；修改内容后重新提交，同意进入等待，载入示例回复后完成归档，刷新保留状态。
8. 暂停任务后将机器切离线，恢复被阻止且原状态保留，可定位机器。
9. 切换客户后编辑回复，审批收件人与客户匹配，同意后会话记录内容一致；供应商对比与 CSV 下载可用。
10. 服装首套报价：原价 24,120.00，95% 成交价 22,914.00，运费 300.00，税额 3,017.82，总额 26,231.82；成交系数改为 90% 后总额为 24,869.04。
11. 数量超过库存时阻止保存；保存 V01 后修改当前折扣，历史快照仍为 26,231.82，刷新不变，CSV 实际下载。
12. 锁定商品后重搭不改变锁定项，刷新保留锁定；商品搜索和未命中空态可用。
13. 上述流程无浏览器 JavaScript 运行异常。

运行命令（工作目录为本工作区根目录；使用附带 Node，无依赖安装）：

```powershell
& 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' 'prototype/business-suite-v1/verify.cjs'
& 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' 'prototype/business-suite-v1/verify-flows.cjs'
& 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' 'prototype/business-suite-v1/verify-delivery.cjs'
```

以上最终运行退出码均为 0。各脚本可通过 `PROTOTYPE_NODE_MODULES`、`PROTOTYPE_BROWSER` 指定已有 Playwright 包和浏览器路径。静态检查使用 `node --check`，不执行生产构建。验证浏览器上下文关闭后，不会把测试订单保存到用户日常浏览器。

## 截图

| 画面 | 桌面 | 窄屏或其他状态 |
| --- | --- | --- |
| 生产排程 | [资源甘特图](output/playwright/01-production-scheduling-desktop.png) | [手机首屏](output/playwright/01-production-scheduling-mobile.png)、[任务编辑抽屉](output/playwright/scheduling-mobile-drawer.png) |
| Aden | [任务中心](output/playwright/02-agent-desktop-execution-desktop.png)、[任务运行](output/playwright/aden-running-desktop.png) | [手机首屏](output/playwright/02-agent-desktop-execution-mobile.png) |
| 服装搭配 | [搭配工作台](output/playwright/03-ai-fashion-styling-quotation-desktop.png)、[报价交付](output/playwright/fashion-quotation-desktop.png) | [手机首屏](output/playwright/03-ai-fashion-styling-quotation-mobile.png)、[打印媒体](output/playwright/fashion-quotation-print.png) |
| 总入口 | 顶部三项目切换已验证 | [窄屏切换](output/playwright/suite-mobile.png) |

## 发现与修正

- 初轮排程窄屏菜单使用局部样式，与共享展开状态不一致，导航无法点击。统一改用共享导航开关后，7 个窄屏视图均通过。
- 服装原始演示商品名称与图片存在颜色/款式不符，逐张核对 15 张图后修正名称、色码、标签和搭配说明。
- Aden 暂停后可把机器切离线，但恢复动作未核对机器状态。补充恢复、继续和载入回复前的在线校验，并实际验证阻断。
- 无效服装报价可以绕过编辑页直接在交付页导出。统一校验草稿交付路径；过期有效期、超库存等错误阻止保存、导出和打印，历史快照保持独立。
- 第一轮打印断言只检查正文和金额可见，自动检查为 Pass；随后人工看图发现商品超出拼图区，视觉结果为 Fail。保留[修复前截图](output/playwright/fashion-quotation-print-before-fix.png)，将图片网格改为允许收缩的行列并限制图片最小尺寸；补充图片边界断言，重跑 EV-03 并人工复看修复后的打印截图，结果 Pass。
- 验证脚本初版误把“返回总入口”作为业务页，并出现品牌/导航链接及重复操作按钮的定位歧义；懒加载图片的等待也缺少上限。修正验证脚本后重新运行。上述脚本失败不作为产品通过证据；最终 JSON 仅记录完整的最终执行。

## 未覆盖与产品边界

用户可用性评审尚未进行。本轮验证仅说明示例原型中列明的操作与页面表现，不证明真实产品能力或上线条件。

- 排程示例按订单的单道关键工序、设备单占用和小时粒度运行，未实现完整 APS 的多工序、人员、工装和换型求解。
- Aden 的执行画面为 HTML 示意，任务由本地状态转换推进；未控制真实桌面，未接入 ERP、1688 或企业微信。
- 服装推荐为固定商品池的本地规则示例；SKU、库存、价格为示例。支持 CSV 与浏览器打印入口，未实现 PPTX、VTO 或真实 AI 推荐。
- 只验证本机 Chromium 环境和上述视口；未覆盖 Firefox、Safari、真实移动设备、真实读屏器、实体打印机与所有 PDF 分页设置。
- 商用图片权利、平台接入授权、接口准确率与真实数据性能继续按上游调研中的待验证项处理。
