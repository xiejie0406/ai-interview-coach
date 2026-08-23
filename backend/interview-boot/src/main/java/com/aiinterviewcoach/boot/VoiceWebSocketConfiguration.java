package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.inbound.websocket.VoiceCaptureCoordinator;
import com.aiinterviewcoach.adapters.inbound.websocket.VoiceWebSocketHandler;
import com.aiinterviewcoach.adapters.outbound.voice.InMemoryVoiceSessionTicketAdapter;
import com.aiinterviewcoach.application.agent.port.SpeechToTextPort;
import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.boot.properties.ProviderProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "web-socket-endpoints-enabled",
        havingValue = "true")
public class VoiceWebSocketConfiguration {

    @Bean
    VoiceCaptureCoordinator voiceCaptureCoordinator(VoiceRepository repository, ObjectStoragePort storage,
                                                     SpeechToTextPort speechToText, IdGeneratorPort ids,
                                                     ContentDigestPort digest, DomainEventPort events,
                                                     TransactionPort transaction, ClockPort clock,
                                                     ProviderProperties providers) {
        var asr = providers.getVolcengineSpeech().getAsr();
        return new VoiceCaptureCoordinator(repository, storage, speechToText, ids, digest, events,
                transaction, clock, "volcengine", asr.getModel(), asr.getLanguage());
    }

    @Bean
    VoiceWebSocketHandler voiceWebSocketHandler(ObjectMapper json,
                                                 InMemoryVoiceSessionTicketAdapter tickets,
                                                 VoiceCaptureCoordinator coordinator) {
        return new VoiceWebSocketHandler(json, tickets, coordinator);
    }

    @Bean
    WebSocketConfigurer voiceWebSocketConfigurer(VoiceWebSocketHandler handler) {
        return registry -> registry.addHandler(handler, "/ws/v1/interviews/*/voice");
    }
}
