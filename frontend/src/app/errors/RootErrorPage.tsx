import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';

function ErrorState({ status }: { status: number }) {
  const message = status === 404 ? '找不到这个页面' : '页面暂时无法打开';
  return (
    <section className="center-state" role="alert">
      <span className="eyebrow">{status}</span>
      <h1>{message}</h1>
      <p>你可以返回首页，或稍后重试。已有的面试事实仍以服务端状态为准。</p>
      <Link to="/" className="button button-primary">回到首页</Link>
    </section>
  );
}

export function RootErrorPage() {
  const routeError = useRouteError();
  return <ErrorState status={isRouteErrorResponse(routeError) ? routeError.status : 500} />;
}

export function NotFoundPage() { return <ErrorState status={404} />; }
