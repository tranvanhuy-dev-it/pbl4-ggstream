package com.project.meet.livestream.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Public (no auth) — a livestream viewer isn't a meeting participant, see
 * docs/api/rest.md. Deliberately returns only {status, hlsUrl}, never the
 * meeting's title/host — an anonymous viewer resolving a code shouldn't
 * learn anything about the meeting beyond "is it live and where".
 */
@Component
public class GetLivestreamStatusUseCase {

	private final MeetingRepository meetingRepository;
	private final LivestreamRegistry registry;

	public GetLivestreamStatusUseCase(MeetingRepository meetingRepository, LivestreamRegistry registry) {
		this.meetingRepository = meetingRepository;
		this.registry = registry;
	}

	public LivestreamStatusResponse execute(UUID meetingId) {
		meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		return statusFor(meetingId);
	}

	/** Used by the public /watch/{code} viewer page, which only ever knows the join code, not the meeting's UUID. */
	public LivestreamStatusResponse executeByCode(String code) {
		Meeting meeting = meetingRepository.findByCode(code)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		return statusFor(meeting.getId());
	}

	private LivestreamStatusResponse statusFor(UUID meetingId) {
		LivestreamSession session = registry.get(meetingId);
		return session != null ? LivestreamStatusResponse.live(session.getHlsUrl()) : LivestreamStatusResponse.ended();
	}
}
