package com.project.meet.livestream.infrastructure;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.common.security.JwtService;
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
 * Authenticates the livestream ingest WebSocket upgrade — same JWT-via-
 * query-param convention as {@code SignalingHandshakeInterceptor}
 * (browsers can't set an Authorization header on a WS upgrade), but
 * deliberately doesn't reuse that class: it looks up a {@code Meeting},
 * and a livestream has no meeting at all (see LivestreamSession). This
 * interceptor only verifies the JWT and extracts the livestream id from
 * the path — whether that id actually corresponds to a live session is
 * checked afterward, in LivestreamIngestWebSocketHandler.
 */
public class LivestreamHandshakeInterceptor implements HandshakeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(LivestreamHandshakeInterceptor.class);

	public static final String ATTR_USER = "authenticatedUser";
	public static final String ATTR_LIVESTREAM_ID = "livestreamId";

	private final JwtService jwtService;

	public LivestreamHandshakeInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
	) {
		UUID livestreamId = extractLivestreamId(request);
		if (livestreamId == null) {
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
			log.debug("Rejected livestream ingest handshake: invalid token ({})", ex.getMessage());
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		attributes.put(ATTR_USER, user);
		attributes.put(ATTR_LIVESTREAM_ID, livestreamId);
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

	private UUID extractLivestreamId(ServerHttpRequest request) {
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
			return UUID.fromString(pathVariables.get("id"));
		} catch (IllegalArgumentException | NullPointerException ex) {
			return null;
		}
	}
}
