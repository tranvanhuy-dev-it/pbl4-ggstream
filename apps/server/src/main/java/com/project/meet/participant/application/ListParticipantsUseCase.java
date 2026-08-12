package com.project.meet.participant.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.participant.api.ParticipantResponse;
import com.project.meet.participant.infrastructure.MeetingParticipantRepository;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListParticipantsUseCase {

	private final MeetingRepository meetingRepository;
	private final MeetingParticipantRepository participantRepository;

	public ListParticipantsUseCase(MeetingRepository meetingRepository, MeetingParticipantRepository participantRepository) {
		this.meetingRepository = meetingRepository;
		this.participantRepository = participantRepository;
	}

	@Transactional(readOnly = true)
	public List<ParticipantResponse> activeParticipants(UUID meetingId) {
		if (!meetingRepository.existsById(meetingId)) {
			throw new ResourceNotFoundException("MEETING_NOT_FOUND", "Meeting not found");
		}
		return participantRepository.findByMeetingIdAndLeftAtIsNull(meetingId).stream()
				.map(ParticipantResponse::from)
				.toList();
	}
}
