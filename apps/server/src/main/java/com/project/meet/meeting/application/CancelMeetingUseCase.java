package com.project.meet.meeting.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class CancelMeetingUseCase {
	private final MeetingRepository repository;
	public CancelMeetingUseCase(MeetingRepository repository) { this.repository = repository; }

	@Transactional
	public MeetingResponse execute(UUID meetingId, UUID userId) {
		Meeting meeting = repository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		if (!meeting.isHostedBy(userId)) throw new NotMeetingHostException();
		meeting.cancel();
		return MeetingResponse.from(meeting);
	}
}
