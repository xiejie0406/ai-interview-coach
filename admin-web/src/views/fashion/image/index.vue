<template>
  <div class="app-container image-workbench">
    <el-alert title="体验样例、外部上传、商品原图拼版和真实 Provider 来源严格区分；查看图片不等于复核通过。" type="info" :closable="false" show-icon class="mb16" />
    <el-card shadow="never" v-loading="loading">
      <template #header><div class="header"><strong>方案图片工作台</strong><el-button icon="Refresh" @click="load">从 Java 恢复任务</el-button></div></template>
      <el-form :inline="true" @submit.prevent="load"><el-form-item label="方案 ID"><el-input v-model="quoteId" style="width:230px" /></el-form-item><el-form-item><el-button type="primary" @click="load">载入</el-button></el-form-item></el-form>
      <template v-if="workspace">
        <el-descriptions :column="4" border><el-descriptions-item label="方案状态">{{workspace.quoteStatus}}</el-descriptions-item><el-descriptions-item label="本月已结算">¥{{workspace.monthSettledCost}}</el-descriptions-item><el-descriptions-item label="月度预算">¥{{workspace.monthlyBudget}}</el-descriptions-item><el-descriptions-item label="真实生成"><el-tag :type="workspace.providerEnabled?'success':'info'">{{workspace.providerEnabled?'已启用':'未启用'}}</el-tag></el-descriptions-item></el-descriptions>
        <el-form :inline="true" class="mt16">
          <el-form-item label="当前组合"><el-select v-model="comboId" style="width:240px"><el-option v-for="combo in currentCombos" :key="combo.id" :label="`${combo.name} / ${combo.categoryCount}品类`" :value="combo.id" /></el-select></el-form-item>
          <el-form-item label="图片类型"><el-select v-model="imageType" style="width:180px"><el-option label="模特示意图" value="model" /><el-option label="搭配示意图" value="styling" /><el-option label="电商商品图" value="ecommerce" /><el-option label="商品原图拼版" value="composition" /></el-select></el-form-item>
          <el-form-item label="候选数"><el-input-number v-model="requestedCount" :min="1" :max="4" /></el-form-item>
        </el-form>
        <div class="actions">
          <el-button type="primary" plain v-hasPermi="['fashion:image:create']" :disabled="!selectedCombo" @click="create('sample')">体验样例任务</el-button>
          <el-button type="success" plain v-hasPermi="['fashion:image:create']" :disabled="!selectedCombo" @click="create('composition')">生成原图拼版</el-button>
          <el-button type="primary" v-hasPermi="['fashion:image:create']" :disabled="!workspace.providerEnabled||!selectedCombo" @click="create('provider')">真实 Provider 生成</el-button>
          <label class="upload-button"><input type="file" accept=".jpg,.jpeg,.png,.webp" @change="upload" /><span>上传已生成图片</span></label>
        </div>
        <div v-if="!workspace.providerEnabled" class="hint">真实 Provider 默认关闭；额度和接入未放行时不会提交，也不会计费。样例任务结果只用于体验流程。</div>
      </template>
    </el-card>

    <el-row :gutter="16" class="mt16" v-if="workspace">
      <el-col :span="8"><el-card shadow="never"><template #header><strong>任务总览（{{workspace.tasks.length}}）</strong></template>
        <el-empty v-if="!workspace.tasks.length" description="暂无图片任务" />
        <div v-for="task in workspace.tasks" :key="task.id" class="task" :class="{active:selectedTask?.id===task.id}" @click="selectTask(task)">
          <div class="header"><span><el-tag size="small">{{task.sourceLabel}}</el-tag> {{typeText(task.imageType)}}</span><el-tag size="small" :type="statusType(task.displayStatus)">{{statusText(task.displayStatus)}}</el-tag></div>
          <div class="hint">{{task.id}} · {{task.requestedCount}} 张 · 费用 {{costText(task)}}</div>
          <div v-if="task.errorMessage" class="error">{{task.errorMessage}}</div>
        </div>
      </el-card></el-col>
      <el-col :span="16"><el-card shadow="never" v-if="selectedTask"><template #header><div class="header"><strong>原图与候选并排复核</strong><el-button v-if="pending(selectedTask)" type="danger" plain size="small" @click="cancel">取消任务</el-button></div></template>
        <el-alert v-if="selectedTask.stale" title="组合或商品原图已变化：此任务需要重新复核，不能自动成为当前采用图。" type="error" :closable="false" show-icon class="mb16" />
        <el-divider content-position="left">本次真实输入原图</el-divider><div class="image-grid"><figure v-for="slot in selectedTask.inputs.slots" :key="slot.slot_code"><img v-if="originalUrls[slot.slot_code]" :src="originalUrls[slot.slot_code]" :alt="slot.slot_code" /><figcaption>{{slot.slot_code}} · {{short(slot.image_hash)}}</figcaption></figure></div>
        <el-divider content-position="left">候选结果</el-divider><el-empty v-if="!selectedTask.results.length" description="任务尚未返回结果" />
        <div v-for="result in selectedTask.results" :key="result.no" class="result-card"><div class="result-image"><img v-if="resultUrls[result.no]" :src="resultUrls[result.no]" :alt="`候选 ${result.no}`" /><el-result v-else icon="error" title="结果不可用" :sub-title="result.error" /></div>
          <div class="review"><h4>候选 {{result.no}}</h4><div class="hint">{{result.width}}×{{result.height}} · {{short(result.sha256)}}</div><el-checkbox-group v-model="reviewChecks[result.no]"><el-checkbox v-for="item in checks" :key="item.value" :value="item.value">{{item.label}}</el-checkbox></el-checkbox-group>
            <el-select v-model="rejectReasons[result.no]" clearable placeholder="拒绝时选择原因" style="width:100%"><el-option v-for="item in reasons" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <div class="actions"><el-button type="success" size="small" v-hasPermi="['fashion:image:review']" @click="review(result.no,'pass')">复核通过</el-button><el-button type="danger" size="small" v-hasPermi="['fashion:image:review']" @click="review(result.no,'reject')">拒绝</el-button><el-button type="primary" size="small" :disabled="!latestPassed(result)||selectedTask.stale" @click="adopt(result.no)">采用</el-button></div>
            <div v-if="result.reviews.length" class="history">最近复核：{{reviewText(result.reviews.at(-1))}}</div>
          </div></div>
      </el-card><el-empty v-else description="从左侧选择任务" /></el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adoptQuoteImage, cancelQuoteImageTask, createQuoteImageTask, getQuoteImageOriginalContent, getQuoteImageResultContent, getQuoteImageWorkspace, reviewQuoteImage, uploadQuoteImage } from '@/api/fashion/image'
