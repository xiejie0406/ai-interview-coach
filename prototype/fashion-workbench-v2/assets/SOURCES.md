# 商品与模特示例素材

本目录素材为 2026-09-11 使用 Codex 内置 imagegen 生成的虚构商品摄影与搭配示例，未抓取京东商品。仅供原型和方案样例使用，不代表真实在售库存、品牌款号、实际人体试穿或模型服务实时输出。用户的生产商品应通过原图导入及人工映射替换。

| 文件 | 用途 | 对应关系 |
| --- | --- | --- |
| catalog-grid.png | 四列三行、12件商品摄影素材表 | 列：上衣、裤子、帽子、鞋；行：米杏/苔绿、藏蓝/卡其、鼠尾草/炭灰组合。core.js 的 crop 为0–1归一化裁切；各格按原始比例显示 |
| model-olive.png | 四品类模特示例 | 只关联 p00/p01/p02/p03 且 imageVersion=1 的组合；其他组合不会复用此样例冒充生成成功 |

工具路径：内置 imagegen，未使用 CLI/API key。原始生成文件保留在用户 `.codex/generated_images/01a08c23-2c54-7700-b579-d6b6aa5e88a8/`。项目内为副本。

## 实际提示词

商品图：`Use case: product-mockup. Asset type: twelve-SKU demo catalog contact sheet for a fashion wholesale styling workstation. Create one precise 4-column by 3-row evenly divided contact sheet, 1536x1152 landscape, seamless warm white #f4f2ec studio background, NO grid lines, NO letters or text, NO branding. Each of the 12 cells contains exactly one complete isolated apparel product, centered with generous empty padding, entire product inside its cell. Column 1: upper body garments; column 2: trousers; column 3: baseball cap; column 4: pair of sneakers. Row 1: plain beige short-sleeved polo shirt, dark olive straight cotton trousers, plain olive baseball cap, clean off-white low-top sneakers. Row 2: plain navy short-sleeved polo shirt, beige straight cotton trousers, plain navy baseball cap, navy and white low-top sneakers. Row 3: plain sage green short-sleeved crewneck T shirt, charcoal straight cotton trousers, plain beige baseball cap, gray and white low-top sneakers. Photoreal commercial product studio photographs with realistic fabric and soft shadows. Front flat-lay / ghost mannequin clothes, caps 3/4 view and shoes side 3/4. Grid placement is essential: 4 equal columns and 3 equal rows. No humans, no hands, no accessories, no watermarks, no cropped products.`

模特图（以商品图为参考）：`Use case: compositing. Reference image: catalog contact sheet, use ONLY the four products in its TOP ROW. Create a photorealistic full body fashion catalog photograph of an adult East Asian male model wearing exactly the top-row beige short sleeve polo, top-row dark olive straight trousers, top-row olive baseball cap and top-row off-white sneakers. Preserve each garment's color, collar, silhouette, seams and materials closely. Neutral relaxed upright pose with arms slightly away from the polo so all items can be inspected. Warm off-white plain seamless studio background, gentle shadows, accurate natural anatomy. Portrait 3:4 composition, complete head, cap and shoes visible with margins. A polished but restrained ecommerce fashion photograph. No words, labels, logos, watermarks, extra items, or accessories. This is a prototype illustration, not a fit or sizing guarantee.`
