package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.configuration.properties.VoiceRuntimeProperties;
import com.ruoyi.interview.infrastructure.voice.InMemoryVoiceSessionTicketAdapter;
import com.ruoyi.interview.infrastructure.voice.RedisVoiceSessionTicketAdapter;
import com.ruoyi.interview.infrastructure.voice.UnavailableVoiceSessionTicketAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Voice ticket 存储的明确装配；Redis 失效时 fail-closed，不静默回退到内存。 */
@Configuration
public class VoiceTicketConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "interview.voice-runtime", name = "ticket-store",
            havingValue = "redis", matchIfMissing = false)
    VoiceSessionTicketPort redisVoiceSessionTicketPort(
            VoiceRuntimeProperties properties,
            ObjectProvider<RedisConnectionFactory> connectionFactories,
            ObjectMapper objectMapper,
            Clock clock) {
        RedisConnectionFactory factory = connectionFactories.getIfAvailable();
        if (factory == null) {
            return new UnavailableVoiceSessionTicketAdapter();
        }
        return new RedisVoiceSessionTicketAdapter(factory, objectMapper, clock,
                properties.getTicketRedisKeyPrefix());
    }

    @Bean
    @ConditionalOnProperty(prefix = "interview.voice-runtime", name = "ticket-store",
            havingValue = "memory", matchIfMissing = true)
    VoiceSessionTicketPort inMemoryVoiceSessionTicketPort(Clock clock) {
        return new InMemoryVoiceSessionTicketAdapter(clock);
    }
}
