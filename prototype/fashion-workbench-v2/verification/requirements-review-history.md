# 服装原型需求与证据映射（历史复核）

> 本文件保留本次实现过程中的独立复核和失败发现，当前状态以[最终需求映射](requirements-map.md)为准。历史缺口不等于最终版本结论。

> 类型：当前实现与验证范围对照 · 日期：2026-09-11  
> 上游：[PRD v1.2](../../../文档/项目/智能选品项目/产品/产品需求文档.md)、[设计与任务](../../../文档/项目/智能选品项目/原型/服装工作台原型设计与生产任务转交.md)  
> 对象：[服装工作台 v2](../index.html) · 复核：独立代理读取当前代码、实际输出、测试脚本及截图  
> PRD SHA-256：`2135A64E0CEF9B1E0A929DA6238F8BE81ADAE63D0E1FA7166CB1724532A8166C`

本表以PRD原编号定位，不重复需求正文，不把源文件存在当作运行证据，也不把局部Pass合并成整个AC或生产系统通过。运行层分为内核规则、真实本地文件/浏览器、实际导出、生产接入；同一AC可以有已执行的Pass和另一个层面的NotRun，含义分别说明。第4节记录本次发现的原型内部缺口，它们不能列为永久生产依赖或通过修改PRD移除。

## 1 已读取并核对的证据

| 证据代号 | 真实来源 | 实际结果及可证明的范围 |
| --- | --- | --- |
| EV-CORE | [core-review.md](../core-review.md)、[core-test.cjs](../core-test.cjs) | 检查代理直接运行Node，第四轮44/44通过、退出码0；被测core SHA-256 `95e08d316e231d2a700e729ced647ac26b2893db5a5d57133e0378f05f575467`。覆盖指定金额、配比、库存、价表、例外、快照、恢复及候选输入，不证明UI或服务端 |
| EV-CAT-RULE | [catalog-test.cjs](../catalog-test.cjs) | 检查代理直接运行Node，最终17项通过、退出码0；包括CSV解析、严格全量、版本冲突、恢复、客户表及税口径版本化。脚本先打印16项计数，再执行第17项税口径断言，不能误报只有16项 |
| EV-MAIN-UI | [browser-results.json](browser-results.json)、[browser-test.cjs](../browser-test.cjs) | 记录时间2026-09-10 17:38:08 UTC，11项检查、errors为空。脚本实际覆盖12页面可访问、四档各2候选、配比错误、模特样例逐项复核、JPG下载、快照确认/刷新、390px无整体溢出、放大字体与减少动画环境 |
| EV-CAT-UI | [商品浏览器结果](../../../output/playwright/fashion-catalog/result.json)、[catalog-browser-check.cjs](../catalog-browser-check.cjs) | 记录时间2026-09-10 17:38:58 UTC，Chrome152.0.7977.83，14项记录、errors为空。真实CSV下载再上传、实际XLSX库存含0、缺行阻断、专属未税表、图片去重/主图匹配/坏图/ZIP及375px布局 |
| EV-DEL-STRUCT | [delivery-structure-check.json](../evidence/delivery-structure-check.json)、[delivery-check.cjs](../delivery-check.cjs)、[实际结构样本PPTX](../evidence/delivery-structure-sample.pptx) | 记录时间2026-09-10 17:38:40 UTC，PptxGenJS4.0.1，36页、143个原生表格、14张PNG；断言完整SKU、长名续页、金额24050元、原生文本表格、图像比例、OOXML边界、敏感哨兵不泄露、历史/禁用素材、合并费用及未税行。没有桌面应用渲染证据 |
| EV-DEL-UI | [delivery-browser-check.json](../evidence/delivery-browser-check.json)、[delivery-browser-check.cjs](../delivery-browser-check.cjs) | 记录时间2026-09-10 17:38:57 UTC，21页PPT、11张嵌入图、ZIP内10张图片；实际按钮下载[PPTX](../evidence/browser-delivery.pptx)、[CSV](../evidence/browser-delivery.csv)、[ZIP](../evidence/browser-images.zip)，历史金额及模板不改写、禁用素材阻断、390px及无JS异常 |
| EV-STUDIO-UI | [studio-results.json](studio-results.json)、[studio-browser-check.cjs](../studio-browser-check.cjs)、[本轮不可变结果](studio-results-2026-09-11T03-04-19-942Z.json) | 图片专项代理于2026-09-11 03:04:19–03:05:06 UTC直接运行，16/16 Pass、退出码0、全部场景控制台错误为空；独立Chrome与127.0.0.1临时服务。覆盖积分预留/成功/失败/取消/重试/刷新/过期退款、防重、真实上传逐项审核、免费电商拼版、键盘焦点、1–4档真实JPG、部分成功保留、外部图采用后换款禁下；仅ST-10注入该隔离页面的credits=0，其余操作均经UI |
| EV-VISUAL | [搭配桌面截图](looks-desktop.png)、[交付桌面截图](../evidence/delivery-desktop.png)、[窄屏导入截图](../../../output/playwright/fashion-catalog/imports-375.png)、[旧拼版JPG](商品拼版.jpg)；新[单品](2026-09-11T03-04-19-942Z-composition-1-category.jpg)/[二品类](2026-09-11T03-04-19-942Z-composition-2-category.jpg)/[三品类](2026-09-11T03-04-19-942Z-composition-3-category.jpg)/[四品类](2026-09-11T03-04-19-942Z-composition-4-category.jpg) | 初次复核发现旧拼版JPG宽高比失真，保留原失败事实。2026-09-11图片专项代理实际打开ST-14的四个新JPG：均为1200×900，衣服/长裤/鞋帽按原裁片比例完整居中，标题、名称和页脚无重叠；四个档位商品数准确。G06当前原型缺陷已修复，未以下载成功代替视觉复查 |

