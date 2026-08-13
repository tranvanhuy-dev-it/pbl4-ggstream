package com.project.meet.livestream.api;

import com.project.meet.livestream.domain.LivestreamStatus;

public record LivestreamStatusResponse(LivestreamStatus status, String hlsUrl) {

	public static LivestreamStatusResponse live(String hlsUrl) {
		return new LivestreamStatusResponse(LivestreamStatus.LIVE, hlsUrl);
	}

	public static LivestreamStatusResponse ended() {
		return new LivestreamStatusResponse(LivestreamStatus.ENDED, null);
	}
}
