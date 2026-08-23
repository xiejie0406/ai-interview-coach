import { useQuery } from '@tanstack/react-query';
import { statusApi } from '../api/statusApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';

export function StatusPage() {
  const status = useQuery({ queryKey: queryKeys.operations.status, queryFn: ({ signal }) => statusApi.getPublicStatus(signal), retry: false });
  const health = useQuery({ queryKey: queryKeys.operations.health, queryFn: ({ signal }) => statusApi.getHealth(signal), retry: false });
  return (
    <PageFrame eyebrow="SERVICE STATUS" title="系统状态" description="公开维护投影与 HTTP health 分开；Provider、队列、成本和质量只在授权后台展示。">
      <div className="status-grid">
        <AsyncState loading={status.isPending} error={status.error} publicResource onRetry={() => void status.refetch()}>
          {status.data && <div className="status-panel"><span className={status.data.status === 'OPERATIONAL' ? 'status-dot' : 'status-dot warning'} /><div><strong>{statusLabel(status.data.status)}</strong><p className="muted">{status.data.message ?? '没有公开维护说明'} · 更新 {new Date(status.data.updatedAt).toLocaleString()}</p></div></div>}
        </AsyncState>
        <AsyncState loading={health.isPending} error={health.error} onRetry={() => void health.refetch()}>
          {health.data && <div className="status-panel"><span className={health.data.status === 'UP' ? 'status-dot' : 'status-dot warning'} /><div><strong>HTTP {health.data.status}</strong><p className="muted">{health.data.service} · {health.data.version}</p></div></div>}
        </AsyncState>
      </div>
    </PageFrame>
  );
}

function statusLabel(status: 'OPERATIONAL' | 'DEGRADED' | 'MAINTENANCE') {
  return ({ OPERATIONAL: '服务正常', DEGRADED: '部分能力降级', MAINTENANCE: '维护中' } as const)[status];
}
