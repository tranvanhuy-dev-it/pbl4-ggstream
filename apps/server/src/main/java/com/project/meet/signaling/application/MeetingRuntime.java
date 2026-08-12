package com.project.meet.signaling.application;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence state for one active meeting: who's currently
 * connected over WebSocket, keyed by session id, plus anyone held in the
 * waiting room lane. Never persisted — see
 * docs/database/schema.md#what-is-not-persisted for why this lives in
 * memory instead of PostgreSQL.
 */
public class MeetingRuntime {

	private final UUID meetingId;
	private final Map<String, RuntimeParticipant> participantsBySessionId = new ConcurrentHashMap<>();
	private final Map<String, WaitingParticipant> waitingBySessionId = new ConcurrentHashMap<>();

	public MeetingRuntime(UUID meetingId) {
		this.meetingId = meetingId;
	}

	public UUID getMeetingId() {
		return meetingId;
	}

	public void add(String sessionId, RuntimeParticipant participant) {
		participantsBySessionId.put(sessionId, participant);
	}

	public RuntimeParticipant get(String sessionId) {
		return participantsBySessionId.get(sessionId);
	}

	public RuntimeParticipant remove(String sessionId) {
		return participantsBySessionId.remove(sessionId);
	}

	public boolean isEmpty() {
		return participantsBySessionId.isEmpty() && waitingBySessionId.isEmpty();
	}

	public Collection<RuntimeParticipant> participants() {
		return participantsBySessionId.values();
	}

	public RuntimeParticipant findActiveByUserId(UUID userId) {
		return participantsBySessionId.values().stream()
				.filter(p -> p.getUserId().equals(userId))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Re-points an existing participant (found by user id, regardless of
	 * whether they're mid-grace-period after a drop) at a brand new
	 * WebSocket session, keyed under the new session's id. Returns
	 * {@code null} if no matching participant exists — i.e. this is a
	 * genuinely new join, not a reconnect, and the caller should fall back
	 * to the normal join flow.
	 */
	public RuntimeParticipant reconnect(UUID userId, WebSocketSession newSession) {
		RuntimeParticipant existing = findActiveByUserId(userId);
		if (existing == null) {
			return null;
		}
		participantsBySessionId.remove(existing.getSession().getId());
		existing.reconnect(newSession);
		participantsBySessionId.put(newSession.getId(), existing);
		return existing;
	}

	public void addWaiting(String sessionId, WaitingParticipant waiting) {
		waitingBySessionId.put(sessionId, waiting);
	}

	public WaitingParticipant removeWaiting(String sessionId) {
		return waitingBySessionId.remove(sessionId);
	}

	public WaitingParticipant removeWaitingByUserId(UUID userId) {
		WaitingParticipant match = waitingBySessionId.values().stream()
				.filter(w -> w.getUserId().equals(userId))
				.findFirst()
				.orElse(null);
		if (match != null) {
			waitingBySessionId.remove(match.getSession().getId());
		}
		return match;
	}

	public Collection<WaitingParticipant> waitingParticipants() {
		return waitingBySessionId.values();
	}
}
