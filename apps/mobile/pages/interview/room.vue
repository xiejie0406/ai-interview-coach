<script setup>
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getInterview, startInterview, submitInterviewAnswer, applyInterviewCommand, voicePreflight, openVoiceSession, confirmTranscript, grantConsent } from '@/api/interview'
import { currentPolicies } from '@/api/session'

const snapshot=ref(null),etag=ref(''),answer=ref(''),error=ref(''),loading=ref(true),busy=ref(false)
const voiceState=ref('IDLE'),consent=ref(false),recordedBytes=ref(0),transcript=ref(null),ttsState=ref('IDLE')
let interviewId='',recorder=null,mediaStream=null,mediaRecorder=null,socket=null,voiceHandle=null
let clientSequence=0,lastServerSequence=0,totalBytes=0,totalDuration=0,chunkQueue=Promise.resolve()
let ttsChunks=[],ttsAudio=null,ttsObjectUrl='',pendingChunks=new Map(),serverPaused=false
const currentTurn=computed(()=>snapshot.value?.state==='IN_PROGRESS'?[...(snapshot.value?.turns||[])].reverse().find(turn=>turn.state==='QUESTION_COMMITTED'):null)
const terminal=computed(()=>['COMPLETED','CANCELLED','FAILED_FINAL'].includes(snapshot.value?.state||''))
const lowConfidence=computed(()=>transcript.value?.lowConfidenceSpans?.length||0)

function apply(response){snapshot.value=response.data;etag.value=response.etag||`"v${response.data.version}"`}
async function load(){loading.value=true;error.value='';try{apply(await getInterview(interviewId))}catch(cause){if(cause?.status===401){uni.redirectTo({url:'/pages/login'});return}error.value=cause instanceof Error?cause.message:'会话恢复失败'}finally{loading.value=false}}
async function start(){busy.value=true;error.value='';try{apply(await startInterview(interviewId,etag.value))}catch(cause){error.value=cause instanceof Error?cause.message:'开始面试失败'}finally{busy.value=false}}
async function submit(){if(!currentTurn.value||!answer.value.trim())return;busy.value=true;error.value='';try{if(transcript.value){await confirmTranscript(transcript.value.transcriptId,transcript.value.transcriptVersionId,transcript.value.transcriptVersion,answer.value.trim());transcript.value=null;await load()}else{apply(await submitInterviewAnswer(interviewId,etag.value,{turnId:currentTurn.value.turnId,turnSequence:currentTurn.value.sequence,text:answer.value.trim()}))}answer.value='';cleanupVoice(true);voiceState.value='IDLE'}catch(cause){error.value=cause instanceof Error?cause.message:'回答提交失败'}finally{busy.value=false}}
async function command(name){cancelVoice('SESSION_COMMAND');busy.value=true;error.value='';try{apply(await applyInterviewCommand(interviewId,name,etag.value))}catch(cause){error.value=cause instanceof Error?cause.message:'操作失败'}finally{busy.value=false}}

async function grantVoicePolicies(){const policies=(await currentPolicies()).policies||[];for(const purpose of ['VOICE_CAPTURE','MODEL_PROCESSING']){const policy=policies.find(item=>item.purpose===purpose);if(!policy)throw new Error(`缺少 ${purpose} 服务端政策`);await grantConsent(purpose,policy.versionId)}}

async function beginVoice(){
  if(!consent.value){error.value='请先确认语音用途说明';return}
  error.value='';transcript.value=null;answer.value='';voiceState.value='PREPARING'
  try{
    await grantVoicePolicies()
    // #ifdef H5
    const codecs=['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/wav'].filter(type=>MediaRecorder.isTypeSupported(type))
    if(!codecs.length)throw new Error('当前浏览器没有可用录音格式')
    const preflight=await voicePreflight(interviewId,currentTurn.value.turnId,codecs)
    if(!preflight.enabled||preflight.consentRequired)throw new Error(preflight.unavailableReasonCode||'服务端语音能力不可用')
    const codec=codecs.find(item=>preflight.supportedCodecs.includes(item));if(!codec)throw new Error('服务端不支持当前录音格式')
    voiceHandle=await openVoiceSession(interviewId,currentTurn.value.turnId,codec,snapshot.value.version)
    connectVoiceSocket()
    // #endif
    // #ifndef H5
    recorder=uni.getRecorderManager()
    recorder.onStart(()=>{voiceState.value='LISTENING'})
    recorder.onStop(result=>{recordedBytes.value=result?.tempFileSize||0;voiceState.value='UNAVAILABLE';error.value='录音已完成，但当前非 H5 平台尚未接入分片上传，请改用文字回答。'})
    recorder.onError(()=>{voiceState.value='UNAVAILABLE';error.value='录音不可用，请直接输入文字回答。'})
    recorder.start({duration:120000,sampleRate:16000,numberOfChannels:1,encodeBitRate:48000,format:'mp3'})
    // #endif
  }catch(cause){voiceState.value='UNAVAILABLE';error.value=(cause instanceof Error?cause.message:'语音准备失败')+'，可直接输入文字回答。';cleanupVoice(true)}
}

