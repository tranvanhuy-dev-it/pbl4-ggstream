package com.project.meet.signaling.infrastructure;

import com.project.meet.common.config.WebSocketProperties;
import com.project.meet.signaling.application.MeetingRuntime;
import com.project.meet.signaling.application.MeetingRuntimeRegistry;
import com.project.meet.signaling.application.RuntimeParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Closes WebSocket sessions that stopped sending PING heartbeats. Closing
 * the session triggers the handler's normal
 * {@code afterConnectionClosed} → room cleanup + PARTICIPANT_LEFT broadcast
 * path, so this class doesn't duplicate any of that logic — it only decides
 * *when* a session counts as dead.
 */
@Component
public class HeartbeatReaper {

	private static final Logger log = LoggerFactory.getLogger(HeartbeatReaper.class);
	private static final CloseStatus HEARTBEAT_TIMEOUT = new CloseStatus(4000, "Heartbeat timeout");

	private final MeetingRuntimeRegistry runtimeRegistry;
	private final WebSocketProperties properties;

	public HeartbeatReaper(MeetingRuntimeRegistry runtimeRegistry, WebSocketProperties properties) {
		this.runtimeRegistry = runtimeRegistry;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${app.websocket.heartbeat-check-interval-ms:5000}")
	void reapStaleSessions() {
		Duration timeout = Duration.ofSeconds(properties.heartbeatTimeoutSeconds());
		Instant now = Instant.now();

		for (MeetingRuntime runtime : runtimeRegistry.all()) {
			for (RuntimeParticipant participant : runtime.participants()) {
				if (Duration.between(participant.getLastHeartbeatAt(), now).compareTo(timeout) > 0) {
					log.info("HEARTBEAT_TIMEOUT meetingId={} userId={}", runtime.getMeetingId(), participant.getUserId());
					try {
						participant.getSession().close(HEARTBEAT_TIMEOUT);
					} catch (IOException ex) {
						log.debug("Failed to close stale session: {}", ex.getMessage());
					}
				}
			}
		}
	}
}
