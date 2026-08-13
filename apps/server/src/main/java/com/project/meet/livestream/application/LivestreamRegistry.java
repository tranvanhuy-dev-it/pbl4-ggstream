package com.project.meet.livestream.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One active {@link LivestreamSession} per meeting id, at most. In-memory only — see LivestreamSession. */
@Component
public class LivestreamRegistry {

	private final Map<UUID, LivestreamSession> sessionsByMeetingId = new ConcurrentHashMap<>();

	public LivestreamSession get(UUID meetingId) {
		return sessionsByMeetingId.get(meetingId);
	}

	public void put(UUID meetingId, LivestreamSession session) {
		sessionsByMeetingId.put(meetingId, session);
	}

	public LivestreamSession remove(UUID meetingId) {
		return sessionsByMeetingId.remove(meetingId);
	}
}