浏览器JSON来自主代理和专项代理的已执行记录，本复核同时读取对应脚本确认断言范围；本复核没有冒称自己重复运行了这些浏览器脚本。上述UTC时间换算为北京时间均为2026-09-11。浏览器记录没有固定源码摘要，后续代码变化需对受影响流程重跑并补当前证据，不能默认为新代码也已通过。

EV-STUDIO-UI本轮补有开始/结束源码SHA-256且运行期间未改变：core.js `b9eef4fe429ab1a1d23691d27d18491f1a206f2404f1fa1fbdd92f08e195e24f`、app.js `e4db96d3a3fc7054858d0f7e3ff517d771c86b766af159172bfb9a1dc8318bd0`，其余样式/入口/advanced摘要见JSON。首轮[失败记录](studio-results-2026-09-11T01-55-40-034Z.json)保留缺失advanced资源，以及重试重复提交、来源误标、Tab焦点离开和重搭未退款的真实失败；修复后的本轮16项结果不改写这些历史事实。样例图和上传图均来自项目内素材，不证明真实外部模型调用或实际商品保真。

已知运行入口限制：EV-DEL-UI实际验证 `file://` 图片处理被浏览器拦截；通过本地HTTP预览服务才完成PPT及图片导出。这是已执行的环境差异，用户交付入口应提供本地预览启动方法，不能只交双击HTML并声称导出可用。

## 2 17项需求定位

页面hash来自当前[app.js](../app.js)导航；模块名指向当前实现职责。此表的“已有证据”只说明被测部分，完整AC判定见下一节。

