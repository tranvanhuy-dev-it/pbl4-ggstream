package com.project.meet.sfu.application;

import com.project.meet.common.config.LiveKitProperties;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.sfu.api.SfuTokenResponse;
import com.project.meet.sfu.domain.MediaParticipantConfig;
import com.project.meet.sfu.domain.MediaServer;
import com.project.meet.sfu.domain.NotAnSfuMeetingException;
import com.project.meet.sfu.domain.SfuNotConfiguredException;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueSfuTokenUseCaseTest {

	@Mock
	private MeetingRepository meetingRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private MediaServer mediaServer;

	private static final LiveKitProperties CONFIGURED =
			new LiveKitProperties("ws://localhost:7880", "test-key", "test-secret-not-for-production");

	@Test
	void rejectsAMeshModeMeeting() {
		IssueSfuTokenUseCase useCase = new IssueSfuTokenUseCase(meetingRepository, userRepository, mediaServer, CONFIGURED);
		UUID meetingId = UUID.randomUUID();
		User host = new User("host@example.com", "hash", "Host");
		Meeting meshMeeting = new Meeting("abc-defg-hij", "Standup", host, MeetingAccessType.PUBLIC);
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meshMeeting));

		assertThatThrownBy(() -> useCase.execute(meetingId, UUID.randomUUID()))
				.isInstanceOf(NotAnSfuMeetingException.class);
	}

	@Test
	void rejectsWhenLiveKitIsNotConfigured() {
		IssueSfuTokenUseCase useCase = new IssueSfuTokenUseCase(
				meetingRepository, userRepository, mediaServer, new LiveKitProperties(null, null, null));

		assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(SfuNotConfiguredException.class);
	}

	@Test
	void returnsTheTokenAndUrlForAnSfuModeMeeting() {
		IssueSfuTokenUseCase useCase = new IssueSfuTokenUseCase(meetingRepository, userRepository, mediaServer, CONFIGURED);
		UUID meetingId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		User host = new User("host@example.com", "hash", "Host");
		Meeting sfuMeeting = new Meeting("abc-defg-hij", "Big all-hands", host, MeetingAccessType.PUBLIC,
				null, null, null, MediaMode.SFU);
		User participant = new User("guest@example.com", "hash", "Guest");

		when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(sfuMeeting));
		when(userRepository.findById(userId)).thenReturn(Optional.of(participant));
		when(mediaServer.createParticipant(meetingId, userId, "Guest"))
				.thenReturn(new MediaParticipantConfig("ws://localhost:7880", "signed.jwt.token"));

		SfuTokenResponse response = useCase.execute(meetingId, userId);

		assertThat(response.url()).isEqualTo("ws://localhost:7880");
		assertThat(response.token()).isEqualTo("signed.jwt.token");
	}

	@Test
	void meetingNotFoundIsA404() {
		IssueSfuTokenUseCase useCase = new IssueSfuTokenUseCase(meetingRepository, userRepository, mediaServer, CONFIGURED);
		UUID meetingId = UUID.randomUUID();
		when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.execute(meetingId, UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
