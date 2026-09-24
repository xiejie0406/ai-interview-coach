# 服装智能选品与报价专项市场调研

> 类型：产品调研补充  
> 版本：1.0 · 状态：Draft · 调研及取得日期：2026-09-11  
> owner：用户负责产品决定；Codex 整理公开资料  
> 上游：[2026-09-10 市场调研](earlier-research.md) · 下游：[PRD](prd.md)

## 1 本次决策问题

用户已明确首期由内部销售服务团购或批发客户。需要把商品导入、价格全量更新、库存全量更新、京东图片下载上传、单品到四品类搭配、AI 模特及电商图、报价和 PPT 输出串成一条业务流程。

本次重点核实三件事：是否有可直接采用的完整产品；哪些视觉产品能处理上衣、裤子、帽子和鞋；商品全量更新与 PPT 输出有哪些不能混淆的业务语义。用户口述中的“ipa a”具体指代尚未确认，图片入口暂按 Chrome 插件下载后上传设计。

## 2 研究结论

**研究判断：推荐建设面向内部销售的选品报价工作台，图片生成能力评估现成服务。** 公开资料能分别证实服装批发商品目录、受库存约束的搭配推荐、AI 商品图和模板化 PPTX 生成能力。本次核实的产品中，尚无充分证据证明任何一个可直接覆盖本次完整流程；这不表示市场上绝对没有同类产品。

商品、价格、库存、组合清单和报价版本应由本系统管理。AI 负责理解需求、提出搭配和生成候选图片。采用哪一家供应商，需结合真实商品、调用方式和实际成本测试决定。以下“未核实”只表示本轮证据不足，不等于该产品不具备相应功能。

## 3 代表产品对比

| 产品 | 官方资料证实的能力 | 对本项目的启示及边界 |
| --- | --- | --- |
| JOOR | 数字商品目录展示图片、属性、价格和可售信息，支持批量维护及系统集成 [R01] | 借鉴 B2B 商品展示和选品；未核实本次完整搭配、AI 出图、报价快照和 PPTX 流程 |
| NuORDER | 商品目录、客户价格表、库存数据导入；目录可输出 PDF、XLSX [R02–R05] | 借鉴商品变体、价格表和库存规则；PDF、XLSX 不能当作 PPTX 输出证据 |
| Vue.ai | 从商品目录推荐 outfit，支持主题、场景、库存及商业规则、缺货替代 [R06] | 借鉴先满足可售约束再搭配的逻辑；未核实四件 SKU 同图保真及报价 PPTX |
| Style3D Moda 和 StyleWork | Moda 官方更新涵盖服装、鞋、帽、包试穿；StyleWork 支持桌面批处理 [R07–R08] | 国内视觉候选；尚未核实四个独立 SKU 一次生成的可用 API 和一致性，缺少的侧背面可能由 AI 推断 |
| FASHN | Try-On Max 支持衣服、鞋帽等商品与人物图输入，有异步 API [R09] | 公开接口便于概念验证；已查文档是单个 product_image，1–4 张输出不是 1–4 件商品输入，且接口标为 Preview |
| Photoroom | 应用帮助明确可用多张服饰及配饰图组成 outfit；有批量能力和 Virtual Model API [R10–R11] | 与本需求较接近，值得试用；API 的 additionalProductImages 描述为同一商品不同角度，应用能力不能直接推定为 API 能力 |
| Botika | 已有模特图换人、平铺图或人台图转模特，部分流程支持批量 [R12] | 官方 FAQ 明确鞋类不支持，帽子等配饰未正式支持且可能被改动，不宜作为四件套唯一方案 |
| OnModel | 换模特、换背景、商品图转模特，有 API 与批量入口 [R13–R14] | FAQ 明确商品图转模特当前主要支持正面，鞋及珠宝仍在规划，四件套需其他能力补足 |
| Google Shopping 和 Cloud VTO | Shopping 支持部分服装及鞋的试穿；Cloud 有人物图加单商品图输入类型 [R15–R16] | 消费端结果不等于可供销售系统调用的资产；Shopping 不支持配饰，Cloud 数组名称不代表多商品输入 |
| Plumsail Documents | 以 PowerPoint 模板和结构化数据生成 PPTX，支持扩展幻灯片、表格与图片 [R17–R19] | 可作为文档输出组件候选；需本系统提供经过核算的报价和已确认图片，不能代替选品业务 |
| ImageAssistant | 官方项目页描述网页图片提取、筛选、命名和批量下载 [R20] | Chrome 图片下载工具候选，未实测当前京东页面的图片完整率，也不证明能自动匹配本系统商品 |