| 需求 | 页面和代码入口 | 已有真实证据 | 本轮范围与限制 |
| --- | --- | --- | --- |
| REQ01 商品导入 | `#catalog`、`#imports`；[catalog.js](../catalog.js)的商品维护/导入页；[file-io.js](../file-io.js)的readFile、validate、apply | EV-CAT-RULE、EV-CAT-UI | 商品元数据与价库存隔离、重复SKU、草稿和真实文件读取有证据；所有模板字段和10万行容量尚无逐项实测 |
| REQ02 价格全量 | `#imports`价格全量；IO.validate/apply/restore、[core.js](../core.js)的price | EV-CAT-RULE、EV-CAT-UI、EV-CORE24–25 | 严格缺行、币种/单位、同范围冲突、客户表独立有证据；价表税口径进入报价的校验缺口见G01 |
| REQ03 库存全量 | `#imports`库存全量及历史批次；IO.validate/apply/restore、F.calcLook | EV-CAT-RULE、EV-CAT-UI、EV-CORE13–23 | 实际XLSX含0、旧时间拒绝、恢复旧时间与逐SKU核验；跨标签页冲突另见G04，生产原子事务未接入 |
| REQ04 京东图片 | `#media`；catalog媒体上传/映射/主图确认；IO.validJD/unzip | EV-CAT-UI、EV-CAT-RULE | 真实上传、ZIP、坏图、去重和人工挂图可操作；未实际登录京东下载，不能记采集成功 |
| REQ05 商品理解与修正 | `#catalog`商品属性、`#media`；主图变化要求属性复核；F.invalidateImages | EV-CAT-UI、EV-CORE32 | 人工标签修订与图片版本状态有证据；真实模型属性理解未接入，不能称AI识别成功 |
| REQ06 客户需求 | `#projects`、`#brief`、`#customers`；projectDialog、brief页面、customerDialog | EV-MAIN-UI页面访问、EV-CORE24–25、37 | 创建/复制/客户字段与本地规则解析有实现；解析及空预算的完整操作仍需补证，空预算UI缺口见G01 |
| REQ07 四档候选 | `#looks`；F.generateLooks、F.allocate、F.calcLook | EV-MAIN-UI、EV-CORE38–44 | 四档生成、预算可行性、去重、递进及库存硬约束有证据；没有销量预测或外部商品发现承诺 |
| REQ08 人工编辑 | `#looks`调整弹窗；editLook、F.touch | EV-MAIN-UI配比错误、EV-CORE13–15、29、32 | 逐SKU配比、手动替换/锁定有实现；保持锁定重搭与候选排序入口缺口见G05 |
| REQ09 图片类型 | `#studio`；newJob、processJob、boardImage、上传入口 | EV-STUDIO-UI01、06、08–10、12、14；EV-CORE33 | 样例模特、真实外部图文件上传、电商用途实际生成composition JPG及1–4档拼版均已运行，标签与文件内容一致；外部实时生图和供应商画幅控制仍属生产接入 |
| REQ10 图片复核 | `#studio`；reviewJob、F.calcLook最终图版本检查 | EV-STUDIO-UI06–07、16；EV-CORE32–33 | 未勾/部分勾选不能采用、驳回必须写原因、完整审核记录来源/商品版本/复核人、换款清除采用并禁下旧图均有浏览器证据；真实四件商品保真仍未测试 |
| REQ11 异步与恢复 | `#studio`任务列表、取消/重新准备、刷新中断状态 | EV-STUDIO-UI01–05、10–11、13、15 | 排队/处理中取消、失败后重试防重、零额度整批阻断、刷新中断、重搭过期退款及同工作区部分成功保留均实际通过；真实费用未知结果与供应商对账未接入 |
| REQ12 报价计算 | `#quotes`；F.totals/percentOf/validException、quoteSettings | EV-CORE01–12、19、26–28、41；EV-DEL-STRUCT | 内核含未税、舍入、折扣和例外已测；费用税口径/价表税一致性的UI与链路缺口见G01 |
| REQ13 备选/合并 | `#quotes`；F.quoteGroups；delivery的displayGroups/combined布局 | EV-CORE16–19、EV-DEL-STRUCT | 数量累计和整单费用有真实规则证据，合并PPT保留每个组合；模式切换浏览器操作需补当前证据 |
| REQ14 尺码库存 | `#looks`配比弹窗、`#quotes`；F.calcLook/quoteGroups | EV-CORE13–23、EV-MAIN-UI缺配 | 具体尺码、守恒、共享SKU与24小时边界已测；库存刷新是已实现解决路径，不代表库存预占或真实盘点 |
| REQ15 报价版本 | `#quotes`确认、`#delivery`版本选择；F.createSnapshot、delivery.diff/checkExport | EV-CORE27–32、EV-DEL-UI、EV-DEL-STRUCT | 历史金额/模板隔离、版本选择与禁用素材有证据；真实多用户事务/并发和运行中外部更新仍是独立验证 |
| REQ16 PPT布局 | `#delivery`；[delivery.js](../delivery.js)的buildPages/tablePages/buildPptx | EV-DEL-STRUCT、EV-DEL-UI、EV-VISUAL | 一至四档、长名多尺码、真实原生文本/表格、嵌入图片及版本口径已测；PowerPoint/WPS实际打开尚未执行 |
| REQ17 预览下载 | `#delivery`；checkExport/runExport/imageData/csv/ZIP | EV-DEL-UI、EV-DEL-STRUCT、EV-CORE29–36 | 真正下载PPTX/CSV/ZIP、历史回读/禁用阻断；导出失败后同快照重试、完整键盘流程仍需定向证据 |

