package com.ruoyi.interview.configuration;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VoiceJwtFilterTest {
    @Test void authenticatesVoiceUpgradeBeforeAuthorizationButRejectsOtherPathsAndDuplicateTokens() throws Exception {
        var tokens=mock(TokenService.class); var user=mock(LoginUser.class);
        when(tokens.getLoginUserByToken("test-token")).thenReturn(user);
        var filter=new JwtAuthenticationTokenFilter(); ReflectionTestUtils.setField(filter,"tokenService",tokens);
        String path="/ws/v1/interviews/00000000-0000-4000-8000-000000000001/voice";
        try {
            var request=new MockHttpServletRequest("GET",path); request.addHeader("Upgrade","websocket");
            request.addHeader("Sec-WebSocket-Protocol","aic.voice.v1, ruoyi-bearer.test-token");
            filter.doFilter(request,new MockHttpServletResponse(),(req,res) -> assertNotNull(SecurityContextHolder.getContext().getAuthentication()));
            SecurityContextHolder.clearContext();
            var other=new MockHttpServletRequest("GET","/api/v1/interviews"); other.addHeader("Upgrade","websocket");
            other.addHeader("Sec-WebSocket-Protocol","ruoyi-bearer.test-token");
            filter.doFilter(other,new MockHttpServletResponse(),(req,res) -> assertNull(SecurityContextHolder.getContext().getAuthentication()));
            var duplicate=new MockHttpServletRequest("GET",path); duplicate.addHeader("Upgrade","websocket");
            duplicate.addHeader("Sec-WebSocket-Protocol","ruoyi-bearer.test-token, ruoyi-bearer.another");
            filter.doFilter(duplicate,new MockHttpServletResponse(),(req,res) -> assertNull(SecurityContextHolder.getContext().getAuthentication()));
            verify(tokens,times(1)).getLoginUserByToken("test-token");
        } finally { SecurityContextHolder.clearContext(); }
    }
}
