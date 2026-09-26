<template>
  <div class="app-container delivery-page">
    <el-alert title="交付文件只读取单一已确认报价版本；生成期间商品涨价或库存变化不会混入历史文件。" type="info" :closable="false" show-icon class="mb16" />
    <el-card shadow="never" class="mb16">
      <el-form :inline="true" @submit.prevent="load">
        <el-form-item label="已确认报价 ID"><el-input v-model="quoteId" placeholder="从方案页进入或输入报价 ID" clearable /></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="load">载入交付工作区</el-button></el-form-item>
      </el-form>
      <el-descriptions v-if="workspace" :column="4" border>
        <el-descriptions-item label="方案">{{workspace.quoteTitle}}</el-descriptions-item>
        <el-descriptions-item label="版本">{{workspace.quoteNo}} / V{{workspace.versionNo}}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag type="success">已确认并冻结</el-tag></el-descriptions-item>
        <el-descriptions-item label="内容摘要"><code>{{workspace.quoteHash.slice(0,12)}}…</code></el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="workspace" shadow="never" class="mb16">
      <template #header><div class="card-header"><strong>生成客户交付文件</strong><span>同一版本、格式和渲染器重复请求会复用原任务</span></div></template>
      <div class="format-grid">
        <button v-for="format in formats" :key="format.type" class="format-card" type="button" v-hasPermi="['fashion:quote:export']" @click="create(format.type)">
          <strong>{{format.title}}</strong><span>{{format.description}}</span>
        </button>
      </div>
    </el-card>

    <el-card v-if="workspace" shadow="never">
      <template #header><div class="card-header"><strong>交付任务</strong><el-button icon="Refresh" @click="load">刷新</el-button></div></template>
      <el-table :data="workspace.files" v-loading="loading" empty-text="尚未创建交付任务">
        <el-table-column label="格式" width="110"><template #default="{row}"><el-tag>{{typeLabel(row.fileType)}}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="statusType(row.status)">{{statusLabel(row.status)}}</el-tag></template></el-table-column>
        <el-table-column label="产物" min-width="300"><template #default="{row}"><div v-if="row.files.length===0" class="muted">等待生成</div><div v-for="(file,index) in row.files" :key="file.sha256" class="artifact"><span>{{file.fileName}}</span><small>{{bytes(file.byteSize)}}<template v-if="file.pageCount"> · {{file.pageCount}} 页</template> · SHA {{file.sha256.slice(0,10)}}…</small><div><el-button link type="primary" v-hasPermi="['fashion:quote:export']" @click="download(row,index)">下载</el-button><el-button v-if="row.fileType==='jpg'" link @click="preview(row,index)">预览</el-button></div></div></template></el-table-column>
        <el-table-column label="恢复" min-width="210"><template #default="{row}"><span v-if="row.errorMessage" class="error">{{row.errorMessage}}</span><el-button v-if="row.status==='failed'&&row.retryCount<2" link type="warning" v-hasPermi="['fashion:quote:export']" @click="retry(row)">基于同版本重试</el-button><span v-if="row.retryCount">重试 {{row.retryCount}} / 2</span></template></el-table-column>
        <el-table-column label="下载" width="110"><template #default="{row}">{{row.downloadCount}} 次</template></el-table-column>
      </el-table>
      <el-alert class="mt16" title="“下载次数”只记录服务端完整写出响应；不代表文件已转发或被客户接受。" type="warning" :closable="false" />
    </el-card>

    <el-dialog v-model="previewVisible" title="已确认交付图片预览" width="min(760px, 92vw)" @closed="releasePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="交付图片预览" class="preview-image" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { downloadDeliveryArtifact, getDeliveryWorkspace, requestDeliveryFile, retryDeliveryFile } from '@/api/fashion/quoteFile'
import { FashionApiError } from '@/api/fashion/client'
import type { DeliveryFile, DeliveryFileType, DeliveryWorkspace } from '@/api/fashion/types'

const route=useRoute(),router=useRouter(),quoteId=ref(String(route.query.quoteId??'')),workspace=ref<DeliveryWorkspace>(),loading=ref(false)
const previewVisible=ref(false),previewUrl=ref('')
let controller:AbortController|undefined,pollTimer:number|undefined
const formats:Array<{type:DeliveryFileType;title:string;description:string}>=[
  {type:'pptx',title:'可编辑 PPTX',description:'16:9 原生文本、表格与嵌入图片'},
  {type:'csv',title:'报价明细 CSV',description:'UTF-8 与表格公式注入防护'},
  {type:'image_zip',title:'图片 ZIP',description:'原图、采用图及 manifest 清单'},
  {type:'jpg',title:'采用图 JPG',description:'逐组合生成真实 JPEG 文件'}]