## 3 32项AC的证据范围

“Pass”只用于该单元格明确的已执行断言；“NotRun”列出尚无对应运行证据的部分；源代码发现的真实不符以“Fail（静态复核）”标明，并关联可修正缺口。生产层结果独立列在第5节。

| AC | 可复查的通过证据 | 尚未证明或当前不符 |
| --- | --- | --- |
| AC01 | Pass：EV-CAT-RULE商品元数据重复/冲突、未知商务资料保持和重复文件不增SKU | NotRun：同一个实际商品文件混入所有指定错误类型的完整浏览器纠错流程；商品规则层不等于全部文件格式容量 |
| AC02 | Pass：EV-CAT-RULE缺SKU/货币与单位错误不生效，EV-CAT-UI实际完整与缺行CSV | NotRun：生产数据库发布中断的事务证据；当前是克隆状态与本地保存 |
| AC03 | Pass：EV-CAT-UI实际XLSX显式0、EV-CAT-RULE空白/旧时间，EV-CORE20–23 | NotRun：真实库存源时间、生产进程崩溃后的原子恢复 |
| AC04 | Pass：EV-CAT-RULE同范围冲突、价格库存独立、商品范围改变重验 | Fail（静态复核）：当前工作区多标签页没有存储revision冲突处理，见G04；生产多用户并发NotRun |
| AC05 | Pass：EV-CAT-UI真实图片上传、SHA去重、人工主图挂载、坏图可重试、ZIP | NotRun：同款两颜色错配/缺映射的完整浏览器纠正组合；京东原图实际采集 |
| AC06 | Pass：EV-CAT-UI人工属性修订；EV-MAIN-UI需求页面可访问 | NotRun：错误自然语言条件解析→人工修正→候选采纳完整操作；源码解析明确是规则演示 |
| AC07 | Pass：EV-MAIN-UI四档各2候选；EV-CORE38–44预算、去重、递进、交叉可行及过期过滤 | NotRun：管理员其他槽位模板配置；首期用户四个槽位主流程已覆盖 |
| AC08 | Pass：EV-CORE32最终拒绝旧图版本；EV-MAIN-UI尺码失败输入保留 | NotRun：锁定上衣换裤子后异步结果晚到的真实浏览器动作；保持锁定重搭缺口见G05 |
| AC09 | Pass：EV-STUDIO-UI06–07未勾/部分勾选不能采用、驳回须原因并保存；EV-CORE32过时图拒绝 | NotRun：真实遗漏帽子/错Logo图的商品质量判断与真实模型结果质量；上传样例测试证明审核流程，不代替实际商品准确率 |
| AC10 | Pass：EV-STUDIO-UI01–05、13、15实际成功结算、创建和重试防重、排队/运行取消、失败退款、过期不回写、部分成功项保留；ST-08拼版不记AI | NotRun：真实供应商账单与未知结果对账；本地演示流程已关闭G03已复现缺陷 |
| AC11 | Pass：EV-CORE02精确24050元，EV-DEL-STRUCT实际OOXML同金额 | 无新增原型规则缺口；真实企业价格样本业务接受NotRun |
| AC12 | Pass：EV-CORE16–19备选/合并110顶库存及费用一次；EV-DEL-STRUCT合并视觉和总额行 | NotRun：实际浏览器模式切换、修改采购量后再下载的专项链路 |
| AC13 | Pass：EV-CORE14、20–23逐SKU不足、24小时及非法未来时间；EV-CAT-UI库存更新 | NotRun：真实业务核实记录；当前通过刷新批次解决，不提供库存锁定 |
| AC14 | Pass：EV-CORE29–30输入及返回引用隔离；EV-DEL-UI价格更新后历史CSV/PPT来源不变 | NotRun：生成过程同时到达更新的受控异步竞态；多用户生产版本冲突 |
| AC15 | Pass：EV-DEL-STRUCT36页、完整SKU/长名还原、原生表格文本、图像比例；EV-DEL-UI实际21页PPTX | NotRun：PowerPoint/WPS本机打开编辑另存；不能写原生应用兼容已通过 |
| AC16 | Pass：EV-CORE31客户快照白名单；EV-DEL-STRUCT所有XML/CSV无内部哨兵；EV-CAT-UI销售无商品写入口 | NotRun：真实API字段权限及文件ID越权；客户端隐藏按钮不证明服务端权限 |
| AC17 | Pass：EV-MAIN-UI确认后刷新恢复；EV-CORE34–36持久化失败和恢复；EV-DEL-UI实际下载 | NotRun：导出任务失败注入、同一快照重试成功、下载结果持久记录全链路 |
| AC18 | Pass：EV-CAT-UI主图更换版本/待属性复核；EV-CORE32旧图版本拒绝；EV-DEL-UI禁用阻断；EV-DEL-STRUCT历史素材撤回 | NotRun：所有素材使用范围撤回组合；生产对象存储访问控制 |
| AC19 | Pass：EV-CORE13–15的90/110和非法尺码、EV-MAIN-UI配比错误保留输入 | 无新增确定性规则缺口；真实客户尺码来源仍由业务提供 |
| AC20 | Pass：EV-CORE01、03–04、08、11金额精度、56.46元、80000舍入参照和拒绝；EV-CAT-RULE导入金额 | 无新增确定性规则缺口 |
| AC21 | Pass：EV-CAT-RULECSV BOM/逗号/换行/引号和公式文本转义；EV-CAT-UI真实XLSX/CSV/坏图/ZIP | NotRun：000123全链路、重复表头/Excel公式金额/压缩超限完整浏览器输入矩阵和目标容量 |
| AC22 | Pass：EV-CORE24–25指定表缺失不回退；EV-CAT-RULE/UI客户表更新与批发价隔离 | NotRun：实际客户切换使旧预览失效的专项UI证据；价表税口径混用见G01 |
| AC23 | Pass：EV-CORE05–08含/未税数值及不再加税运费；EV-CAT-RULE税模式版本化 | Fail（静态复核）：用户无法选择费用税口径，报价未对比商品税模式，见G01；实际完整税切换链路NotRun |
| AC24 | Pass：EV-CAT-UI销售界面无商品写入操作；EV-CORE26销售不可自批 | NotRun：伪造客户端角色后服务端真实拒绝、跨客户下载地址访问；该部分属于生产层 |
| AC25 | Pass：EV-CORE09–10、26–28零价/全折拒绝、负责人例外签名与到期 | NotRun：负责人UI批准再改数量触发失效的完整浏览器场景 |
| AC26 | Pass：EV-CORE34–36保存失败不污染、五种坏备份、有效/未知schema恢复；EV-MAIN-UI刷新 | NotRun：真实备份文件下载/再上传/确认恢复、重置仅影响本键的浏览器交互；多标签页见G04 |
| AC27 | Pass：EV-STUDIO-UI01–05、10–11、13、15额度预留、成功结算、零额度整批拒绝、创建/重试防重、部分失败、取消和刷新退款；样例均cost=0 | NotRun：真实供应商扣费、结果未知时费用状态及对账，属于生产层；ST-10只在隔离测试页面注入credits=0 |
| AC28 | Pass：EV-STUDIO-UI06、16真实PNG经studio上传→逐项复核→采用→保持锁定重搭→旧图stale且下载拒绝；ST-13旧处理中任务不覆盖新组合；EV-CORE32–33 | NotRun：真实外部供应商迟到回调；原型不伪造供应商任务或收费记录 |
| AC29 | Pass：EV-MAIN-UI390px多页面无整体溢出、字体放大/减少动画；EV-CAT-UI375px；EV-STUDIO-UI09、12以Enter打开/Tab循环/Escape关闭并恢复焦点，390px复核完整图像 | NotRun：全部页面完整键盘路径及所有设备尺寸；图片弹窗局部通过不代表整个产品可访问性全覆盖 |
| AC30 | Pass：EV-DEL-UI真实PPTX/CSV/ZIP下载和解包，EV-DEL-STRUCT长名、表格、图像、敏感字段检查；EV-STUDIO-UI14与EV-VISUAL证明1–4档JPG真实生成且修复比例 | NotRun：原生PowerPoint/WPS打开编辑；G06旧JPG失真历史保留，当前新导出四档无该问题 |
| AC31 | Pass：EV-STUDIO-UI01、06、08、14实际样例/外部上传/电商composition及原图拼版来源准确，免费处理不冒充AI；EV-CORE33、EV-DEL-UI下载和未成交说明 | NotRun：真实AI供应商效果；原型本地图片标签与额度控制缺口G02/G03已定向修复验证 |
| AC32 | 当前生产层NotRun | 未部署数据库/服务端权限/真实模型及真实京东、容量、恢复演练、原生PPT应用兼容；详见第5节 |

