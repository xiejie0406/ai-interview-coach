<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useWorkspaceStore } from '../../stores/workspace'
import { useSessionStore } from '../../stores/session'
import { collectionRequest } from './collection-request'
import type { CollectionAction, CollectionBatch, CollectionCuration, CollectionDetail, CollectionPage, CollectionSnapshot, CollectionVersionPage } from '../../../../shared/contracts/collection'

const workspace = useWorkspaceStore(); const session = useSessionStore()
const page = ref<CollectionPage>({ items: [], total: 0, page: 1, pageSize: 20 })
const q = ref(''); const deleted = ref(false); const selected = ref<string[]>([])
const appliedQuery = ref('')
const detail = ref<CollectionDetail | null>(null); const snapshotId = ref('')
const snapshot = ref<CollectionSnapshot | null>(null)
const versions = ref<CollectionVersionPage>({ items: [], total: 0, page: 1, pageSize: 20 })
const images = reactive<Record<string, string>>({}); const busy = ref(false); const error = ref(''); const notice = ref('')
const thumbnails = reactive<Record<string, string>>({})
const tab = ref('fields'); const creating = ref(false); const exportAll = ref(false)
const draft = reactive({ title: '', price: '', sourceUrl: '', notes: '', idempotencyKey: crypto.randomUUID() })
const curation = ref<CollectionCuration>({ revision: 0, notes: '', tags: [], overrides: {}, removedImageIds: [], mainImageId: null, imageOrder: [] })
const tags = ref(''); const correctedTitle = ref(''); const correctedPrice = ref('')
let requestId = 0; let detailRequest = 0
const can = (action: string) => session.permissions.includes('*:*:*') || session.permissions.includes(`aden:collection:${action}`)
async function call<T>(action: CollectionAction, input: Record<string, unknown>): Promise<T> {
  const context = workspace.context()
  const result = await window.adenDesktop.collection.execute<T>(collectionRequest(context, action, input))
  const now = workspace.context()
  if (now.workspaceId !== context.workspaceId || now.sessionEpoch !== context.sessionEpoch || now.workspaceEpoch !== context.workspaceEpoch) throw new Error('工作空间已变化，请重新操作')
  return result.value
}
async function run(action: () => Promise<void>): Promise<void> {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await action() } catch (e) { error.value = e instanceof Error ? e.message : '操作失败，请重试' } finally { busy.value = false }
}
async function load(number = 1, keepSelection = false): Promise<void> {
  const current = ++requestId
  const priorSelection = [...selected.value]
  const result = await call<CollectionPage>('list', { q: appliedQuery.value, deleted: deleted.value, page: number })
  if (current === requestId) {
    page.value = result; selected.value = keepSelection ? priorSelection.filter(id => result.items.some(item => item.itemId === id)) : []
    for (const key of Object.keys(thumbnails)) delete thumbnails[key]
    void (async () => { for (const item of result.items) {
      if (!item.thumbnailAssetId) continue
      try { const data = await call<string>('image', { assetId: item.thumbnailAssetId, thumbnail: true }); if (current === requestId) thumbnails[item.thumbnailAssetId] = data }
      catch { /* 列表缩略图失败不阻断数据，详情读取会显示错误。 */ }
      if (current !== requestId) break
    } })()
  }
}
async function open(itemId: string): Promise<void> {
  const current = ++detailRequest
  const result = await call<CollectionDetail>('detail', { itemId })
  if (current !== detailRequest) return
  detail.value = result; snapshotId.value = result.snapshotId; snapshot.value = result.currentSnapshot ?? result.snapshots[0]
  curation.value = { revision: result.curation.revision, notes: result.curation.notes ?? '', tags: [...(result.curation.tags ?? [])], overrides: { ...result.curation.overrides }, removedImageIds: [...(result.curation.removedImageIds ?? [])], mainImageId: result.curation.mainImageId ?? null, imageOrder: [...(result.curation.imageOrder ?? [])] }
  if (result.curation.snapshotId && result.curation.snapshotId !== result.snapshotId) {
    curation.value.overrides = {}; curation.value.removedImageIds = []; curation.value.mainImageId = null; curation.value.imageOrder = []
  }
  tags.value = curation.value.tags.join('，'); correctedTitle.value = curation.value.overrides.title ?? ''; correctedPrice.value = curation.value.overrides.price ?? ''
  for (const key of Object.keys(images)) delete images[key]
  await loadVersions(1)
}
async function loadVersions(number: number): Promise<void> {
  if (!detail.value) return
  const current = detailRequest
  const result = await call<CollectionVersionPage>('versions', { itemId: detail.value.itemId, page: number })
  if (current === detailRequest) versions.value = result
}
async function selectSnapshot(id: string): Promise<void> {
  if (!detail.value) return
  const current = detailRequest; const itemId = detail.value.itemId
  snapshotId.value = id; snapshot.value = null
  for (const key of Object.keys(images)) delete images[key]
  const result = await call<CollectionSnapshot>('snapshot', { itemId, snapshotId: id })
  if (current === detailRequest && snapshotId.value === id) snapshot.value = result
}
watch(snapshot, async (value) => {
  if (!value) return
  const current = detailRequest
  for (const image of value.images) {
    if (current !== detailRequest) break
    if (!image.assetId || images[image.assetId]) continue
    try { const data = await call<string>('image', { assetId: image.assetId }); if (current === detailRequest) images[image.assetId] = data }
    catch (e) { if (current === detailRequest) error.value = e instanceof Error ? e.message : '图片读取失败' }
  }
})
watch(() => [session.context.sessionEpoch, session.context.workspaceEpoch, workspace.selectedId], () => {
  ++requestId; ++detailRequest; detail.value = null; selected.value = []; page.value = { items: [], total: 0, page: 1, pageSize: 20 }; creating.value = false
  snapshot.value = null; versions.value = { items: [], total: 0, page: 1, pageSize: 20 }
  Object.assign(draft, { title: '', price: '', sourceUrl: '', notes: '', idempotencyKey: crypto.randomUUID() })
  curation.value = { revision: 0, notes: '', tags: [], overrides: {}, removedImageIds: [], mainImageId: null, imageOrder: [] }
  tags.value = ''; correctedTitle.value = ''; correctedPrice.value = ''; notice.value = ''; error.value = ''
  q.value = ''; appliedQuery.value = ''; deleted.value = false; exportAll.value = false
  for (const key of Object.keys(images)) delete images[key]
  for (const key of Object.keys(thumbnails)) delete thumbnails[key]
  if (workspace.selectedId && session.context.authenticated) void load().catch(e => { error.value = e instanceof Error ? e.message : '读取采集库失败' })
}, { deep: true })
onMounted(() => { if (workspace.selectedId) void run(() => load()) })
onBeforeUnmount(() => { ++requestId; ++detailRequest })
async function filter(): Promise<void> { appliedQuery.value = q.value; selected.value = []; detail.value = null; ++detailRequest; await load(); notice.value = '已更新筛选，已清空选择' }
async function create(): Promise<void> {
  await run(async () => { const result = await call<CollectionDetail>('create', { ...draft }); creating.value = false; await load(); await open(result.itemId); Object.assign(draft, { title: '', price: '', sourceUrl: '', notes: '', idempotencyKey: crypto.randomUUID() }); notice.value = '商品已保存，可继续添加本地图片' })
}
async function batch(): Promise<void> {
  const ids = [...selected.value]
  if (!ids.length || !window.confirm(deleted.value ? `恢复所选 ${ids.length} 个商品？` : `将所选 ${ids.length} 个商品及全部版本移入回收站？可以恢复。`)) return
  await run(async () => { const result = await call<CollectionBatch>(deleted.value ? 'restore' : 'trash', { itemIds: ids }); await load(); detail.value = null; ++detailRequest; notice.value = result.results.map(r => r.success ? `${r.itemId}：成功` : `${r.itemId}：${r.error ?? '失败'}`).join('；') })
}
async function saveCuration(): Promise<void> {
  if (!detail.value || snapshotId.value !== detail.value.snapshotId) return
  const itemId = detail.value.itemId
  await run(async () => {
    const overrides = { ...curation.value.overrides }
    if (correctedTitle.value) overrides.title = correctedTitle.value; else delete overrides.title
    if (correctedPrice.value) { if (!/^\d+(\.\d{1,4})?$/.test(correctedPrice.value)) throw new Error('纠错价格应为非负金额'); overrides.price = correctedPrice.value } else delete overrides.price
    await call('curation', { itemId, curation: { ...curation.value, snapshotId: snapshotId.value, tags: tags.value.split(/[,，]/).map(t => t.trim()).filter(Boolean), overrides } }); await open(itemId); await load(page.value.page, true); notice.value = '人工补充已保存，原始快照保持不变'
  })
}
function toggleImage(imageId: string): void {
  const restoring = curation.value.removedImageIds.includes(imageId)
  curation.value.removedImageIds = restoring ? curation.value.removedImageIds.filter(id => id !== imageId) : [...curation.value.removedImageIds, imageId]
  if (!restoring && curation.value.mainImageId === imageId) curation.value.mainImageId = null
}
function moveImage(imageId: string, delta: number): void {
  const ids = orderedImages.value.map(i => i.imageId); const from = ids.indexOf(imageId); const to = from + delta
  if (to < 0 || to >= ids.length) return
  ;[ids[from], ids[to]] = [ids[to], ids[from]]; curation.value.imageOrder = ids
}
const orderedImages = computed(() => [...(snapshot.value?.images ?? [])].sort((a, b) => {
  if (snapshotId.value !== detail.value?.snapshotId) return a.order - b.order
  const ai = curation.value.imageOrder.indexOf(a.imageId); const bi = curation.value.imageOrder.indexOf(b.imageId)
  return (ai < 0 ? 1000 + a.order : ai) - (bi < 0 ? 1000 + b.order : bi)
}))
const imageRemoved = (imageId: string) => snapshotId.value === detail.value?.snapshotId && curation.value.removedImageIds.includes(imageId)
const imageMain = (imageId: string) => snapshotId.value === detail.value?.snapshotId && curation.value.mainImageId === imageId
async function upload(): Promise<void> {
  if (!detail.value) return
  const itemId = detail.value.itemId
  await run(async () => { const result = await call<{ canceled: boolean; uploaded: number }>('upload', { itemId, snapshotId: snapshotId.value }); await open(itemId); await load(page.value.page, true); notice.value = result.canceled ? '已取消添加图片' : `已保存 ${result.uploaded} 张本地图片` })
}
async function exportItems(format: 'XLSX' | 'ZIP', historical = false): Promise<void> {
  await run(async () => {
    let ids = historical && detail.value ? [detail.value.itemId] : [...selected.value]
    if (!ids.length && exportAll.value) {
      if (page.value.total > 100) throw new Error('单次最多导出 100 个商品，请缩小筛选范围')
      if (!window.confirm(`导出当前筛选的 ${page.value.total} 个商品？`)) return
      for (let p = 1; ids.length < page.value.total; p++) {
        const result = await call<CollectionPage>('list', { q: appliedQuery.value, deleted: false, page: p }); if (!result.items.length) break; ids.push(...result.items.map(i => i.itemId))
      }
      ids = [...new Set(ids)]
      if (ids.length !== page.value.total) throw new Error('商品列表已变化，请刷新后重新确认导出数量')
    }
    if (!ids.length) throw new Error('请选择商品或勾选导出当前筛选全部')
    const input: Record<string, unknown> = { itemIds: ids, format }
    if (historical && detail.value) input.snapshotId = snapshotId.value
    const result = await call<{ canceled: boolean }>('export', input); notice.value = result.canceled ? '已取消导出' : '导出文件已保存'
  })
}
const display = (value: unknown) => value === undefined || value === null || value === '' ? '未采集' : typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
const fieldName = (key: string) => ({ title: '商品标题', price: '价格数值', priceText: '价格原文', sourceUrl: '来源链接', sku: '商品 SKU', specification: '所选规格', shop: '店铺', parameters: '商品参数', description: '商品描述', stock: '库存原文', delivery: '配送原文' }[key] ?? key)
</script>

