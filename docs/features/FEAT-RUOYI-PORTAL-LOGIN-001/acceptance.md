# Portal RuoYi 登录用户验收

> Feature ID：`FEAT-RUOYI-PORTAL-LOGIN-001`  
> 阶段：9 用户验收  
> 阶段状态：Blocked  
> 证据结果：Blocked

| UAT | 关联 AC | 场景 | 结果 |
|---|---|---|---|
| UAT-01 | AC-PORTAL-LOGIN-01 | 未登录访问 `/interviews/new` | Pass（浏览器） |
| UAT-02 | AC-PORTAL-LOGIN-02 | 登录页加载/刷新验证码、空表单和错误提示 | Pass（UI）；验证码接口 Blocked |
| UAT-03 | AC-PORTAL-LOGIN-03 | 有效 RuoYi 账号登录并进入 Portal 首页 | Blocked：8081 无监听 |
| UAT-04 | AC-PORTAL-LOGIN-04 | 刷新恢复、401 清理、退出后重新登录 | NotRun：需 RuoYi 运行实例 |
| UAT-05 | AC-PORTAL-LOGIN-02/04 | 390px 移动端布局 | Pass（浏览器指标） |

业务 owner 尚未对真实账号链路形成验收结论；当前不得视为 UAT 通过或已发布。
