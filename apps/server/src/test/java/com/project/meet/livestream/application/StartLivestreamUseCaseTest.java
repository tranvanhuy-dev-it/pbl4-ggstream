package com.project.meet.livestream.application;

import com.project.meet.common.config.LivestreamProperties;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.livestream.domain.LivestreamStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StartLivestreamUseCaseTest {

	@TempDir
	private Path tempDir;

	private final FakeLivestreamProcessLauncher processLauncher = new FakeLivestreamProcessLauncher();
	private final LivestreamRegistry registry = new LivestreamRegistry();
	private final LivestreamCodeGenerator codeGenerator = new LivestreamCodeGenerator();

	private StartLivestreamUseCase newUseCase() {
		return new StartLivestreamUseCase(registry, processLauncher, new LivestreamProperties("ffmpeg", tempDir.toString(), "-c:v libx264 -preset veryfast"), codeGenerator);
	}

	@Test
	void startingALivestreamNeedsNoMeetingAtAll() {
		UUID hostId = UUID.randomUUID();

		LivestreamStatusResponse response = newUseCase().execute(hostId, "Buổi phát của tôi");

		assertThat(response.status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(response.id()).isNotBlank();
		assertThat(response.code()).matches("[a-z]{3}-[a-z]{4}-[a-z]{3}");
		assertThat(response.title()).isEqualTo("Buổi phát của tôi");
		assertThat(response.hlsUrl()).isEqualTo("/livestreams/" + response.id() + "/stream.m3u8");
		assertThat(registry.get(UUID.fromString(response.id()))).isNotNull();
		assertThat(registry.getByCode(response.code())).isNotNull();
	}

	@Test
	void blankTitleDefaultsToAGenericLabel() {
		LivestreamStatusResponse response = newUseCase().execute(UUID.randomUUID(), "   ");

		assertThat(response.title()).isEqualTo("Livestream");
	}

	@Test
	void everyStartGetsAUniqueIdAndCode() {
		StartLivestreamUseCase useCase = newUseCase();

		LivestreamStatusResponse a = useCase.execute(UUID.randomUUID(), "A");
		LivestreamStatusResponse b = useCase.execute(UUID.randomUUID(), "B");

		assertThat(a.id()).isNotEqualTo(b.id());
		assertThat(a.code()).isNotEqualTo(b.code());
	}
}