// #ifdef H5
function connectVoiceSocket(){releaseTtsAudio();const protocol=location.protocol==='https:'?'wss:':'ws:';const token=uni.getStorageSync('App-Token');if(!token){error.value='登录状态已失效，请重新登录。';voiceState.value='UNAVAILABLE';return}socket=new WebSocket(`${protocol}//${location.host}${voiceHandle.websocketPath}`,['aic.voice.v1',`ruoyi-bearer.${token}`]);clientSequence=0;lastServerSequence=voiceHandle.initialServerSequence;totalBytes=0;totalDuration=0;recordedBytes.value=0;socket.onopen=()=>sendVoice('client.hello',{socketTicket:voiceHandle.socketTicket,protocolVersion:1,resumeFromServerSequence:voiceHandle.initialServerSequence,capabilities:['BASE64_AUDIO','TTS_PLAYBACK','TRANSCRIPT_REVIEW']});socket.onmessage=event=>handleServerMessage(JSON.parse(String(event.data)));socket.onerror=()=>{error.value='语音连接失败，可直接输入文字回答。';voiceState.value='UNAVAILABLE'};socket.onclose=()=>{if(!['REVIEWING','UNAVAILABLE','IDLE'].includes(voiceState.value))voiceState.value='UNAVAILABLE'}}
async function handleServerMessage(message){if(message.sequence!==lastServerSequence+1){error.value='语音消息序号异常，请改用文字回答。';cancelVoice('SERVER_SEQUENCE_GAP');return}lastServerSequence=message.sequence;if(message.type==='server.hello')await beginCapture();else if(message.type==='server.ack'){const accepted=Number(message.data.acceptedClientSequence);if(Number.isSafeInteger(accepted))for(const sequence of pendingChunks.keys())if(sequence<=accepted)pendingChunks.delete(sequence);if(serverPaused&&pendingChunks.size<(voiceHandle?.maxInFlightChunks||8)){serverPaused=false;if(mediaRecorder?.state==='paused')mediaRecorder.resume()}}else if(message.type==='server.flow-control'){serverPaused=message.data.paused===true;if(serverPaused&&mediaRecorder?.state==='recording')mediaRecorder.pause();if(!serverPaused&&mediaRecorder?.state==='paused')mediaRecorder.resume()}else if(message.type==='voice.turn.state'&&message.data.state==='TRANSCRIBING')voiceState.value='TRANSCRIBING';else if(message.type==='asr.final'){transcript.value=message.data;answer.value=message.data.text;voiceState.value='REVIEWING';cleanupVoice(false)}else if(message.type==='speech.failed'||message.type==='voice.turn.degraded'){error.value=`语音识别失败（${message.data.reasonCode||'UNKNOWN'}），可直接输入文字回答。`;voiceState.value='UNAVAILABLE';cleanupVoice(false)}else if(message.type==='server.nack'||message.type==='server.resync-required'){error.value='语音连接需要重新同步，请改用文字或重新录音。';cancelVoice('SERVER_RESYNC_REQUIRED')}else if(message.type==='tts.state'){if(message.data.state==='STARTED'){ttsChunks=[];ttsState.value='BUFFERING'}else if(message.data.state==='COMPLETED'&&!ttsChunks.length){ttsState.value='FAILED'}else if(message.data.state==='FAILED'){releaseTtsAudio();ttsState.value='FAILED'}else if(message.data.state==='CANCELLED'){releaseTtsAudio();ttsState.value='IDLE'}}else if(message.type==='tts.chunk'){ttsChunks.push(base64ToBytes(String(message.data.bytesBase64||'')));if(message.data.endOfOutput===true)playTts()}}
async function beginCapture(){mediaStream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:false});mediaRecorder=new MediaRecorder(mediaStream,{mimeType:voiceHandle.codec});mediaRecorder.ondataavailable=event=>{if(event.data.size<=0)return;chunkQueue=chunkQueue.then(async()=>{const bytes=await event.data.arrayBuffer();if(bytes.byteLength>voiceHandle.maxChunkBytes||totalBytes+bytes.byteLength>voiceHandle.maxBytes||totalDuration+250>voiceHandle.maxDurationSeconds*1000||serverPaused||pendingChunks.size>=(voiceHandle.maxInFlightChunks||8)||pendingBufferedDuration()+250>(voiceHandle.maxBufferedDurationMs||4000)){cancelVoice('AUDIO_LIMIT_OR_BACKPRESSURE');return}totalBytes+=bytes.byteLength;totalDuration+=250;recordedBytes.value=totalBytes;const sequence=sendVoice('client.audio.chunk',{bytesBase64:arrayBufferToBase64(bytes),durationMs:250});if(sequence)pendingChunks.set(sequence,250)})};mediaRecorder.onstop=()=>{chunkQueue.then(()=>sendVoice('client.audio.stop',{totalBytes,totalDurationMs:totalDuration})).catch(()=>cancelVoice('CHUNK_SEND_FAILED'))};mediaRecorder.onerror=()=>cancelVoice('RECORDER_ERROR');sendVoice('client.audio.start',{codec:voiceHandle.codec,sampleRate:48000,channelCount:1});mediaRecorder.start(250);voiceState.value='LISTENING'}
function pendingBufferedDuration(){let total=0;pendingChunks.forEach(duration=>{total+=duration});return total}
function sendVoice(type,data){if(!socket||socket.readyState!==WebSocket.OPEN||!voiceHandle)return 0;clientSequence+=1;const sequence=clientSequence;socket.send(JSON.stringify({messageId:crypto.randomUUID(),type,voiceSessionId:voiceHandle.voiceSessionId,sessionId:interviewId,turnId:currentTurn.value?.turnId,generation:voiceHandle.socketGeneration,sequence,ackSequence:lastServerSequence,occurredAt:new Date().toISOString(),schemaVersion:1,data}));return sequence}
function arrayBufferToBase64(buffer){const bytes=new Uint8Array(buffer);let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,Math.min(i+0x8000,bytes.length)));return btoa(binary)}
function base64ToBytes(value){const binary=atob(value);return Uint8Array.from(binary,char=>char.charCodeAt(0))}
function playTts(){const blob=new Blob(ttsChunks,{type:'audio/ogg;codecs=opus'});releaseTtsAudio();ttsObjectUrl=URL.createObjectURL(blob);ttsAudio=new Audio(ttsObjectUrl);ttsAudio.onended=()=>{releaseTtsAudio();ttsState.value='IDLE'};ttsAudio.onerror=()=>{releaseTtsAudio();ttsState.value='FAILED'};ttsState.value='PLAYING';ttsAudio.play().catch(()=>{ttsState.value='FAILED'})}
function cancelTts(){if(socket?.readyState===WebSocket.OPEN)sendVoice('client.tts.cancel',{reasonCode:'USER_CANCELLED_PLAYBACK'});releaseTtsAudio();ttsState.value='IDLE'}
function releaseTtsAudio(){try{ttsAudio?.pause()}catch{}ttsAudio=null;if(ttsObjectUrl)URL.revokeObjectURL(ttsObjectUrl);ttsObjectUrl='';ttsChunks=[]}
// #endif