<template>
  <header class="topbar"><div><p class="eyebrow">商品采集</p><h1>商品库</h1><p class="lede">在 Chrome 自行打开商品详情页，点击扩展采集；在这里查看、整理和导出。</p></div><button :disabled="busy || !workspace.selectedId || !can('create')" @click="creating = !creating">手动新增</button></header>
  <details class="collection-help"><summary>如何连接 Chrome 扩展</summary><p>安装并启用 Aden 商品采集扩展，保持 Aden 已登录并选中工作空间。在 Chrome 点击扩展工具栏，首次连接时在 Aden 确认配对。每次采集只读取你当前授权的详情页。</p><p>当前工作空间：{{ workspace.selected?.displayName ?? '尚未选择，请前往任务中心选择' }}。图片缺失和未加载内容会单独标注。</p></details>
  <p v-if="error" role="alert" class="error-banner">{{ error }}</p><p v-if="notice" role="status" class="collection-notice">{{ notice }}</p>
  <form v-if="creating" class="collection-panel collection-form" @submit.prevent="create"><h2>手动新增商品</h2><label>标题（必填）<input v-model.trim="draft.title" required maxlength="500" autofocus /></label><label>价格（可留空）<input v-model="draft.price" inputmode="decimal" pattern="[0-9]+(\.[0-9]{1,4})?" /></label><label>来源链接（仅记录）<input v-model.trim="draft.sourceUrl" maxlength="2000" /></label><label>备注<textarea v-model="draft.notes" maxlength="2000" /></label><p>保存后可选择本机图片上传，来源会标为人工添加。</p><div class="collection-buttons"><button type="submit" :disabled="busy">保存商品</button><button type="button" :disabled="busy" @click="creating = false">取消</button></div></form>
  <form class="toolbar-card collection-toolbar" @submit.prevent="run(filter)"><label>库内搜索<input v-model="q" placeholder="商品标题或 SKU" maxlength="200" /></label><label>范围<select v-model="deleted" :disabled="busy" @change="run(filter)"><option :value="false">商品库</option><option :value="true">回收站</option></select></label><button :disabled="busy || !workspace.selectedId">查询 / 刷新</button></form>
  <div class="collection-actions"><span>共 {{ page.total }} 个 · 已选 {{ selected.length }} 个（当页）</span><button :disabled="busy || !selected.length || !can(deleted ? 'restore' : 'delete')" @click="batch">{{ deleted ? '恢复所选' : '移入回收站' }}</button><template v-if="!deleted"><label><input v-model="exportAll" type="checkbox" :disabled="selected.length > 0" /> 无选择时导出当前筛选全部（最多 100 个）</label><button :disabled="busy || !can('export') || (!selected.length && !exportAll)" @click="exportItems('XLSX')">导出 Excel</button><button :disabled="busy || !can('export') || (!selected.length && !exportAll)" @click="exportItems('ZIP')">Excel + 图片 ZIP</button></template></div>
  <p v-if="busy" role="status">正在处理，请稍候…</p>
  <section class="collection-layout" :class="{ 'with-detail': detail }"><div><div class="collection-table"><table><thead><tr><th><input type="checkbox" aria-label="选择当页全部商品" :checked="page.items.length > 0 && selected.length === page.items.length" @change="selected = ($event.target as HTMLInputElement).checked ? page.items.map(i => i.itemId) : []" /></th><th>商品</th><th>价格 / 规格</th><th>来源 / SKU</th><th>保存状态</th><th>更新时间</th></tr></thead><tbody><tr v-for="item in page.items" :key="item.itemId"><td><input v-model="selected" :value="item.itemId" type="checkbox" :aria-label="`选择 ${item.title}`" /></td><td><img v-if="item.thumbnailAssetId && thumbnails[item.thumbnailAssetId]" class="collection-thumb" :src="thumbnails[item.thumbnailAssetId]" alt="商品主图" /><button class="collection-title" @click="run(() => open(item.itemId))">{{ item.title }}</button></td><td>{{ item.priceText || item.price || '未采集' }}<small>{{ item.specification }}</small></td><td>{{ item.platform }}<small>{{ item.sku ?? '手动记录' }}</small></td><td>{{ item.status }}<small v-if="item.imageTotal !== undefined">图片 {{ item.imageSaved }} / {{ item.imageTotal }}</small></td><td>{{ item.updatedAt }}</td></tr></tbody></table><p v-if="!page.items.length && !busy && !error" class="empty-card">{{ deleted ? '回收站为空' : '还没有商品；在详情页点击采集，或手动新增。' }}</p></div><div class="collection-pagination"><button :disabled="busy || page.page <= 1" @click="run(() => load(page.page - 1))">上一页</button><span>第 {{ page.page }} 页</span><button :disabled="busy || page.page * page.pageSize >= page.total" @click="run(() => load(page.page + 1))">下一页</button></div></div>
    <aside v-if="detail" class="collection-panel collection-detail"><div class="collection-detail-heading"><h2>{{ detail.title }}</h2><button @click="detail = null; ++detailRequest">关闭详情</button></div><label>采集版本<select v-model="snapshotId" :disabled="busy" @change="run(() => selectSnapshot(snapshotId))"><option v-if="snapshot && !versions.items.some(s => s.snapshotId === snapshotId)" :value="snapshotId">{{ snapshot.createdAt }} · 当前选中版本</option><option v-for="s in versions.items" :key="s.snapshotId" :value="s.snapshotId">{{ s.createdAt }} · {{ s.source }} · {{ s.status }}</option></select></label><div class="collection-buttons"><span>共 {{ versions.total }} 个版本 · 第 {{ versions.page }} 页</span><button :disabled="busy || versions.page <= 1" @click="run(() => loadVersions(versions.page - 1))">上一页版本</button><button :disabled="busy || versions.page * versions.pageSize >= versions.total" @click="run(() => loadVersions(versions.page + 1))">下一页版本</button><button :disabled="busy || snapshotId === detail.snapshotId" @click="run(() => selectSnapshot(detail!.snapshotId))">查看当前版本</button></div><div class="collection-buttons"><button v-for="entry in [['fields','商品信息'],['images','图片'],['snapshot','内容快照']]" :key="entry[0]" :aria-pressed="tab === entry[0]" @click="tab = entry[0]">{{ entry[1] }}</button></div>
      <template v-if="snapshot"><dl v-if="tab === 'fields'" class="collection-fields"><template v-for="(value,key) in snapshot.fields" :key="key"><dt>{{ fieldName(String(key)) }}（原始）</dt><dd>{{ display(value) }}</dd></template></dl><div v-if="tab === 'snapshot'"><p>已加载内容快照 · 原文只读</p><article v-for="(block,index) in snapshot.blocks" :key="index" class="snapshot-block"><strong>{{ block.type }}</strong><p>{{ block.text }}</p><small v-if="block.imageId">图片引用：{{ block.imageId }}</small></article><p v-if="!snapshot.blocks.length">没有已保存的内容区块。</p></div><div v-if="tab === 'images'"><p>图片 {{ snapshot.images.filter(i => i.assetId).length }} / {{ snapshot.images.length }} 已保存</p><button v-if="!detail.deleted" :disabled="busy || !can('edit') || snapshotId !== detail.snapshotId" @click="upload">添加本地图片</button><div class="collection-images"><article v-for="image in orderedImages" :key="image.imageId" :class="{ removed: imageRemoved(image.imageId) }"><img v-if="image.assetId && images[image.assetId]" :src="images[image.assetId]" :alt="`${image.group} 商品图片`" /><p>{{ image.group }} · {{ image.status }}<br />{{ image.error }}</p><small v-if="imageMain(image.imageId)">当前主图</small><div v-if="!detail.deleted && snapshotId === detail.snapshotId" class="collection-buttons"><button :disabled="!can('edit')" @click="curation.mainImageId = image.imageId">设主图</button><button :disabled="!can('edit')" @click="toggleImage(image.imageId)">{{ curation.removedImageIds.includes(image.imageId) ? '恢复' : '移除' }}</button><button aria-label="图片前移" :disabled="!can('edit')" @click="moveImage(image.imageId,-1)">前移</button><button aria-label="图片后移" :disabled="!can('edit')" @click="moveImage(image.imageId,1)">后移</button></div></article></div></div></template>
      <form v-if="!detail.deleted && snapshotId === detail.snapshotId" class="collection-form" @submit.prevent="saveCuration"><h3>人工补充</h3><label>备注<textarea v-model="curation.notes" maxlength="2000" /></label><label>标签（逗号分隔）<input v-model="tags" maxlength="500" /></label><label>纠错标题<input v-model="correctedTitle" maxlength="500" /></label><label>纠错价格<input v-model="correctedPrice" inputmode="decimal" /></label><button :disabled="busy || !can('edit')">保存人工补充及图片整理</button></form><div v-if="!detail.deleted" class="collection-buttons"><button :disabled="busy || !can('export')" @click="exportItems('XLSX', true)">导出此版本 Excel</button><button :disabled="busy || !can('export')" @click="exportItems('ZIP', true)">导出此版本 ZIP</button></div>
    </aside>
  </section>
