package com.project.meet.participant.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingEndedException;
import com.project.meet.meeting.domain.MeetingNotStartedException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.participant.api.ParticipantResponse;
import com.project.meet.participant.domain.MeetingParticipant;
import com.project.meet.participant.domain.ParticipantRole;
import com.project.meet.participant.infrastructure.MeetingParticipantRepository;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class JoinMeetingUseCase {

	private final MeetingRepository meetingRepository;
	private final MeetingParticipantRepository participantRepository;
	private final UserRepository userRepository;

	public JoinMeetingUseCase(
			MeetingRepository meetingRepository,
			MeetingParticipantRepository participantRepository,
			UserRepository userRepository
	) {
		this.meetingRepository = meetingRepository;
		this.participantRepository = participantRepository;
		this.userRepository = userRepository;
	}

	/**
	 * Idempotent: re-joining while already an active participant (e.g. a
	 * page refresh) returns the existing participant row rather than
	 * creating a duplicate.
	 */
	@Transactional
	public ParticipantResponse execute(UUID meetingId, UUID userId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));

		if (meeting.isEnded()) {
			throw new MeetingEndedException();
		}
		if (meeting.isScheduledAndNotStarted()) {
			throw new MeetingNotStartedException();
		}

		MeetingParticipant existing = participantRepository
				.findFirstByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
				.orElse(null);
		if (existing != null) {
			return ParticipantResponse.from(existing);
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không còn tồn tại"));

		ParticipantRole role = meeting.isHostedBy(userId) ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;
		MeetingParticipant participant = new MeetingParticipant(meeting, user, user.getDisplayName(), role);
		participantRepository.save(participant);

		meeting.markActive();

		return ParticipantResponse.from(participant);
	}
}
