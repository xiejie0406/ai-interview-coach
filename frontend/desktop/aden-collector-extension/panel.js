import { CONFIG } from './config.js';
import { approvedResource } from './resources.js';
const $ = id => document.getElementById(id);
let preview;
let connected = false;
async function call(action, extra = {}) { const r = await chrome.runtime.sendMessage({ action, ...extra }); if (!r?.ok)
    throw new Error(r?.error || '连接中断'); return r.data; }
function render(job) {
    let message = job.message || JSON.stringify(job);
    if (Number.isSafeInteger(job.saved) && Number.isSafeInteger(job.total) && job.saved >= 0 && job.total >= job.saved) {
        const progress = `图片 ${job.saved}/${job.total} 已保存`;
        if (!message.includes(progress)) message += `；${progress}`;
    }
    $('status').textContent = message;
    $('failures').replaceChildren(...(job.failures || []).map(f => { const li = document.createElement('li'); li.textContent = `${f.imageId}：${f.error}`; return li; }));
    $('capture').disabled = !!job.running || !preview || !connected;
}
function action(id, fn) { $(id).onclick = async () => { try {
    await fn();
}
catch (error) {
    $('status').textContent = error.message;
} }; }
action('connect', async () => { const data = await call('connect'); connected = true; $('status').textContent = `已连接 Aden ${data?.workspaceName || data?.workspaceId || ''}`; $('capture').disabled = !preview; });
action('preview', async () => { preview = undefined; $('capture').disabled = true; const value = await call('preview'); preview = value; $('title').textContent = value.fields.title; $('spec').textContent = `SKU ${value.fields.sku} · ${value.fields.specification || '页面未显示规格'}`; $('permission').disabled = false; $('capture').disabled = !connected; });
action('permission', async () => { if (!preview)
    return; const origins = [...new Set(preview.images.flatMap(i => { try {
        return [`${approvedResource(i.url, CONFIG).origin}/*`];
    }
    catch {
        return [];
    } }))]; const granted = !origins.length || await chrome.permissions.request({ origins }); $('status').textContent = granted ? '已授予识别出的受控图片域名权限' : '未授权，文本仍可采集，图片将标记缺失'; });
async function startCapture() { $('capture').disabled = true; try {
    await call('capture', { groups: [...document.querySelectorAll('input:checked')].map(x => x.value) });
}
finally {
    $('capture').disabled = !preview || !connected;
} }
action('capture', async () => { if (!preview)
    return; const existing = await call('lookup'); if (existing.deleted) {
    $('status').textContent = '此商品在回收站，请到采集库恢复后再采集';
    return;
} if (existing.exists)
    $('captureDialog').showModal();
else
    await startCapture(); });
action('confirmCapture', async () => { $('captureDialog').close(); await startCapture(); });
action('viewExisting', async () => { $('captureDialog').close(); await call('library'); });
action('cancelCapture', async () => { $('captureDialog').close(); $('capture').focus(); });
action('stop', async () => { await call('stop'); $('status').textContent = '已请求停止，等待在途保存确认'; });
action('refresh', async () => { const data = await call('status'); if (data?.local)
    render({ ...data.local, message: `服务端状态：${data.server?.status || '未知'}` });
else
    $('status').textContent = '没有可查询的捕获记录'; });
action('retry', async () => { render(await call('retry')); });
action('pending', async () => {
    const rows = await call('spool.list');
    $('pendingList').replaceChildren(...rows.map(row => {
        const option = document.createElement('option'); option.value = row.captureId;
        option.textContent = `${row.title} · 到期 ${new Date(row.expiresAt).toLocaleString()}`; return option;
    }));
    $('status').textContent = `当前账号与空间共有 ${rows.length} 个待恢复捕获`;
});
action('resume', async () => { if (!$('pendingList').value) throw new Error('请先查询并选择暂存'); render(await call('spool.resume', { captureId: $('pendingList').value })); });
action('clearSpool', async () => { if (confirm('清理本机尚未完成的离线暂存？服务端已保存结果保持不变。')) { await call('spool.clear'); $('pendingList').replaceChildren(); $('status').textContent = '本机离线暂存已清理'; } });
action('library', () => call('library'));
chrome.runtime.onMessage.addListener(message => {
    if (message.event === 'progress') render(message.job);
    if (message.event === 'revoked') {
        connected = false; preview = undefined;
        $('pendingList').replaceChildren(); $('failures').replaceChildren();
        $('title').textContent = '账号或工作空间已变化'; $('spec').textContent = '';
        $('capture').disabled = true;
        $('status').textContent = '本机旧暂存已清理，请重新连接';
    }
});
call('local').then(saved => { if (saved)
    render(saved); }).catch(error => { $('status').textContent = error.message; });
// 离开浏览器时遮蔽已查询的本机记录；回来需再次认证查询。
window.addEventListener('blur', () => {
    $('pendingList').replaceChildren();
    $('failures').replaceChildren();
    connected = false; $('capture').disabled = true;
    $('status').textContent = '请重新连接 Aden 以确认当前账号和工作空间';
});
