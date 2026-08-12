package com.project.meet.signaling.application;

import com.project.meet.participant.domain.ParticipantRole;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

/**
 * A single live WebSocket connection into a meeting room. All mutation of
 * this object happens inside that room's serialized command queue (see
 * {@link RoomCommandDispatcher}), so fields are plain (not atomic/volatile)
 * by design — the ordering guarantee comes from the dispatcher, not from
 * this class.
 */
public class RuntimeParticipant {

	private WebSocketSession session;
	private final UUID userId;
	private final String displayName;
	private final ParticipantRole role;
	private Instant lastHeartbeatAt;
	private Instant disconnectedAt;

	public RuntimeParticipant(WebSocketSession session, UUID userId, String displayName, ParticipantRole role) {
		this.session = session;
		this.userId = userId;
		this.displayName = displayName;
		this.role = role;
		this.lastHeartbeatAt = Instant.now();
	}

	public void recordHeartbeat() {
		this.lastHeartbeatAt = Instant.now();
	}

	/**
	 * Re-point this participant at a new WebSocket session after a reconnect
	 * (see {@link MeetingRuntime}'s grace-period handling) — same identity,
	 * new transport. Mutated only inside the room's serialized command queue,
	 * same as every other field here.
	 */
	public void reconnect(WebSocketSession newSession) {
		this.session = newSession;
		this.disconnectedAt = null;
		this.lastHeartbeatAt = Instant.now();
	}

	public void markDisconnected() {
		this.disconnectedAt = Instant.now();
	}

	public boolean isPendingRemoval() {
		return disconnectedAt != null;
	}

	public Instant getDisconnectedAt() {
		return disconnectedAt;
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

	public ParticipantRole getRole() {
		return role;
	}

	public Instant getLastHeartbeatAt() {
		return lastHeartbeatAt;
	}
}
