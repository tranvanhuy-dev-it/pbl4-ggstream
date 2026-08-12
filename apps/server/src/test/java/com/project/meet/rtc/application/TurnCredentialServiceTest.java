package com.project.meet.rtc.application;

import com.project.meet.common.config.TurnProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnCredentialServiceTest {

	@Test
	void generatesAUsernameThatEncodesAnExpiryTimestampAndTheCaller() {
		UUID userId = UUID.randomUUID();
		TurnCredentialService service = new TurnCredentialService(
				new TurnProperties("turn.example.com", 3478, "shared-secret", 3600, "stun:stun.l.google.com:19302"));

		TurnCredentialService.TurnCredential credential = service.generate(userId);

		String[] parts = credential.username().split(":", 2);
		assertThat(parts).hasSize(2);
		long expiry = Long.parseLong(parts[0]);
		assertThat(expiry).isGreaterThan(System.currentTimeMillis() / 1000);
		assertThat(parts[1]).isEqualTo(userId.toString());
	}

	@Test
	void credentialIsAValidHmacSha1OfTheUsernameUnderTheSharedSecret() throws Exception {
		String secret = "shared-secret";
		TurnCredentialService service = new TurnCredentialService(
				new TurnProperties("turn.example.com", 3478, secret, 3600, "stun:stun.l.google.com:19302"));

		TurnCredentialService.TurnCredential credential = service.generate(UUID.randomUUID());

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		String expected = Base64.getEncoder().encodeToString(mac.doFinal(credential.username().getBytes(StandardCharsets.UTF_8)));

		assertThat(credential.credential()).isEqualTo(expected);
	}

	@Test
	void differentUsersGetDifferentCredentials() {
		TurnCredentialService service = new TurnCredentialService(
				new TurnProperties("turn.example.com", 3478, "shared-secret", 3600, "stun:stun.l.google.com:19302"));

		TurnCredentialService.TurnCredential a = service.generate(UUID.randomUUID());
		TurnCredentialService.TurnCredential b = service.generate(UUID.randomUUID());

		assertThat(a.username()).isNotEqualTo(b.username());
		assertThat(a.credential()).isNotEqualTo(b.credential());
	}
}
