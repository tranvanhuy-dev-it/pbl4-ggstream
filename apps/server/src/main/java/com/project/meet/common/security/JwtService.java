package com.project.meet.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

	private static final String EMAIL_CLAIM = "email";

	private final JwtProperties properties;
	private final SecretKey signingKey;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		if (properties.secret() == null || properties.secret().isBlank()) {
			throw new IllegalStateException("app.jwt.secret (JWT_SECRET env var) must be set");
		}
		this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateToken(UUID userId, String email) {
		Instant now = Instant.now();
		Instant expiry = now.plus(Duration.ofMinutes(properties.expirationMinutes()));

		return Jwts.builder()
				.subject(userId.toString())
				.claim(EMAIL_CLAIM, email)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(signingKey)
				.compact();
	}

	public Instant expirationFor(String token) {
		return parse(token).getExpiration().toInstant();
	}

	/**
	 * @throws JwtException if the token is malformed, expired, or has an invalid signature.
	 */
	public AuthenticatedUser parseAuthenticatedUser(String token) {
		Claims claims = parse(token);
		return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class));
	}

	private Claims parse(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