## 4 原型内部发现与待补证

以下均已发给主代理，无需用户再回答需求。实际修复结果应在产生新代码和对应定向证据后追加；本表不通过文案重定义完成标准。

| 编号 | 本轮检查时的具体事实 | 需要的完成证据 |
| --- | --- | --- |
| G01 报价可操作口径 | 内核支持feeTaxable=false，UI无选择；空预算UI仍调用parseMoney拒绝；UI有效期1–90而内核1–30；IO保存税模式但core报价未读取 | 费用税口径、空预算和有效期UI与PRD一致；含/未税价格表与报价模式一致性校验；实际页面切换及金额/阻断证据；交付文字正确解释费用税基 |
| G02 电商图片用途 | 初次发现ecommerce返回同一模特样例；首轮浏览器又复现composition误标为本地上传。当前按用途生成真实商品拼版，并显示商品电商拼版来源 | 已修复并定向Pass：EV-STUDIO-UI08真实JPG1200×900、source=composition、0积分、卡片/审核标签及下载；ST-06/07证明真实图上传审核。参见[电商文件](2026-09-11T03-04-19-942Z-ecommerce-composition.jpg) |
| G03 任务用量与重复/恢复 | 初次缺少额度/防重；首轮实际复现重试入口重复提交及过期任务仍占积分。当前提交预留、成功结算、失败/取消/过期仅退reserved，创建和重试统一防重 | 已修复并定向Pass：EV-STUDIO-UI01–05、10–11、13、15；两张成功120→118，取消120→119→120，部分成功失败最终119，零额度整批拒绝。均为明示的演示积分，不发生真实费用 |
| G04 跨标签页保存 | 当前没有storage事件或持久化revision比较，两个页面可相互覆盖 | 本地冲突检测、保留本页未保存副本/恢复方式、用户可重新载入；用两独立标签实际复现旧版本写入拒绝；不冒称生产多用户锁 |
| G05 搭配编辑闭环 | 可锁定和手动替换；当前重新生成入口整批替换，没有保持锁定重搭及候选排序入口 | 锁定上衣重搭其他件、保持基础款、重新核价和图片失效、顺序调整后PPT按所选顺序输出的真实操作证据 |
| G06 商品拼版比例 | 初次旧JPG中长裤被压短、鞋帽变宽；当前boardImage按裁片自然宽高等比缩放并居中，模特卡及审核图contain | 已修复并定向Pass：EV-STUDIO-UI09、12、14；1–4档分别真实生成/下载1200×900 JPG且专项代理实际逐张查看，原始商品形态、名称、标题和页脚完整；四个新图链接见EV-VISUAL |
| G07 未覆盖的交互 | 现有浏览器主测试未操作自然语言纠正、锁定换款/重搭、外部生成图上传、任务取消/重试/额度、备份文件恢复或完整键盘路径 | 针对上述已实现或补齐入口补一轮实际操作，记录结果、输入、输出和缺陷；不把12页面访问称为全部流程验收 |

