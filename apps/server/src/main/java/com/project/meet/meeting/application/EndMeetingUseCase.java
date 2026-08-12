package com.project.meet.meeting.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.participant.domain.MeetingParticipant;
import com.project.meet.participant.infrastructure.MeetingParticipantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class EndMeetingUseCase {

	private final MeetingRepository meetingRepository;
	private final MeetingParticipantRepository participantRepository;

	public EndMeetingUseCase(MeetingRepository meetingRepository, MeetingParticipantRepository participantRepository) {
		this.meetingRepository = meetingRepository;
		this.participantRepository = participantRepository;
	}

	/** Idempotent for an already-ended meeting — repeated end calls (e.g. double-click) just return the current state. */
	@Transactional
	public MeetingResponse execute(UUID meetingId, UUID requesterUserId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Meeting not found"));

		if (!meeting.isHostedBy(requesterUserId)) {
			throw new NotMeetingHostException();
		}

		if (!meeting.isEnded()) {
			meeting.end();
			for (MeetingParticipant participant : participantRepository.findByMeetingIdAndLeftAtIsNull(meetingId)) {
				participant.leave();
			}
		}

		return MeetingResponse.from(meeting);
	}
}
