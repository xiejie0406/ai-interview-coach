package com.ruoyi.interview.configuration;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.List;

/**
 * 语音 WebSocket 由 RuoYi 唯一启动容器暴露。
 * 升级只接受 HTTP 握手时已有的 RuoYi SecurityContext，不解析 query ticket、Cookie 或第二套 session。
 */
@Configuration
@EnableWebSocket
public class VoiceWebSocketConfiguration implements WebSocketConfigurer {

    @Autowired
    private ObjectProvider<HandshakeInterceptor> voiceHandshakeInterceptor;

    @Autowired
    private ObjectProvider<WebSocketHandler> ruoyiVoiceWebSocketHandler;

    @Bean
    public com.ruoyi.interview.controller.websocket.VoiceWebSocketHandler ruoyiVoiceWebSocketHandler(
            tools.jackson.databind.ObjectMapper json,
            com.ruoyi.interview.infrastructure.voice.InMemoryVoiceSessionTicketAdapter tickets,
            com.ruoyi.interview.controller.websocket.VoiceCaptureCoordinator coordinator) {
        return new com.ruoyi.interview.controller.websocket.VoiceWebSocketHandler(json, tickets, coordinator);
    }

    @Bean
    public HandshakeInterceptor ruoyiVoiceHandshakeInterceptor(TokenService tokenService) {
        return new RuoyiVoiceHandshakeInterceptor(tokenService);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ruoyiVoiceWebSocketHandler.getObject(), "/ws/v1/interviews/*/voice")
                .addInterceptors(voiceHandshakeInterceptor.getObject())
                .setAllowedOriginPatterns("http://127.0.0.1:*", "http://localhost:*");
    }

    static final class RuoyiVoiceHandshakeInterceptor implements HandshakeInterceptor {
        private static final String TOKEN_SUBPROTOCOL_PREFIX = "ruoyi-bearer.";
        private final TokenService tokenService;

        RuoyiVoiceHandshakeInterceptor(TokenService tokenService) {
            this.tokenService = tokenService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            Authentication authentication = SecurityUtils.getAuthentication();
            LoginUser loginUser = authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof LoginUser principal
                    ? principal : null;
            if (loginUser == null) {
                String bearer = bearerFromHandshake(request);
                loginUser = bearer == null ? null : tokenService.getLoginUserByToken(bearer);
                if (loginUser != null) {
                    tokenService.verifyToken(loginUser);
                    var tokenAuthentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null, loginUser.getAuthorities());
                    if (request instanceof org.springframework.http.server.ServletServerHttpRequest servlet) {
                        tokenAuthentication.setDetails(new WebAuthenticationDetailsSource()
                                .buildDetails(servlet.getServletRequest()));
                    }
                    SecurityContextHolder.getContext().setAuthentication(tokenAuthentication);
                }
            }
            if (loginUser == null) {
                return false;
            }
            if (!SecurityUtils.hasPermi(loginUser.getPermissions(), "interview:voice:upload")) {
                return false;
            }
            attributes.put("ruoyiUserId", loginUser.getUserId());
            attributes.put("ruoyiPrincipal", loginUser);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 不记录 token、Cookie、URL query 或音频正文。
        }

        private static String bearerFromHandshake(ServerHttpRequest request) {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return authorization;
            }
            List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
            if (protocols == null) return null;
            for (String value : protocols) {
                for (String protocol : value.split(",")) {
                    String candidate = protocol.trim();
                    if (candidate.startsWith(TOKEN_SUBPROTOCOL_PREFIX)
                            && candidate.length() > TOKEN_SUBPROTOCOL_PREFIX.length()) {
                        return "Bearer " + candidate.substring(TOKEN_SUBPROTOCOL_PREFIX.length());
                    }
                }
            }
            return null;
        }
    }

    static final class VoiceNotReadyWebSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            if (!session.getAttributes().containsKey("ruoyiUserId")) {
                session.close(new CloseStatus(1008, "ruoyi_auth_required"));
                return;
            }
            session.close(new CloseStatus(1013, "voice_not_ready"));
        }
    }
}
