const TTL = 24 * 60 * 60 * 1000;
const LIMIT = 100 * 1024 * 1024;

// 仅由扩展 Worker 访问；内容脚本和网页无 IndexedDB 访问入口。
export class IndexedDbStore {
    constructor(factory = indexedDB) {
        this.ready = new Promise((resolve, reject) => {
            const request = factory.open('aden-collector-spool-v1', 1);
            request.onupgradeneeded = () => request.result.createObjectStore('entries', { keyPath: 'key' });
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }
    async operation(mode, run) {
        const db = await this.ready;
        return new Promise((resolve, reject) => {
            const tx = db.transaction('entries', mode);
            const request = run(tx.objectStore('entries'));
            let result;
            request.onsuccess = () => { result = request.result; };
            tx.oncomplete = () => resolve(result);
            tx.onerror = () => reject(tx.error || request.error);
            tx.onabort = () => reject(tx.error || new Error('暂存事务已中止'));
        });
    }
    list() { return this.operation('readonly', store => store.getAll()); }
    put(record) { return this.operation('readwrite', store => store.put(record)); }
    delete(key) { return this.operation('readwrite', store => store.delete(key)); }
}

export function scrubMetadata(value) {
    if (Array.isArray(value)) return value.map(scrubMetadata);
    if (value && typeof value === 'object') {
        const result = {};
        for (const [key, item] of Object.entries(value)) {
            if (/^(cookie|token|authorization|password|credential|secret)$/i.test(key)) continue;
            if (['url', 'sourceUrl'].includes(key) && typeof item === 'string') {
                try { const u = new URL(item); result[key] = u.origin + u.pathname; }
                catch { result[key] = ''; }
            } else result[key] = scrubMetadata(item);
        }
        return result;
    }
    return value;
}

export class CaptureSpool {
    constructor(store, { clock = Date.now, maxBytes = LIMIT, ttl = TTL } = {}) {
        this.store = store; this.clock = clock; this.maxBytes = maxBytes; this.ttl = ttl;
        this.serial = Promise.resolve();
    }
    exclusive(action) {
        const operation = this.serial.then(action);
        this.serial = operation.catch(() => {});
        return operation;
    }
    async sweepInternal() {
        const rows = await this.store.list();
        for (const row of rows) if (row.expiresAt <= this.clock()) await this.store.delete(row.key);
        return rows.filter(row => row.expiresAt > this.clock());
    }
    sweep() { return this.exclusive(() => this.sweepInternal()); }
    bind(identity) {
        this.validateIdentity(identity);
        return this.exclusive(async () => {
            for (const row of await this.sweepInternal()) {
                if (!this.matches(row, identity)) await this.store.delete(row.key);
            }
        });
    }
    validateIdentity(identity) {
        if (!identity?.workspaceId || !identity?.principalId) throw new Error('尚未确认账号与工作空间，不能读取离线暂存');
    }
    matches(row, identity) { return row.workspaceId === identity.workspaceId && row.principalId === identity.principalId; }
    async putRecord(identity, captureId, suffix, content, byteSize) {
        this.validateIdentity(identity);
        return this.exclusive(async () => {
            const rows = await this.sweepInternal();
            const key = `${identity.principalId}|${identity.workspaceId}|${captureId}|${suffix}`;
            const existing = rows.find(r => r.key === key);
            const capture = rows.find(r => r.captureId === captureId && r.kind === 'capture' && this.matches(r, identity));
            if (suffix !== 'capture' && !capture) throw new Error('捕获暂存不存在或已过期，请重新采集');
            const expiresAt = existing?.expiresAt || capture?.expiresAt || this.clock() + this.ttl;
            const metadataBytes = new TextEncoder().encode(JSON.stringify({ ...content, bytes: undefined })).length;
            const size = byteSize + metadataBytes;
            if (rows.reduce((n, r) => n + (r.key === key ? 0 : r.size), 0) + size > this.maxBytes) throw new Error('离线暂存已达到 100 MiB 上限，请恢复或清理已有暂存');
            await this.store.put({ key, principalId: identity.principalId, workspaceId: identity.workspaceId, captureId, expiresAt, createdAt: existing?.createdAt || this.clock(), size, ...content });
        });
    }
    putCapture(identity, captureId, payload, context) {
        return this.putRecord(identity, captureId, 'capture', { kind: 'capture', payload: scrubMetadata(payload), context: scrubMetadata(context) }, 0);
    }
    putAsset(identity, captureId, imageId, resource, documentId) {
        if (!(resource.bytes instanceof Uint8Array) || resource.bytes.length > 10 * 1024 * 1024) throw new Error('暂存图片大小无效');
        return this.putRecord(identity, captureId, `asset:${imageId}`, { kind: 'asset', imageId, documentId, bytes: resource.bytes, mimeType: resource.mimeType, sha256: resource.sha256 }, resource.bytes.length);
    }
    read(identity, captureId) {
        this.validateIdentity(identity);
        return this.exclusive(async () => (await this.sweepInternal()).filter(r => this.matches(r, identity) && (!captureId || r.captureId === captureId)));
    }
    remove(identity, captureId, imageId) {
        this.validateIdentity(identity);
        return this.exclusive(async () => {
            for (const row of await this.sweepInternal()) if (this.matches(row, identity) && row.captureId === captureId && (!imageId || row.imageId === imageId)) await this.store.delete(row.key);
        });
    }
    clear() { return this.exclusive(async () => { for (const row of await this.store.list()) await this.store.delete(row.key); }); }
}
