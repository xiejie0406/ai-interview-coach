import { CONFIG } from './config.js';
import { extractProduct } from './parser.js';
import { approvedResource, downloadImage } from './resources.js';
import { CaptureSpool, IndexedDbStore, scrubMetadata } from './spool.js';
const spool = new CaptureSpool(new IndexedDbStore());
let identity = null;
const AUTH_ERRORS = new Set(['AUTH_REQUIRED', 'SESSION_REVOKED', 'UNAUTHENTICATED', 'FORBIDDEN']);
async function clearPrivateState() {
    identity = null; job = null; stop = true;
    await spool.clear();
    await chrome.storage.session.set({ job: null });
    chrome.runtime.sendMessage({ event: 'revoked' }).catch(() => {});
}
let port;
const pending = new Map();
let job = null;
let stop = false;
const restored = chrome.storage.session.get('job').then(({ job: saved }) => {
    if (saved)
        job = { ...saved, running: false, needsVerification: true, message: '上次采集尚需服务端确认，请连接后查询结果' };
});
function native(type, payload = {}) {
    if (type === 'hello')
        payload = { extensionId: chrome.runtime.id };
    if (!port) {
        port = chrome.runtime.connectNative(CONFIG.host);
        port.onMessage.addListener(r => { const p = pending.get(r.messageId); if (p) {
            clearTimeout(p.timer);
            pending.delete(r.messageId);
            if (r.ok) p.resolve(r.data);
            else {
                const error = new Error(r.error?.message || 'Aden 拒绝请求');
                error.code = r.error?.code;
                if (AUTH_ERRORS.has(error.code)) {
                    clearPrivateState().finally(() => p.reject(error));
                } else p.reject(error);
            }
        } });
        port.onDisconnect.addListener(() => { const message = chrome.runtime.lastError?.message || 'Aden 连接断开'; port = null; for (const p of pending.values()) {
            clearTimeout(p.timer);
            p.reject(new Error(message));
        } pending.clear(); });
    }
    const messageId = crypto.randomUUID();
    return new Promise((resolve, reject) => { const timer = setTimeout(() => { pending.delete(messageId); reject(new Error('Aden 确认超时，结果尚未确认，请查询状态')); }, 60000); pending.set(messageId, { resolve, reject, timer }); port.postMessage({ protocolVersion: 1, messageId, type, payload }); });
}
async function authenticate() {
    const connection = await native('hello');
    const next = { principalId: String(connection.principalId || ''), workspaceId: connection.workspaceId };
    if (!next.principalId || !next.workspaceId) throw new Error('Aden 未返回已认证的账号标识，请升级桌面端');
    if ((identity && (identity.principalId !== next.principalId || identity.workspaceId !== next.workspaceId)) ||
        (job?.captureId && (job.principalId !== next.principalId || job.workspaceId !== next.workspaceId))) {
        job = null; await chrome.storage.session.set({ job: null });
        chrome.runtime.sendMessage({ event: 'revoked' }).catch(() => {});
    }
    await spool.bind(next);
    identity = next;
    return connection;
}
async function status(update) { job = { ...job, ...update }; await chrome.storage.session.set({ job }); chrome.runtime.sendMessage({ event: 'progress', job }).catch(() => { }); }
async function current() { const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true }); if (!tab?.id)
    throw new Error('没有当前标签页'); return tab; }
async function parse(tabId) {
    const [r] = await chrome.scripting.executeScript({ target: { tabId }, func: extractProduct, args: [{ fixtureOrigin: CONFIG.fixtureOrigin, returnErrors: true }] });
    if (!r?.result) throw new Error('当前页授权失效，请点击工具栏重新授权');
    if (r.result.error) {
        const message = typeof r.result.error.message === 'string' ? r.result.error.message.slice(0, 300) : '页面商品结构解析失败，请反馈页面模板';
        throw new Error(message);
    }
    return { ...r.result, documentId: r.documentId };
}
async function ensurePage(tabId, before) { const after = await parse(tabId); if (after.documentId !== before.documentId || after.fingerprint !== before.fingerprint)
    throw new Error('页面或规格已经变化，请重新采集'); }
