package com.ruoyi.framework.security.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.TokenService;

/**
 * token过滤器 验证token有效性
 * 
 * @author ruoyi
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        // 浏览器 WebSocket 的同一若依令牌必须在 SecurityFilterChain 授权之前解析。
        // 只接受语音升级路径的子协议，不开放匿名端点，也不接受 query token。
        if (loginUser == null && "GET".equals(request.getMethod())
                && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"))
                && request.getRequestURI().matches("/ws/v1/interviews/[0-9a-fA-F-]{36}/voice"))
        {
            String protocols = request.getHeader("Sec-WebSocket-Protocol");
            if (protocols != null)
            {
                String bearer = null;
                for (String protocol : protocols.split(","))
                {
                    String candidate = protocol.trim();
                    if (candidate.startsWith("ruoyi-bearer."))
                    {
                        if (bearer != null) { bearer = null; break; }
                        bearer = candidate.substring("ruoyi-bearer.".length());
                    }
                }
                if (bearer != null && !bearer.isBlank()) loginUser = tokenService.getLoginUserByToken(bearer);
            }
        }
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNull(SecurityUtils.getAuthentication()))
        {
            tokenService.verifyToken(loginUser);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        chain.doFilter(request, response);
    }
}
