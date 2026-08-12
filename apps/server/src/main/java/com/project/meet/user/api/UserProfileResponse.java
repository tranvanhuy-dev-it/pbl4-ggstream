package com.project.meet.user.api;

import com.project.meet.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String displayName,
		String avatarUrl,
		Instant createdAt
) {

	public static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(), user.getCreatedAt());
	}
}