</template>

<style scoped>
.collection-panel{padding:22px;border:1px solid #d8e1dc;border-radius:12px;background:#fff;color:#24392e;min-width:0}
.collection-thumb{width:56px;height:56px;object-fit:contain;display:block;margin-bottom:8px}
.collection-help{padding:16px;background:#fff;border:1px solid #d8e1dc;border-radius:12px;margin-bottom:20px}
.collection-help summary{cursor:pointer;font-weight:600}
.collection-notice{padding:12px;background:#eaf3ed;overflow-wrap:anywhere}
.collection-actions{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0}
.collection-actions label{display:flex;gap:6px;align-items:center;color:#435a4d}
.collection-layout{display:grid;gap:20px;align-items:start}
.collection-layout>div{min-width:0}
.collection-layout.with-detail{grid-template-columns:minmax(0,1fr) minmax(390px,.95fr)}
.collection-table{max-width:100%;overflow-x:auto;border:1px solid #d8e1dc;border-radius:12px;background:#fff}
.collection-table table{border-collapse:collapse;min-width:620px;width:100%;text-align:left}
th,td{padding:12px;border-bottom:1px solid #e6e9ef;vertical-align:top}
th{white-space:nowrap;background:#f8faf9;color:#344c40}
td small{display:block;color:#52675b;margin-top:5px}
td:nth-child(2){min-width:160px;max-width:260px}
.collection-table .collection-title{border:0;background:none;text-align:left;color:#235bc1;padding:0;white-space:normal;overflow-wrap:anywhere;min-width:130px;max-width:250px;font-weight:600}
.collection-pagination{display:flex;justify-content:center;align-items:center;gap:14px;margin:16px}
.collection-detail{overflow-wrap:anywhere;max-height:calc(100vh - 56px);overflow-y:auto;scrollbar-gutter:stable}
.collection-detail-heading{display:flex;align-items:flex-start;flex-wrap:wrap;gap:12px;margin-bottom:20px}
.collection-detail-heading h2{font-size:20px;line-height:1.5;flex:1;min-width:190px;margin:0;color:#24392e}
.collection-detail-heading button{flex:none}
.collection-detail>label,.collection-form label{display:grid;gap:7px;color:#435a4d;font-size:13px}
.collection-fields dt{font-weight:600;margin-top:14px;color:#435a4d}
.collection-fields dd{margin:4px 0;white-space:pre-wrap;line-height:1.6}
.collection-form{display:grid;gap:14px;margin:20px 0}
.collection-form h3{margin:8px 0;color:#24392e}
.collection-buttons{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:14px 0}
.collection-buttons>span{flex:1 0 100%;white-space:normal;line-height:1.6;color:#52675b}
.collection-images{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:14px 0}
.collection-images article{border:1px solid #d8e1dc;padding:12px;border-radius:8px;min-width:0}
.collection-images img{width:100%;height:160px;object-fit:contain;background:#f8faf9}
.collection-images .removed{border-style:dashed;background:#f1f2f4}
.snapshot-block{white-space:pre-wrap;border-bottom:1px solid #d8e1dc;padding:12px 0}
button{min-height:38px;padding:9px 13px;border:1px solid #bdcfc3;border-radius:8px;background:#eef5f0;color:#23543b;cursor:pointer;line-height:1.4;white-space:nowrap;font-weight:600}
button:hover:not(:disabled){background:#e1eee5;border-color:#799d88}
button[aria-pressed=true]{background:#2f7655;color:white;border-color:#2f7655}
button[aria-pressed=true]:hover:not(:disabled){background:#245f43;color:white;border-color:#245f43}
button:disabled{opacity:1;background:#edf0ee;color:#697b70;border-color:#d2dbd5;cursor:not-allowed}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible{outline:3px solid #2563eb;outline-offset:2px}
input[type=checkbox]{width:18px;height:18px;flex-shrink:0}
input,textarea,select{max-width:100%;min-width:0;color:#24392e}
.collection-toolbar button{color:white;background:#2f7655}
.collection-toolbar button:disabled{color:#697b70;background:#edf0ee}
@media(max-width:1200px){.collection-layout.with-detail{grid-template-columns:minmax(0,1fr)}.collection-detail{grid-row:1;max-height:calc(100vh - 48px)}.collection-toolbar{grid-template-columns:minmax(180px,1fr) 150px auto}}
</style>
