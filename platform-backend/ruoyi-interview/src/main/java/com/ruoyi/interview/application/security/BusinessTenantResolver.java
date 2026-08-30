package com.ruoyi.interview.application.security;

import com.ruoyi.interview.domain.platform.TenantId;

/**
 * 服务端业务工作区解析端口。
 * 实现可以读取 PostgreSQL 业务扩展，但不得从请求参数推断登录主体或权限。
 */
@FunctionalInterface
public interface BusinessTenantResolver {

    TenantId resolveFor(Long ruoyiUserId);
}
