# Aden 详情页采集扩展（开发通道）

MV3 原生模块，无运行时依赖。用户自行浏览商品，点击工具栏授权当前页，再在侧栏手动采集。没有自动导航、搜索、滚动或切规格操作。

开发 ID：`mnejmmlalapfhnanlnckfdhmfpbahidm`；Native Host：`com.aden.collector.dev`。发布前必须单独确定商店 ID、签名与兼容矩阵。当前 JD DOM 选择器是候选适配，未在真实京东验证。

## 本地开发与验证

- `npm test`：复用相邻 aden-desktop 已安装 jsdom，测试不连接京东。
- Chrome 开发者模式加载本目录；需由用户执行或明确授权自动化安装。Host 随 Aden 安装并连接已登录工作空间。
- 图片域名由 `config.js` 精确名单与 manifest 可选权限共同约束。未知域名记失败，核对来源后通过新扩展版本增加；不允许用户输入任意下载域名。
- `node scripts/prepare-test.mjs <独立输出目录> http://127.0.0.1:19876` 生成仅用于隔离浏览器的合成构建。正式目录没有 localhost 权限。测试 fixture 在 `fixtures/detail.html`，须通过本地服务提供。
- 每次采集采用 UUID 幂等标识，等待内容/每片/最终服务端 ACK。断连后“查询上次保存结果”回读服务端，不把缓存当成功。Worker 恢复将旧运行标志重置为待确认。“重试未保存图片”先核对服务端、工作空间及原 document/SKU，只重新读取失败资源；原文档已经变化时要求重新采集。
- 离线暂存使用扩展自己的 IndexedDB，记录关联 principalId、workspaceId、captureId、documentId。捕获内容和已下载但尚未得到完整上传 ACK 的图片字节最多保留 24 小时，所有记录合计最多 100 MiB；达到上限停止新增，不驱逐未过期记录。浏览器关闭后可在期限内恢复，恢复前必须连接同一账号与工作空间，并由服务端确认捕获；不重新请求源站。已保存图片得到 ACK 后删除本地字节，完整提交后删除暂存记录。
- 过期记录在每次读写、Worker 启动及每小时 alarm 清理；浏览器完全退出时不能执行清理，重新启动后先清理，过期内容不可读取或恢复。明确退出/撤权错误会清理暂存；切换账号或工作空间会清理旧记录。桌面无法主动推送退出事件时，在下一次认证或请求拒绝时清理，未经重新认证不显示持久内容。
- 暂存不包含后端 Token、京东 Cookie 或 URL 签名查询参数。浏览器/系统自身清理站点存储可能提前移除暂存；暂存不是已保存业务数据，UI 不据此报告已入库。资源还未下载完成的字节无法恢复，应回到原页重采。

## 桥协议 v1

请求 `{protocolVersion:1,messageId,type,payload}`，响应 `{protocolVersion:1,messageId,ok,data?,error?:{code,message}}`。

| 类型 | payload |
| --- | --- |
| hello | `{extensionId}`，配对由 Aden 确认并绑定登录会话 |
| capture.lookup | `{sku}`，首次直接采集，重复先选择新增快照/查看已有 |
| capture.prepare | captureId、source、fields、blocks、images、documentId、parserVersion、completeness |
| asset.chunk | captureId、imageId、generation、offset、totalSize、sha256、mimeType、dataBase64 |
| capture.commit | captureId、generation、failures |
| capture.status | captureId |
| library.open | `{}` |

传输为 UInt32LE 长度 + UTF-8 JSON，总包不超过 256 KiB，原始资源片不超过 128 KiB。Host 保持当前用户命名管道长连接；Electron 及后端最终裁决权限，扩展与 Host 不保留后端 Token 或京东 Cookie。

图片逐个下载，credentials omit，禁止重定向，30 秒超时，大小/签名/可解码尺寸校验后分片；图片不受支持、未授权、失败和停止均作为缺失清单提交。
