// 仅读取当前已加载的商品区块；此函数以隔离世界注入，不调用页面函数。
export function extractProduct(options = {}) {
    try {
    const url = new URL(location.href);
    const fixture = options.fixtureOrigin && url.origin === options.fixtureOrigin;
    if (!(url.protocol === 'https:' && url.hostname === 'item.jd.com' && /^\/(\d+)\.html$/.test(url.pathname)) && !fixture)
        throw new Error('请打开京东商品详情页');
    const clean = (s, max = 20000) => String(s || '').replace(/\s+/g, ' ').trim().slice(0, max);
    const text = (selector) => clean(document.querySelector(selector)?.textContent);
    if (document.querySelector('#JDJRV-wrap, .JDJRV-wrap, #captcha, [data-captcha], form[action*="login"]') || /验证|访问受限|安全检查/.test(document.title))
        throw new Error('页面需要登录或验证，请手动处理后重新采集');
    const sku = fixture ? document.querySelector('[data-product-sku]')?.getAttribute('data-product-sku') : url.pathname.match(/^\/(\d+)\.html$/)?.[1];
    const title = text('.sku-name, [data-product-title]');
    if (!sku || !/^\d{1,30}$/.test(sku) || !title)
        throw new Error('无法确认商品身份或标题，请手动新增或反馈页面模板');
    const selected = Array.from(document.querySelectorAll('#choose-attrs .selected, #choose .selected, [data-selected-spec]')).map(n => clean(n.getAttribute('data-value') || n.textContent)).filter(Boolean).join(' / ');
    const declaredSku = document.querySelector('[data-current-sku]')?.getAttribute('data-current-sku');
    if (declaredSku && declaredSku !== sku)
        throw new Error('当前规格与页面 SKU 不一致，请等待页面完成后重试');
    const priceText = text('.summary-price .p-price, .summary-price-wrap .p-price, [data-product-price]');
    const priceMatch = priceText.match(/^[￥¥]?\s*(\d+(?:\.\d{1,2})?)$/);
    const fields = { title, sku, sourceUrl: fixture ? url.origin + url.pathname : `https://item.jd.com/${sku}.html`, specification: selected, priceText, shop: text('.J-hove-wrap .name, .seller-infor .name, [data-product-shop]'), parameters: text('.parameter2, #parameter2, [data-product-parameters]') };
    if (priceMatch)
        fields.price = priceMatch[1];
    const blocks = [{ type: 'title', text: title }];
    if (fields.parameters)
        blocks.push({ type: 'parameters', text: fields.parameters });
    const description = document.querySelector('#J-detail-content, #detail .detail-content, [data-product-description]');
    if (description) {
        const copy = description.cloneNode(true);
        copy.querySelectorAll('script,style,iframe,form,input,button,[data-personal],.recommend,.comment').forEach(n => n.remove());
        if (clean(copy.textContent))
            blocks.push({ type: 'description', text: clean(copy.textContent, 60000) });
    }
    const images = [];
    const seen = new Set();
    for (const [group, selector] of [['MAIN', '#spec-img, [data-product-main]'], ['GALLERY', '#spec-list img, [data-product-gallery] img'], ['DETAIL', '#J-detail-content img, #detail .detail-content img, [data-product-description] img']]) {
        for (const img of document.querySelectorAll(selector)) {
            if (img.closest('[data-personal], .recommend, .comment, form'))
                continue;
            if (!img.complete || !img.naturalWidth || !img.currentSrc && !img.getAttribute('src'))
                continue;
            const raw = img.currentSrc || img.getAttribute('src');
            let imageUrl;
            try {
                imageUrl = new URL(raw, url).href;
            }
            catch {
                continue;
            }
            if (imageUrl.length > 4096)
                continue;
            if (seen.has(imageUrl))
                continue;
            seen.add(imageUrl);
            images.push({ imageId: `image-${images.length + 1}`, url: imageUrl, group, order: images.length, width: img.naturalWidth, height: img.naturalHeight });
        }
    }
    return { source: 'JD', fields, blocks, images, parserVersion: 'jd-dom-0.1.0', fingerprint: JSON.stringify([url.origin, url.pathname, sku, selected]), completeness: { fixture: !!fixture, scope: 'LOADED_PRODUCT_BLOCKS', description: description ? 'LOADED_SCOPE_ONLY' : 'NOT_LOADED', images: 'DISCOVERED_ONLY', totalDiscovered: images.length } };
    } catch (error) {
        if (!options.returnErrors) throw error;
        // executeScript 不保证序列化抛出的 Error，明确返回受控业务原因。
        const knownMessages = [
            '请打开京东商品详情页',
            '页面需要登录或验证，请手动处理后重新采集',
            '无法确认商品身份或标题，请手动新增或反馈页面模板',
            '当前规格与页面 SKU 不一致，请等待页面完成后重试',
        ];
        const message = knownMessages.includes(error?.message) ? error.message : '页面商品结构解析失败，请反馈页面模板';
        return { error: { code: 'PAGE_PARSE_REJECTED', message } };
    }
}
