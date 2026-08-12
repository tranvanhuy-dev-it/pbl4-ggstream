package com.project.meet.sfu.application;

import com.project.meet.common.config.LiveKitProperties;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.sfu.api.SfuTokenResponse;
import com.project.meet.sfu.domain.MediaServer;
import com.project.meet.sfu.domain.NotAnSfuMeetingException;
import com.project.meet.sfu.domain.SfuNotConfiguredException;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SFU equivalent of {@code GetIceServersUseCase}/{@code TurnCredentialService}:
 * issues a short-lived, per-user credential (here, a signed LiveKit access
 * token) rather than exposing the shared secret. Pure JWT signing under the
 * hood ({@link MediaServer#createParticipant}) — no network call, so this
 * use case doesn't depend on the LiveKit server actually being reachable.
 */
@Component
public class IssueSfuTokenUseCase {

	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final MediaServer mediaServer;
	private final LiveKitProperties liveKitProperties;

	public IssueSfuTokenUseCase(
			MeetingRepository meetingRepository,
			UserRepository userRepository,
			MediaServer mediaServer,
			LiveKitProperties liveKitProperties
	) {
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.mediaServer = mediaServer;
		this.liveKitProperties = liveKitProperties;
	}

	public SfuTokenResponse execute(UUID meetingId, UUID userId) {
		if (!liveKitProperties.isConfigured()) {
			throw new SfuNotConfiguredException();
		}

		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		if (meeting.getMediaMode() != MediaMode.SFU) {
			throw new NotAnSfuMeetingException();
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không còn tồn tại"));

		var config = mediaServer.createParticipant(meetingId, userId, user.getDisplayName());
		return new SfuTokenResponse(config.url(), config.token());
	}
}
