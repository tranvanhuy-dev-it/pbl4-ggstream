package com.project.meet.livestream.infrastructure;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.livestream.application.LivestreamRegistry;
import com.project.meet.livestream.application.LivestreamSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.util.UUID;

/**
 * Ingest endpoint for the browser's MediaRecorder chunks — one-directional
 * (client → server only), carrying binary frames instead of JSON
 * envelopes. Auth is handled by {@link LivestreamHandshakeInterceptor}
 * (JWT via query param, no meeting involved).
 */
public class LivestreamIngestWebSocketHandler extends BinaryWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(LivestreamIngestWebSocketHandler.class);
	private static final String ATTR_LIVE_SESSION = "livestreamSession";

	private final LivestreamRegistry registry;

	public LivestreamIngestWebSocketHandler(LivestreamRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws IOException {
		UUID livestreamId = (UUID) session.getAttributes().get(LivestreamHandshakeInterceptor.ATTR_LIVESTREAM_ID);
		AuthenticatedUser user = (AuthenticatedUser) session.getAttributes().get(LivestreamHandshakeInterceptor.ATTR_USER);
		LivestreamSession live = registry.get(livestreamId);

		// Only the user who called POST /live/start (and got this id back)
		// may push bytes into that session's FFmpeg process.
		if (live == null || !live.getHostUserId().equals(user.userId())) {
			session.close(CloseStatus.NOT_ACCEPTABLE.withReason("No active livestream with this id"));
			return;
		}
		session.getAttributes().put(ATTR_LIVE_SESSION, live);
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
		LivestreamSession live = (LivestreamSession) session.getAttributes().get(ATTR_LIVE_SESSION);
		if (live == null) {
			return;
		}
		try {
			live.writeChunk(message.getPayload());
		} catch (IOException ex) {
			// FFmpeg's stdin pipe closed/broke (e.g. the process exited) —
			// nothing more this connection can do; the broadcaster will
			// notice the socket close on its own.
			log.warn("Failed to write livestream chunk for {}: {}", live.getId(), ex.getMessage());
		}
	}
}
