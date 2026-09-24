<template>
  <div class="app-container fashion-materials">
    <el-alert title="先用 Chrome 图片插件下载文件，再上传到这里；系统不直连京东。明确 SKU 或款号＋颜色并人工确认后才会关联商品。" type="info" :closable="false" show-icon class="mb20" />
    <el-card shadow="never">
      <el-form label-width="100px">
        <el-form-item label="来源"><el-select v-model="sourceCode" style="width:220px"><el-option v-for="item in sources" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="图片/ZIP"><input type="file" multiple accept=".jpg,.jpeg,.png,.webp,.zip" @change="selectFiles"><div class="hint">支持 JPG/PNG/WebP 或 ZIP；ZIP 映射可在下表补充。</div></el-form-item>
      </el-form>
      <el-table v-if="mappings.length" :data="mappings" size="small">
        <el-table-column label="预览" width="92"><template #default="{row}"><el-image v-if="previewUrls[row.filename]" :src="previewUrls[row.filename]" fit="cover" class="thumbnail" /><span v-else>ZIP 内文件</span></template></el-table-column>
        <el-table-column prop="filename" label="文件" min-width="150" /><el-table-column label="SKU" width="140"><template #default="{row}"><el-input v-model="row.skuCode" placeholder="与款色二选一" /></template></el-table-column>
        <el-table-column label="款号" width="130"><template #default="{row}"><el-input v-model="row.styleCode" /></template></el-table-column><el-table-column label="颜色编码" width="120"><template #default="{row}"><el-input v-model="row.colorCode" /></template></el-table-column>
        <el-table-column label="用途" width="110"><template #default="{row}"><el-select v-model="row.usage"><el-option label="主图" value="main" /><el-option label="细节图" value="detail" /></el-select></template></el-table-column>
        <el-table-column label="主图" width="70"><template #default="{row}"><el-checkbox v-model="row.main" /></template></el-table-column><el-table-column label="颜色确认" width="95"><template #default="{row}"><el-checkbox v-model="row.colorConfirmed" /></template></el-table-column>
        <el-table-column label="提案可用" width="95"><template #default="{row}"><el-checkbox v-model="row.allowProposal" /></template></el-table-column>
      </el-table>
      <el-button class="mt20" type="primary" :disabled="!files.length || !sourceCode" :loading="loading" v-hasPermi="['fashion:product:image']" @click="preview">上传并预览</el-button>
    </el-card>
    <el-card v-if="batch" shadow="never" class="mt20"><template #header><div class="header"><span>{{batch.batchNo}} · 错误 {{batch.errorCount}}</span><el-button type="success" :disabled="batch.status!=='validated'" @click="confirm">人工确认入库</el-button></div></template>
      <el-table :data="batch.details"><el-table-column prop="businessKey" label="图片项" min-width="180" /><el-table-column label="像素" width="110"><template #default="{row}">{{row.normalizedData?.width&&row.normalizedData?.height?`${row.normalizedData.width}×${row.normalizedData.height}`:'未识别'}}</template></el-table-column><el-table-column prop="status" label="状态" width="90" /><el-table-column label="错误/待办" min-width="260"><template #default="{row}"><div v-for="error in row.errors" :key="error.code" class="row-error">{{error.message}}</div><span v-if="!row.errors.length">映射明确，待最终确认</span></template></el-table-column></el-table>
    </el-card>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDicts } from '@/api/system/dict/data'
import { confirmMaterials, previewMaterials, type ImageMapping } from '@/api/fashion/material'
import type { ImportBatchView } from '@/api/fashion/types'
interface Option{label:string;value:string}
const sources=ref<Option[]>([]),sourceCode=ref(''),files=ref<File[]>([]),mappings=ref<ImageMapping[]>([]),batch=ref<ImportBatchView>(),loading=ref(false),previewUrls=reactive<Record<string,string>>({})
function clearPreviews(){for(const url of Object.values(previewUrls))URL.revokeObjectURL(url);for(const key of Object.keys(previewUrls))delete previewUrls[key]}
function selectFiles(event:Event){clearPreviews();files.value=Array.from((event.target as HTMLInputElement).files??[]);const capturedAt=new Date().toISOString();for(const file of files.value){if(!file.name.toLowerCase().endsWith('.zip'))previewUrls[file.name]=URL.createObjectURL(file)}mappings.value=files.value.filter(file=>!file.name.toLowerCase().endsWith('.zip')).map(file=>({filename:file.name,skuCode:file.name.split('__')[0]||'',usage:'detail',main:false,colorConfirmed:false,sourceType:'jd_plugin',capturedAt,allowAi:true,allowProposal:true,allowEcommerce:false}))}
async function preview(){loading.value=true;try{const response=await previewMaterials(files.value,sourceCode.value,new Date().toISOString(),mappings.value);batch.value=response.data;for(const detail of response.data.details){const filename=String(detail.rawData?.filename??'');if(filename&&!mappings.value.some(item=>item.filename===filename)){mappings.value.push({filename,skuCode:'',usage:'detail',main:false,colorConfirmed:false,sourceType:'jd_plugin',capturedAt:new Date().toISOString(),allowAi:true,allowProposal:true,allowEcommerce:false})}}ElMessage.success(response.data.errorCount?'已列出待匹配项；修正映射后可重新预览':'预览通过，请人工确认入库')}finally{loading.value=false}}
async function confirm(){if(!batch.value)return;const response=await confirmMaterials(batch.value.batchId,batch.value.rowVersion);batch.value=response.data;ElMessage.success('图片素材已关联商品')}
onMounted(async()=>{const response=await getDicts('fashion_product_source');sources.value=(response.data??[]).filter((item:any)=>String(item.status)==='0').map((item:any)=>({label:String(item.dictLabel),value:String(item.dictValue)}))})
onBeforeUnmount(clearPreviews)
</script>
<style scoped>.hint{margin-left:12px;color:var(--el-text-color-secondary)}.mt20{margin-top:20px}.header{display:flex;justify-content:space-between}.row-error{color:var(--el-color-danger)}.thumbnail{width:64px;height:64px;border-radius:4px}</style>
