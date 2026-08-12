package com.project.meet.meeting.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetMeetingUseCase {

	private final MeetingRepository meetingRepository;

	public GetMeetingUseCase(MeetingRepository meetingRepository) {
		this.meetingRepository = meetingRepository;
	}

	@Transactional(readOnly = true)
	public MeetingResponse byId(UUID meetingId) {
		return MeetingResponse.from(findMeeting(meetingId));
	}

	@Transactional(readOnly = true)
	public MeetingResponse byCode(String code) {
		Meeting meeting = meetingRepository.findByCode(code)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "No meeting found for code '" + code + "'"));
		return MeetingResponse.from(meeting);
	}

	private Meeting findMeeting(UUID meetingId) {
		return meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Meeting not found"));
	}
}
