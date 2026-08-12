package com.project.meet.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-at-least-32-bytes-long-1234567890";

	@Test
	void generatesTokenThatRoundTripsToTheSameUser() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));
		UUID userId = UUID.randomUUID();

		String token = jwtService.generateToken(userId, "user@example.com");
		AuthenticatedUser parsed = jwtService.parseAuthenticatedUser(token);

		assertThat(parsed.userId()).isEqualTo(userId);
		assertThat(parsed.email()).isEqualTo("user@example.com");
	}

	@Test
	void rejectsExpiredToken() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1));
		String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

		assertThatThrownBy(() -> jwtService.parseAuthenticatedUser(token))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejectsTokenSignedWithADifferentSecret() {
		JwtService signer = new JwtService(new JwtProperties(SECRET, 60));
		JwtService verifier = new JwtService(new JwtProperties("a-completely-different-secret-value-of-the-same-length-12345", 60));

		String token = signer.generateToken(UUID.randomUUID(), "user@example.com");

		assertThatThrownBy(() -> verifier.parseAuthenticatedUser(token))
				.isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
	}

	@Test
	void constructorRejectsBlankSecret() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties(" ", 60)))
				.isInstanceOf(IllegalStateException.class);
	}
}
