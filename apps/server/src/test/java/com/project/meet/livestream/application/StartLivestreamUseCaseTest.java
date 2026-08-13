package com.project.meet.livestream.application;

import com.project.meet.common.config.LivestreamProperties;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.livestream.domain.LivestreamAlreadyLiveException;
import com.project.meet.livestream.domain.LivestreamStatus;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.domain.MeetingEndedException;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartLivestreamUseCaseTest {

	@Mock
	private MeetingRepository meetingRepository;

	@TempDir
	private Path tempDir;

	private final FakeLivestreamProcessLauncher processLauncher = new FakeLivestreamProcessLauncher();
	private final LivestreamRegistry registry = new LivestreamRegistry();

	private StartLivestreamUseCase newUseCase() {
		return new StartLivestreamUseCase(meetingRepository, registry, processLauncher, new LivestreamProperties("ffmpeg", tempDir.toString()));
	}

	private Meeting meetingHostedBy(UUID hostId) throws Exception {
		User host = new User("host@example.com", "hash", "Host");
		setId(host, hostId);
		return new Meeting("abc-defg-hij", "Đại hội đồng", host, MeetingAccessType.PUBLIC);
	}

	private void setId(User user, UUID id) throws Exception {
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
	}

	@Test
	void hostCanStartALivestreamAndReceivesAnHlsUrl() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(hostId)));

		LivestreamStatusResponse response = newUseCase().execute(meetingId, hostId);

		assertThat(response.status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(response.hlsUrl()).isEqualTo("/livestreams/" + meetingId + "/stream.m3u8");
		assertThat(registry.get(meetingId)).isNotNull();
	}

	@Test
	void nonHostCannotStartALivestream() throws Exception {
		UUID meetingId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(UUID.randomUUID())));

		assertThatThrownBy(() -> newUseCase().execute(meetingId, UUID.randomUUID()))
				.isInstanceOf(NotMeetingHostException.class);
	}

	@Test
	void cannotStartALivestreamTwice() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(hostId)));
		StartLivestreamUseCase useCase = newUseCase();
		useCase.execute(meetingId, hostId);

		assertThatThrownBy(() -> useCase.execute(meetingId, hostId))
				.isInstanceOf(LivestreamAlreadyLiveException.class);
	}

	@Test
	void cannotStartALivestreamForAnEndedMeeting() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		Meeting meeting = meetingHostedBy(hostId);
		meeting.end();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

		assertThatThrownBy(() -> newUseCase().execute(meetingId, hostId))
				.isInstanceOf(MeetingEndedException.class);
	}

	@Test
	void meetingNotFoundIsA404() {
		UUID meetingId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> newUseCase().execute(meetingId, UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