async function load(){if(!/^\d+$/.test(quoteId.value)){workspace.value=undefined;return}controller?.abort();controller=new AbortController();loading.value=true;try{workspace.value=(await getDeliveryWorkspace(quoteId.value,controller.signal)).data;await router.replace({query:{...route.query,quoteId:quoteId.value}});schedule()}finally{loading.value=false}}
async function create(type:DeliveryFileType){try{await requestDeliveryFile(quoteId.value,type);ElMessage.success('交付任务已创建或复用');await load()}catch(error){await recover(error)}}
async function retry(file:DeliveryFile){try{await retryDeliveryFile(quoteId.value,file.id,file.rowVersion);ElMessage.success('已按同一确认版本重新排队');await load()}catch(error){await recover(error)}}
async function download(file:DeliveryFile,index:number){const artifact=file.files[index];const blob=await downloadDeliveryArtifact(quoteId.value,file.id,index+1);saveBlob(blob,artifact.fileName);ElMessage.success('文件响应已完成');await load()}
async function preview(file:DeliveryFile,index:number){releasePreview();const blob=await downloadDeliveryArtifact(quoteId.value,file.id,index+1);previewUrl.value=URL.createObjectURL(blob);previewVisible.value=true}
async function recover(error:unknown){if(error instanceof FashionApiError&&error.status===409){ElMessage.warning('数据已变化，已刷新当前任务');await load();return}throw error}
function schedule(){if(pollTimer)window.clearTimeout(pollTimer);if(workspace.value?.files.some(file=>file.status==='queued'||file.status==='running'))pollTimer=window.setTimeout(load,2000)}
function releasePreview(){if(previewUrl.value)URL.revokeObjectURL(previewUrl.value);previewUrl.value=''}
function typeLabel(type:DeliveryFileType){return({pptx:'PPTX',csv:'CSV',image_zip:'图片 ZIP',jpg:'JPG'} as const)[type]}
function statusLabel(status:string){return({queued:'排队中',running:'生成中',success:'成功',failed:'失败'} as Record<string,string>)[status]??status}
function statusType(status:string){return status==='success'?'success':status==='failed'?'danger':status==='running'?'warning':'info'}
function bytes(value:number){return value<1024?`${value} B`:value<1048576?`${(value/1024).toFixed(1)} KB`:`${(value/1048576).toFixed(1)} MB`}
function saveBlob(blob:Blob,fileName:string){const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=fileName;link.rel='noopener';link.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000)}
onMounted(load);onBeforeUnmount(()=>{controller?.abort();if(pollTimer)window.clearTimeout(pollTimer);releasePreview()})
</script>

<style scoped>.mb16{margin-bottom:16px}.mt16{margin-top:16px}.card-header{display:flex;justify-content:space-between;align-items:center;gap:16px}.card-header span,.muted,small{color:var(--el-text-color-secondary)}.format-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.format-card{min-height:96px;padding:16px;border:1px solid var(--el-border-color);border-radius:8px;background:var(--el-fill-color-blank);text-align:left;cursor:pointer}.format-card:focus-visible{outline:3px solid var(--el-color-primary-light-5);outline-offset:2px}.format-card strong,.format-card span{display:block}.format-card span{margin-top:8px;color:var(--el-text-color-secondary)}.artifact{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:2px 12px;padding:5px 0}.artifact small{grid-column:1}.artifact div{grid-row:1/3;grid-column:2;align-self:center}.error{display:block;color:var(--el-color-danger);overflow-wrap:anywhere}.preview-image{display:block;max-width:100%;max-height:68vh;margin:auto;object-fit:contain}@media(max-width:900px){.format-grid{grid-template-columns:1fr 1fr}.card-header{align-items:flex-start;flex-direction:column}.delivery-page :deep(.el-descriptions__body){overflow:auto}.artifact{grid-template-columns:1fr}.artifact div{grid-row:auto;grid-column:1}}@media(max-width:520px){.format-grid{grid-template-columns:1fr}}</style>
