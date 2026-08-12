package com.project.meet.sfu.infrastructure;

import com.project.meet.common.config.LiveKitProperties;
import com.project.meet.sfu.domain.MediaParticipantConfig;
import com.project.meet.sfu.domain.MediaRoom;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LiveKitMediaServerTest {

	private static final LiveKitProperties CONFIGURED =
			new LiveKitProperties("ws://localhost:7880", "test-key", "test-secret-not-for-production");

	@Test
	void createParticipantReturnsTheConfiguredUrlAndAThreePartJwt() {
		LiveKitMediaServer server = new LiveKitMediaServer(CONFIGURED);
		UUID meetingId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		MediaParticipantConfig config = server.createParticipant(meetingId, userId, "Guest");

		assertThat(config.url()).isEqualTo("ws://localhost:7880");
		// A signed JWT is header.payload.signature — this is pure local
		// signing (io.livekit.server.AccessToken#toJwt), no network call,
		// so it must succeed even if no LiveKit server is actually running.
		assertThat(config.token().split("\\.")).hasSize(3);
	}

	@Test
	void differentUsersGetDifferentTokens() {
		LiveKitMediaServer server = new LiveKitMediaServer(CONFIGURED);
		UUID meetingId = UUID.randomUUID();

		MediaParticipantConfig a = server.createParticipant(meetingId, UUID.randomUUID(), "A");
		MediaParticipantConfig b = server.createParticipant(meetingId, UUID.randomUUID(), "B");

		assertThat(a.token()).isNotEqualTo(b.token());
	}

	@Test
	void roomLifecycleCallsAreNoOpsWhenLiveKitIsNotConfigured() {
		LiveKitMediaServer server = new LiveKitMediaServer(
				new LiveKitProperties(null, null, null));
		UUID meetingId = UUID.randomUUID();

		assertThatCode(() -> {
			MediaRoom room = server.createRoom(meetingId);
			assertThat(room.meetingId()).isEqualTo(meetingId);
			server.closeRoom(meetingId);
		}).doesNotThrowAnyException();
	}
}