## 5 尚未实测的生产条件

这些条件来自PRD第12–14节，保留为生产接入任务；本次原型内部缺口应先按第4节修正，不混入此表。

| 生产条件 | 当前真实状态 | 放行所需证据 |
| --- | --- | --- |
| 企业商品、客户、价表及库存源 | NotRun：只有本地虚构夹具及用户可上传文件流程 | 授权企业样本、明确SKU与可售量口径、全量/恢复回读及业务接受 |
| 京东采集 | NotRun：有链接/映射/上传，未执行真实京东账号下载 | 从授权商品页面经Chrome插件下载、映射、颜色款确认的完整实测，保留失败样例 |
| 实际生图与费用 | NotRun：样例任务和本地图像处理不产生供应商调用证据 | 80商品/40搭配任务的真实输入输出、四件一致性、每采用图成本、超时/取消/账单与预算控制 |
| 服务端身份及权限 | NotRun：本地角色可由浏览器用户改变 | 真实会话、客户归属、字段与下载权限的拒绝测试，生产Secret不在浏览器 |
| 数据库与持久任务并发 | NotRun：浏览器存储不是数据库事务或任务队列 | 原子批次发布、并发版本冲突、幂等、晚到结果、进程中断恢复 |
| 容量与可用性 | NotRun：未执行10万SKU/10万行/20并发负载 | 记录资源环境及P95、导入/导出耗时、持续可用性与故障告警 |
| 备份灾备 | NotRun：本地JSON恢复只证明原型状态恢复 | 每日备份、30天保留、隔离恢复演练达到RPO24小时/RTO4小时、媒体引用与快照核对 |
| PowerPoint/WPS兼容 | NotRun：有真实可解析PPTX和预览，没有原生应用打开 | 指定应用版本实际打开、编辑、另存并逐页检查 |
| 用户接受及正式发布 | NotRun：Agent不代签，原型交付不是部署 | 用户/业务负责人对真实场景和限制形成接受决定，明确部署/恢复对象与发布执行证据 |

## 6 维护方式

新增证据先保存真实输出，再更新对应行；修复后的状态必须带来源与作用范围，保留旧失败事实。该映射汇总已有证据，不制造第二套验收标准，也不对未运行生产条件填写Pass。最终交付须能从每个REQ/AC回到当前代码和真实证据，并清楚看见未验证范围。
