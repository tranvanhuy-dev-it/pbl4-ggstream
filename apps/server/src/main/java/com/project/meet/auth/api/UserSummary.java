package com.project.meet.auth.api;

import com.project.meet.user.domain.User;

import java.util.UUID;

public record UserSummary(UUID id, String email, String displayName) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName());
	}
}
