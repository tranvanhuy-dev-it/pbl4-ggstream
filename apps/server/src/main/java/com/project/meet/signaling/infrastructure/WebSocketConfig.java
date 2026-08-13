package com.project.meet.signaling.infrastructure;

import com.project.meet.chat.application.SendChatMessageUseCase;
import com.project.meet.common.config.CorsProperties;
import com.project.meet.common.config.WebSocketProperties;
import com.project.meet.common.security.JwtService;
import com.project.meet.livestream.application.LivestreamRegistry;
import com.project.meet.livestream.infrastructure.LivestreamIngestWebSocketHandler;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.signaling.application.MeetingRuntimeRegistry;
import com.project.meet.signaling.application.RoomCommandDispatcher;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final JwtService jwtService;
	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final MeetingRuntimeRegistry runtimeRegistry;
	private final RoomCommandDispatcher dispatcher;
	private final SendChatMessageUseCase sendChatMessageUseCase;
	private final ObjectMapper objectMapper;
	private final CorsProperties corsProperties;
	private final WebSocketProperties webSocketProperties;
	private final LivestreamRegistry livestreamRegistry;

	public WebSocketConfig(
			JwtService jwtService,
			MeetingRepository meetingRepository,
			UserRepository userRepository,
			MeetingRuntimeRegistry runtimeRegistry,
			RoomCommandDispatcher dispatcher,
			SendChatMessageUseCase sendChatMessageUseCase,
			ObjectMapper objectMapper,
			CorsProperties corsProperties,
			WebSocketProperties webSocketProperties,
			LivestreamRegistry livestreamRegistry
	) {
		this.jwtService = jwtService;
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.runtimeRegistry = runtimeRegistry;
		this.dispatcher = dispatcher;
		this.sendChatMessageUseCase = sendChatMessageUseCase;
		this.objectMapper = objectMapper;
		this.corsProperties = corsProperties;
		this.webSocketProperties = webSocketProperties;
		this.livestreamRegistry = livestreamRegistry;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(
						new SignalingWebSocketHandler(runtimeRegistry, dispatcher, meetingRepository, userRepository, sendChatMessageUseCase, objectMapper, webSocketProperties),
						"/ws/meetings/{meetingId}")
				.addInterceptors(new SignalingHandshakeInterceptor(jwtService, meetingRepository))
				.setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));

		registry.addHandler(
						new LivestreamIngestWebSocketHandler(livestreamRegistry),
						"/ws/meetings/{meetingId}/livestream-ingest")
				.addInterceptors(new SignalingHandshakeInterceptor(jwtService, meetingRepository))
				.setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));
	}
}