function finishVoice(){try{recorder?.stop()}catch{}/* #ifdef H5 */try{if(mediaRecorder?.state==='recording')mediaRecorder.stop()}catch{}/* #endif */}
function cancelVoice(reason='USER_CANCELLED_RECORDING'){/* #ifdef H5 */try{if(socket?.readyState===WebSocket.OPEN&&voiceState.value==='LISTENING')sendVoice('client.audio.cancel',{reasonCode:reason})}catch{}/* #endif */cleanupVoice(true);if(voiceState.value!=='REVIEWING')voiceState.value='IDLE'}
function cleanupVoice(closeSocket){try{recorder?.stop()}catch{}/* #ifdef H5 */try{if(mediaRecorder?.state==='recording'||mediaRecorder?.state==='paused')mediaRecorder.stop()}catch{}try{mediaStream?.getTracks().forEach(track=>track.stop())}catch{}const currentSocket=socket;if(closeSocket&&currentSocket&&currentSocket.readyState<=WebSocket.OPEN)currentSocket.close(1000,'client_cleanup');/* #endif */pendingChunks.clear();serverPaused=false;recorder=null;mediaRecorder=null;mediaStream=null;if(closeSocket)socket=null}

onLoad(options=>{interviewId=options?.interviewId||'';if(!interviewId){error.value='缺少面试 ID';loading.value=false}else load()})
onUnload(()=>{cancelVoice('COMPONENT_UNMOUNTED');releaseTtsAudio()})
</script>

