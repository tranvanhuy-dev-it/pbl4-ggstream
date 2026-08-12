package com.project.meet.signaling.application;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MeetingRuntimeRegistry {

	private final Map<UUID, MeetingRuntime> runtimesByMeetingId = new ConcurrentHashMap<>();

	public MeetingRuntime getOrCreate(UUID meetingId) {
		return runtimesByMeetingId.computeIfAbsent(meetingId, MeetingRuntime::new);
	}

	public MeetingRuntime get(UUID meetingId) {
		return runtimesByMeetingId.get(meetingId);
	}

	public Collection<MeetingRuntime> all() {
		return runtimesByMeetingId.values();
	}

	/** Evicts the runtime once its last participant disconnects, so empty rooms don't linger in memory forever. */
	public void evictIfEmpty(UUID meetingId) {
		runtimesByMeetingId.computeIfPresent(meetingId, (id, runtime) -> runtime.isEmpty() ? null : runtime);
	}
}
