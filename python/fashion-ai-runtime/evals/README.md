# 真实视觉试点评测入口

本目录只保存经授权后的评测清单、脱敏结果和 code-first evaluator；当前不含真实客户、商品、图片、Provider 响应或模拟通过数据。

IMP-10 的固定试点口径来自产品需求文档：80 个内部授权商品，覆盖上衣、裤子、帽子和鞋；40 个任务，每组 10 个；每任务首轮 3 个候选、最多重试一轮；每组至少 8/10 获得业务可用图，失败样本必须保留在分母。四品类缺件、关键款式/颜色/标识错误不能判为可用或采用。

开始真实试点前必须记录 Provider、模型/配置版本、授权数据范围、人民币费用上限、停止条件、审核人和候选环境。运行结果不得包含联系人、Secret、临时下载签名或未授权图片；原型样例和外部上传图片不得计入真实 Provider 样本。

## 候选验收执行顺序

以下命令均从工作区根目录执行。先复制候选清单模板并只填写环境、授权、数据集和秘密管理引用；不得填写账号口令、Token 或密钥值：

```powershell
Copy-Item ruoyi-backend/ruoyi-fashion/scripts/candidate-validation-manifest.example.json candidate-validation-manifest.json
pwsh -NoProfile -ExecutionPolicy Bypass -File ruoyi-backend/ruoyi-fashion/scripts/test-candidate-validation-preflight.ps1 -ManifestPath candidate-validation-manifest.json -OutputPath candidate-preflight-report.json
```

正式候选流程不得使用 `-AllowIncomplete`。预检只有在授权时间和 UAT 窗口均带时区、三项执行授权齐备、获批商品不少于 80 个、四品类及质量标签齐备、40 个任务和固定 8/10 门槛成立、人民币费用上限有效、目标桌面客户端与 UAT 决策人明确时才成功；它只做离线读取，不连接环境、写数据库或调用 Provider。

预检通过后再执行获批的 40 个真实视觉任务，将脱敏结果写入从模板复制出的 `visual-trial-results.json`，然后离线汇总：

```powershell
Copy-Item python/fashion-ai-runtime/evals/visual-trial-results.example.json visual-trial-results.json
$env:PYTHONUTF8 = '1'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
uv run --project python/fashion-ai-runtime fashion-evaluate-visual-trial visual-trial-results.json visual-trial-report.json
```

候选环境技术验证和正式 UAT 完成后，复制证据模板。每个已执行 gate 都必须引用同一证据包内的相对文件，并填写该文件真实 SHA-256；可用 `Get-FileHash -Algorithm SHA256 <证据文件>` 计算，不得手填占位摘要。最后执行交叉核对：

```powershell
Copy-Item python/fashion-ai-runtime/evals/candidate-readiness-evidence.example.json candidate-readiness-evidence.json
$env:PYTHONUTF8 = '1'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
uv run --project python/fashion-ai-runtime fashion-evaluate-candidate-readiness candidate-validation-manifest.json candidate-preflight-report.json visual-trial-report.json candidate-readiness-evidence.json candidate-readiness-report.json
```

预检存在字段差距时会先生成带 `gaps` 的报告再以非零结果停止流程；JSON 解析错误、敏感字段或输入输出重叠则在写报告前拒绝。视觉汇总的 `fail/blocked` 和最终门的 `ReleaseReady=false` 均以退出码 2 返回：前者会保留有效聚合报告，后者会保留带明确 `reasons` 的非发布报告；模型校验错误、敏感信息、证据文件错误或路径重叠不会生成新报告。任何输出路径都不能覆盖输入证据。退出码 0 和 `ReleaseReady=true` 仍只表示证据门通过，报告固定 `ReleaseAuthorized=false`、`NotReleased`，不能据此自动部署或发布。

输入可从 `visual-trial-results.example.json` 复制，但模板故意保持未填写，不能作为通过样本。结果必须包含预登记的 40 个唯一任务 ID，并与 40 条结果精确相等；1/2/3/4 品类组各 10 条，全部任务使用同一 Provider、模型/配置、输入输出规格和人工复核规则。每条任务记录首轮三图、最多两次尝试、实际费用和账单状态、首轮/最终可用、采用时间、实际物品数、关键款式/颜色/标识错误及失败原因；提交、复核和采用时间必须带时区，采用不能早于人工复核，Provider/配置/复核人引用不能只有空白字符。

报告自动给出首轮可用率、两轮内可交付率、每组 8/10 门槛、所有任务实际费用、每张采用图成本、采用耗时中位数/P95、未结账单和停止条件。任一账单仍为 `reserved/unknown` 时结论为 `blocked`；低于质量门、超过费用上限、使用未授权输入、可交付图存在关键身份错误或缺件时为 `fail`。失败任务 ID 始终保留在 40 个任务的分母中；报告只做离线评测，不连接 Provider、不替代业务 UAT 或发布决定。

`candidate-readiness-evidence.example.json` 显式列出七个技术 gate 与 `UAT-01～UAT-06`，所有证据、人员和时间字段保持 `null`、状态保持 `not_run`，因此不能冒充通过；实际执行后再分别填写结果、证据引用、证据包内相对文件路径 `evidence_file`、该文件的 SHA-256、非空执行人和带时区时间。

最终门会实际读取原始候选授权清单并重算 SHA-256，再与预检、视觉和技术/UAT 三份报告中的授权摘要逐一核对；清单在预检后发生任何字节变化都会保持不就绪。它还会核对视觉试点费用上限与预检确认的人民币上限完全一致，逐个打开已执行 gate 的实际证据文件并重算 SHA-256，同时重新检查视觉报告的 40 个任务、每组 10 个、固定 8/10、账单和停止条件。证据文件必须位于证据清单同目录或其子目录，绝对路径、`..` 越界、不存在、超过 50 MiB、疑似 Secret/私钥或摘要不一致都会拒绝，不生成可用通过报告。只有七个候选技术 gate 全部 Pass、六组 UAT 全部 Pass、真实视觉报告 Pass，且业务决定明确为不携带任何条件的 `accepted` 时才输出 `ReleaseReady=true`；`accepted` 夹带条件会直接作为无效证据拒绝。`accepted_with_conditions`、任何 NotRun/Blocked/Fail、原始授权清单未实读、证据文件未核验、费用上限/摘要不一致或未结账单都保持不就绪。报告无论结果如何都固定为 `ReleaseAuthorized=false`、`NotReleased`；实际发布必须另行授权。
