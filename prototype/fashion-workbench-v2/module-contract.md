# 服装工作台原型模块约定

本文件只约定本地原型的模块边界，不代替产品 PRD 或生产 API 契约。所有脚本以普通 defer script 顺序加载，支持本地静态目录；无后端或真实模型调用。

## 共享入口

`window.F` 由 core.js 提供。模块注册 `F.pages.catalog` 等函数，渲染到 `F.main`（当前 main DOM）。`F.render()` 重绘当前页面，`F.go('catalog')` 导航，`F.save()` 保存 state，`F.log(action,detail)` 审计；`F.commit(action, mutator)` 对克隆状态应用改动，成功保存后替换，失败保持旧状态并抛错。直接小改动也可 `F.save()`。

- `F.state` 为当前状态，`F.clone(x)` 深拷贝，`F.uid(prefix)` ID。
- `F.esc(x)` HTML 转义；`F.icon(name)` Lucide SVG；`F.money(cents)` 人民币两位小数。
- `F.notify(text, kind='success')` 反馈；`F.modal({title,body,footer,bind})` 打开原生 dialog，bind 在插入后执行；`F.closeModal()` 关闭并恢复焦点。
- `F.product(id)` 当前商品；`F.stock(p)` 尺码库存之和；`F.price(p,size)` 返回尺寸价格或商品基础价格（分）；`F.photo(p,extraClass='')` 返回带名称alt的图片HTML，支持裁切素材格。
- `F.requireRole(roles)` 无权限抛错（原型角色演示，非生产安全边界）；`F.invalidateImages(productIds,reason)` 使使用相关商品的候选采用图需重新复核。
- `F.skuRows()` 将所有 active 商品展开为 `{productId,sku,size,name,category,color,price,stock,source,warehouse}`。
- `F.download(blob,filename)` 实际下载；`F.parseMoney(text)` 校验最多2位小数，返回非负整数分。
- `F.calcLook(look)` 返回 `{lines,subtotal,discount,fee,total,perSet,errors}`；`F.quoteGroups()` 返回备选逐组/合并采购实际核价；`F.createSnapshot()` 严格检查后存不可变快照。

## 数据结构

```js
state = {
 schema:2, revision:1, role:'sales', productVersion:1, priceVersion:1, stockVersion:1,
 priceAsOf: ISO, stockAsOf: ISO,
 products:[{id,sku,style,name,category,color,unit,price,source:'演示供应商',warehouse:'主仓',
   sizes:[{sku,size,stock,price}], image, crop:null, imageVersion:1,imageAllowed:true,
   status:'active',tags:['休闲'],season:'四季',material:'棉',jdId:'',jdUrl:'',confirmed:true}],
 clients:[{id,name,person,type,city,priceTable:'批发价',note}],
 brief:{clientId,title,scene,style,season,qty:100,budget:30000,groups:[1,2,3,4],perGroup:2,
   progressive:true,exclude:'',text:'',confirmed:true},
 looks:[{id,name,group:4,productIds:[],locks:[],selected:true,qty:100,
   allocations:{PRODUCT_ID:{SIZE:COUNT}},reason:'',revision:1,imageId:null,imageMode:'original'}],
 quote:{mode:'alternatives',discountType:'percent',discount:5,fee:30000,
   validDays:7,note:'现货报价，以订单确认时库存为准。',exception:null},
 imageJobs:[{id,lookId,lookRevision,productVersions:{id:version},kind:'model',status,
   image,createdAt,review:null,reviewer:null,source:'sample',message:'',cost:0}],
 snapshots:[],batches:[],media:[],audit:[],history:[],settings:{brand:'织选',stockTTL:24,credits:120,contact:'销售顾问 林亦',phone:'400-000-0000'}
}
```

商品 category 使用“上衣”“裤子”“帽子”“鞋”。各尺寸 SKU 是导入/核价最小单位；price 均为整数分，stock 为非负整数。商品图片替换需 imageVersion++ 并 invalidateImages。数据全量更新也要 priceVersion/stockVersion++，仅成功才改变有效时间。

## 报价快照

```js
snapshot = {id,createdAt,revision,mode,clientName,title,brief,quote,
 priceVersion,stockVersion,stockAsOf,validUntil,brand,contact,
 groups:[{id,name,group,qty,reason,lines:[{sku,size,name,category,color,unit,qty,price,amount,image,crop}],
   subtotal,discount,fee,total,perSet,modelImage:null,imageLabel:'商品原图拼版'}]}
```

快照只含对客字段，无成本、内部备注。导出模块只能读取快照，不再从当前商品库取价。失效素材须在重新下载前校验，但历史数据本身不改写。

## 页面及文件归属

- 主代理：core.js、app.js、style.css、index.html、tests及最终 verification。
- 商品代理：catalog.js（catalog/imports/media 页面及文件IO），可新增 file-io.js，自己的 catalog-test.cjs；勿修改 core/app/style。
- 导出代理：delivery.js（delivery 页面与模板预览/PPTX/CSV/ZIP输出），可复制捆绑 vendor 依赖并维护 vendor/README.md；勿修改 core/app/style。
- 共享新增 CSS 写各自 catalog.css/delivery.css，主样式使用 `.btn.primary` `.btn.secondary` `.panel` `.table-wrap` `.field` `.badge` `.grid` `.empty` `.page-heading`。

每个页面所有重要动作要有真实本地状态或实际文件结果；外部服务部分明确演示来源。禁止只有 toast 的虚假完成。
