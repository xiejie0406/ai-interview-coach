import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { extractProduct } from '../parser.js';
import { approvedResource, imageMime } from '../resources.js';
import { CONFIG } from '../config.js';
const require = createRequire(new URL('../../aden-desktop/package.json', import.meta.url));
const { JSDOM } = require('jsdom');
const fixture = await readFile(new URL('../fixtures/detail.html', import.meta.url), 'utf8');
function documentAt(url = 'http://127.0.0.1:19876/detail.html') {
    const dom = new JSDOM(fixture, { url });
    globalThis.document = dom.window.document;
    globalThis.location = dom.window.location;
    for (const image of document.querySelectorAll('img')) {
        Object.defineProperty(image, 'complete', { value: true, configurable: true });
        Object.defineProperty(image, 'naturalWidth', { value: 10 });
        Object.defineProperty(image, 'naturalHeight', { value: 10 });
    }
    return dom;
}
test('合成详情仅读商品区块，保留文本金额与SKU，排除个人内容及推荐', () => { documentAt(); const p = extractProduct({ fixtureOrigin: 'http://127.0.0.1:19876' }); assert.equal(p.fields.sku, '100000000000001'); assert.equal(p.fields.price, '129.90'); assert.equal(p.images.length, 1); assert.equal(p.fields.specification, '蓝色 / 标准款'); assert(!JSON.stringify(p).includes('收货地址')); assert(!JSON.stringify(p).includes('secret')); assert(!JSON.stringify(p).includes('无关推荐')); });
test('生产解析不接受localhost，精确限制JD域名与路径', () => { documentAt(); assert.throws(() => extractProduct(), /京东/); documentAt('https://item.jd.com.attacker.test/123.html'); assert.throws(() => extractProduct(), /京东/); documentAt('https://item.jd.com/123.html'); assert.throws(() => extractProduct(), /SKU/); });
test('验证码与SKU变更拒绝采集', () => { documentAt(); document.title = '安全检查'; assert.throws(() => extractProduct({ fixtureOrigin: location.origin }), /验证/); documentAt(); document.querySelector('[data-current-sku]').setAttribute('data-current-sku', '999'); assert.throws(() => extractProduct({ fixtureOrigin: location.origin }), /SKU/); });
test('条件价格不强转数字，未加载图片不收录', () => { documentAt(); document.querySelector('[data-product-price]').textContent = '券后 ¥99'; for (const image of document.querySelectorAll('img'))
    Object.defineProperty(image, 'complete', { value: false, configurable: true }); const p = extractProduct({ fixtureOrigin: location.origin }); assert.equal(p.fields.price, undefined); assert.equal(p.images.length, 0); });
test('下载地址精确白名单，不允许私网、用户凭据、HTTP与后缀冒充', () => { assert.equal(approvedResource('https://img10.360buyimg.com/a.jpg', CONFIG).hostname, 'img10.360buyimg.com'); for (const value of ['http://img10.360buyimg.com/a', 'https://127.0.0.1/a', 'https://img10.360buyimg.com.evil/a', 'https://user@img10.360buyimg.com/a', 'https://img10.360buyimg.com:444/a'])
    assert.throws(() => approvedResource(value, CONFIG)); });
test('实际字节检测拒绝SVG与HTML', () => { assert.equal(imageMime(new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])), 'image/png'); assert.throws(() => imageMime(new TextEncoder().encode('<svg></svg>'))); });
test('注入返回模式保留验证码和SKU拒绝原因，不误报授权失效', () => {
    documentAt(); document.title='安全检查';
    const challenge=extractProduct({fixtureOrigin:location.origin,returnErrors:true});
    assert.equal(challenge.error.code,'PAGE_PARSE_REJECTED');
    assert.match(challenge.error.message,/登录或验证.*手动处理/);
    assert.doesNotMatch(challenge.error.message,/授权/);
    documentAt();document.querySelector('[data-current-sku]').setAttribute('data-current-sku','999');
    const mismatch=extractProduct({fixtureOrigin:location.origin,returnErrors:true});
    assert.match(mismatch.error.message,/SKU 不一致/);
    assert.equal(mismatch.fields,undefined);
});
test('注入返回模式对未知异常只返回受控模板提示', () => {
    documentAt();document.querySelector=()=>{throw new Error('SECRET-UNEXPECTED-CONTENT')};
    const result=extractProduct({fixtureOrigin:location.origin,returnErrors:true});
    assert.equal(result.error.message,'页面商品结构解析失败，请反馈页面模板');
    assert.doesNotMatch(JSON.stringify(result),/SECRET/);
});