async function cleanupConfirmed(captureId, failures) {
    if (failures.length) return '';
    try { await spool.remove(identity, captureId); return ''; }
    catch { return '后端已确认保存；本机暂存清理失败，请稍后清理本机暂存'; }
}
async function saveImages(images, captureId, generation, tabId, captured, cachedOnly = false) {
    let totalBytes = 0;
    const failures = [];
    for (const img of images) {
        try {
            if (stop)
                throw new Error('用户停止了资源保存');
            const stored = (await spool.read(identity, captureId)).find(row => row.kind === 'asset' && row.imageId === img.imageId);
            let resource = stored;
            if (resource) {
                if (resource.documentId !== captured.documentId) throw new Error('暂存页面关联不一致');
                const digest = await crypto.subtle.digest('SHA-256', resource.bytes);
                const hash = [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, '0')).join('');
                if (hash !== resource.sha256) { await spool.remove(identity, captureId, img.imageId); throw new Error('暂存文件摘要校验失败，请重新采集'); }
            } else {
                if (cachedOnly) throw new Error('此图片没有离线字节，请回原页重试或重新采集');
                await ensurePage(tabId, captured);
                const url = approvedResource(img.url, CONFIG);
                if (!(CONFIG.fixtureOrigin && url.origin === CONFIG.fixtureOrigin) && !await chrome.permissions.contains({ origins: [`${url.origin}/*`] })) throw new Error('未授权图片域名，请先点击“授权本页图片域名”');
                const controller = new AbortController();
                const timer = setTimeout(() => controller.abort(), 30000);
                try { resource = await downloadImage(img.url, CONFIG, controller.signal); }
                finally { clearTimeout(timer); }
                await ensurePage(tabId, captured);
                await spool.putAsset(identity, captureId, img.imageId, resource, captured.documentId);
            }
            totalBytes += resource.bytes.length;
            if (totalBytes > CONFIG.maxTotalBytes)
                throw new Error('超过本次图片总大小上限');
            for (let offset = 0; offset < resource.bytes.length; offset += 128 * 1024) {
                if (stop)
                    throw new Error('用户停止了资源保存');
                const chunk = resource.bytes.slice(offset, offset + 128 * 1024);
                let binary = '';
                for (const byte of chunk)
                    binary += String.fromCharCode(byte);
                await native('asset.chunk', { captureId, imageId: img.imageId, generation, offset, totalSize: resource.bytes.length, sha256: resource.sha256, mimeType: resource.mimeType, dataBase64: btoa(binary) });
            }
            await spool.remove(identity, captureId, img.imageId);
            await status({ saved: job.saved + 1, message: `内容已保存；图片 ${job.saved + 1}/${job.total} 已保存` });
        }
        catch (error) {
            if (AUTH_ERRORS.has(error.code)) throw error;
            failures.push({ imageId: img.imageId, error: error.message });
            await status({ failures: [...failures] });
            if (/页面或规格|HTTP (403|429)|验证/.test(error.message))
                stop = true;
        }
    }
    return failures;
}
async function retryImages() {
    if (job?.running)
        throw new Error('采集正在进行');
    if (!job?.context || !job.captureId)
        throw new Error('原页面捕获上下文已丢失，请重新采集');
    const connection = await authenticate();
    if (!job) throw new Error('身份已变化，旧捕获已清理');
    if (connection.workspaceId !== job.workspaceId)
        throw new Error('工作空间已变化，不能在新空间重试旧捕获');
    const server = await native('capture.status', { captureId: job.captureId });
    if (server.generation !== job.generation)
        throw new Error('商品保存代次已变化，请重新采集');
    const { tabId, captured, payload } = job.context;
    await ensurePage(tabId, captured);
    const savedIds = new Set((server.images || []).filter(i => i.status === 'SAVED').map(i => i.imageId));
    const remaining = payload.images.filter(i => !savedIds.has(i.imageId));
    if (!remaining.length) {
        await status({ running: false, message: '服务端确认图片均已保存', failures: [] });
        return job;
    }
    stop = false;
    await status({ running: true, saved: savedIds.size, failures: [], message: `正在重试 ${remaining.length} 张未保存图片` });
    try {
        const failures = await saveImages(remaining, job.captureId, job.generation, tabId, captured);
        const result = await native('capture.commit', { captureId: job.captureId, generation: job.generation, failures });
        const cleanupWarning = await cleanupConfirmed(job.captureId, failures);
        await status({ running: false, result, failures, cleanupWarning, message: cleanupWarning || (failures.length ? '部分保存，仍有图片失败' : '重试完成；选定图片已保存，资源清单已追加版本') });
    }
    catch (error) {
        await status({ running: false, message: `重试结果待确认：${error.message}`, needsVerification: true });
        throw error;
    }
    return job;
}
async function capture(groups) {
    if (!Array.isArray(groups) || groups.some(group => !['MAIN', 'GALLERY', 'DETAIL'].includes(group)))
        throw new Error('图片分组参数无效');
    if (job?.running)
        throw new Error('正在采集，请等待或停止');
    const tab = await current();
    const captured = await parse(tab.id);
    const connection = await authenticate();
    const captureId = crypto.randomUUID();
    stop = false;
    const selectedImages = captured.images.filter(i => groups.includes(i.group));
    const images = selectedImages.slice(0, CONFIG.maxImages);
    const excludedByChoice = captured.images.length - selectedImages.length;
    const excludedByLimit = Math.max(0, selectedImages.length - images.length);
    const payload = scrubMetadata({ ...captured, captureId, images, completeness: { ...captured.completeness, excludedByChoice, excludedByLimit } });
    delete payload.fingerprint;
    job = null;
    await status({ captureId, principalId: identity.principalId, workspaceId: identity.workspaceId, running: true, saved: 0, total: images.length, excludedByChoice, excludedByLimit, failures: [], message: '正在保存商品内容', context: { tabId: tab.id, captured, payload } });
    try {
        await spool.putCapture(identity, captureId, payload, { tabId: tab.id, captured });
        await ensurePage(tab.id, captured);
        const saved = await native('capture.prepare', payload);
        await status({ generation: saved.generation, message: '商品内容已保存，正在保存图片', contentSaved: true, workspaceId: connection?.workspaceId });
        const failures = await saveImages(images, captureId, saved.generation, tab.id, captured);
        const result = await native('capture.commit', { captureId, generation: saved.generation, failures });
        const cleanupWarning = await cleanupConfirmed(captureId, failures);
        await status({ running: false, result, cleanupWarning, message: cleanupWarning || ((failures.length ? '部分保存，请查看缺失图片清单' : '本次选定内容已保存') + `；主动排除 ${excludedByChoice} 张，超限排除 ${excludedByLimit} 张。仅代表已加载范围。`) });
    }
    catch (error) {
        await status({ running: false, message: `保存结果未完全确认：${error.message}`, error: true });
        throw error;
    }
}
async function resumeOffline(captureId) {
    if (job?.running) throw new Error('当前采集尚未结束');
    await authenticate();
    const rows = await spool.read(identity, captureId);
    const record = rows.find(row => row.kind === 'capture');
    if (!record) throw new Error('暂存不存在或已超过 24 小时，请重新采集');
    // prepare 幂等地确认服务器可访问性、Workspace 和捕获权限，然后查询已保存资源。
    const prepared = await native('capture.prepare', record.payload);
    const server = await native('capture.status', { captureId });
    const savedIds = new Set((server.images || []).filter(i => i.status === 'SAVED').map(i => i.imageId));
    const remaining = record.payload.images.filter(i => !savedIds.has(i.imageId));
    const { tabId, captured } = record.context;
    job = { captureId, principalId: identity.principalId, workspaceId: identity.workspaceId, generation: prepared.generation, context: { tabId, captured, payload: record.payload }, total: record.payload.images.length, saved: savedIds.size, failures: [] };
    stop = false;
    await status({ running: true, contentSaved: true, message: '服务端已确认，正在恢复离线字节' });
    try {
        const failures = await saveImages(remaining, captureId, prepared.generation, tabId, captured, true);
        const result = await native('capture.commit', { captureId, generation: prepared.generation, failures });
        const cleanupWarning = await cleanupConfirmed(captureId, failures);
        await status({ running: false, result, failures, cleanupWarning, message: cleanupWarning || (failures.length ? '离线字节已恢复，仍有未暂存图片需回原页重采' : '离线暂存已全部保存到采集库') });
        return job;
    } catch (error) { await status({ running: false, message: '恢复尚未确认，请稍后查询服务端结果' }); throw error; }
}
spool.sweep().catch(() => {});
chrome.alarms.create('aden-spool-expiry', { periodInMinutes: 60 });
chrome.alarms.onAlarm.addListener(alarm => { if (alarm.name === 'aden-spool-expiry') void spool.sweep(); });
chrome.action.onClicked.addListener(tab => chrome.sidePanel.open({ tabId: tab.id }));
chrome.runtime.onMessage.addListener((request, sender, reply) => {
    // 唯一入口是扩展自己的侧栏，拒绝内容脚本和外部页面转发。
    if (sender.id !== chrome.runtime.id || sender.url !== chrome.runtime.getURL('panel.html'))
        return;
    const run = async () => {
        await restored;
        if (request.action === 'preview') {
            const tab = await current();
            return parse(tab.id);
        }
        if (request.action === 'connect')
            return authenticate();
        if (request.action === 'lookup') {
            const tab = await current();
            const item = await parse(tab.id);
            await authenticate();
            return native('capture.lookup', { sku: item.fields.sku });
        }
        if (request.action === 'capture') {
            await capture(request.groups);
            return job;
        }
        if (request.action === 'stop') {
            stop = true;
            return { stopped: true };
        }
        if (request.action === 'retry')
            return retryImages();
        if (request.action === 'local')
            return { running: false, message: '请连接 Aden 后查询保存结果与离线暂存' };
        if (request.action === 'spool.list') {
            await authenticate();
            return (await spool.read(identity)).filter(row => row.kind === 'capture').map(row => ({ captureId: row.captureId, title: row.payload.fields.title, expiresAt: row.expiresAt }));
        }
        if (request.action === 'spool.resume') return resumeOffline(request.captureId);
        if (request.action === 'spool.clear') { await authenticate(); await spool.clear(); return { cleared: true }; }
        if (request.action === 'status') {
            if (!job?.captureId)
                return job;
            await authenticate();
            if (!job) throw new Error('身份已变化，旧捕获已清理');
            const server = await native('capture.status', { captureId: job.captureId });
            await status({ needsVerification: false, server });
            return { local: job, server };
        }
        if (request.action === 'library')
            return native('library.open');
        throw new Error('不支持的操作');
    };
    run().then(data => reply({ ok: true, data })).catch(error => reply({ ok: false, error: error.message, errorCode: error.code }));
    return true;
});
