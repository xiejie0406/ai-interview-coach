import test from 'node:test';
import assert from 'node:assert/strict';
import { fakeIndexedDB } from './idb-fixture.mjs';
let blockCleanup = false;
let failCleanup = false;
let cleanupEntered;
let releaseCleanup;
globalThis.indexedDB = fakeIndexedDB({ beforeDelete: async key => {
    if (!key.endsWith('|capture')) return;
    if (failCleanup) throw new Error('合成暂存清理失败');
    if (blockCleanup) { cleanupEntered(); await new Promise(resolve => { releaseCleanup = resolve; }); }
} });
const listeners = {};
const calls = [];
let progress;
let changed = false;
let sourceSaved = false;
let failedResource = true;
let failUpload = false;
let denyHello = false;
const event = name => ({ addListener: fn => { listeners[name] = fn; } });
const record = { source: 'JD', fields: { sku: '123', title: '合成' }, images: [{ imageId: 'one', url: 'https://img10.360buyimg.com/one.png', group: 'MAIN' }, { imageId: 'two', url: 'https://img10.360buyimg.com/two.png', group: 'DETAIL' }], blocks: [], parserVersion: 'test', fingerprint: 'same', completeness: {} };
globalThis.chrome = { runtime: { id: 'mnejmmlalapfhnanlnckfdhmfpbahidm', getURL: p => 'chrome-extension://mnejmmlalapfhnanlnckfdhmfpbahidm/' + p, onMessage: event('request'), sendMessage: async (m) => { if(m.job) progress = m.job; }, connectNative: () => ({ onMessage: event('native'), onDisconnect: event('disconnect'), postMessage: m => { calls.push(m); if ((failUpload && m.type === 'asset.chunk') || (denyHello && m.type === 'hello')) { queueMicrotask(()=>listeners.native({protocolVersion:1,messageId:m.messageId,ok:false,error:{code:denyHello?'AUTH_REQUIRED':'OFFLINE',message:'合成断连或注销'}})); return; } if (m.type === 'capture.prepare')
                sourceSaved = true; queueMicrotask(() => listeners.native({ protocolVersion: 1, messageId: m.messageId, ok: true, data: m.type === 'capture.prepare' ? { generation: 7 } : m.type === 'capture.status' ? { generation: 7, images: [{ imageId: 'one', status: 'SAVED' }, { imageId: 'two', status: 'FAILED' }] } : m.type === 'capture.commit' ? { status: 'PARTIAL' } : { workspaceId: 'ws', principalId: 'user-1' } })); } }) }, action: { onClicked: event('action') }, sidePanel: { open: async () => { } }, storage: { session: { set: async () => { }, get: async () => ({ job: progress }) } }, tabs: { query: async () => [{ id: 1 }] }, scripting: { executeScript: async () => [{ documentId: changed && sourceSaved ? 'new' : 'original', result: structuredClone(record) }] }, permissions: { contains: async () => true }, alarms: { create(){}, onAlarm: event('alarm') } };
globalThis.fetch = async (url) => { if (url.includes('two') && failedResource)
    return new Response('denied', { status: 403 }); return new Response(new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0])); };
