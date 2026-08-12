package com.project.meet.auth.api;

import java.time.Instant;

public record AuthResponse(
		String accessToken,
		String tokenType,
		Instant expiresAt,
		UserSummary user
) {

	public static AuthResponse of(String accessToken, Instant expiresAt, UserSummary user) {
		return new AuthResponse(accessToken, "Bearer", expiresAt, user);
	}
}
