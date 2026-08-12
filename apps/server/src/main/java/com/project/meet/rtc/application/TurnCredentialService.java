package com.project.meet.rtc.application;

import com.project.meet.common.config.TurnProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates short-lived TURN credentials using Coturn's REST API auth
 * convention ({@code use-auth-secret} / {@code static-auth-secret} in
 * turnserver.conf): the "username" is an expiry timestamp (optionally
 * prefixed with a caller id), and the "credential" is a
 * Base64(HMAC-SHA1(sharedSecret, username)) that Coturn recomputes itself
 * to verify the credential without ever storing per-user passwords. The
 * shared secret this depends on ({@link TurnProperties#secret()}) never
 * leaves the backend — only the derived, time-boxed credential does.
 */
@Component
public class TurnCredentialService {

	private static final String HMAC_ALGORITHM = "HmacSHA1";

	private final TurnProperties properties;

	public TurnCredentialService(TurnProperties properties) {
		this.properties = properties;
	}

	public TurnCredential generate(UUID userId) {
		long expiresAtEpochSeconds = Instant.now().plusSeconds(properties.credentialTtlSeconds()).getEpochSecond();
		String username = expiresAtEpochSeconds + ":" + userId;
		String credential = hmacSha1Base64(properties.secret(), username);
		return new TurnCredential(username, credential);
	}

	private String hmacSha1Base64(String secret, String message) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
			throw new IllegalStateException("Failed to compute TURN credential", ex);
		}
	}

	public record TurnCredential(String username, String credential) {
	}
}