## 4 影响 PRD 的关键发现

### FIND 01 全量更新必须定义覆盖范围和缺失语义

NuORDER 官方 SFTP 文档中，价格文件缺失某商品时保留原价格；库存的 Full Feed 按款式和颜色要求完整尺码及日期，但整个商品未出现时保留旧库存，已出现商品缺少尺码时可置零。即使文档叫 Full Feed，也不等同整个商品库替换。[R05]

**本项目建议：** 每次更新明确来源、商品范围、价格表或仓库、业务时间和缺失行规则。默认严格全量：应覆盖的在用 SKU 必须出现，缺行或空白不自动当作零，整批校验通过后生效。这样销售不会把未更新的旧价旧库存误认为新数据。

### FIND 02 四品类搭配与四件商品同时准确出图是不同验收项

FASHN 的单商品接口和 Google Cloud 的单商品输入均不能证明四件同时输入。Botika、OnModel 的品类限制更具体。Photoroom 应用的多商品 outfit 有直接帮助文档依据，但其 API 附图字段另有语义。[R09–R16]

**本项目建议：** 首期保留单品至四品类的结构化搭配和 AI 出图任务；把帽鞋缺失、商品被替换、颜色或 Logo 改动设为人工验图重点。不得将“商品清单正确”当作“模特图正确”，也不得把原图拼版记为 AI 模特图生成成功。

### FIND 03 新版首期视觉范围应按本次用户要求更新

原始 Word 和 2026-09-10 调研将 AI 模特能力后置；本次用户明确要求生成 AI 示意图、电商图和模特示意图。这是新的需求输入，PRD 应明确记录范围变化。使用原图拼版可作为失败后的交付选择，不能因此把 AI 图需求从首期移除。

### FIND 04 商品事实与 AI 图需要分别维护

Style3D 对缺少视角的推断、Google 对试穿与真实合身或尺码库存的区分，说明视觉输出不能承担商品事实职责。[R07、R15]

**本项目建议：** AI 图绑定所用商品及原图版本，并与原图一起复核。新增或替换组合商品后，原图可以立即更新，旧 AI 图须重新复核或重生成；单纯修改报价金额不要求重生成服装图。

### FIND 05 报价提案应要求真实的可编辑 PPTX

NuORDER 所核实的目录导出是 PDF 或 XLSX。Plumsail 官方教程则明确 PPTX 模板、PPTX 默认输出、集合扩展页面和图片适配。[R03、R17–R19]

**本项目建议：** PPT 由已确认方案快照生成，文字与报价表可编辑，原图与 AI 图作为独立图片嵌入；图片保持比例。不能以网页截图、PDF 改后缀或整页图片代替。

### FIND 06 京东图片路径需要业务映射

ImageAssistant 能提取网页图片，但其公开能力不等于知道本系统的商品款号或颜色。京东官方商品详情文档包含 token 和单 SKU 查询，并明确校验商品池范围。[R20–R21]

**本项目建议：** 首期把“京东商品 ID/链接—内部款号及颜色—本地图片文件”做成映射清单，上传后预览确认；登录失效或采集失败可重试、手工补图。官方 API 仅作为待核验的授权接入候选，不承诺可获取任意京东商品全部高清图。

## 5 方案选择

| 路线 | 优点 | 代价与待验证点 | 本次建议 |
| --- | --- | --- | --- |
| 继续人工表格和 PPT | 使用门槛低，可立即处理特殊客户 | 反复核价、配图和排版；没有统一版本 | 可作效率基线和故障后的应急流程 |
| 购买 B2B 目录平台并使用独立 AI 作图工具 | 商品目录和图像生产有成熟产品可借鉴 | 中国本地业务接入、四品类报价及 PPTX 衔接需演示或定制 | 有完整可演示流程且总成本更低时再考虑 |
| 自建业务工作台并接入图像服务 | 能围绕已有商品、报价规则和客户提案控制流程 | 需建设商品治理、推荐、核价、版本和导出 | 推荐进入原型和概念验证 |
| 自建模型训练和 3D 服装资产体系 | 可能获得更强定制能力 | 需要训练数据或 3D 资产，超出本次输入条件 | 首期不建议 |

