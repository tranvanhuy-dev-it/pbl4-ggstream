package com.project.meet.signaling.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence state for one active meeting: who's currently
 * connected over WebSocket, keyed by session id. Never persisted — see
 * docs/database/schema.md#what-is-not-persisted for why this lives in
 * memory instead of PostgreSQL.
 */
public class MeetingRuntime {

	private final UUID meetingId;
	private final Map<String, RuntimeParticipant> participantsBySessionId = new ConcurrentHashMap<>();

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
		return participantsBySessionId.isEmpty();
	}

	public Collection<RuntimeParticipant> participants() {
		return participantsBySessionId.values();
	}
}