import type { QuoteImageResult, QuoteImageTask, QuoteImageWorkspace } from '@/api/fashion/types'

const route=useRoute(),router=useRouter(),quoteId=ref(String(route.query.quoteId??'')),workspace=ref<QuoteImageWorkspace>(),comboId=ref(''),imageType=ref<'model'|'styling'|'ecommerce'|'composition'>('model'),requestedCount=ref(2),loading=ref(false),selectedTask=ref<QuoteImageTask>()
const originalUrls=reactive<Record<string,string>>({}),resultUrls=reactive<Record<number,string>>({}),reviewChecks=reactive<Record<number,string[]>>({}),rejectReasons=reactive<Record<number,string>>({})
const checks=[{value:'slot_count',label:'槽位数量'},{value:'style',label:'款式'},{value:'color',label:'颜色'},{value:'logo',label:'印花/Logo'},{value:'completeness',label:'鞋帽完整性'},{value:'pose',label:'姿态自然'},{value:'quality',label:'关键细节与质量'}]
const reasons=[{value:'missing_item',label:'缺件'},{value:'wrong_style',label:'错款'},{value:'wrong_color',label:'错色'},{value:'logo_error',label:'标识错误'},{value:'abnormal_pose',label:'姿态异常'},{value:'low_quality',label:'质量不足'},{value:'other',label:'其他'}]
const currentCombos=computed(()=>workspace.value?.combinations.filter(item=>item.selected)??[]),selectedCombo=computed(()=>currentCombos.value.find(item=>item.id===comboId.value))
let timer:number|undefined,controller:AbortController|undefined
function key(prefix:string){return `${prefix}-${globalThis.crypto?.randomUUID?.()??`${Date.now()}-${Math.random().toString(36).slice(2)}`}`}
async function load(){if(!quoteId.value.trim())return;controller?.abort();controller=new AbortController();loading.value=true;try{const response=await getQuoteImageWorkspace(quoteId.value.trim(),controller.signal);workspace.value=response.data;if(!currentCombos.value.some(x=>x.id===comboId.value))comboId.value=currentCombos.value[0]?.id??'';if(selectedTask.value){const current=response.data.tasks.find(x=>x.id===selectedTask.value?.id);if(current)await selectTask(current)}await router.replace({query:{...route.query,quoteId:quoteId.value.trim()}});schedule()}finally{loading.value=false}}
async function create(mode:'sample'|'provider'|'composition'){const combo=selectedCombo.value;if(!combo||!workspace.value)return;const response=await createQuoteImageTask(quoteId.value,{comboId:combo.id,imageType:mode==='composition'?'composition':imageType.value,sourceMode:mode,requestedCount:mode==='composition'?1:requestedCount.value,parameters:{aspectRatio:imageType.value==='ecommerce'?'1:1':'3:4',promptVersion:'1.0'},requestKey:key(mode),quoteRowVersion:workspace.value.quoteRowVersion,comboRowVersion:combo.rowVersion,comboVisualHash:combo.visualHash});ElMessage.success('图片任务已创建');await load();await selectTask(response.data)}
async function upload(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0],combo=selectedCombo.value;if(!file||!combo||!workspace.value)return;const response=await uploadQuoteImage(quoteId.value,combo,workspace.value.quoteRowVersion,imageType.value,key('upload'),file);input.value='';ElMessage.success('真实图片已上传，等待人工复核');await load();await selectTask(response.data)}
async function selectTask(task:QuoteImageTask){selectedTask.value=task;clearUrls();for(const slot of task.inputs.slots){try{originalUrls[slot.slot_code]=URL.createObjectURL(await getQuoteImageOriginalContent(quoteId.value,task.comboId,slot.slot_code))}catch{originalUrls[slot.slot_code]=''}}for(const result of task.results){reviewChecks[result.no]=[];if(result.status==='success'){try{resultUrls[result.no]=URL.createObjectURL(await getQuoteImageResultContent(quoteId.value,task.id,result.no))}catch{resultUrls[result.no]=''}}}}
async function cancel(){if(!selectedTask.value)return;await ElMessageBox.confirm('取消只阻止后续采用；Provider 已处理时仍可能产生费用。','取消图片任务',{type:'warning'});await cancelQuoteImageTask(quoteId.value,selectedTask.value);ElMessage.success('取消请求已记录');await load()}
async function review(no:number,decision:'pass'|'reject'){if(!selectedTask.value)return;const reason=rejectReasons[no];if(decision==='reject'&&!reason){ElMessage.warning('请选择拒绝原因');return}const response=await reviewQuoteImage(quoteId.value,selectedTask.value,no,decision,reviewChecks[no]??[],reason);ElMessage.success(decision==='pass'?'已记录人工复核通过':'已记录拒绝原因');await load();await selectTask(response.data)}
async function adopt(no:number){if(!selectedTask.value)return;await ElMessageBox.confirm('采用后才可进入后续正式交付；系统会再次核对组合和原图版本。','采用图片',{type:'warning'});const response=await adoptQuoteImage(quoteId.value,selectedTask.value,no);ElMessage.success('图片已采用');await load();await selectTask(response.data)}
function schedule(){if(timer!==undefined)clearTimeout(timer);if(workspace.value?.tasks.some(pending))timer=window.setTimeout(load,2000)}
function pending(task:QuoteImageTask){return['queued','running','unknown','cancel_requested'].includes(task.status)}
function latestPassed(result:QuoteImageResult){return result.reviews.at(-1)?.decision==='pass'}
function reviewText(review:QuoteImageResult['reviews'][number]|undefined){return review?`${review.decision==='pass'?'通过':'拒绝'} · ${review.reason??'全部检查通过'} · ${review.time}`:''}
function statusText(value:string){return({queued:'排队中',running:'生成中',unknown:'结果未知',cancel_requested:'取消中',cancelled:'已取消',failed:'失败',pending_review:'待复核',adopted:'已采用',not_adopted:'未采用',needs_review:'版本变化待复核'} as Record<string,string>)[value]??value}
function statusType(value:string){return value==='adopted'?'success':['failed','needs_review'].includes(value)?'danger':['pending_review','unknown','cancel_requested'].includes(value)?'warning':'info'}
function typeText(value:string){return({model:'模特示意图',styling:'搭配示意图',ecommerce:'电商商品图',composition:'商品原图拼版'} as Record<string,string>)[value]??value}
function costText(task:QuoteImageTask){return task.billingStatus==='not_applicable'?'不计费':task.billingStatus==='unknown'?'未知，不自动退款':`${task.actualCost??task.estimatedCost??'待核对'} ${task.costCurrency??''}`}
function short(value?:string){return value?`${value.slice(0,10)}…`:'—'}
function clearUrls(){Object.values(originalUrls).forEach(url=>url&&URL.revokeObjectURL(url));Object.values(resultUrls).forEach(url=>url&&URL.revokeObjectURL(url));Object.keys(originalUrls).forEach(k=>delete originalUrls[k]);Object.keys(resultUrls).forEach(k=>delete resultUrls[Number(k)])}
onMounted(()=>{if(quoteId.value)load()});onBeforeUnmount(()=>{controller?.abort();if(timer!==undefined)clearTimeout(timer);clearUrls()})
</script>

