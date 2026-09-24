# 本地交付依赖

交付模块使用已有 Codex 捆绑运行时中的浏览器构建，不从 CDN 加载，不执行 npm 安装。

| 文件 | 版本 | 来源 | 许可 |
| --- | --- | --- | --- |
| `pptxgen.bundle.js` | PptxGenJS 4.0.1 | `C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/pptxgenjs/dist/pptxgen.bundle.js` | MIT，见 `pptxgenjs-LICENSE` |
| `jszip.min.js` | JSZip 3.10.1 | 同运行时 `node_modules/jszip/dist/jszip.min.js` | MIT 或 GPLv3，采用 MIT，见 `jszip-LICENSE.markdown` |

index 依赖加载顺序：JSZip → PptxGenJS → core → delivery。PptxGenJS 浏览器 bundle 内含其打包依赖；文件保留上游许可注释。

交付边界：PPTX 标题、正文与表格为真实 OOXML 可编辑对象，图片为嵌入 PNG。HTML 内容预览和文件使用同一套页面数据。文件结构检查不能代替 PowerPoint / WPS 的实际打开与排版验收。
