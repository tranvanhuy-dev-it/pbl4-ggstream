package com.project.meet.livestream.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.livestream.domain.LivestreamNotLiveException;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StopLivestreamUseCase {

	private final MeetingRepository meetingRepository;
	private final LivestreamRegistry registry;

	public StopLivestreamUseCase(MeetingRepository meetingRepository, LivestreamRegistry registry) {
		this.meetingRepository = meetingRepository;
		this.registry = registry;
	}

	public void execute(UUID meetingId, UUID requesterUserId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		if (!meeting.isHostedBy(requesterUserId)) {
			throw new NotMeetingHostException();
		}

		LivestreamSession session = registry.remove(meetingId);
		if (session == null) {
			throw new LivestreamNotLiveException();
		}
		session.stop();
	}
}
