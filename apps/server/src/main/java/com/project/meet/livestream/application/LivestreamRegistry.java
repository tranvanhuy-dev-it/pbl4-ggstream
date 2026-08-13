package com.project.meet.livestream.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every currently-live {@link LivestreamSession}, indexed both by the
 * broadcaster's own id (for start/stop) and by the public join code (for
 * anonymous viewers) — both maps always point at the same session object.
 */
@Component
public class LivestreamRegistry {

	private final Map<UUID, LivestreamSession> byId = new ConcurrentHashMap<>();
	private final Map<String, LivestreamSession> byCode = new ConcurrentHashMap<>();

	public LivestreamSession get(UUID id) {
		return byId.get(id);
	}

	public LivestreamSession getByCode(String code) {
		return byCode.get(code);
	}

	public boolean codeInUse(String code) {
		return byCode.containsKey(code);
	}

	public void put(LivestreamSession session) {
		byId.put(session.getId(), session);
		byCode.put(session.getCode(), session);
	}

	public LivestreamSession remove(UUID id) {
		LivestreamSession session = byId.remove(id);
		if (session != null) {
			byCode.remove(session.getCode());
		}
		return session;
	}
}