图像服务候选优先研究 Style3D、FASHN、Photoroom；这不是质量排名或采购承诺。对比时统一输入商品集、输出尺寸、重试上限与人工标准，记录“每张被接受的图片成本”，而非只比较单次调用价格。总成本还包括集成、商品整理、存储、导出和人工复核；本轮未询价，不提供虚构报价。

## 6 来源台账

以下均为厂商官方产品页、帮助文档、开发文档或项目作者页面，统一访问日期为 2026-09-11。除明确列出的页面日期外，均按持续更新网页处理，未标注稳定发布日期。官方宣称说明功能范围，不证明实际效果、SLA 或商业授权。

- R01 JOOR [Line Sheet Software](https://www.joor.com/line-sheet-software)：数字商品目录及相关集成。
- R02 NuORDER [Linesheets overview for brands](https://helpdesk.nuorder.com/hc/en-us/articles/203078485-Linesheets-overview-for-brands)：目录功能及呈现。
- R03 NuORDER [Linesheet imports and exports](https://helpdesk.nuorder.com/hc/en-us/articles/360057746931-Linesheet-imports-and-exports)：目录导入导出格式。
- R04 NuORDER [Price sheet overview](https://helpdesk.nuorder.com/hc/en-us/articles/115005758446-Price-sheet-overview)：客户价格表。
- R05 NuORDER [Flat File SFTP Integration](https://helpdesk.nuorder.com/hc/en-us/articles/16409979437339-NuORDER-Flat-File-SFTP-Integration)：商品、价格、库存文件和更新语义。
- R06 Vue.ai [Outfit Recommendations](https://www.vue.ai/products/outfit-recommendations/)：商品目录搭配、业务与库存规则、替代商品。
- R07 Style3D [Moda June updates](https://help.style3d.com/cloud/en/74be/0b0a/5abd1)：页面日期 2026-07-03，试穿品类、视角及推断边界。
- R08 Style3D [StyleWork batch processing](https://help.style3d.com/cloud/en/c615/93dc4)：页面日期 2026-07-16，桌面批处理。
- R09 FASHN [Try-On Max API](https://docs.fashn.ai/api-reference/tryon-max)：单商品输入、输出数量、品类及 Preview 状态。
- R10 Photoroom [How to use AI Fashion Models](https://help.photoroom.com/en/articles/11154440-how-to-use-ai-fashion-models)：应用中的多服饰组合及批量入口。
- R11 Photoroom [Virtual Model API](https://docs.photoroom.com/image-editing-api-plus-plan/virtual-model)：API 输入及同商品多角度附图。
- R12 Botika [FAQs](https://botika.com/resources/faqs)：输入、批量和鞋帽限制。
- R13 OnModel [API](https://onmodel.ai/api)：接入方式。
- R14 OnModel [FAQ](https://onmodel.ai/misc/faq/?header=show)：输入和品类限制。
- R15 Google [Shopping virtual try-on for merchants](https://support.google.com/merchants/answer/16159685?hl=en)：消费端品类、生成图访问及尺码边界。
- R16 Google Cloud [VirtualTryOnModelInstance](https://docs.cloud.google.com/gemini-enterprise-agent-platform/reference/rest/Shared.Types/VirtualTryOnModelInstance)：单个人物图和商品图的输入类型。
- R17 Plumsail [Create PPTX from template](https://plumsail.com/docs/documents/v1.x/user-guide/processes/examples/create-pptx-from-template-processes.html)：真实 PPTX 输出。
- R18 Plumsail [Slides](https://plumsail.com/docs/documents/v1.x/document-generation/pptx/slides.html)：集合数据扩展页面。
- R19 Plumsail [Pictures](https://plumsail.com/docs/documents/v1.x/document-generation/pptx/pictures.html)：图片嵌入和 Fit 适配。
- R20 ImageAssistant 作者 [项目主页](https://www.pullywood.com/ImageAssistant/)：网页图片提取、筛选和下载；页面可见更新记录不等于当前京东适配测试。
- R21 京东 [查询商品详情](https://opendoc.jd.com/iopv2/iopv2/商品/查询商品详情.html)：授权 token、商品池和单 SKU 查询；其他京东业务线不可据此类推。

## 7 尚需验证的事项

本轮完成公开资料核实，未进行厂商登录试用、京东图片下载实测、API 调用、商务询价、真实客户访谈或生成效果测试。先用实际商品样本和历史报价模板验证多品类图片一致性、商品图片映射、全量文件完整性，以及从需求到客户 PPT 的人工耗时。PRD 中的性能和质量数字均是候选验收目标，不是本次调研已测出的结果。
