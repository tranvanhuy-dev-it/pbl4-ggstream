package com.project.meet.livestream.application;

import com.project.meet.livestream.domain.LivestreamNotLiveException;
import com.project.meet.livestream.domain.NotLivestreamOwnerException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StopLivestreamUseCaseTest {

	private final LivestreamRegistry registry = new LivestreamRegistry();
	private final StopLivestreamUseCase useCase = new StopLivestreamUseCase(registry);

	private LivestreamSession register(UUID id, UUID hostId) {
		FakeLivestreamProcessLauncher launcher = new FakeLivestreamProcessLauncher();
		LivestreamProcessHandle handle = launcher.launch(id, null);
		LivestreamSession session = new LivestreamSession(id, "abc-defg-hij", hostId, "Title", "/livestreams/x/stream.m3u8", handle);
		registry.put(session);
		return session;
	}

	@Test
	void ownerCanStopAnActiveLivestream() {
		UUID id = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		register(id, hostId);

		useCase.execute(id, hostId);

		assertThat(registry.get(id)).isNull();
	}

	@Test
	void nonOwnerCannotStopSomeoneElsesLivestream() {
		UUID id = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		register(id, hostId);

		assertThatThrownBy(() -> useCase.execute(id, UUID.randomUUID()))
				.isInstanceOf(NotLivestreamOwnerException.class);
		assertThat(registry.get(id)).as("a rejected stop must not remove the session").isNotNull();
	}

	@Test
	void stoppingAnUnknownIdIs404() {
		assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(LivestreamNotLiveException.class);
	}
}
