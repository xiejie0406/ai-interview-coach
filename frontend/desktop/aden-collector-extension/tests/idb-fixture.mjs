// 确定性事务事件夹具；不替代真实浏览器持久化验收。
export function fakeIndexedDB(hooks = {}) {
    const data = new Map();
    const db = {
        createObjectStore() {},
        transaction() {
            const transaction = { error: null };
            transaction.objectStore = () => {
                const operation = fn => {
                    const request = {};
                    queueMicrotask(async () => {
                        try { request.result = await fn(); request.onsuccess?.(); queueMicrotask(() => transaction.oncomplete?.()); }
                        catch (error) { transaction.error = error; transaction.onerror?.(); }
                    });
                    return request;
                };
                return {
                    getAll: () => operation(() => [...data.values()].map(value => structuredClone(value))),
                    put: row => operation(() => { data.set(row.key, structuredClone(row)); return row.key; }),
                    delete: key => operation(async () => { await hooks.beforeDelete?.(key); return data.delete(key); }),
                };
            };
            return transaction;
        },
    };
    return { open() { const request = { result: db }; queueMicrotask(() => { request.onupgradeneeded?.(); request.onsuccess?.(); }); return request; } };
}
