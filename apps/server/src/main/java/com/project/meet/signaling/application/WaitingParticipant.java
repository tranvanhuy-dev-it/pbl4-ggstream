package com.project.meet.signaling.application;

import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/**
 * A connection held in the waiting room lane: not yet part of
 * {@link MeetingRuntime}'s active participant set, so they don't appear in
 * anyone's roster and don't receive room broadcasts, until the host
 * approves them (see {@code SignalingWebSocketHandler#handleApprovalDecision}).
 */
public class WaitingParticipant {

	private final WebSocketSession session;
	private final UUID userId;
	private final String displayName;

	public WaitingParticipant(WebSocketSession session, UUID userId, String displayName) {
		this.session = session;
		this.userId = userId;
		this.displayName = displayName;
	}

	public WebSocketSession getSession() {
		return session;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getDisplayName() {
		return displayName;
	}
}