globalThis.createImageBitmap = async () => ({ width: 1, height: 1, close() { } });
await import('../background.js');
const invoke = action => new Promise(resolve => listeners.request(action, { id: chrome.runtime.id, url: chrome.runtime.getURL('panel.html') }, resolve));
test('模拟桥流程顺序等待ACK，上传文件字节并记录图片部分失败', async () => { const result = await invoke({ action: 'capture', groups: ['MAIN', 'DETAIL'] }); assert(result.ok); assert.deepEqual(calls.map(x => x.type), ['hello', 'capture.prepare', 'asset.chunk', 'capture.commit']); const chunk = calls.find(x => x.type === 'asset.chunk').payload; assert.equal(chunk.generation, 7); assert.equal(chunk.totalSize, 9); assert.equal(chunk.offset, 0); assert.equal(Buffer.from(chunk.dataBase64, 'base64')[0], 137); assert.equal(calls.at(-1).payload.failures[0].imageId, 'two'); assert.equal(progress.saved, 1); assert.match(progress.message, /部分保存/); });
test('失败图片重试先查服务端且只重新上传缺失图片', async () => { calls.length = 0; failedResource = false; const result = await invoke({ action: 'retry' }); assert(result.ok); assert.deepEqual(calls.map(x => x.type), ['hello', 'capture.status', 'asset.chunk', 'capture.commit']); assert.equal(calls.find(x => x.type === 'asset.chunk').payload.imageId, 'two'); assert.equal(progress.saved, 2); });
test('准备后document变化拒绝图片上传，保留内容并标注失败', async () => { calls.length = 0; sourceSaved = false; changed = true; const result = await invoke({ action: 'capture', groups: ['MAIN'] }); assert(result.ok); assert.equal(calls.filter(x => x.type === 'asset.chunk').length, 0); assert.match(calls.at(-1).payload.failures[0].error, /页面或规格/); });
test('不接受内容脚本伪造侧栏消息', () => { assert.equal(listeners.request({ action: 'capture' }, { id: chrome.runtime.id, url: 'https://item.jd.com/123.html' }, () => { throw new Error('不应响应'); }), undefined); });
test('拒绝未知图片分组', async () => { const result = await invoke({ action: 'capture', groups: ['COOKIE'] }); assert.equal(result.ok, false); assert.match(result.error, /分组/); });
test('上传断连后保存真实字节，原页关闭仍可只用暂存恢复且不再fetch', async () => {
    changed=false; sourceSaved=false; failedResource=false; failUpload=true;
    await invoke({action:'capture',groups:['DETAIL']});
    const captureId=progress.captureId;
    const listed=await invoke({action:'spool.list'});
    assert(listed.data.some(row=>row.captureId===captureId));
    failUpload=false; changed=true;
    const fetchBefore=globalThis.fetch;
    globalThis.fetch=async()=>{throw new Error('离线恢复不得访问源站');};
    try {
        const result=await invoke({action:'spool.resume',captureId});
        assert(result.ok,result.error);
        assert.match(progress.message,/全部保存/);
        assert(!(await invoke({action:'spool.list'})).data.some(row=>row.captureId===captureId));
    } finally {globalThis.fetch=fetchBefore;}
});
test('收到注销拒绝后清理暂存，未认证local不返回旧捕获', async () => {
    changed=false;sourceSaved=false;failUpload=true;
    await invoke({action:'capture',groups:['DETAIL']});
    denyHello=true;
    const denied=await invoke({action:'spool.list'});
    assert.equal(denied.ok,false);
    assert.equal((await invoke({action:'local'})).data.captureId,undefined);
    denyHello=false;failUpload=false;
    assert.deepEqual((await invoke({action:'spool.list'})).data,[]);
});
test('后端成功后等待IDB清理事务完成才广播采集完成', async () => {
    changed=false;sourceSaved=false;failedResource=false;failUpload=false;denyHello=false;
    blockCleanup=true;
    const entered=new Promise(resolve=>{cleanupEntered=resolve;});
    const operation=invoke({action:'capture',groups:['MAIN']});
    await entered;
    assert.equal(progress.running,true);
    assert.doesNotMatch(progress.message,/本次选定内容已保存/);
    blockCleanup=false;releaseCleanup();
    const response=await operation;
    assert.equal(response.ok,true);
    assert.equal(progress.running,false);
    assert.match(progress.message,/本次选定内容已保存/);
});
test('本地清理失败保留后端成功结果并明确本地待清理', async () => {
    failCleanup=true;sourceSaved=false;
    try {
        const response=await invoke({action:'capture',groups:['MAIN']});
        assert.equal(response.ok,true);
        assert(progress.result);
        assert.match(progress.message,/后端已确认保存.*暂存清理失败/);
        assert.equal(progress.running,false);
    } finally { failCleanup=false; }
});
