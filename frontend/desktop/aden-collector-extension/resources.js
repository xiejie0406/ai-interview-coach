export function approvedResource(raw, config) {
    const u = new URL(raw);
    if (u.username || u.password || u.hash)
        throw new Error('图片地址包含不允许的凭据或片段');
    if (config.fixtureOrigin && u.origin === config.fixtureOrigin)
        return u;
    if (u.protocol !== 'https:' || u.port && u.port !== '443' || !config.resourceHosts.includes(u.hostname))
        throw new Error('图片域名未被来源策略批准');
    return u;
}
export function imageMime(bytes) {
    if (bytes.length >= 8 && [137, 80, 78, 71, 13, 10, 26, 10].every((v, i) => bytes[i] === v))
        return 'image/png';
    if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255)
        return 'image/jpeg';
    if (bytes.length >= 12 && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP')
        return 'image/webp';
    throw new Error('仅支持真实 JPEG、PNG、WebP 图片');
}
export async function downloadImage(raw, config, signal) {
    const u = approvedResource(raw, config);
    const response = await fetch(u.href, { credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer', signal });
    if (!response.ok)
        throw new Error(`图片请求失败 HTTP ${response.status}`);
    if (Number(response.headers.get('content-length')) > config.maxImageBytes)
        throw new Error('图片超过大小限制');
    const reader = response.body.getReader();
    let length = 0;
    const chunks = [];
    while (true) {
        const { done, value } = await reader.read();
        if (done)
            break;
        length += value.length;
        if (length > config.maxImageBytes) {
            await reader.cancel();
            throw new Error('图片超过大小限制');
        }
        chunks.push(value);
    }
    const bytes = new Uint8Array(length);
    let at = 0;
    for (const chunk of chunks) {
        bytes.set(chunk, at);
        at += chunk.length;
    }
    const mimeType = imageMime(bytes);
    const bitmap = await createImageBitmap(new Blob([bytes], { type: mimeType }));
    const { width, height } = bitmap;
    bitmap.close();
    if (!width || !height || width * height > 100000000)
        throw new Error('图片尺寸超过限制');
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return { bytes, mimeType, width, height, sha256: Array.from(new Uint8Array(digest), x => x.toString(16).padStart(2, '0')).join('') };
}