<style scoped>.header,.actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.actions{justify-content:flex-start;flex-wrap:wrap;margin-top:12px}.mb16{margin-bottom:16px}.mt16{margin-top:16px}.hint{font-size:12px;color:var(--el-text-color-secondary);margin-top:8px}.error{color:var(--el-color-danger);font-size:12px}.task{padding:12px;border:1px solid var(--el-border-color);border-radius:8px;margin-bottom:10px;cursor:pointer}.task.active{border-color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.image-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}.image-grid figure{margin:0}.image-grid img,.result-image img{width:100%;height:220px;object-fit:contain;background:var(--el-fill-color-light);border-radius:6px}.image-grid figcaption{font-size:12px;margin-top:5px}.result-card{display:grid;grid-template-columns:minmax(260px,1fr) minmax(260px,1fr);gap:16px;padding:14px;border:1px solid var(--el-border-color);border-radius:8px;margin-bottom:14px}.review :deep(.el-checkbox){display:block;margin-right:0}.history{font-size:12px;color:var(--el-text-color-secondary);margin-top:10px}.upload-button input{display:none}.upload-button span{display:inline-block;padding:7px 15px;border:1px solid var(--el-border-color);border-radius:4px;cursor:pointer}.upload-button span:hover{color:var(--el-color-primary);border-color:var(--el-color-primary)}@media(max-width:900px){.result-card{grid-template-columns:1fr}}</style>
