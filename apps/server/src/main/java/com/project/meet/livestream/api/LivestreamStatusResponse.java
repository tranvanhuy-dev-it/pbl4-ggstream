package com.project.meet.livestream.api;

import com.project.meet.livestream.domain.LivestreamStatus;

/**
 * {@code id} is only ever populated for the broadcaster's own start
 * response (it's the credential needed to call stop) — never returned from
 * the public by-code status lookup, so an anonymous viewer has no way to
 * stop someone else's stream.
 */
public record LivestreamStatusResponse(String id, String code, String title, LivestreamStatus status, String hlsUrl) {

	public static LivestreamStatusResponse started(String id, String code, String title, String hlsUrl) {
		return new LivestreamStatusResponse(id, code, title, LivestreamStatus.LIVE, hlsUrl);
	}

	public static LivestreamStatusResponse publicLive(String code, String title, String hlsUrl) {
		return new LivestreamStatusResponse(null, code, title, LivestreamStatus.LIVE, hlsUrl);
	}

	public static LivestreamStatusResponse publicEnded(String code) {
		return new LivestreamStatusResponse(null, code, null, LivestreamStatus.ENDED, null);
	}
}
