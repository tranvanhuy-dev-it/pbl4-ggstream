package com.project.meet.livestream.application;

import com.project.meet.livestream.domain.LivestreamNotLiveException;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StopLivestreamUseCaseTest {

	@Mock
	private MeetingRepository meetingRepository;

	private final LivestreamRegistry registry = new LivestreamRegistry();

	private StopLivestreamUseCase newUseCase() {
		return new StopLivestreamUseCase(meetingRepository, registry);
	}

	private Meeting meetingHostedBy(UUID hostId) throws Exception {
		User host = new User("host@example.com", "hash", "Host");
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(host, hostId);
		return new Meeting("abc-defg-hij", "Đại hội đồng", host, MeetingAccessType.PUBLIC);
	}

	@Test
	void hostCanStopAnActiveLivestream() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(hostId)));
		FakeLivestreamProcessLauncher launcher = new FakeLivestreamProcessLauncher();
		LivestreamProcessHandle handle = launcher.launch(meetingId, null);
		registry.put(meetingId, new LivestreamSession(meetingId, hostId, "/livestreams/x/stream.m3u8", handle));

		newUseCase().execute(meetingId, hostId);

		assertThat(registry.get(meetingId)).isNull();
		assertThat(launcher.wasStopped(meetingId)).isTrue();
	}

	@Test
	void nonHostCannotStopALivestream() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(hostId)));

		assertThatThrownBy(() -> newUseCase().execute(meetingId, UUID.randomUUID()))
				.isInstanceOf(NotMeetingHostException.class);
	}

	@Test
	void stoppingWhenNothingIsLiveIs404() throws Exception {
		UUID meetingId = UUID.randomUUID();
		UUID hostId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meetingHostedBy(hostId)));

		assertThatThrownBy(() -> newUseCase().execute(meetingId, hostId))
				.isInstanceOf(LivestreamNotLiveException.class);
	}
}
