import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../../aden-desktop/package.json', import.meta.url));
const { JSDOM } = require('jsdom');
const html = await readFile(new URL('../panel.html', import.meta.url), 'utf8');
const dom = new JSDOM(html, { url: 'https://extension.test/panel.html' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
let receive;
globalThis.chrome = { runtime: { sendMessage: async () => ({ ok: true, data: null }), onMessage: { addListener(listener) { receive = listener; } } } };
await import('../panel.js');
test('部分保存、重试和离线完成持续显示准确图片计数', () => {
    for (const [message, saved, total] of [['部分保存，请查看失败清单', 6, 8], ['重试完成', 8, 8], ['离线暂存已全部保存', 1, 1]]) {
        receive({ event: 'progress', job: { message, saved, total, running: false } });
        assert.equal(document.getElementById('status').textContent, `${message}；图片 ${saved}/${total} 已保存`);
    }
});
test('已有计数不重复，无计数提示及非法计数不追加', () => {
    const message = '内容已保存；图片 6/8 已保存';
    receive({ event: 'progress', job: { message, saved: 6, total: 8 } });
    assert.equal(document.getElementById('status').textContent, message);
    for (const job of [{ message: '请连接 Aden' }, { message: '待确认', saved: 9, total: 8 }, { message: '待确认', saved: NaN, total: 8 }]) {
        receive({ event: 'progress', job });
        assert.equal(document.getElementById('status').textContent, job.message);
    }
});
