package com.project.meet.common.security;

import java.util.UUID;

/**
 * The Spring Security principal for a JWT-authenticated request — resolved
 * by {@link JwtAuthenticationFilter} and injectable in controllers via
 * {@code @AuthenticationPrincipal AuthenticatedUser}.
 */
public record AuthenticatedUser(UUID userId, String email) {
}
