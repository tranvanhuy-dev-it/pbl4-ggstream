package com.project.meet.livestream.application;

import com.project.meet.livestream.api.LivestreamStatusResponse;
import org.springframework.stereotype.Component;

/**
 * Public (no auth) — backs the /watch/{code} viewer page. A code that
 * isn't currently registered (never existed, or the stream already ended)
 * is reported as ENDED rather than a 404 — nothing is persisted once a
 * livestream stops, so "unknown code" and "code was live, now isn't" are
 * indistinguishable, and both should just mean "nothing to watch here".
 */
@Component
public class GetLivestreamStatusUseCase {

	private final LivestreamRegistry registry;

	public GetLivestreamStatusUseCase(LivestreamRegistry registry) {
		this.registry = registry;
	}

	public LivestreamStatusResponse execute(String code) {
		LivestreamSession session = registry.getByCode(code);
		return session != null
				? LivestreamStatusResponse.publicLive(code, session.getTitle(), session.getHlsUrl())
				: LivestreamStatusResponse.publicEnded(code);
	}
}