<template><view class="page"><view class="header"><text class="eyebrow">INTERVIEW ROOM</text><text class="title">模拟面试</text><text class="hint">{{snapshot?.mode==='CASCADE_VOICE'?'语音模式':'文字模式'}} · {{snapshot?.state||'加载中'}}</text></view>
  <view v-if="error" class="error"><text>{{error}}</text><button v-if="loading" size="mini" @click="load">重试</button></view><text v-if="loading" class="hint">正在恢复面试会话…</text>
  <view v-if="snapshot?.state==='READY'" class="card"><text>面试计划已准备好</text><button type="primary" :loading="busy" :disabled="busy" @click="start">开始面试</button></view><view v-if="snapshot?.state==='PAUSED'" class="card"><text>面试已暂停，恢复后可继续回答当前题。</text></view>
  <view v-for="turn in snapshot?.turns||[]" :key="turn.turnId" class="card"><text class="hint">第 {{turn.sequence}} 题 · {{turn.state}}</text><text class="question">{{turn.questionText||'等待问题'}}</text></view>
  <view v-if="currentTurn&&!terminal" class="card answer-card"><view v-if="snapshot?.mode==='CASCADE_VOICE'" class="voice-box"><label class="consent"><checkbox :checked="consent" @click="consent=!consent"/>我同意本轮语音采集和模型转写；失败后仍可输入文字</label><button v-if="!['LISTENING','TRANSCRIBING'].includes(voiceState)" :disabled="busy||voiceState==='PREPARING'||voiceState==='REVIEWING'" @click="beginVoice">{{voiceState==='PREPARING'?'正在准备…':'开始语音回答'}}</button><button v-if="voiceState==='LISTENING'" type="warn" @click="finishVoice">停止并上传</button><text class="hint">{{voiceState==='LISTENING'?`正在录音并上传（${recordedBytes} bytes）`:voiceState==='TRANSCRIBING'?'服务端正在转写…':voiceState==='REVIEWING'?`转写完成，${lowConfidence} 处低置信，请检查文字`:''}}</text></view>
  <view v-if="ttsState!=='IDLE'" class="card"><text>{{ttsState==='PLAYING'?'正在播放下一题':ttsState==='BUFFERING'?'正在准备题目语音':'题目语音不可用，文字仍可继续'}}</text><button v-if="ttsState==='PLAYING'||ttsState==='BUFFERING'" @click="cancelTts">停止播放</button></view>
    <textarea v-model="answer" placeholder="输入回答；服务端转写完成后会填入这里，提交前可修改" maxlength="30000"/><view class="row"><button type="primary" :disabled="busy||!answer.trim()" @click="submit">{{transcript?'确认转写并提交':'确认并提交'}}</button><button v-if="snapshot?.allowedCommands?.includes('SKIP')" :disabled="busy" @click="command('skip')">跳过</button></view></view>
  <view v-if="snapshot&&!terminal" class="row controls"><button v-if="snapshot.allowedCommands?.includes('PAUSE')" :disabled="busy" @click="command('pause')">暂停</button><button v-if="snapshot.allowedCommands?.includes('RESUME')" :disabled="busy" @click="command('resume')">继续</button><button v-if="snapshot.allowedCommands?.includes('COMPLETE')" :disabled="busy" @click="command('complete')">提前结束</button></view>
  <view v-if="snapshot?.state==='COMPLETED'" class="success"><text class="section-title">面试已完成</text><text>本次 MVP 到此结束，不生成评分报告。</text></view></view></template>

<style scoped>.page{min-height:100vh;padding:30rpx;background:#f5f7fb;display:flex;flex-direction:column;gap:22rpx}.header{display:flex;flex-direction:column;gap:12rpx;padding:20rpx}.eyebrow{color:#3157d5;font-size:21rpx;font-weight:800;letter-spacing:3rpx}.title{font-size:44rpx;font-weight:900}.card,.error,.success{display:flex;flex-direction:column;gap:20rpx;padding:28rpx;background:#fff;border-radius:24rpx}.question{font-size:31rpx;line-height:1.7;font-weight:650}.hint{color:#74809a}.answer-card textarea{width:auto;min-height:240rpx;padding:22rpx;border:1px solid #d8deeb;border-radius:16rpx}.voice-box{display:flex;flex-direction:column;gap:16rpx;padding:20rpx;background:#f6f8ff;border-radius:18rpx}.consent{display:flex;align-items:flex-start;line-height:1.6}.row{display:flex;gap:14rpx}.row button{flex:1}.controls{padding-bottom:50rpx}.error{color:#b42318;background:#fff1f0}.success{background:#ecfdf3;color:#166534}.section-title{font-size:34rpx;font-weight:800}</style>
