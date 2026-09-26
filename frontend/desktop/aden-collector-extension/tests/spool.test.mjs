import test from 'node:test';
import assert from 'node:assert/strict';
import { CaptureSpool, IndexedDbStore } from '../spool.js';
import { fakeIndexedDB } from './idb-fixture.mjs';
const a = { principalId: 'user-a', workspaceId: 'ws' };
const b = { principalId: 'user-b', workspaceId: 'ws' };
const content = { fields: { title: '商品', sourceUrl: 'https://item.jd.com/1.html?token=secret' }, images: [] };
test('暂存字节在Worker实例重建后可恢复，元数据无凭据与签名参数', async () => {
    const store = new IndexedDbStore(fakeIndexedDB());
    const first = new CaptureSpool(store);
    await first.putCapture(a, 'capture', { ...content, token: 'private' }, { documentId: 'doc' });
    await first.putAsset(a, 'capture', 'image', { bytes: new Uint8Array([1,2,3]), mimeType: 'image/png', sha256: 'hash' }, 'doc');
    const restarted = new CaptureSpool(store);
    const rows = await restarted.read(a, 'capture');
    assert.equal(rows.length, 2);
    assert.deepEqual(rows.find(r=>r.kind==='asset').bytes, new Uint8Array([1,2,3]));
    assert.equal(rows[0].payload.token, undefined);
    assert.equal(rows[0].payload.fields.sourceUrl, 'https://item.jd.com/1.html');
});
test('同空间不同账号不能读取，绑定新身份清除旧暂存', async () => {
    const spool = new CaptureSpool(new IndexedDbStore(fakeIndexedDB()));
    await spool.putCapture(a, 'capture', content, {});
    assert.deepEqual(await spool.read(b), []);
    await spool.bind(b);
    assert.deepEqual(await spool.read(a), []);
    assert.throws(() => spool.read({ workspaceId:'ws' }), /账号/);
});
test('24小时过期，资源更新不延长初始保留期限', async () => {
    let now=0;
    const spool = new CaptureSpool(new IndexedDbStore(fakeIndexedDB()), { clock:()=>now, ttl:100 });
    await spool.putCapture(a, 'capture', content, {});
    now=50;
    await spool.putAsset(a, 'capture', 'image', { bytes:new Uint8Array([1]),sha256:'x',mimeType:'image/png' }, 'doc');
    now=100;
    assert.deepEqual(await spool.read(a), []);
});
test('配额串行计入字节与元数据，超限不驱逐未到期内容', async () => {
    const spool = new CaptureSpool(new IndexedDbStore(fakeIndexedDB()), { maxBytes:1000 });
    await spool.putCapture(a, 'capture', content, {});
    await spool.putAsset(a, 'capture', 'one', {bytes:new Uint8Array(500),sha256:'x',mimeType:'image/png'}, 'doc');
    await assert.rejects(() => spool.putAsset(a, 'capture', 'two', {bytes:new Uint8Array(500),sha256:'x',mimeType:'image/png'}, 'doc'), /上限/);
    assert.equal((await spool.read(a)).length,2);
    await spool.clear(); assert.deepEqual(await spool.read(a),[]);
});
