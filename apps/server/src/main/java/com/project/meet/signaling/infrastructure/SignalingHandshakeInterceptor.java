package com.project.meet.signaling.infrastructure;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.common.security.JwtService;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticates a WebSocket upgrade request before the handshake completes.
 * Browsers can't set an Authorization header on a native WebSocket
 * connection, so the JWT travels as a query parameter instead
 * (?token=...) — the one deliberate exception to "JWT always via
 * Authorization header" elsewhere in this API.
 */
public class SignalingHandshakeInterceptor implements HandshakeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(SignalingHandshakeInterceptor.class);

	public static final String ATTR_USER = "authenticatedUser";
	public static final String ATTR_MEETING_ID = "meetingId";

	private final JwtService jwtService;
	private final MeetingRepository meetingRepository;

	public SignalingHandshakeInterceptor(JwtService jwtService, MeetingRepository meetingRepository) {
		this.jwtService = jwtService;
		this.meetingRepository = meetingRepository;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
	) {
		UUID meetingId = extractMeetingId(request);
		if (meetingId == null) {
			response.setStatusCode(HttpStatus.NOT_FOUND);
			return false;
		}

		String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
		if (token == null || token.isBlank()) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		AuthenticatedUser user;
		try {
			user = jwtService.parseAuthenticatedUser(token);
		} catch (JwtException | IllegalArgumentException ex) {
			log.debug("Rejected WebSocket handshake: invalid token ({})", ex.getMessage());
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
		if (meeting == null || meeting.isEnded()) {
			response.setStatusCode(HttpStatus.NOT_FOUND);
			return false;
		}

		attributes.put(ATTR_USER, user);
		attributes.put(ATTR_MEETING_ID, meetingId);
		return true;
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception
	) {
		// no-op
	}

	private UUID extractMeetingId(ServerHttpRequest request) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return null;
		}
		HttpServletRequest httpRequest = servletRequest.getServletRequest();
		@SuppressWarnings("unchecked")
		Map<String, String> pathVariables =
				(Map<String, String>) httpRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (pathVariables == null) {
			return null;
		}
		try {
			return UUID.fromString(pathVariables.get("meetingId"));
		} catch (IllegalArgumentException | NullPointerException ex) {
			return null;
		}
	}
}
