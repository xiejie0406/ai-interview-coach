import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { billingApi, type PlanView } from '../api/billingApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useSession } from '../../../shared/session/SessionProvider';

export function PricingPage() {
  const { queryScope } = useSession();
  const plans = useQuery({ queryKey: queryKeys.billing.plans(queryScope), queryFn: ({ signal }) => billingApi.listPlans(signal) });
  return (
    <PageFrame eyebrow="PRICING" title="价格与权益候选" description="只展示服务端返回的有效价格版本；金额由服务端复算。">
      <AsyncState loading={plans.isPending} error={plans.error} empty={Boolean(plans.data && plans.data.length === 0)} publicResource onRetry={() => void plans.refetch()}>
        <div className="billing-grid">{(plans.data ?? []).map((plan) => <PlanCard plan={plan} key={plan.priceVersionId} />)}</div>
      </AsyncState>
      <div className="notice">真实 checkout、支付回调和退款尚未获执行授权；此页面不会创建订单或跳转第三方支付。</div>
    </PageFrame>
  );
}

export function UsagePage() {
  const { queryScope } = useSession();
  const entitlements = useQuery({ queryKey: queryKeys.billing.entitlements(queryScope), queryFn: ({ signal }) => billingApi.getEntitlements(signal) });
  const usage = useQuery({ queryKey: queryKeys.billing.usage(queryScope), queryFn: ({ signal }) => billingApi.getUsage(signal) });
  return (
    <PageFrame eyebrow="ENTITLEMENT & USAGE" title="开始前先看清额度" description="权益、预留、结算和平台成本是不同账本；客户端不自行扣减。">
      <AsyncState loading={entitlements.isPending} error={entitlements.error} empty={Boolean(entitlements.data && entitlements.data.length === 0)} onRetry={() => void entitlements.refetch()}>
        <div className="billing-grid">{(entitlements.data ?? []).map((item) => <section className="plan-card" key={`${item.capability}:${item.unit}`}><span className="card-kicker">{item.source}</span><h2>{item.capability}</h2><strong>{item.granted ? `${item.remaining} ${item.unit}` : '未授予'}</strong></section>)}</div>
      </AsyncState>
      <section className="panel usage-contract-panel">
        <span className="card-kicker">USAGE LEDGER</span>
        <AsyncState loading={usage.isPending} error={usage.error} onRetry={() => void usage.refetch()}>
          {usage.data && <><p className="muted">投影版本 {usage.data.projectionVersion} · 截至 {new Date(usage.data.asOf).toLocaleString()}</p><div className="learning-list">{usage.data.reservations.map((item) => <div className="learning-item" key={item.reservationId}><span className="status-chip">{item.state}</span><div><h2>{item.capability}</h2><p className="muted">预留 {item.quantity} {item.unit} · operation {item.businessOperationId}</p></div></div>)}{!usage.data.reservations.length && <p className="muted">当前没有用量预留记录。</p>}</div><p className="muted">结算 {usage.data.settlements.length} 条 · 释放 {usage.data.releases.length} 条。Provider 成本账本不在用户用量响应中混算。</p></>}
        </AsyncState>
      </section>
      <div className="form-actions"><Link to="/pricing" className="button button-ghost">查看公开价格</Link><Link to="/app/interviews/new" className="button button-secondary">配置练习</Link></div>
    </PageFrame>
  );
}

export function BillingPage() {
  return <UsagePage />;
}

export function OrderStatusPage() {
  const { orderId } = useParams();
  const { queryScope } = useSession();
  const order = useQuery({
    queryKey: queryKeys.billing.order(queryScope, orderId ?? 'missing'),
    queryFn: ({ signal }) => billingApi.getOrder(orderId!, signal),
    enabled: Boolean(orderId),
  });
  return (
    <PageFrame eyebrow="ORDER STATUS" title="订单状态" description="浏览器返回页不等于支付成功；这里只读取服务端 owner-scoped 订单事实。">
      <AsyncState loading={order.isPending} error={order.error} onRetry={() => void order.refetch()}>
        {order.data && <section className="panel"><span className="card-kicker">{order.data.state}</span><h2>{formatMoney(order.data.currency, order.data.currencyExponent, order.data.amountMinor)}</h2><p className="muted">Order {order.data.id} · v{order.data.version}</p>{order.data.checkoutUrl && <div className="notice">服务端返回了 checkout URL，但真实支付尚未获执行授权，本页不会自动跳转。</div>}<button type="button" className="button button-ghost" onClick={() => void order.refetch()}>读取最新订单状态</button></section>}
      </AsyncState>
    </PageFrame>
  );
}

function PlanCard({ plan }: { plan: PlanView }) {
  const amount = formatMoney(plan.currency, plan.currencyExponent, plan.amountMinor);
  return <section className="plan-card"><span className="card-kicker">{plan.code}</span><h2>{amount}</h2><ul>{plan.entitlements.map((item) => <li key={`${item.capability}:${item.unit}`}>{item.capability}：{item.quantity} {item.unit}</li>)}</ul><small className="muted">Price version {plan.priceVersionId}</small><button type="button" className="button button-ghost" disabled>支付未启用</button></section>;
}

function formatMoney(currency: string, currencyExponent: number, amountMinor: number) {
  const amount = amountMinor / 10 ** currencyExponent;
  try {
    return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, currencyDisplay: 'code', minimumFractionDigits: currencyExponent, maximumFractionDigits: currencyExponent }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(currencyExponent)}`;
  }
}
