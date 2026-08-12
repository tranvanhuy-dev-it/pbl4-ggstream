package com.project.meet.participant.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.participant.domain.MeetingParticipant;
import com.project.meet.participant.infrastructure.MeetingParticipantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class LeaveMeetingUseCase {

	private final MeetingParticipantRepository participantRepository;

	public LeaveMeetingUseCase(MeetingParticipantRepository participantRepository) {
		this.participantRepository = participantRepository;
	}

	@Transactional
	public void execute(UUID meetingId, UUID userId) {
		MeetingParticipant participant = participantRepository
				.findFirstByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("NOT_IN_MEETING", "Bạn hiện không có mặt trong cuộc họp này"));

		participant.leave();
	}
}
