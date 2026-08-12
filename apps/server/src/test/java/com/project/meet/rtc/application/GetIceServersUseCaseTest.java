package com.project.meet.rtc.application;

import com.project.meet.common.config.TurnProperties;
import com.project.meet.rtc.api.IceServer;
import com.project.meet.rtc.api.IceServersResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetIceServersUseCaseTest {

	@Test
	void returnsStunOnlyWhenTurnIsNotConfigured() {
		TurnProperties properties = new TurnProperties(null, 3478, null, 3600, "stun:stun.l.google.com:19302");
		GetIceServersUseCase useCase = new GetIceServersUseCase(properties, new TurnCredentialService(properties));

		IceServersResponse response = useCase.execute(UUID.randomUUID());

		assertThat(response.iceServers()).hasSize(1);
		assertThat(response.iceServers().get(0).urls()).containsExactly("stun:stun.l.google.com:19302");
		assertThat(response.iceServers().get(0).credential()).isNull();
	}

	@Test
	void includesTurnWithCredentialsWhenConfigured() {
		TurnProperties properties = new TurnProperties("turn.example.com", 3478, "shared-secret", 3600, "stun:stun.l.google.com:19302");
		GetIceServersUseCase useCase = new GetIceServersUseCase(properties, new TurnCredentialService(properties));

		IceServersResponse response = useCase.execute(UUID.randomUUID());

		assertThat(response.iceServers()).hasSize(2);
		IceServer turn = response.iceServers().stream()
				.filter(s -> s.credential() != null)
				.findFirst()
				.orElseThrow();
		assertThat(turn.urls()).contains("turn:turn.example.com:3478?transport=udp", "turn:turn.example.com:3478?transport=tcp");
		assertThat(turn.username()).isNotBlank();
		assertThat(turn.credential()).isNotBlank();
	}

	@Test
	void neverExposesTheTurnSharedSecretItself() {
		TurnProperties properties = new TurnProperties("turn.example.com", 3478, "super-secret-value", 3600, "stun:stun.l.google.com:19302");
		GetIceServersUseCase useCase = new GetIceServersUseCase(properties, new TurnCredentialService(properties));

		IceServersResponse response = useCase.execute(UUID.randomUUID());

		assertThat(response.iceServers()).noneMatch(s ->
				s.credential() != null && s.credential().contains("super-secret-value"));
	}
}
